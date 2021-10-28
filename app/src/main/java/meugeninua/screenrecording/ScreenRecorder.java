package meugeninua.screenrecording;

import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
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

    private int seconds;
    private MediaProjectionManager manager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private Surface surface;

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = seconds;
    }

    public void setManager(MediaProjectionManager manager) {
        this.manager = manager;
    }

    public void continueRecording(ActivityResult result, Display display) throws IOException {
        this.projection = manager.getMediaProjection(
            result.getResultCode(), result.getData()
        );
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        MediaFormat mediaFormat = buildMediaFormat(metrics.widthPixels, metrics.heightPixels);

        MediaCodec mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.setCallback(new MediaCodec.Callback() {
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

                byte[] data = new byte[encodedData.remaining()];
                encodedData.get(data);

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
        });
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = mediaCodec.createInputSurface();
        mediaCodec.start();

        virtualDisplay = projection.createVirtualDisplay(
            "Record", metrics.widthPixels, metrics.heightPixels,
            metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        );
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
        if (projection != null) {
            projection.stop();
        }
        projection = null;
        if (surface != null) {
            surface.release();
        }
        surface = null;
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        virtualDisplay = null;
        manager = null;
    }
}
