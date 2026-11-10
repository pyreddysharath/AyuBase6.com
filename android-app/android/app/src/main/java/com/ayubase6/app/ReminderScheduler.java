package com.ayubase6.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.widget.RemoteViews;

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
    static final String CHANNEL_REMINDERS_QUIET = "reminders_novib";
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
        long now = System.currentTimeMillis() / 60000L;
        if (rules != null) {
            for (long t = now + 1; t <= now + SEARCH_DAYS * 1440L; t++) {
                if (!ReminderRules.due(rules, local(t), t).isEmpty()) { next = t; break; }
            }
        }
        // "Not yet" asks again later: the earliest of those, if it comes first
        org.json.JSONArray sn = snoozes(c);
        for (int i = 0; i < sn.length(); i++) {
            long t = sn.optJSONObject(i) == null ? 0 : sn.optJSONObject(i).optLong("t");
            if (t > now && (next == 0 || t < next)) next = t;
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
            Map<String, Long> dueAt = new java.util.HashMap<>();
            for (long t = from; t <= now; t++) {
                for (ReminderRules.Due d : ReminderRules.due(rules, local(t), t)) { latest.put(d.key, d); dueAt.put(d.key, t); }
            }
            for (Map.Entry<String, ReminderRules.Due> e : latest.entrySet()) {
                ReminderRules.Due d = e.getValue();
                // Checks → Alarm rings (only if it is due now, not hours late); everything else is a notification
                boolean ring = d.key.startsWith("alarm-") && AlarmService.config(c, d.key) != null && now - dueAt.getOrDefault(d.key, 0L) <= 2;
                if (ring && AlarmService.start(c, d.key, d.title, d.body, 0)) continue;
                show(c, "nudge-" + d.key, d.title, d.body, false, NotificationArt.forSection(d.section), d.section, d.key, actionsFor(d.key), d.vibrate, d.sound);
                if (!d.say.isEmpty() && NotificationManagerCompat.from(c).areNotificationsEnabled()) Speaker.say(c, d.say, d.voice, d.lang);
            }
        }
        // "Not yet" reminders that are due now (or missed by less than an hour)
        org.json.JSONArray sn = snoozes(c), keep = new org.json.JSONArray();
        for (int i = 0; i < sn.length(); i++) {
            JSONObject o = sn.optJSONObject(i); if (o == null) continue;
            long t = o.optLong("t");
            if (t > now) { keep.put(o); continue; }
            if (o.optBoolean("alarm", false) && now - t <= 2 && AlarmService.config(c, o.optString("key")) != null
                    && AlarmService.start(c, o.optString("key"), o.optString("title"), o.optString("body"), o.optInt("count", 1))) continue;
            if (now - t <= MAX_LATE_MIN) {
                String k = o.optString("key");
                String sec = k.startsWith("alarm-rem") ? "alerts" : "routines";   // Reminders alarms: "rem", "rem-<id>"
                show(c, "nudge-" + k, o.optString("title"), o.optString("body"), false, NotificationArt.forSection(sec), sec, k, actionsFor(k));
            }
        }
        saveSnoozes(c, keep);
        reschedule(c);
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
        if (nm.getNotificationChannel(CHANNEL_REMINDERS_QUIET) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_REMINDERS_QUIET, "Reminders (no vibration)", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminders whose Vibrate switch is off");
            ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(false);
            ch.setVibrationPattern(new long[]{0});
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

    static void show(Context c, String tag, String title, String body, boolean silent, NotificationArt.Theme theme, String section, String tapKey) {
        show(c, tag, title, body, silent, theme, section, tapKey, new String[0]);
    }

    // ── Buttons on reminders, the same as the website's: Checks "✓ Done" / "Not yet", the others "Turn off this" / "Turn off all" ──
    static final String A_DONE = "done", A_NOT_YET = "not-yet", A_OFF_THIS = "disable-this", A_OFF_ALL = "disable-all";
    static String[] actionsFor(String key) {
        if (key.startsWith("routine-")) return new String[]{A_DONE, A_NOT_YET};
        if (key.matches("^(break|walk|drink|eyeex)$")) return new String[]{A_OFF_THIS, A_OFF_ALL};
        return new String[0];   // My Reminders: no buttons, as on the website
    }
    private static String actionLabel(String a) {
        switch (a) {
            case A_DONE: return "✓ Done";
            case A_NOT_YET: return "Not yet";
            case A_OFF_THIS: return "Turn off this";
            default: return "Turn off all";
        }
    }

    static void show(Context c, String tag, String title, String body, boolean silent, NotificationArt.Theme theme, String section, String tapKey, String[] actions) {
        show(c, tag, title, body, silent, theme, section, tapKey, actions, true);
    }

    static void show(Context c, String tag, String title, String body, boolean silent, NotificationArt.Theme theme, String section, String tapKey, String[] actions, boolean vibrate) {
        show(c, tag, title, body, silent, theme, section, tapKey, actions, vibrate, "");
    }

    /** A channel per app tone (Android keeps the sound per channel), with and without vibration. */
    static String toneChannel(Context c, String tone, boolean vibrate) {
        String id = "tone_" + tone + (vibrate ? "" : "_nv");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return id;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(id) != null) return id;
        NotificationChannel ch = new NotificationChannel(id, "Checks · " + tone + (vibrate ? "" : " (no vibration)"), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Checks notifications with this sound");
        ch.setSound(AlarmService.rawTone(c, tone), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        ch.enableVibration(vibrate);
        ch.setVibrationPattern(vibrate ? new long[]{0, 200, 100, 200, 100, 200} : new long[]{0});
        ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        return id;
    }

    static void show(Context c, String tag, String title, String body, boolean silent, NotificationArt.Theme theme, String section, String tapKey, String[] actions, boolean vibrate, String sound) {
        ensureChannels(c);
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) return;
        Intent open = new Intent(c, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_KEY, tapKey == null ? "" : tapKey)
            .putExtra(EXTRA_SECTION, section == null ? "" : section);
        PendingIntent pi = PendingIntent.getActivity(c, idFor(tag), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Vibrate switch off: the same sound, without vibration (Android 8+ keeps vibration per channel)
        String channel = silent ? CHANNEL_UPDATES : (sound != null && !sound.isEmpty()) ? toneChannel(c, sound, vibrate) : (vibrate ? CHANNEL_REMINDERS : CHANNEL_REMINDERS_QUIET);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_lotus)
            .setContentTitle(title)
            .setContentText(body)
            // Standard Android look (Android 12+ always frames a custom picture): the section logo on the right, the section colour for the app name and buttons
            .setLargeIcon(art("logo" + theme.key(), () -> NotificationArt.logo(theme)))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setColor(theme.deep)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .setCategory(silent ? NotificationCompat.CATEGORY_PROGRESS : NotificationCompat.CATEGORY_REMINDER)
            .setPriority(silent ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (!silent && !vibrate) b.setVibrate(new long[]{0});   // Android 7 and older
        for (String a : actions) {
            Intent ai = new Intent(c, ActionReceiver.class).setAction("com.ayubase6.app.ACTION." + a)
                .putExtra(EXTRA_KEY, tapKey).putExtra(ActionReceiver.EXTRA_ACTION, a).putExtra(ActionReceiver.EXTRA_TAG, tag)
                .putExtra(ActionReceiver.EXTRA_TITLE, title).putExtra(ActionReceiver.EXTRA_BODY, body);
            PendingIntent api = PendingIntent.getBroadcast(c, (tag + "|" + a).hashCode(), ai, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(0, actionLabel(a), api);
        }
        try {
            nm.notify(idFor(tag), b.build());
        } catch (SecurityException e) {
            Log.w(TAG, "notification permission missing");
        }
    }

    // The section-coloured card: light section gradient with a faint lotus, the section logo, dark section-coloured text.
    private static final java.util.Map<String, Bitmap> ART = new java.util.HashMap<>();
    private static synchronized Bitmap art(String k, java.util.function.Supplier<Bitmap> make) {
        Bitmap b = ART.get(k); if (b == null) { b = make.get(); ART.put(k, b); } return b;
    }
    private static RemoteViews cardView(Context c, int layout, NotificationArt.Theme t, String title, String body, int w, int h) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        v.setImageViewBitmap(R.id.notif_bg, art("card" + t.key() + h, () -> NotificationArt.card(t, w, h)));
        v.setImageViewBitmap(R.id.notif_logo, art("logo" + t.key(), () -> NotificationArt.logo(t)));
        v.setTextViewText(R.id.notif_title, title);
        v.setTextColor(R.id.notif_title, t.title);
        v.setTextViewText(R.id.notif_body, body);
        v.setTextColor(R.id.notif_body, t.body);
        return v;
    }

    // ── What a notification button does, on the phone, even with the app closed ──
    private static final String PREF_SNOOZE = "snoozes", PREF_PENDING = "pending";
    static synchronized org.json.JSONArray snoozes(Context c) {
        try { return new org.json.JSONArray(prefs(c).getString(PREF_SNOOZE, "[]")); } catch (Exception e) { return new org.json.JSONArray(); }
    }
    static synchronized void saveSnoozes(Context c, org.json.JSONArray a) { prefs(c).edit().putString(PREF_SNOOZE, a.toString()).apply(); }

    /** Actions the app itself must also apply (tick the activity, turn its switch off) when it is next open. */
    static synchronized org.json.JSONArray takePending(Context c) {
        org.json.JSONArray a;
        try { a = new org.json.JSONArray(prefs(c).getString(PREF_PENDING, "[]")); } catch (Exception e) { a = new org.json.JSONArray(); }
        prefs(c).edit().remove(PREF_PENDING).apply();
        return a;
    }
    private static synchronized void addPending(Context c, String key, String action) {
        org.json.JSONArray a;
        try { a = new org.json.JSONArray(prefs(c).getString(PREF_PENDING, "[]")); } catch (Exception e) { a = new org.json.JSONArray(); }
        try {
            long now = System.currentTimeMillis();
            a.put(new JSONObject().put("key", key).put("action", action).put("at", now).put("day", local(now / 60000L).day));
        } catch (Exception ignored) {}
        org.json.JSONArray keep = new org.json.JSONArray();   // the last 50 are plenty
        for (int i = Math.max(0, a.length() - 50); i < a.length(); i++) keep.put(a.opt(i));
        prefs(c).edit().putString(PREF_PENDING, keep.toString()).apply();
    }

    static void applyAction(Context c, String key, String action, String tag, String title, String body) {
        if (key == null) key = "";
        JSONObject rules = loadRules(c);
        long now = System.currentTimeMillis() / 60000L;
        String today = local(now).day;
        try {
            if (A_DONE.equals(action) && key.startsWith("routine-")) {
                mutate(rules, key, action, today);
                dropSnooze(c, "routine-" + key.replaceFirst("^routine-(end-|again-)?", ""));
                addPending(c, key, action);
            } else if (A_NOT_YET.equals(action) && key.startsWith("routine-")) {
                String item = key.replaceFirst("^routine-(end-|again-)?", "");
                org.json.JSONArray sn = snoozes(c), keep = new org.json.JSONArray();
                for (int i = 0; i < sn.length(); i++) { JSONObject o = sn.optJSONObject(i); if (o != null && !("routine-" + item).equals(o.optString("key"))) keep.put(o); }
                String b = body == null ? "" : body;
                keep.put(new JSONObject().put("t", now + 15).put("key", "routine-" + item)
                    .put("title", title == null ? "" : title).put("body", b.startsWith("Still to do: ") ? b : "Still to do: " + b));
                saveSnoozes(c, keep);
            } else if ((A_OFF_THIS.equals(action) || A_OFF_ALL.equals(action)) && rules != null) {
                mutate(rules, key, action, today);
                addPending(c, key, action);
            }
        } catch (Exception e) { Log.w(TAG, "action " + action + " failed", e); }
        if (rules != null) saveRules(c, rules);
        if (tag != null) cancel(c, tag);
        reschedule(c);
    }
    /** The change a button makes to the stored rules (Done: ticked for today; Turn off: that reminder, or all Reminders, off). */
    private static void mutate(JSONObject rules, String key, String action, String today) throws org.json.JSONException {
        if (rules == null) return;
        if (A_DONE.equals(action) && key.startsWith("routine-")) {
            String item = key.replaceFirst("^routine-(end-|again-)?", "");
            JSONObject ck = rules.optJSONObject("checks");
            if (ck == null) return;
            if (!today.equals(ck.optString("doneDay"))) { ck.put("doneDay", today); ck.put("done", new org.json.JSONArray()); }
            org.json.JSONArray done = ck.optJSONArray("done");
            if (done == null) { done = new org.json.JSONArray(); ck.put("done", done); }
            for (int i = 0; i < done.length(); i++) if (item.equals(done.optString(i))) return;
            done.put(item);
        } else if (A_OFF_THIS.equals(action)) {
            if ("eyeex".equals(key)) { rules.put("exercise", JSONObject.NULL); return; }
            org.json.JSONArray r = rules.optJSONArray("reminders"), keep = new org.json.JSONArray();
            if (r != null) for (int i = 0; i < r.length(); i++) { JSONObject o = r.optJSONObject(i); if (o != null && !key.equals(o.optString("key"))) keep.put(o); }
            rules.put("reminders", keep);
        } else if (A_OFF_ALL.equals(action)) {
            // the Reminders switch off: Eyes, Walk, Water, Eye Exercise and My Reminders; Checks has its own switch
            rules.put("reminders", new org.json.JSONArray()); rules.put("exercise", JSONObject.NULL); rules.put("custom", new org.json.JSONArray());
        }
    }

    /**
     * New rules from the app while button actions are still waiting for it: keep those actions in force,
     * so the app's older state cannot switch a reminder back on before it has applied them.
     */
    static synchronized void keepPendingInForce(Context c, JSONObject rules) {
        if (rules == null) return;
        org.json.JSONArray a;
        try { a = new org.json.JSONArray(prefs(c).getString(PREF_PENDING, "[]")); } catch (Exception e) { return; }
        String today = local(System.currentTimeMillis() / 60000L).day;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue;
            if (A_DONE.equals(o.optString("action")) && !today.equals(o.optString("day"))) continue;
            try { mutate(rules, o.optString("key"), o.optString("action"), today); } catch (Exception ignored) {}
        }
    }

    /** Snooze on a ringing alarm: ring again after `minutes`; `count` snoozes used so far. */
    static void addAlarmSnooze(Context c, String key, String title, String body, int count, int minutes) {
        try {
            org.json.JSONArray sn = snoozes(c), keep = new org.json.JSONArray();
            for (int i = 0; i < sn.length(); i++) { JSONObject o = sn.optJSONObject(i); if (o != null && !key.equals(o.optString("key"))) keep.put(o); }
            keep.put(new JSONObject().put("t", System.currentTimeMillis() / 60000L + minutes).put("key", key)
                .put("title", title).put("body", body).put("alarm", true).put("count", count));
            saveSnoozes(c, keep);
        } catch (Exception ignored) {}
        reschedule(c);
    }

    private static void dropSnooze(Context c, String key) {
        org.json.JSONArray sn = snoozes(c), keep = new org.json.JSONArray();
        for (int i = 0; i < sn.length(); i++) { JSONObject o = sn.optJSONObject(i); if (o != null && !key.equals(o.optString("key"))) keep.put(o); }
        saveSnoozes(c, keep);
    }

    static void cancel(Context c, String tag) { NotificationManagerCompat.from(c).cancel(idFor(tag)); }
}
