package meugeninua.screenrecording.recorder;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.activity.result.ActivityResult;

public class ScreenRecorderParams implements Parcelable {

    public static final Creator<ScreenRecorderParams> CREATOR = new ClassLoaderCreator<ScreenRecorderParams>() {
        @Override
        public ScreenRecorderParams createFromParcel(Parcel source) {
            return createFromParcel(source, null);
        }

        @Override
        public ScreenRecorderParams[] newArray(int size) {
            return new ScreenRecorderParams[size];
        }

        @Override
        public ScreenRecorderParams createFromParcel(Parcel source, ClassLoader loader) {
            return new ScreenRecorderParams(
                source.readInt(),
                source.readParcelable(loader)
            );
        }
    };

    private final int seconds;
    private final ActivityResult activityResult;

    public ScreenRecorderParams(int seconds, ActivityResult activityResult) {
        this.seconds = seconds;
        this.activityResult = activityResult;
        if (seconds <= 0) {
            throw new IllegalArgumentException("Not valid value for seconds: " + seconds);
        }
    }

    public int getSeconds() {
        return seconds;
    }

    public ActivityResult getActivityResult() {
        return activityResult;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(seconds);
        dest.writeParcelable(activityResult, flags);
    }
}
