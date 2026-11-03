package com.ayubase6.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps one Android alarm set for the next minute at which a reminder is due.
 * When it rings, AlarmReceiver shows what is due and sets the alarm for the next one.
 * The rules are stored on the phone, so this works with the app closed and after a restart.
 */
final class ReminderScheduler {
    private ReminderScheduler() {}

    static final String TAG = "AyuReminders";
    static final String CHANNEL_REMINDERS = "reminders";
    static final String CHANNEL_UPDATES = "updates";
    static final String EXTRA_MINUTE = "ayu_minute";
    static final String EXTRA_KEY = "ayu_key";
    static final String EXTRA_SECTION = "ayu_section";
    private static final String PREFS = "ayu_reminders";
    private static final String PREF_RULES = "rules";
    private static final String PREF_NEXT = "next";
    private static final int ALARM_REQUEST = 7001;
    private static final int SEARCH_DAYS = 8;      // every rule repeats at least weekly
    private static final int MAX_LATE_MIN = 60;    // an alarm this late (phone was off) shows only what is still recent

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static JSONObject loadRules(Context c) {
        String s = prefs(c).getString(PREF_RULES, null);
        if (s == null) return null;
        try { return new JSONObject(s); } catch (Exception e) { return null; }
    }

    static void saveRules(Context c, JSONObject rules) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (rules == null) e.remove(PREF_RULES); else e.putString(PREF_RULES, rules.toString());
        e.apply();
    }

    static long nextAt(Context c) { return prefs(c).getLong(PREF_NEXT, 0); }

    static ReminderRules.Local local(long epochMin) {
        ZonedDateTime z = Instant.ofEpochMilli(epochMin * 60000L).atZone(ZoneId.systemDefault());
        String day = String.format(Locale.US, "%04d-%02d-%02d", z.getYear(), z.getMonthValue(), z.getDayOfMonth());
        int dow = z.getDayOfWeek().getValue() % 7;   // Monday=1 ... Sunday=7 -> Sunday=0
        return new ReminderRules.Local(day, z.getHour() * 60 + z.getMinute(), dow);
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private static PendingIntent alarmIntent(Context c, long minute) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction("com.ayubase6.app.REMINDER");
        i.putExtra(EXTRA_MINUTE, minute);
        return PendingIntent.getBroadcast(c, ALARM_REQUEST, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Sets the alarm for the next due minute after now. Returns its time in ms, or 0 when nothing is due. */
    static long reschedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return 0;
        am.cancel(alarmIntent(c, 0));
        JSONObject rules = loadRules(c);
        long next = 0;
        if (rules != null) {
            long now = System.currentTimeMillis() / 60000L;
            for (long t = now + 1; t <= now + SEARCH_DAYS * 1440L; t++) {
                if (!ReminderRules.due(rules, local(t), t).isEmpty()) { next = t; break; }
            }
        }
        if (next > 0) {
            long ms = next * 60000L;
            PendingIntent pi = alarmIntent(c, next);
            try {
                if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
            }
        }
        long nextMs = next * 60000L;
        prefs(c).edit().putLong(PREF_NEXT, nextMs).apply();
        Log.i(TAG, "next reminder at " + (next > 0 ? Instant.ofEpochMilli(nextMs).toString() : "none") + (canExact(c) ? " (exact)" : " (inexact)"));
        return nextMs;
    }

    /** The alarm for minute `target` rang: show what is due (latest per reminder), then set the next alarm. */
    static void onAlarm(Context c, long target) {
        JSONObject rules = loadRules(c);
        long now = System.currentTimeMillis() / 60000L;
        if (rules != null && target > 0) {
            long from = Math.max(target, now - MAX_LATE_MIN);
            Map<String, ReminderRules.Due> latest = new LinkedHashMap<>();
            for (long t = from; t <= now; t++) {
                for (ReminderRules.Due d : ReminderRules.due(rules, local(t), t)) latest.put(d.key, d);
            }
            for (ReminderRules.Due d : latest.values()) {
                show(c, "nudge-" + d.key, d.title, d.body, false, sectionColor(d.section), d.section, d.key);
            }
        }
        reschedule(c);
    }

    static int sectionColor(String section) {
        return "routines".equals(section) ? Color.parseColor("#d97706") : Color.parseColor("#0d9488");
    }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_REMINDERS) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Eyes, Walk, Water, Eye Exercise, Checks and My Reminders");
            ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 200, 100, 200, 100, 200});
            ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CHANNEL_UPDATES) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_UPDATES, "Timers", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Countdowns that update quietly, such as the 20-second eye timer");
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
    }

    static int idFor(String tag) { return tag == null ? 0 : tag.hashCode(); }

    static void show(Context c, String tag, String title, String body, boolean silent, int color, String section, String tapKey) {
        ensureChannels(c);
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) return;
        Intent open = new Intent(c, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_KEY, tapKey == null ? "" : tapKey)
            .putExtra(EXTRA_SECTION, section == null ? "" : section);
        PendingIntent pi = PendingIntent.getActivity(c, idFor(tag), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, silent ? CHANNEL_UPDATES : CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_lotus)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setColor(color)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .setCategory(silent ? NotificationCompat.CATEGORY_PROGRESS : NotificationCompat.CATEGORY_REMINDER)
            .setPriority(silent ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        try {
            nm.notify(idFor(tag), b.build());
        } catch (SecurityException e) {
            Log.w(TAG, "notification permission missing");
        }
    }

    static void cancel(Context c, String tag) { NotificationManagerCompat.from(c).cancel(idFor(tag)); }
}
