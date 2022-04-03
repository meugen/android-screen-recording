package meugeninua.screenrecording.recorder.audio;

import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;

import meugeninua.screenrecording.app.ContextSingleton;

public class Source {

    private static final int BUFFER_SIZE = 1024;

    private final AudioRecord audioRecord;
    private final AudioRecordConfig config;
    private final int bufferSizeInBytes;

    public Source(AudioRecordConfig config, AudioPlaybackCaptureConfiguration configuration) {
        this.config = config;
        this.bufferSizeInBytes = BUFFER_SIZE * config().bytesPerSample();
        this.audioRecord = ContextSingleton.buildAudioRecord(config, configuration, bufferSizeInBytes);
    }

    public AudioRecord audioRecord() {
        return audioRecord;
    }

    public AudioRecordConfig config() {
        return config;
    }

    public int bufferSizeInBytes() {
        return bufferSizeInBytes;
    }

    public int bufferSize() {
        return BUFFER_SIZE;
    }
}
