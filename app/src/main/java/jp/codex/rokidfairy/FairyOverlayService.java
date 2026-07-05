package jp.codex.rokidfairy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.Random;

public class FairyOverlayService extends Service {
    private static final String CHANNEL_ID = "fairy_overlay";
    private static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private BroadcastReceiver overlayRestoreReceiver;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private FairyView fairyView;
    private Runnable movementLoop;
    private long actionStartedAt;
    private long actionDurationMs;
    private float x = 180f;
    private float y = 130f;
    private float vx = 0.08f;
    private float vy = 0.05f;
    private float speedSeedA = 1f;
    private float speedSeedB = 2f;
    private int displayWidth = 480;
    private int displayHeight = 640;
    private int windowSize = 150;
    private FairyView.Action action = FairyView.Action.WAVE;

    static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startForeground(1, createNotification());
        registerOverlayRestoreReceiver();
        showOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (fairyView == null) {
            showOverlay();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        unregisterOverlayRestoreReceiver();
        handler.removeCallbacksAndMessages(null);
        if (windowManager != null && fairyView != null) {
            windowManager.removeView(fairyView);
        }
        fairyView = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        displayWidth = metrics.widthPixels;
        displayHeight = metrics.heightPixels;
        int shortEdge = Math.max(1, Math.min(displayWidth, displayHeight));
        windowSize = Math.round(clamp(shortEdge * 0.18f, 90f, 165f) * 1.18f);

        fairyView = new FairyView(this);

        overlayParams = new WindowManager.LayoutParams(
                windowSize,
                windowSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = Math.round(x);
        overlayParams.y = Math.round(y);
        windowManager.addView(fairyView, overlayParams);
        startMovementLoop();
    }

    private void registerOverlayRestoreReceiver() {
        overlayRestoreReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handler.postDelayed(() -> forceReattachOverlay(), 650L);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(overlayRestoreReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(overlayRestoreReceiver, filter);
        }
    }

    private void unregisterOverlayRestoreReceiver() {
        if (overlayRestoreReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(overlayRestoreReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may already be unregistered during process teardown.
        }
        overlayRestoreReceiver = null;
    }

    private void forceReattachOverlay() {
        if (!running || windowManager == null || fairyView == null) {
            return;
        }

        if (movementLoop != null) {
            handler.removeCallbacks(movementLoop);
        }
        try {
            windowManager.removeView(fairyView);
        } catch (IllegalArgumentException ignored) {
            // Rokid may have already detached the overlay while closing menus.
        }
        fairyView = null;
        showOverlay();
    }

    private void startMovementLoop() {
        chooseNextAction(android.os.SystemClock.uptimeMillis());
        movementLoop = () -> {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - actionStartedAt > actionDurationMs) {
                chooseNextAction(now);
            }

            step(now);
            fairyView.setAction(action, actionStartedAt);
            overlayParams.x = Math.round(x);
            overlayParams.y = Math.round(y);
            windowManager.updateViewLayout(fairyView, overlayParams);
            handler.postDelayed(movementLoop, 16L);
        };
        handler.post(movementLoop);
    }

    private void chooseNextAction(long now) {
        actionStartedAt = now;
        int pick = random.nextInt(100);
        if (pick < 12) {
            action = FairyView.Action.KNEE_HUG;
            actionDurationMs = randomBetween(45_000, 110_000);
            vx = 0f;
            vy = 0f;
        } else if (pick < 20) {
            action = FairyView.Action.SLEEP;
            actionDurationMs = randomBetween(18_000, 45_000);
            vx = 0f;
            vy = 0f;
            moveToRandomCorner();
        } else if (pick < 34) {
            action = FairyView.Action.WAVE;
            actionDurationMs = randomBetween(8_000, 18_000);
            vx = 0f;
            vy = 0f;
        } else if (pick < 48) {
            action = FairyView.Action.DANCE;
            actionDurationMs = randomBetween(10_000, 24_000);
            vx = 0.015f;
            vy = 0.01f;
            randomizeSpeedSeeds();
        } else {
            chooseMovement();
            actionDurationMs = randomBetween(18_000, 48_000);
        }
        fairyView.setAction(action, actionStartedAt);
    }

    private void chooseMovement() {
        int pick = random.nextInt(6);
        float speed = 0.035f + random.nextFloat() * 0.12f;
        randomizeSpeedSeeds();
        if (pick == 0) {
            action = FairyView.Action.ASCEND;
            vx = randomSigned(speed * 0.35f);
            vy = -speed;
        } else if (pick == 1) {
            action = FairyView.Action.DESCEND;
            vx = randomSigned(speed * 0.35f);
            vy = speed;
        } else if (pick == 2) {
            action = FairyView.Action.LEFT;
            vx = -speed;
            vy = randomSigned(speed * 0.35f);
        } else if (pick == 3) {
            action = FairyView.Action.RIGHT;
            vx = speed;
            vy = randomSigned(speed * 0.35f);
        } else {
            action = FairyView.Action.DOWNWARD;
            vx = randomSigned(speed * 0.4f);
            vy = speed * 0.8f;
        }
    }

    private void step(long now) {
        float dt = 16f;
        float speedScale = movementSpeedScale(now);
        x += vx * speedScale * dt;
        y += vy * speedScale * dt;

        float maxX = Math.max(0, displayWidth - windowSize);
        float maxY = Math.max(0, displayHeight - windowSize);
        if (x < 0 || x > maxX) {
            vx = -vx;
            x = clamp(x, 0, maxX);
            action = vx < 0 ? FairyView.Action.LEFT : FairyView.Action.RIGHT;
        }
        if (y < 0 || y > maxY) {
            vy = -vy;
            y = clamp(y, 0, maxY);
            action = vy < 0 ? FairyView.Action.ASCEND : FairyView.Action.DESCEND;
        }
    }

    private float movementSpeedScale(long now) {
        if (!isMoving(action)) {
            return 1f;
        }

        float elapsed = Math.max(0f, now - actionStartedAt);
        float wobble = 1f
                + (float) Math.sin(elapsed / (420f + speedSeedA * 260f)) * (0.24f + speedSeedA * 0.16f)
                + (float) Math.sin(elapsed / (980f + speedSeedB * 520f)) * (0.12f + speedSeedB * 0.10f);
        wobble = clamp(wobble, 0.45f, 1.7f);

        long remaining = actionStartedAt + actionDurationMs - now;
        float slowdownWindow = Math.min(5_000f, Math.max(2_000f, actionDurationMs * 0.22f));
        if (remaining < slowdownWindow) {
            float t = clamp(remaining / slowdownWindow, 0f, 1f);
            float smooth = t * t * (3f - 2f * t);
            wobble *= 0.18f + smooth * 0.82f;
        }
        return wobble;
    }

    private boolean isMoving(FairyView.Action value) {
        return value == FairyView.Action.ASCEND
                || value == FairyView.Action.DESCEND
                || value == FairyView.Action.LEFT
                || value == FairyView.Action.RIGHT
                || value == FairyView.Action.DOWNWARD
                || value == FairyView.Action.DANCE;
    }

    private void moveToRandomCorner() {
        float margin = 10f;
        float maxX = Math.max(0, displayWidth - windowSize - margin);
        float maxY = Math.max(0, displayHeight - windowSize - margin);
        x = random.nextBoolean() ? margin : maxX;
        y = random.nextBoolean() ? margin : maxY;
    }

    private long randomBetween(long min, long max) {
        return min + Math.abs(random.nextLong()) % (max - min + 1);
    }

    private float randomSigned(float value) {
        return random.nextBoolean() ? value : -value;
    }

    private void randomizeSpeedSeeds() {
        speedSeedA = 0.2f + random.nextFloat() * 1.8f;
        speedSeedB = 0.2f + random.nextFloat() * 1.8f;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void scheduleRestart() {
        Intent intent = new Intent(this, RokiRestartReceiver.class);
        intent.setAction(RokiRestartReceiver.ACTION_RESTART);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000L,
                pendingIntent);
    }

    private Notification createNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.star_on)
                .setOngoing(true)
                .build();
    }
}
