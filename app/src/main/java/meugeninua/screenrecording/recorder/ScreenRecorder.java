package meugeninua.screenrecording.recorder;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import meugeninua.screenrecording.recorder.audio.AudioRecordConfig;
import meugeninua.screenrecording.recorder.audio.Source;
import meugeninua.screenrecording.utils.CyclicAudioBuffer;
import meugeninua.screenrecording.utils.CyclicVideoBuffer;

public class ScreenRecorder {

    public static final ScreenRecorder INSTANCE = new ScreenRecorder();
    public static final String TAG = ScreenRecorder.class.getSimpleName();
    private static final String MIME_TYPE = "video/avc";
    private static final int SAMPLING_RATE_IN_HZ = 44100;

    private static final Object SYNC = new Object();

    private CyclicVideoBuffer videoBuffer;
    private CyclicAudioBuffer audioBuffer;
    private MediaFormat videoFormat;

    private MediaProjectionManager manager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private MediaCodec mediaCodec;

    private Thread audioThread;
    private Source audioSource;

    public void setSeconds(int seconds) {
        this.videoBuffer = new CyclicVideoBuffer(seconds);
        this.audioBuffer = new CyclicAudioBuffer(seconds);
    }

    public void setManager(MediaProjectionManager manager) {
        this.manager = manager;
    }

    public void continueRecording(
        ActivityResult result, Rect rect, Configuration configuration, Handler handler
    ) throws IOException {
        this.projection = manager.getMediaProjection(
            result.getResultCode(), result.getData()
        );
        List<CodecInfo> codecInfos = findCodecInfo(rect);
        Log.d(TAG, "Found codec infos: " + codecInfos);
        CodecInfo selectedCodecInfo = codecInfos.get(0);
        Log.d(TAG, "Selected codec info: " + selectedCodecInfo);
        videoFormat = buildMediaFormat(selectedCodecInfo.width, selectedCodecInfo.height);

        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String codecName = codecList.findEncoderForFormat(videoFormat);
        Log.d(TAG, "[RESULT] codec name = " + codecName);
        mediaCodec = MediaCodec.createByCodecName(codecName);
        mediaCodec.setCallback(new MediaCodecCallback(mediaCodec, videoBuffer), handler);
        mediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = mediaCodec.createInputSurface();
        mediaCodec.start();

        virtualDisplay = projection.createVirtualDisplay(
            "Record", selectedCodecInfo.width, selectedCodecInfo.height,
            configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, handler
        );

        AudioPlaybackCaptureConfiguration captureConfiguration =
            new AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build();
        AudioRecordConfig audioRecordConfig = new AudioRecordConfig(
            AudioFormat.CHANNEL_IN_MONO,
            SAMPLING_RATE_IN_HZ,
            AudioFormat.ENCODING_PCM_16BIT
        );
        this.audioSource = new Source(audioRecordConfig, captureConfiguration);

        this.audioThread = new Thread(new AudioRunnable(audioSource, audioBuffer));
        this.audioThread.start();
    }

    private List<CodecInfo> findCodecInfo(Rect rect) {
        Log.d(TAG, String.format("Original size = %dx%d", rect.width(), rect.height()));

        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
//        String defName = codecList.findEncoderForFormat(buildMediaFormat(width, height));
        List<CodecInfo> result = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (!codecInfo.isEncoder()) continue;
            MediaCodecInfo.CodecCapabilities capabilities;
            try {
                capabilities = codecInfo.getCapabilitiesForType(MIME_TYPE);
                MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities
                    .getVideoCapabilities();
                if (videoCapabilities == null) continue;
                int newWidth = rect.width();
                int newHeight = rect.height();
                int sliceHeight = 1;
                while (!videoCapabilities.getSupportedHeights().contains(newHeight) || !videoCapabilities.getSupportedWidths().contains(newWidth)) {
                    sliceHeight *= 2;
                    newWidth /= 2;
                    newHeight /= 2;
                }
                newWidth = makeEvenValue(newWidth);
                newHeight = makeEvenValue(newHeight);
                CodecInfo info = new CodecInfo(codecInfo.getName(),
                    capabilities.isFormatSupported(buildMediaFormat(newWidth, newHeight)),
                    newWidth, newHeight, sliceHeight
                );
                if (info.formatSupported) {
                    result.add(info);
                }
                result.add(info);
            } catch (Exception e) { }
        }
        return result;
    }

    private int makeEvenValue(int value) {
        return value % 2 == 0 ? value : value - 1;
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

    public void flashTo(String videoPath, String audioPath) throws IOException {
        CyclicVideoBuffer.State videoState = videoBuffer.cloneState();
        CyclicAudioBuffer.State audioState = audioBuffer.cloneState();

        MediaMuxer muxer = new MediaMuxer(videoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int videoIndex = muxer.addTrack(videoFormat);
        muxer.start();
        videoState.writeTo(muxer, videoIndex);
        muxer.stop();
        muxer.release();

        audioState.writeToWav(audioSource, audioPath);
    }

    private static class CodecInfo {
        final String name;
        final boolean formatSupported;
        final int width;
        final int height;
        final int sliceHeight;

        public CodecInfo(String name, boolean formatSupported, int width, int height, int sliceHeight) {
            this.name = name;
            this.formatSupported = formatSupported;
            this.width = width;
            this.height = height;
            this.sliceHeight = sliceHeight;
        }

        @Override
        public String toString() {
            return "CodecInfo{" +
                "name='" + name + '\'' +
                ", formatSupported=" + formatSupported +
                ", width=" + width +
                ", height=" + height +
                ", sliceHeight=" + sliceHeight +
                '}';
        }
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

        private final Source audioSource;
        private final CyclicAudioBuffer audioBuffer;

        public AudioRunnable(Source audioSource, CyclicAudioBuffer audioBuffer) {
            this.audioSource = audioSource;
            this.audioBuffer = audioBuffer;
        }

        @Override
        public void run() {
            AudioRecord audioRecord = audioSource.audioRecord();
            audioRecord.startRecording();

            try {
                audioBuffer.start();
                byte[] bytes = new byte[audioSource.bufferSizeInBytes()];
                while (!Thread.interrupted()) {
                    int count = audioRecord.read(bytes, 0, audioSource.bufferSizeInBytes());
                    Log.d(TAG, String.format("Read %d bytes", count));
                    // Log.d(TAG, Arrays.toString(bytes));
                    if (count < 0) {
                        break;
                    }
                    audioBuffer.addBuffer(bytes, count);
                }
            } finally {
                audioRecord.stop();
                audioRecord.release();
            }
        }
    }
}
