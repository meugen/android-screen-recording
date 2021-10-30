package meugeninua.screenrecording;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class ScreenRecorderService extends Service {

    private static final String GROUP_ID = "screen_recording";
    private static final String CHANNEL_ID = "screen_recording_notification";

    private static final int SERVICE_ID = 1;

    public static Intent buildIntent(Context context) {
        return new Intent(context, ScreenRecorderService.class);
    }

    private ScreenRecorder screenRecorder;
    private HandlerThread handlerThread;
    private Handler handler;

    private volatile boolean canStart;
    private volatile boolean canStop;
    private volatile boolean canFlush;

    @Override
    public void onCreate() {
        super.onCreate();
        setupCurrentState(true, false, false);
        screenRecorder = ScreenRecorder.INSTANCE;
        handlerThread = new HandlerThread(getClass().getSimpleName());
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private void setupCurrentState(boolean canStart, boolean canStop, boolean canFlush) {
        this.canStart = canStart;
        this.canStop = canStop;
        this.canFlush = canFlush;
    }

    private ScreenRecorderState buildCurrentState() {
        return new ScreenRecorderState(canStart, canStop, canFlush);
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

    private void startForeground() {
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
    }

    private void startRecording(ScreenRecorderParams params) throws RemoteException {
        if (!canStart) {
            throw new RemoteException("Can't process start recording");
        }
        startForeground();

        handler.postDelayed(
            () -> startMediaProjection(params), 100L
        );
        setupCurrentState(false, true, true);
    }

    private void stopRecording() throws RemoteException {
        if (!canStop) {
            throw new RemoteException("Can't process stop recording");
        }
        screenRecorder.stopRecording();
        stopForeground(true);
        setupCurrentState(true, false, true);
    }

    private void flushRecording() throws RemoteException {
        if (!canFlush) {
            throw new RemoteException("Can't process flush recorded video");
        }
        handler.post(this::flushRecordingAsync);
    }

    private void flushRecordingAsync() {
        File file = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        file = new File(file, UUID.randomUUID().toString());
        String filePath = file.getPath();

        try {
            screenRecorder.flashTo(filePath);
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Result(filePath).buildIntent());
            Log.d(ScreenRecorder.TAG, "Recorded to path: " + filePath);
        } catch (IOException e) {
            Log.e(ScreenRecorder.TAG, e.getMessage(), e);
        }
    }

    private void startMediaProjection(ScreenRecorderParams params) {
        screenRecorder.setSeconds(params.getSeconds());
        screenRecorder.setManager((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE));
        try {
            screenRecorder.continueRecording(
                params.getActivityResult(), getDefaultDisplay(), handler
            );
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
        return new ScreenRecorderBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handlerThread.quitSafely();
        handlerThread = null;
    }

    public static class Result {

        private static final String ACTION = ScreenRecorderService.class.getName();
        private static final String EXTRA_PATH = "path";

        public static IntentFilter buildIntentFilter() {
            return new IntentFilter(ACTION);
        }

        private final String path;

        public Result(String path) {
            this.path = path;
        }

        public Result(Intent intent) {
            this(
                intent.getStringExtra(EXTRA_PATH)
            );
        }

        public String getPath() {
            return path;
        }

        public Intent buildIntent() {
            Intent intent = new Intent(ACTION);
            intent.putExtra(EXTRA_PATH, path);
            return intent;
        }
    }

    private class ScreenRecorderBinder extends IScreenRecorderInterface.Stub {

        @Override
        public ScreenRecorderState currentState() throws RemoteException {
            return buildCurrentState();
        }

        @Override
        public ScreenRecorderState start(ScreenRecorderParams params) throws RemoteException {
            startRecording(params);
            return currentState();
        }

        @Override
        public ScreenRecorderState stop() throws RemoteException {
            stopRecording();
            return currentState();
        }

        @Override
        public ScreenRecorderState flush() throws RemoteException {
            flushRecording();
            return currentState();
        }
    }
}
