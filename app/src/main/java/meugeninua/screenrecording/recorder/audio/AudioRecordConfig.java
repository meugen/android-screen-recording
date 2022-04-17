package meugeninua.screenrecording.recorder.audio;

import static android.media.AudioFormat.ENCODING_AAC_ELD;
import static android.media.AudioFormat.ENCODING_AAC_HE_V1;
import static android.media.AudioFormat.ENCODING_AAC_HE_V2;
import static android.media.AudioFormat.ENCODING_AAC_LC;
import static android.media.AudioFormat.ENCODING_AAC_XHE;
import static android.media.AudioFormat.ENCODING_AC3;
import static android.media.AudioFormat.ENCODING_AC4;
import static android.media.AudioFormat.ENCODING_DEFAULT;
import static android.media.AudioFormat.ENCODING_DOLBY_MAT;
import static android.media.AudioFormat.ENCODING_DOLBY_TRUEHD;
import static android.media.AudioFormat.ENCODING_DTS;
import static android.media.AudioFormat.ENCODING_DTS_HD;
import static android.media.AudioFormat.ENCODING_E_AC3;
import static android.media.AudioFormat.ENCODING_E_AC3_JOC;
import static android.media.AudioFormat.ENCODING_IEC61937;
import static android.media.AudioFormat.ENCODING_MP3;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioFormat.ENCODING_PCM_8BIT;
import static android.media.AudioFormat.ENCODING_PCM_FLOAT;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AudioRecordConfig {

    private final int channelPositionMask;
    private final int frequency;
    private final int audioEncoding;

    public AudioRecordConfig(int channelPositionMask, int frequency, @Encoding int audioEncoding) {
        this.channelPositionMask = channelPositionMask;
        this.frequency = frequency;
        this.audioEncoding = audioEncoding;
    }

    public int channelPositionMask() {
        return channelPositionMask;
    }

    /**
     * @return sampleRateInHz
     */
    public int frequency() {
        return frequency;
    }

    @Encoding
    public int audioEncoding() {
        return audioEncoding;
    }

    public int bitsPerSample() {
        return bytesPerSample() * 8;
    }

    public int bytesPerSample() {
        if (audioEncoding == ENCODING_PCM_16BIT) {
            return 2;
        } else if (audioEncoding == ENCODING_PCM_8BIT) {
            return 1;
        } else {
            return 2;
        }
    }

    @IntDef(flag = false, value = {
        ENCODING_DEFAULT,
        ENCODING_PCM_16BIT,
        ENCODING_PCM_8BIT,
        ENCODING_PCM_FLOAT,
        ENCODING_AC3,
        ENCODING_E_AC3,
        ENCODING_DTS,
        ENCODING_DTS_HD,
        ENCODING_MP3,
        ENCODING_AAC_LC,
        ENCODING_AAC_HE_V1,
        ENCODING_AAC_HE_V2,
        ENCODING_IEC61937,
        ENCODING_DOLBY_TRUEHD,
        ENCODING_AAC_ELD,
        ENCODING_AAC_XHE,
        ENCODING_AC4,
        ENCODING_E_AC3_JOC,
        ENCODING_DOLBY_MAT }
    )
    @Retention(RetentionPolicy.SOURCE)
    public @interface Encoding {}
}
