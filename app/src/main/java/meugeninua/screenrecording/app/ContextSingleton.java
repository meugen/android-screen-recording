package meugeninua.screenrecording.app;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.UUID;

import meugeninua.screenrecording.recorder.audio.AudioRecordConfig;

public class ContextSingleton extends ContextWrapper {

    private static final String TAG = ContextSingleton.class.getSimpleName();
    private static final ContextSingleton INSTANCE = new ContextSingleton();

    public ContextSingleton() {
        super(null);
    }

    public static void attach(Context context) {
        INSTANCE.attachBaseContext(context);
    }

    public static AudioRecord buildAudioRecord(
        AudioRecordConfig audioRecordConfig,
        AudioPlaybackCaptureConfiguration configuration,
        int bufferSizeInBytes
    ) {
        if (ContextCompat.checkSelfPermission(INSTANCE, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new RuntimeException("Permission to record audio is not granted");
        }
        AudioFormat audioFormat = new AudioFormat.Builder()
            .setChannelMask(audioRecordConfig.channelPositionMask())
            .setSampleRate(audioRecordConfig.frequency())
            .setEncoding(audioRecordConfig.audioEncoding())
            .build();
        return new AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setAudioPlaybackCaptureConfig(configuration)
            .setBufferSizeInBytes(bufferSizeInBytes)
            .build();
    }

    public static String getOutputFileName(String type) {
        return getOutputFileName(type, "");
    }

    public static String getOutputFileName(String type, String ext) {
        File file = INSTANCE.getExternalFilesDir(type);
        if (!file.isDirectory() && !file.mkdirs()) {
            throw new RuntimeException("Error while create directories");
        }
        file = new File(file, UUID.randomUUID().toString() + ext);
        Log.d(TAG, "File name is " + file.getAbsolutePath());
        return file.getAbsolutePath();
    }
}
