package meugeninua.screenrecording;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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

import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenRecorder {

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

    public void setSeconds(int seconds) {
        this.buffer = new CyclicVideoBuffer(seconds);
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
        CodecInfo codecInfo = findCodecInfo(metrics);
        Log.d(TAG, "Found codec info: " + codecInfo);
        mediaFormat = buildMediaFormat(codecInfo.width, codecInfo.height, codecInfo.sliceHeight);

        mediaCodec = MediaCodec.createByCodecName(codecInfo.name);
        setupMediaCodecCallback(
            mediaCodec,
            new MediaCodecCallback(mediaCodec, buffer),
            handler
        );
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = mediaCodec.createInputSurface();
        mediaCodec.start();

        virtualDisplay = projection.createVirtualDisplay(
            "Record", codecInfo.width, codecInfo.height,
            metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, handler
        );
    }

    private void setupMediaCodecCallback(
        MediaCodec codec, MediaCodec.Callback callback, Handler handler
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            codec.setCallback(callback, handler);
        } else {
            codec.setCallback(callback);
        }
    }

    private CodecInfo findCodecInfo(DisplayMetrics metrics) {
        Log.d(TAG, String.format("Original size = %dx%d", metrics.widthPixels, metrics.heightPixels));

        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
//        String defName = codecList.findEncoderForFormat(buildMediaFormat(width, height));
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (!codecInfo.isEncoder()) continue;
            MediaCodecInfo.CodecCapabilities capabilities;
            try {
                capabilities = codecInfo.getCapabilitiesForType(MIME_TYPE);
                MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities
                    .getVideoCapabilities();
                if (videoCapabilities == null) continue;
                int newWidth = metrics.widthPixels;
                int newHeight = metrics.heightPixels;
                int sliceHeight = 1;
                while (!videoCapabilities.getSupportedHeights().contains(newHeight) || !videoCapabilities.getSupportedWidths().contains(newWidth)) {
                    sliceHeight *= 2;
                    newWidth /= 2;
                    newHeight /= 2;
                }
                return new CodecInfo(codecInfo.getName(), "",
                    makeEvenValue(newWidth),
                    makeEvenValue(newHeight),
                    sliceHeight
                );
            } catch (Exception e) { }
        }
        return null;
    }

    private int makeEvenValue(int value) {
        return value % 2 == 0 ? value : value - 1;
    }

    private MediaFormat buildMediaFormat(int width, int height, int sliceHeight) {
        Log.d(TAG, String.format("Size = %dx%d", width, height));
        // 1768x2082
        // 840x2081

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / 30);
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_SLICE_HEIGHT, sliceHeight);
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
    }

    public void flashTo(String filePath) throws IOException {
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

    private static class CodecInfo {
        final String name;
        final String defName;
        final int width;
        final int height;
        final int sliceHeight;

        public CodecInfo(String name, String defName, int width, int height, int sliceHeight) {
            this.name = name;
            this.defName = defName;
            this.width = width;
            this.height = height;
            this.sliceHeight = sliceHeight;
        }

        @Override
        public String toString() {
            return "CodecInfo{" +
                "name='" + name + '\'' +
                ", defName='" + defName + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", sliceHeight=" + sliceHeight +
                '}';
        }
    }
}
