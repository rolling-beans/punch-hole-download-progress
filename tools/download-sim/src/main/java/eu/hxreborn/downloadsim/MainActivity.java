package eu.hxreborn.downloadsim;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1;

    private EditText durationSeconds;
    private EditText notificationInterval;
    private EditText initialProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }

        durationSeconds = findViewById(R.id.duration_seconds);
        notificationInterval = findViewById(R.id.notification_interval);
        initialProgress = findViewById(R.id.initial_progress);
        findViewById(R.id.btn_constant).setOnClickListener(v ->
                startTimed(ProgressCurve.MODE_CONSTANT));
        findViewById(R.id.btn_uneven).setOnClickListener(v ->
                startTimed(ProgressCurve.MODE_UNEVEN));
        findViewById(R.id.btn_stop).setOnClickListener(v -> {
            try {
                startService(new Intent(this, DownloadSimService.class)
                        .setAction(DownloadSimService.ACTION_STOP));
            } catch (RuntimeException ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        EditText interval = findViewById(R.id.interval);
        EditText step = findViewById(R.id.step);
        EditText count = findViewById(R.id.count);

        findViewById(R.id.btn_slow).setOnClickListener(v -> startLegacy(1, 2000L, 1));
        findViewById(R.id.btn_fast).setOnClickListener(v -> startLegacy(6, 500L, 1));
        findViewById(R.id.btn_two).setOnClickListener(v -> startLegacy(2, 1500L, 2));
        findViewById(R.id.btn_custom).setOnClickListener(v ->
                startLegacy(read(step, 2), read(interval, 1500), read(count, 1)));
    }

    private void startTimed(int mode) {
        final long durationMs;
        final long intervalMs;
        final int startPercent;

        try {
            startPercent = Integer.parseInt(initialProgress.getText().toString().trim());
            if (startPercent < 0 || startPercent > 99) {
                throw new NumberFormatException("Out of range");
            }
        } catch (NumberFormatException ex) {
            initialProgress.setError(getString(R.string.invalid_initial_progress));
            return;
        }
        try {
            double seconds = Double.parseDouble(durationSeconds.getText().toString()
                    .trim().replace(',', '.'));
            if (!Double.isFinite(seconds) || seconds < 0.5 || seconds > 3600) {
                throw new NumberFormatException("Duration must be 0.5–3600 seconds");
            }
            durationMs = Math.round(seconds * 1000);
        } catch (NumberFormatException ex) {
            durationSeconds.setError(getString(R.string.invalid_duration));
            return;
        }

        try {
            intervalMs = Long.parseLong(notificationInterval.getText().toString().trim());
            if (intervalMs < 50 || intervalMs > 2000) {
                throw new NumberFormatException("Out of range");
            }
        } catch (NumberFormatException ex) {
            notificationInterval.setError(getString(R.string.invalid_interval));
            return;
        }

        launch(new Intent(this, DownloadSimService.class)
                .setAction(DownloadSimService.ACTION_TIMED_START)
                .putExtra(DownloadSimService.EXTRA_DURATION_MS, durationMs)
                .putExtra(DownloadSimService.EXTRA_UPDATE_MS, intervalMs)
                .putExtra(DownloadSimService.EXTRA_INITIAL_PERCENT, startPercent)
                .putExtra(DownloadSimService.EXTRA_MODE, mode));
    }

    private void startLegacy(int step, long interval, int count) {
        launch(new Intent(this, DownloadSimService.class)
                .putExtra(DownloadSimService.EXTRA_STEP, step)
                .putExtra(DownloadSimService.EXTRA_INTERVAL, interval)
                .putExtra(DownloadSimService.EXTRA_COUNT, count));
    }

    private void launch(Intent intent) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            Toast.makeText(this, R.string.notification_permission_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startForegroundService(intent);
        } catch (RuntimeException ex) {
            Toast.makeText(this, String.valueOf(ex.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private int read(EditText field, int fallback) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
