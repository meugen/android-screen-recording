package meugeninua.screenrecording;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MediaSyncEvent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ScreenRecorder {

    private static final int SAMPLING_RATE_IN_HZ = 44100;

    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;

    /**
     * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
     * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
     * size is determined by {@link AudioRecord#getMinBufferSize(int, int, int)} and depends on the
     * recording settings.
     */
    private static final int BUFFER_SIZE_FACTOR = 2;

    /**
     * Size of the buffer where the audio data is stored by Android
     */
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;

    public static final ScreenRecorder INSTANCE = new ScreenRecorder();
    public static final String TAG = ScreenRecorder.class.getSimpleName();
    private static final String MIME_TYPE = "video/avc";

    private CyclicVideoBuffer buffer;
    private MediaFormat mediaFormat;

    private MediaProjectionManager manager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private MediaCodec mediaCodec;

    private Thread audioThread;
    private ByteArrayOutputStream audioStream;

    public void setSeconds(int seconds) {
        this.buffer = new CyclicVideoBuffer(seconds);
    }

    public void setManager(MediaProjectionManager manager) {
        this.manager = manager;
    }

//    private String findEncoderForFormat(MediaCodecList codecList) {
//        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
//            try {
//                MediaCodecInfo.CodecCapabilities capabilities =
//                    codecInfo.getCapabilitiesForType(MIME_TYPE);
//                if (capabilities != null) {
//                    return codecInfo.getName();
//                }
//            } catch (Exception e) {}
//        }
//        return null;
//    }

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
        mediaFormat = buildMediaFormat(width, height);

        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        /*for (MediaCodecInfo info : codecList.getCodecInfos()) {
            Log.d(TAG, "[LIST] codec name = " + info.getName());
            Log.d(TAG, "supported types = " + Arrays.toString(info.getSupportedTypes()));
            Log.d(TAG, "codec canonical name = " + info.getCanonicalName());
        }*/
        String codecName = codecList.findEncoderForFormat(mediaFormat);
        /*if (codecName == null) {
            width /= 2;
            height /= 2;
            mediaFormat = buildMediaFormat(width, height);
            codecName = codecList.findEncoderForFormat(mediaFormat);
        }*/
        Log.d(TAG, "[RESULT] codec name = " + codecName);
        mediaCodec = MediaCodec.createByCodecName(codecName);
        setupMediaCodecCallback(
            mediaCodec,
            new MediaCodecCallback(mediaCodec, buffer),
            handler
        );
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = mediaCodec.createInputSurface();
        mediaCodec.start();

        virtualDisplay = projection.createVirtualDisplay(
            "Record", width, height,
            metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, handler
        );

        AudioPlaybackCaptureConfiguration configuration = new AudioPlaybackCaptureConfiguration.Builder(projection)
            .build();
        AudioFormat audioFormat = new AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(SAMPLING_RATE_IN_HZ)
            .setChannelMask(CHANNEL_CONFIG)
            .build();
        AudioRecord audioRecord = new AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(configuration)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(BUFFER_SIZE)
            .build();
        audioStream = new ByteArrayOutputStream();
        audioThread = new Thread(new AudioRunnable(audioRecord, audioStream));
        audioThread.start();
    }

    private void setupMediaCodecCallback(
        MediaCodec codec, MediaCodec.Callback callback, Handler handler
    ) {
        codec.setCallback(callback, handler);
    }

    private MediaFormat buildMediaFormat(int width, int height) {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
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
        if (audioThread != null) {
            audioThread.interrupt();
        }
        audioThread = null;
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
    }

    public void flashTo(String filePath) throws IOException {
        Log.d(TAG, "audio stream length = " + audioStream.size());

        MediaMuxer muxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        muxer.addTrack(mediaFormat);
        muxer.start();
        buffer.cloneState().writeTo(muxer, 0);
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
        private final OutputStream outputStream;

        public AudioRunnable(AudioRecord audioRecord, OutputStream outputStream) {
            this.audioRecord = audioRecord;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {
            audioRecord.startRecording();

            try {
                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                while (!Thread.interrupted()) {
                    int count = audioRecord.read(buffer, BUFFER_SIZE);
                    if (count < 0) {
                        break;
                    }
                    outputStream.write(buffer.array(), 0, count);
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
