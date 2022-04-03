package meugeninua.screenrecording.recorder;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import meugeninua.screenrecording.app.ContextSingleton;
import meugeninua.screenrecording.recorder.audio.AudioRecordConfig;
import meugeninua.screenrecording.recorder.audio.Source;
import meugeninua.screenrecording.utils.CyclicVideoBuffer;

public class ScreenRecorder {

    public static final ScreenRecorder INSTANCE = new ScreenRecorder();
    public static final String TAG = ScreenRecorder.class.getSimpleName();
    private static final String MIME_TYPE = "video/avc";
    private static final int SAMPLING_RATE_IN_HZ = 44100;

    private static final Object SYNC = new Object();

    private CyclicVideoBuffer videoBuffer;
    private MediaFormat videoFormat;

    private MediaProjectionManager manager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private MediaCodec mediaCodec;

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
        mediaCodec.setCallback(new MediaCodecCallback(mediaCodec, videoBuffer), handler);
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
        AudioRecordConfig audioRecordConfig = new AudioRecordConfig(
            AudioFormat.CHANNEL_IN_MONO,
            SAMPLING_RATE_IN_HZ,
            AudioFormat.ENCODING_PCM_16BIT
        );

        this.audioThread = new Thread(new AudioRunnable(audioRecordConfig, configuration));
        this.audioThread.start();
    }

    private MediaFormat buildMediaFormat(int width, int height) {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
//        mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT);
//        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
//        mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLING_RATE_IN_HZ);
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
        synchronized (SYNC) {
            if (projection != null) {
                projection.stop();
            }
            projection = null;
            if (audioThread != null) {
                audioThread.interrupt();
            }
            audioThread = null;
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
            synchronized (SYNC) {
                Log.d(TAG, "Output buffer available, index = " + index + ", info = " + info);

                try {
                    ByteBuffer encodedData = mediaCodec.getOutputBuffer(index);
                    encodedData.position(info.offset);
                    encodedData.limit(info.offset + info.size);

                    buffer.add(encodedData, info);
                    mediaCodec.releaseOutputBuffer(index, false);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
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

        private final AudioRecordConfig audioRecordConfig;
        private final AudioPlaybackCaptureConfiguration configuration;

        public AudioRunnable(
            AudioRecordConfig audioRecordConfig,
            AudioPlaybackCaptureConfiguration configuration
        ) {
            this.audioRecordConfig = audioRecordConfig;
            this.configuration = configuration;
        }

        @Override
        public void run() {
            Source source = new Source(audioRecordConfig, configuration);
            source.audioRecord().startRecording();

            String fileName = ContextSingleton.getOutputFileName("Media", ".pcm");
            try(FileOutputStream stream = new FileOutputStream(fileName)) {
                byte[] bytes = new byte[source.bufferSizeInBytes()];
                while (!Thread.interrupted()) {
                    int count = source.audioRecord().read(bytes, 0, source.bufferSizeInBytes());
                    Log.d(TAG, String.format("Read %d bytes", count));
                    if (count < 0) {
                        break;
                    }
                    stream.write(bytes, 0, count);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            } finally {
                source.audioRecord().stop();
                source.audioRecord().release();
            }
            Log.d(TAG, "Wav file wrote to " + fileName);
        }
    }
}
