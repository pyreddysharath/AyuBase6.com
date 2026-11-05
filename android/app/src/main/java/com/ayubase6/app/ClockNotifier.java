package com.ayubase6.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Clock on the phone: the running timer and stopwatch as live notifications (shown on the lock screen, with
 * their buttons), the timer's end as an exact alarm, and the World Clock cities (if switched on).
 * State is sent by the page (clockSet) and changed by the notification buttons (ClockReceiver).
 */
final class ClockNotifier {
    private ClockNotifier() {}
    static final String CH = "clock", PREFS = "ayu_clock";
    static final int ID_TIMER = 7401, ID_SW = 7402, ID_WORLD = 7403;
    private static final int RQ_FIRE = 7410;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static JSONObject load(Context c, String k) {
        try { String s = prefs(c).getString(k, null); return s == null ? new JSONObject() : new JSONObject(s); } catch (Exception e) { return new JSONObject(); }
    }
    static void save(Context c, String k, JSONObject o) { prefs(c).edit().putString(k, o.toString()).commit(); }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CH) != null) return;
        NotificationChannel ch = new NotificationChannel(CH, "Clock", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Running timer and stopwatch, and World Clock (silent)");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent act(Context c, String a, int code) {
        Intent i = new Intent(c, ClockReceiver.class).setAction(ClockReceiver.PREFIX + a);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("ayu_open", "clock");
        return PendingIntent.getActivity(c, 7420, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static NotificationCompat.Builder base(Context c) {
        return new NotificationCompat.Builder(c, CH).setSmallIcon(R.drawable.ic_stat_lotus).setColor(NotificationArt.REMINDERS.deep)
            .setOnlyAlertOnce(true).setSilent(true).setOngoing(true).setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(open(c));
    }

    /** Shows / updates / removes all Clock notifications and sets the timer's alarm. */
    static void refresh(Context c) {
        ensureChannel(c);
        long now = System.currentTimeMillis();
        try { postTimer(c, load(c, "timer"), now); } catch (Exception ignored) {}
        try { postStopwatch(c, load(c, "stopwatch"), now); } catch (Exception ignored) {}
        try { postWorld(c, load(c, "world")); } catch (Exception ignored) {}
        scheduleTimer(c, load(c, "timer"), now);
    }

    static String timerTitle(JSONObject t) {
        String n = t.optString("name", "").trim();
        return "⏱ " + (n.isEmpty() ? "Timer" : n);
    }
    static String roundText(JSONObject t) {
        int n = ClockKit.times(t), r = Math.max(1, t.optInt("round", 1));
        if (n == 1) return "Once";
        return n == 0 ? "Alert " + r + " · repeats until stopped" : "Alert " + r + " of " + n;
    }

    static void postTimer(Context c, JSONObject t, long now) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        String run = ClockKit.run(t);
        if ("idle".equals(run)) { nm.cancel(ID_TIMER); return; }
        NotificationCompat.Builder b = base(c).setCategory(NotificationCompat.CATEGORY_ALARM).setContentTitle(timerTitle(t));
        if ("running".equals(run)) {
            b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(t.optLong("endAt")).setShowWhen(true)
             .setContentText(roundText(t) + " · every " + ClockKit.hms(ClockKit.len(t)))
             .addAction(0, "Pause", act(c, "t_pause", 7431)).addAction(0, "+1 min", act(c, "t_add", 7432)).addAction(0, "Reset", act(c, "t_reset", 7433));
        } else {
            b.setShowWhen(false).setContentText("Paused · " + ClockKit.hms(t.optLong("left")) + " left · " + roundText(t))
             .addAction(0, "Resume", act(c, "t_resume", 7434)).addAction(0, "+1 min", act(c, "t_add", 7432)).addAction(0, "Reset", act(c, "t_reset", 7433));
        }
        try { nm.notify(ID_TIMER, b.build()); } catch (SecurityException ignored) {}
    }

    static String swText(long ms) {
        long cs = (ms / 10) % 100, s = ms / 1000;
        return ClockKit.hms(s * 1000) + "." + String.format(java.util.Locale.US, "%02d", cs);
    }

    static void postStopwatch(Context c, JSONObject s, long now) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        String run = ClockKit.run(s);
        if ("idle".equals(run)) { nm.cancel(ID_SW); return; }
        JSONArray laps = s.optJSONArray("laps");
        int n = laps == null ? 0 : laps.length();
        long el = ClockKit.swElapsed(s, now);
        NotificationCompat.Builder b = base(c).setContentTitle("⏱ Stopwatch");
        String lapTxt = n == 0 ? "" : (n == 1 ? "1 lap" : n + " laps");
        if ("running".equals(run)) {
            b.setUsesChronometer(true).setWhen(now - el).setShowWhen(true).setContentText(lapTxt.isEmpty() ? "Running" : lapTxt)
             .addAction(0, "Pause", act(c, "s_pause", 7441)).addAction(0, "Lap", act(c, "s_lap", 7442)).addAction(0, "Reset", act(c, "s_reset", 7443));
        } else {
            b.setShowWhen(false).setContentText("Paused · " + swText(el) + (lapTxt.isEmpty() ? "" : " · " + lapTxt))
             .addAction(0, "Resume", act(c, "s_resume", 7444)).addAction(0, "Reset", act(c, "s_reset", 7443));
        }
        try { nm.notify(ID_SW, b.build()); } catch (SecurityException ignored) {}
    }

    private static final int[] ROW = {R.id.w1, R.id.w2, R.id.w3, R.id.w4, R.id.w5};
    private static final int[] CITY = {R.id.w1_city, R.id.w2_city, R.id.w3_city, R.id.w4_city, R.id.w5_city};
    private static final int[] TIME = {R.id.w1_time, R.id.w2_time, R.id.w3_time, R.id.w4_time, R.id.w5_time};

    private static RemoteViews worldViews(Context c, JSONArray list, int max) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.notif_world);
        boolean h24 = android.text.format.DateFormat.is24HourFormat(c);
        for (int i = 0; i < ROW.length; i++) {
            JSONObject z = list.optJSONObject(i);
            if (z == null || i >= max) { v.setViewVisibility(ROW[i], View.GONE); continue; }
            v.setViewVisibility(ROW[i], View.VISIBLE);
            String place = z.optString("city") + (z.optString("country").isEmpty() ? "" : ", " + z.optString("country"));
            v.setTextViewText(CITY[i], place);
            v.setString(TIME[i], "setTimeZone", z.optString("tz"));
            v.setCharSequence(TIME[i], "setFormat12Hour", h24 ? "HH:mm" : "h:mm a");
            v.setCharSequence(TIME[i], "setFormat24Hour", h24 ? "HH:mm" : "h:mm a");
        }
        return v;
    }

    static void postWorld(Context c, JSONObject w) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        JSONArray list = w.optJSONArray("list");
        if (!w.optBoolean("on", false) || list == null || list.length() == 0) { nm.cancel(ID_WORLD); return; }
        NotificationCompat.Builder b = base(c).setCategory(NotificationCompat.CATEGORY_STATUS).setContentTitle("World Clock").setShowWhen(false)
            .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(worldViews(c, list, 2)).setCustomBigContentView(worldViews(c, list, 5))
            .setPriority(NotificationCompat.PRIORITY_LOW);
        try { nm.notify(ID_WORLD, b.build()); } catch (SecurityException ignored) {}
    }

    private static PendingIntent fireIntent(Context c) {
        Intent i = new Intent(c, ClockReceiver.class).setAction(ClockReceiver.PREFIX + "t_fire");
        return PendingIntent.getBroadcast(c, RQ_FIRE, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleTimer(Context c, JSONObject t, long now) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = fireIntent(c);
        am.cancel(pi);
        if (!"running".equals(ClockKit.run(t))) return;
        long at = Math.max(now + 500, t.optLong("endAt"));
        try {
            if (ReminderScheduler.canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
