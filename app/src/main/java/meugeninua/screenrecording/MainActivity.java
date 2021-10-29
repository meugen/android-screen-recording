package meugeninua.screenrecording;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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

    private ActivityResultLauncher<Intent> activityResultLauncher;
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private ActivityMainBinding binding;

    private int seconds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resultReceiver);
    }

    private void onGotRecordedPath(String path) {
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
        ScreenRecorderService.Params params = new ScreenRecorderService.Params(
            seconds, result
        );
        ContextCompat.startForegroundService(
            this, params.buildIntent(this)
        );
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
        ScreenRecorderService.stopScreenRecording(this);
    }

    private void onFlushRecordedVideo() {

    }
}