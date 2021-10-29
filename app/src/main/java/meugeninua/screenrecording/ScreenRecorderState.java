package meugeninua.screenrecording;

import android.os.Parcel;
import android.os.Parcelable;

public class ScreenRecorderState implements Parcelable {

    public static final Creator<ScreenRecorderState> CREATOR = new Creator<ScreenRecorderState>() {
        @Override
        public ScreenRecorderState createFromParcel(Parcel source) {
            boolean[] array = new boolean[3];
            source.readBooleanArray(array);
            return new ScreenRecorderState(
                array[0], array[1], array[2]
            );
        }

        @Override
        public ScreenRecorderState[] newArray(int size) {
            return new ScreenRecorderState[size];
        }
    };

    private final boolean canStart;
    private final boolean canStop;
    private final boolean canFlush;

    public ScreenRecorderState(boolean canStart, boolean canStop, boolean canFlush) {
        this.canStart = canStart;
        this.canStop = canStop;
        this.canFlush = canFlush;
    }

    public boolean isCanStart() {
        return canStart;
    }

    public boolean isCanStop() {
        return canStop;
    }

    public boolean isCanFlush() {
        return canFlush;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBooleanArray(
            new boolean[] { canStart, canStop, canFlush }
        );
    }
}
