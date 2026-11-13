package eu.hxreborn.downloadsim;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Locale;

public class DownloadSimService extends Service {
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_TIMED_START = "timed_start";
    public static final String EXTRA_STEP = "step";
    public static final String EXTRA_INTERVAL = "interval";
    public static final String EXTRA_COUNT = "count";
    public static final String EXTRA_DURATION_MS = "duration_ms";
    public static final String EXTRA_UPDATE_MS = "update_ms";
    public static final String EXTRA_MODE = "mode";

    private static final int NOTIF_BASE = 4242;
    private static final int MAX_DOWNLOADS = 5;
    private static final int PROGRESS_UNITS = 10000;
    private static final long COMPLETE_HOLD_MS = 2000L;
    private static final String[] FILES = {
            "ubuntu-24.04.2-desktop-amd64.iso",
            "fedora-workstation-40-1.14.iso",
            "debian-12.5.0-amd64-netinst.iso",
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int step = 2;
    private long interval = 1500L;
    private int count = 1;
    private int[] percent = {1};

    private boolean timed;
    private int mode = ProgressCurve.MODE_CONSTANT;
    private long durationMs;
    private long updateMs;
    private long startedAtMs;
    private int lastPublishedUnits;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopAll(nm);
            return START_NOT_STICKY;
        }

        handler.removeCallbacksAndMessages(null);
        if (ACTION_TIMED_START.equals(intent.getAction())) {
            startTimed(nm, intent);
        } else {
            startLegacy(nm, intent);
        }
        return START_NOT_STICKY;
    }

    private void startTimed(NotificationManager nm, Intent intent) {
        timed = true;
        count = 1;
        mode = intent.getIntExtra(EXTRA_MODE, ProgressCurve.MODE_CONSTANT)
                == ProgressCurve.MODE_UNEVEN
                ? ProgressCurve.MODE_UNEVEN : ProgressCurve.MODE_CONSTANT;
        durationMs = Math.max(500L, Math.min(3_600_000L,
                intent.getLongExtra(EXTRA_DURATION_MS, 30_000L)));
        updateMs = Math.max(50L, Math.min(2000L,
                intent.getLongExtra(EXTRA_UPDATE_MS, 100L)));
        startedAtMs = SystemClock.elapsedRealtime();
        lastPublishedUnits = 0;
        for (int i = 1; i < MAX_DOWNLOADS; i++) {
            nm.cancel(NOTIF_BASE + i);
        }
        startForeground(NOTIF_BASE, buildTimed(0, 0, false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        handler.post(timedTick);
    }

    private final Runnable timedTick = new Runnable() {
        @Override
        public void run() {
            if (!timed) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long elapsed = Math.min(durationMs, Math.max(0L, now - startedAtMs));
            boolean complete = elapsed >= durationMs;
            double fraction = ProgressCurve.fraction(elapsed, durationMs, mode);
            int units = complete ? PROGRESS_UNITS
                    : Math.min(PROGRESS_UNITS - 1, (int) Math.round(fraction * PROGRESS_UNITS));
            if (units != lastPublishedUnits || complete) {
                lastPublishedUnits = units;
                getSystemService(NotificationManager.class)
                        .notify(NOTIF_BASE, buildTimed(units, elapsed, complete));
            }
            if (complete) {
                handler.postDelayed(() ->
                        stopAll(getSystemService(NotificationManager.class)), COMPLETE_HOLD_MS);
            } else {
                // Use absolute elapsed-time scheduling to avoid cumulative Handler drift.
                long nextElapsed = Math.min(durationMs,
                        ((elapsed / updateMs) + 1L) * updateMs);
                long delay = Math.max(1L, startedAtMs + nextElapsed
                        - SystemClock.elapsedRealtime());
                handler.postDelayed(this, delay);
            }
        }
    };

    private Notification buildTimed(int units, long elapsed, boolean complete) {
        String kind = mode == ProgressCurve.MODE_UNEVEN ? "Uneven" : "Constant";
        String title = mode == ProgressCurve.MODE_UNEVEN
                ? "variable-speed-test.bin" : "constant-speed-test.bin";
        String content = complete ? "Complete: 100%"
                : String.format(Locale.US, "%s: %.1f%% | %.1f / %.1f s",
                        kind, units / 100.0, elapsed / 1000.0, durationMs / 1000.0);
        return new Notification.Builder(this, SimApp.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(content)
                .setOnlyAlertOnce(true)
                .setOngoing(!complete)
                .setProgress(PROGRESS_UNITS, units, false)
                .setContentIntent(openPendingIntent())
                .addAction(0, "Stop", stopPendingIntent())
                .build();
    }

    private void startLegacy(NotificationManager nm, Intent intent) {
        timed = false;
        step = Math.max(1, intent.getIntExtra(EXTRA_STEP, step));
        interval = Math.max(100L, intent.getLongExtra(EXTRA_INTERVAL, interval));
        count = Math.max(1, Math.min(MAX_DOWNLOADS, intent.getIntExtra(EXTRA_COUNT, count)));
        percent = new int[count];
        for (int i = 0; i < count; i++) {
            percent[i] = 1 + i * 15;
        }
        for (int i = count; i < MAX_DOWNLOADS; i++) {
            nm.cancel(NOTIF_BASE + i);
        }
        startForeground(NOTIF_BASE, buildLegacy(0, percent[0]),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        handler.post(legacyTick);
    }

    private final Runnable legacyTick = new Runnable() {
        @Override
        public void run() {
            if (timed) {
                return;
            }
            NotificationManager nm = getSystemService(NotificationManager.class);
            for (int i = 0; i < count; i++) {
                percent[i] = percent[i] >= 99 ? 1 : percent[i] + step;
                nm.notify(NOTIF_BASE + i, buildLegacy(i, percent[i]));
            }
            handler.postDelayed(this, interval);
        }
    };

    private Notification buildLegacy(int index, int p) {
        return new Notification.Builder(this, SimApp.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(fileName(index))
                .setContentText("Downloading " + p + "%")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, p, false)
                .setContentIntent(openPendingIntent())
                .addAction(0, "Stop", stopPendingIntent())
                .build();
    }

    private PendingIntent stopPendingIntent() {
        return PendingIntent.getService(this, 0,
                new Intent(this, DownloadSimService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent openPendingIntent() {
        return PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private String fileName(int index) {
        return index < FILES.length ? FILES[index] : "download-" + (index + 1) + ".bin";
    }

    private void stopAll(NotificationManager nm) {
        timed = false;
        handler.removeCallbacksAndMessages(null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        for (int i = 0; i < MAX_DOWNLOADS; i++) {
            nm.cancel(NOTIF_BASE + i);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
