package meugeninua.screenrecording;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.EditText;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.window.layout.WindowMetricsCalculator;

import java.util.Map;

import meugeninua.screenrecording.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String EXTRA_SECONDS = "seconds";

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ScreenRecorderService.Result result = new ScreenRecorderService.Result(intent);
            onGotRecordedPath(result.getPath());
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            screenRecorderInterface = IScreenRecorderInterface.Stub.asInterface(service);
            reloadState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            screenRecorderInterface = null;
        }
    };

    private IScreenRecorderInterface screenRecorderInterface;
    private ActivityResultLauncher<Intent> activityResultLauncher;
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private ActivityMainBinding binding;

    private int seconds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        startService(ScreenRecorderService.buildIntent(this));

        LocalBroadcastManager.getInstance(this).registerReceiver(
            resultReceiver, ScreenRecorderService.Result.buildIntentFilter()
        );

        if (savedInstanceState != null) {
            seconds = savedInstanceState.getInt(EXTRA_SECONDS);
        }
        activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onContinueRecording
        );
        requestPermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionsRequested
        );

        binding.startButton.setOnClickListener(
            v -> onStartRecording()
        );
        binding.stopButton.setOnClickListener(
            v -> onStopRecording()
        );
        binding.flushButton.setOnClickListener(
            v -> onFlushRecordedVideo()
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(
            ScreenRecorderService.buildIntent(this),
            connection,
            0
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resultReceiver);
    }

    private void reloadState() {
        try {
            ScreenRecorderState state = screenRecorderInterface.currentState();
            setupButtonEnabled(state);
        } catch (Throwable e) {
            Log.e(getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private void setupButtonEnabled(ScreenRecorderState state) {
        binding.startButton.setEnabled(state.isCanStart());
        binding.stopButton.setEnabled(state.isCanStop());
        binding.flushButton.setEnabled(state.isCanFlush());
    }

    private void onGotRecordedPath(String path) {
        reloadState();
        StoreToGalleryUtil.INSTANCE.storeToGallery(this, path);
        binding.videoView.setVideoPath(path);
        binding.videoView.start();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_SECONDS, seconds);
    }

    private void onPermissionsRequested(Map<String, Boolean> results) {
        for (Boolean item : results.values()) {
            if (!Boolean.TRUE.equals(item)) return;
        }

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        activityResultLauncher.launch(manager.createScreenCaptureIntent());
    }

    private void onContinueRecording(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) return;
        try {
            WindowMetricsCalculator calculator = WindowMetricsCalculator.getOrCreate();
            Rect rect = calculator.computeMaximumWindowMetrics(this).getBounds();
            ScreenRecorderState state = screenRecorderInterface.start(
                new ScreenRecorderParams(seconds, result, rect)
            );
            setupButtonEnabled(state);
        } catch (Throwable e) {
            Log.e(getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private void onStartRecording() {
        binding.tilSeconds.setError(null);
        try {
            EditText editText = findViewById(R.id.etSeconds);
            seconds = Integer.parseInt(editText.getText().toString());
        } catch (Throwable e) {
            binding.tilSeconds.setError(e.getMessage());
            return;
        }

        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions = new String[] {Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        } else {
            permissions = new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }
        requestPermissionsLauncher.launch(permissions);
    }

    private void onStopRecording() {
        try {
            ScreenRecorderState state = screenRecorderInterface.stop();
            setupButtonEnabled(state);
        } catch (Throwable e) {
            Log.e(getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private void onFlushRecordedVideo() {
        try {
            ScreenRecorderState state = screenRecorderInterface.flush();
            setupButtonEnabled(state);
        } catch (Throwable e) {
            Log.e(getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}