package meugeninua.screenrecording;

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
import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class ScreenRecorder {

    public static final ScreenRecorder INSTANCE = new ScreenRecorder();
    public static final String TAG = ScreenRecorder.class.getSimpleName();
    private static final String MIME_TYPE = "video/avc";
    private static final int SAMPLING_RATE_IN_HZ = 44100;

    private CyclicVideoBuffer videoBuffer;
    private CyclicVideoBuffer audioBuffer;
    private MediaFormat videoFormat;

    private MediaProjectionManager manager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private MediaCodec mediaCodec;

    private Thread audioThread;

    public void setSeconds(int seconds) {
        this.videoBuffer = new CyclicVideoBuffer(seconds);
        this.audioBuffer = new CyclicVideoBuffer(seconds);
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
        setupMediaCodecCallback(
            mediaCodec,
            new MediaCodecCallback(mediaCodec, videoBuffer),
            handler
        );
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
        this.audioThread = new Thread(new AudioRunnable(audioRecord, audioBuffer));
        this.audioThread.start();
//        this.audioRecord.startRecording();
    }

    private void setupMediaCodecCallback(
        MediaCodec codec, MediaCodec.Callback callback, Handler handler
    ) {
        codec.setCallback(callback, handler);
    }

    private MediaFormat buildMediaFormat(int width, int height) {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
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
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
        }
        mediaCodec = null;
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        virtualDisplay = null;
        if (surface != null) {
            surface.release();
        }
        surface = null;
        if (audioThread != null) {
            audioThread.interrupt();
        }
        audioThread = null;
    }

    public void flashTo(String filePath) throws IOException {
        CyclicVideoBuffer.State videoState = videoBuffer.cloneState();
        CyclicVideoBuffer.State audioState = audioBuffer.cloneState();

        MediaMuxer muxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int videoIndex = muxer.addTrack(videoFormat);
        int audioIndex = muxer.addTrack(MediaFormat.createAudioFormat(
            MIME_TYPE, SAMPLING_RATE_IN_HZ, 1
        ));
        muxer.start();
        videoState.writeTo(muxer, videoIndex);
        audioState.writeTo(muxer, audioIndex);
        muxer.stop();
        muxer.release();
    }

    private static class MediaCodecCallback extends MediaCodec.Callback {

        private final MediaCodec mediaCodec;
        private final CyclicVideoBuffer buffer;

        public MediaCodecCallback(MediaCodec mediaCodec, CyclicVideoBuffer buffer) {
            this.mediaCodec = mediaCodec;
            this.buffer = buffer;
        }

        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            Log.d(TAG, "Input buffer available, index = " + index);
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            Log.d(TAG, "Output buffer available, index = " + index + ", info = " + info);

            ByteBuffer encodedData = mediaCodec.getOutputBuffer(index);
            encodedData.position(info.offset);
            encodedData.limit(info.offset + info.size);

            buffer.add(encodedData, info);
            mediaCodec.releaseOutputBuffer(index, false);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            Log.d(TAG, "Output format changed to " + format);
        }
    }

    private static class AudioRunnable implements Runnable {

        private final AudioRecord audioRecord;
        private final CyclicVideoBuffer audioBuffer;

        public AudioRunnable(AudioRecord audioRecord, CyclicVideoBuffer audioBuffer) {
            this.audioRecord = audioRecord;
            this.audioBuffer = audioBuffer;
        }

        @Override
        public void run() {
            audioRecord.startRecording();

            try {
                long audioTimeNano = 0L;

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                AudioTimestamp audioTimestamp = new AudioTimestamp();
                int bufferSize = audioRecord.getBufferSizeInFrames() * audioRecord.getFormat().getFrameSizeInBytes();
                ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
                while (!Thread.interrupted()) {
                    int count = audioRecord.read(buffer, bufferSize);
                    if (count < 0) {
                        break;
                    }
                    audioRecord.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC);
                    long newTimeUs = TimeUnit.NANOSECONDS.toMicros(audioTimestamp.nanoTime - audioTimeNano);
                    audioTimeNano = audioTimestamp.nanoTime;
                    bufferInfo.set(0, count, newTimeUs, 0);
                    audioBuffer.add(buffer, bufferInfo);
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
