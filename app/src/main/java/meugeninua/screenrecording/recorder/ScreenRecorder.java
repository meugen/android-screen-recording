package meugeninua.screenrecording.recorder;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import androidx.activity.result.ActivityResult;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import meugeninua.screenrecording.utils.CyclicVideoBuffer;

public class ScreenRecorder {

    public static final ScreenRecorder INSTANCE = new ScreenRecorder();
    public static final String TAG = ScreenRecorder.class.getSimpleName();
    private static final String MIME_TYPE = "video/avc";
    private static final int SAMPLING_RATE_IN_HZ = 44100;

    private CyclicVideoBuffer videoBuffer;
    private MediaFormat videoFormat;

    private MediaProjectionManager manager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private MediaCodec mediaCodec;

    private Thread outputThread;
    private Thread audioThread;

    public void setSeconds(int seconds) {
        this.videoBuffer = new CyclicVideoBuffer(seconds);
    }

    public void setManager(MediaProjectionManager manager) {
        this.manager = manager;
    }

    public void continueRecording(
        ActivityResult result, Display display, Handler handler
    ) throws IOException {
        this.projection = manager.getMediaProjection(
            result.getResultCode(), result.getData()
        );
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        videoFormat = buildMediaFormat(width, height);

        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String codecName = codecList.findEncoderForFormat(videoFormat);
        Log.d(TAG, "[RESULT] codec name = " + codecName);
        mediaCodec = MediaCodec.createByCodecName(codecName);
        mediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = mediaCodec.createInputSurface();
        mediaCodec.start();

        virtualDisplay = projection.createVirtualDisplay(
            "Record", width, height,
            metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, handler
        );

        AudioPlaybackCaptureConfiguration configuration = new AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build();
        AudioFormat audioFormat = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .setSampleRate(SAMPLING_RATE_IN_HZ)
            .build();
        AudioRecord audioRecord = new AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(configuration)
            .setAudioFormat(audioFormat)
            .build();

        this.outputThread = new Thread(new OutputRunnable(mediaCodec, videoBuffer));
        this.outputThread.start();
        this.audioThread = new Thread(new AudioRunnable(audioRecord, mediaCodec));
        this.audioThread.start();
    }

    private MediaFormat buildMediaFormat(int width, int height) {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT);
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
        mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLING_RATE_IN_HZ);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / 30);
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        return mediaFormat;
    }

    public void stopRecording() {
        if (projection != null) {
            projection.stop();
        }
        projection = null;
        if (audioThread != null) {
            audioThread.interrupt();
        }
        audioThread = null;
        if (outputThread != null) {
            outputThread.interrupt();
        }
        outputThread = null;
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        virtualDisplay = null;
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
        }
        mediaCodec = null;
        if (surface != null) {
            surface.release();
        }
        surface = null;
    }

    public void flashTo(String filePath) throws IOException {
        CyclicVideoBuffer.State state = videoBuffer.cloneState();

        MediaMuxer muxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int videoIndex = muxer.addTrack(videoFormat);
        muxer.start();
        state.writeTo(muxer, videoIndex);
        muxer.stop();
        muxer.release();
    }

    private static class OutputRunnable implements Runnable {

        private final MediaCodec mediaCodec;
        private final CyclicVideoBuffer buffer;

        public OutputRunnable(MediaCodec mediaCodec, CyclicVideoBuffer buffer) {
            this.mediaCodec = mediaCodec;
            this.buffer = buffer;
        }

        @Override
        public void run() {
            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (!Thread.interrupted()) {
                    int index = mediaCodec.dequeueOutputBuffer(info, 1_000);
                    if (index >= 0) {
                        ByteBuffer encodedData = mediaCodec.getOutputBuffer(index);
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);

                        buffer.add(encodedData, info);
                        mediaCodec.releaseOutputBuffer(index, false);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private static class AudioRunnable implements Runnable {

        private final AudioRecord audioRecord;
        private final MediaCodec mediaCodec;

        public AudioRunnable(AudioRecord audioRecord, MediaCodec mediaCodec) {
            this.audioRecord = audioRecord;
            this.mediaCodec = mediaCodec;
        }

        @Override
        public void run() {
            audioRecord.startRecording();

            try {
                AudioTimestamp audioTimestamp = new AudioTimestamp();
                while (!Thread.interrupted()) {
                    int index = mediaCodec.dequeueInputBuffer(1_000);
                    if (index >= 0) {
                        ByteBuffer buffer = mediaCodec.getInputBuffer(index);
                        Log.d("AudioRunnable", "buffer.capacity() = " + buffer.capacity());
                        int count = audioRecord.read(buffer, buffer.capacity());
                        if (count < 0) {
                            break;
                        }
                        audioRecord.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC);
                        long newTimeUs = TimeUnit.NANOSECONDS.toMicros(audioTimestamp.nanoTime);
                        mediaCodec.queueInputBuffer(index, 0, count, newTimeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            } finally {
                audioRecord.stop();
                audioRecord.release();
            }
        }
    }
}
