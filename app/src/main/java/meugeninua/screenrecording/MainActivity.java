package meugeninua.screenrecording;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.EditText;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import meugeninua.screenrecording.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String EXTRA_SECONDS = "seconds";

    private ActivityResultLauncher<Intent> activityResultLauncher;
    private ActivityMainBinding binding;

    private int seconds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            seconds = savedInstanceState.getInt(EXTRA_SECONDS);
        }
        activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onContinueRecording
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_SECONDS, seconds);
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

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        activityResultLauncher.launch(manager.createScreenCaptureIntent());
    }

    private void onStopRecording() {
        ScreenRecorderService.stopScreenRecording(this);
    }

    private void onFlushRecordedVideo() {

    }
}