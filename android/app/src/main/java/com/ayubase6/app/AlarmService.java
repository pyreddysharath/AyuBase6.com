package com.ayubase6.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Checks alarm (at the wake-up time): rings (looping ringtone, vibration) until stopped or for the chosen minutes,
 * with Snooze (up to the chosen number of times) and Stop. Runs as a foreground service so it
 * keeps ringing with the app closed; on a locked phone it opens AlarmActivity full screen.
 */
public class AlarmService extends Service {
    static final String A_START = "com.ayubase6.app.ALARM_START";
    static final String EX_KEY = "ayu_key", EX_TITLE = "ayu_title", EX_BODY = "ayu_body", EX_COUNT = "ayu_count";
    static final String CHANNEL_ALARMS = "alarms";
    static final int NOTIF_ID = 7301;

    private static volatile AlarmService running;
    static WeakReference<AlarmActivity> screen = new WeakReference<>(null);

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private Runnable autoStop;
    String key = "", title = "", body = "";
    int count = 0;
    JSONObject cfg = new JSONObject();

    // ── settings sent by the app with the rules (rules.checks.alarm) ──
    static JSONObject config(Context c) {
        JSONObject rules = ReminderScheduler.loadRules(c);
        JSONObject ck = rules == null ? null : rules.optJSONObject("checks");
        JSONObject a = ck == null ? null : ck.optJSONObject("alarm");
        return (a != null && a.optBoolean("on", false)) ? a : null;
    }

    /** Starts ringing for a Checks activity. Returns false if Android did not allow it (then a notification is shown). */
    static boolean start(Context c, String key, String title, String body, int count) {
        Intent i = new Intent(c, AlarmService.class).setAction(A_START)
            .putExtra(EX_KEY, key).putExtra(EX_TITLE, title).putExtra(EX_BODY, body).putExtra(EX_COUNT, count);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i); else c.startService(i);
            return true;
        } catch (Exception e) {
            Log.w(ReminderScheduler.TAG, "alarm could not start", e);
            return false;
        }
    }

    static AlarmService current() { return running; }

    /** A Done from a normal notification for the same activity also silences its alarm. */
    static void stopIfKey(String k) {
        AlarmService s = running;
        if (s != null && s.key.equals(k)) s.main.post(s::stopAll);
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ALARMS) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ALARMS, "Checks alarms", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Checks activities set to ring like an alarm (the ringtone is chosen in the app)");
        ch.setSound(null, null);          // the alarm plays its own ringtone
        ch.enableVibration(false);        // and vibrates itself
        ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        ch.setBypassDnd(false);
        nm.createNotificationChannel(ch);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !A_START.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        JSONObject c = config(this);
        if (running == this && player != null) { leaveNotice(); stopSound(); }   // a second alarm while one rings: the first one becomes a notification
        key = str(intent.getStringExtra(EX_KEY)); title = str(intent.getStringExtra(EX_TITLE)); body = str(intent.getStringExtra(EX_BODY));
        count = intent.getIntExtra(EX_COUNT, 0);
        cfg = c != null ? c : new JSONObject();
        running = this;
        ensureChannel(this);
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
        } catch (Exception e) {
            Log.w(ReminderScheduler.TAG, "alarm foreground refused", e);
            ReminderScheduler.show(this, "nudge-" + key, title, body, false, NotificationArt.ROUTINES, "routines", key, ReminderScheduler.actionsFor(key));
            running = null; stopSelf(); return START_NOT_STICKY;
        }
        startRinging();
        int ringMin = cfg.optInt("ring", 0);
        if (autoStop != null) main.removeCallbacks(autoStop);
        if (ringMin > 0) { autoStop = this::missed; main.postDelayed(autoStop, ringMin * 60000L); }
        AlarmActivity a = screen.get();
        if (a != null) a.refresh();
        return START_NOT_STICKY;
    }

    private static String str(String s) { return s == null ? "" : s; }

    boolean canSnooze() { return count < cfg.optInt("snoozeMax", 0); }
    int snoozeMin() { return Math.max(1, cfg.optInt("snoozeMin", 5)); }
    String keyAction() { return "stop".equals(cfg.optString("keys", "snooze")) ? "stop" : "snooze"; }

    private Uri toneUri() {
        String tone = cfg.optString("tone", "rise");
        if ("phone".equals(tone)) {
            Uri u = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (u != null) return u;
            tone = "rise";
        }
        if ("own".equals(tone)) {
            File f = ownTone(this);
            if (f.exists() && f.length() > 0) return Uri.fromFile(f);
            tone = "rise";
        }
        return rawTone(this, tone);
    }

    static File ownTone(Context c) { return new File(c.getFilesDir(), "alarm-own-tone"); }

    static Uri rawTone(Context c, String id) {
        int res = c.getResources().getIdentifier("tone_" + (id == null ? "" : id.replaceAll("[^a-z]", "")), "raw", c.getPackageName());
        if (res == 0) res = R.raw.tone_rise;
        return Uri.parse("android.resource://" + c.getPackageName() + "/" + res);
    }

    private void startRinging() {
        stopSound();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) { wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ayubase6:alarm"); wake.acquire(15 * 60000L); }
        } catch (Exception ignored) {}
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, toneUri());
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.w(ReminderScheduler.TAG, "ringtone failed, using the app tone", e);
            try {
                player.reset();
                player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                player.setDataSource(this, rawTone(this, "rise"));
                player.setLooping(true); player.prepare(); player.start();
            } catch (Exception ignored) {}
        }
        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] pattern = {0, 700, 700};
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                else vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }

    private void stopSound() {
        try { if (player != null) { player.stop(); player.release(); } } catch (Exception ignored) {}
        player = null;
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (Exception ignored) {}
    }

    private PendingIntent action(String a, int code) {
        Intent i = new Intent(this, AlarmActionReceiver.class).setAction("com.ayubase6.app.ALARM." + a).putExtra(AlarmActionReceiver.EX_ACTION, a);
        return PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent full = PendingIntent.getActivity(this, 7302, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_lotus)
            .setContentTitle("⏰ " + title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setColor(NotificationArt.ROUTINES.deep)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(full)
            .setFullScreenIntent(full, true);
        if (canSnooze()) b.addAction(0, "Snooze " + snoozeMin() + " min", action("snooze", 7311));
        b.addAction(0, "Stop", action("stop", 7312));
        if (key.startsWith("routine-")) b.addAction(0, "✓ Done", action("done", 7313));
        return b.build();
    }

    // ── what the buttons (notification, alarm screen, volume keys) do ──
    void snooze() {
        if (!canSnooze()) { stopAll(); return; }
        ReminderScheduler.addAlarmSnooze(this, key, title, body, count + 1, snoozeMin());
        stopAll();
    }

    void done() {
        final String k = key, t = title, b = body;
        stopAll();
        new Thread(() -> {
            ReminderScheduler.applyAction(getApplicationContext(), k, ReminderScheduler.A_DONE, "nudge-" + k, t, b);
            AyuRemindersPlugin.actionsWaiting();
        }, "ayu-alarm-done").start();
    }

    void keyPressed() { if ("stop".equals(keyAction())) stopAll(); else snooze(); }

    /** Rang for the chosen minutes without an answer: stop ringing, leave a normal notification (Done / Not yet). */
    void missed() {
        leaveNotice();
        stopAll();
    }

    private void leaveNotice() {
        if (!key.isEmpty()) ReminderScheduler.show(this, "nudge-" + key, title, body, false, NotificationArt.ROUTINES, "routines", key, ReminderScheduler.actionsFor(key));
    }

    void stopAll() {
        if (autoStop != null) main.removeCallbacks(autoStop);
        stopSound();
        NotificationManagerCompat.from(this).cancel(NOTIF_ID);
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
        AlarmActivity a = screen.get();
        if (a != null) a.finish();
        if (running == this) running = null;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopSound();
        if (running == this) running = null;
        super.onDestroy();
    }
}
