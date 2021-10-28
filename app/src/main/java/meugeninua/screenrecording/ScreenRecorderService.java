package meugeninua.screenrecording;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import androidx.activity.result.ActivityResult;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;

public class ScreenRecorderService extends Service {

    private static final String GROUP_ID = "screen_recording";
    private static final String CHANNEL_ID = "screen_recording_notification";

    private static final int SERVICE_ID = 1;

    public static void stopScreenRecording(Context context) {
        Intent intent = new Intent(context, ScreenRecorderService.class);
        context.stopService(intent);
    }

    private ScreenRecorder screenRecorder;

    @Override
    public void onCreate() {
        super.onCreate();
        screenRecorder = ScreenRecorder.INSTANCE;
    }

    private void createChannelIfNeeded() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannelGroupCompat group = new NotificationChannelGroupCompat.Builder(GROUP_ID)
            .setName(getText(R.string.app_name))
            .build();
        manager.createNotificationChannelGroup(group);

        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setGroup(GROUP_ID)
            .setName(getText(R.string.app_name))
            .build();
        manager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, 0);

        createChannelIfNeeded();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getText(R.string.app_name))
            .setContentText("The content")
            .setSmallIcon(R.drawable.outline_screenshot_black_18)
            .setContentIntent(pendingIntent)
            .setTicker("The ticker")
            .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(SERVICE_ID, notification);
        }

        new Handler(Looper.getMainLooper()).postDelayed(
            () -> startMediaProjection(intent), 100L
        );

        return START_NOT_STICKY;
    }

    private void startMediaProjection(Intent intent) {
        Params params = new Params(intent);
        screenRecorder.setSeconds(params.getSeconds());
        screenRecorder.setManager((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE));
        try {
            screenRecorder.continueRecording(params.getActivityResult(), getDefaultDisplay());
        } catch (IOException e) {
            Log.e(ScreenRecorder.TAG, e.getMessage(), e);
            stopSelf();
        }
    }

    private Display getDefaultDisplay() {
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        screenRecorder.stopRecording();
    }

    public static class Params {
        private static final String EXTRA_SECONDS = "seconds";
        private static final String EXTRA_ACTIVITY_RESULT = "activity_result";

        private final int seconds;
        private final ActivityResult activityResult;

        public Params(int seconds, ActivityResult activityResult) {
            this.seconds = seconds;
            this.activityResult = activityResult;
            if (seconds <= 0) {
                throw new IllegalArgumentException("Not valid value for seconds: " + seconds);
            }
        }

        public Params(Intent intent) {
            this(
                intent.getIntExtra(EXTRA_SECONDS, -1),
                intent.getParcelableExtra(EXTRA_ACTIVITY_RESULT)
            );
        }

        public int getSeconds() {
            return seconds;
        }

        public ActivityResult getActivityResult() {
            return activityResult;
        }

        public Intent buildIntent(Context context) {
            Intent intent = new Intent(context, ScreenRecorderService.class);
            intent.putExtra(EXTRA_SECONDS, seconds);
            intent.putExtra(EXTRA_ACTIVITY_RESULT, activityResult);
            return intent;
        }
    }
}
