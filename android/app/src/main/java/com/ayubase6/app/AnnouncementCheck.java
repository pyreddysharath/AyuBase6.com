package com.ayubase6.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * A new announcement banner (config/announcements.json on the website, the same file the app reads) as a notification
 * with the app's logo, once per banner, and only while the app is not in use. Checked about every 3 hours.
 * The first check after installing only notes the banners that already exist.
 */
public class AnnouncementCheck extends BroadcastReceiver {
    static final String URL_JSON = "https://ayubase6.com/config/announcements.json", PREFS = "ayu_ann", CH = "announcements";
    private static final String ACTION = "com.ayubase6.app.ANN_CHECK";
    static volatile boolean appVisible = false;

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 7501, new Intent(c, AnnouncementCheck.class).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 20 * 60000L, 3 * AlarmManager.INTERVAL_HOUR, pi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> { try { check(c); } catch (Exception ignored) {} finally { pr.finish(); } }, "ayu-ann").start();
    }

    static void check(Context c) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(URL_JSON + "?t=" + (System.currentTimeMillis() / 600000L)).openConnection();
        h.setConnectTimeout(10000); h.setReadTimeout(10000); h.setUseCaches(false);
        if (h.getResponseCode() != 200) return;
        String body;
        try (InputStream in = h.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0 && out.size() < 200000) out.write(b, 0, n); body = out.toString("UTF-8");
        }
        JSONArray msgs = new JSONObject(body).optJSONArray("messages");
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray seen = new JSONArray(sp.getString("seen", "[]"));
        boolean seeded = sp.getBoolean("seeded", false);
        java.util.Set<String> have = new java.util.HashSet<>();
        for (int i = 0; i < seen.length(); i++) have.add(seen.optString(i));
        if (msgs != null) for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.optJSONObject(i);
            if (m == null) continue;
            String id = m.optString("id", "").trim();
            if (id.isEmpty()) id = (m.optString("title", "") + "|" + m.optString("message", "")).hashCode() + "";
            if (have.contains(id) || !m.optBoolean("banner", true) || expired(m.optString("expires", ""))) continue;
            have.add(id); seen.put(id);
            if (seeded && !appVisible) notify(c, id, m.optString("title", "AyuBase6").trim(), m.optString("message", "").trim());
        }
        while (seen.length() > 200) seen.remove(0);
        sp.edit().putString("seen", seen.toString()).putBoolean("seeded", true).apply();
    }

    /** "05-Nov-2026" or "2026-11-05": past the end of that day. Unreadable dates never expire. */
    static boolean expired(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        s = s.trim();
        java.time.LocalDate d = null;
        try { d = java.time.LocalDate.parse(s); } catch (Exception ignored) {}
        if (d == null) try {
            d = java.time.LocalDate.parse(s, new java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.ENGLISH));
        } catch (Exception ignored) {}
        return d != null && java.time.LocalDate.now().isAfter(d);
    }

    static void notify(Context c, String id, String title, String msg) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CH) == null) {
                NotificationChannel ch = new NotificationChannel(CH, "Announcements", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("News from AyuBase6");
                nm.createNotificationChannel(ch);
            }
        }
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderScheduler.EXTRA_KEY, "ann").putExtra(ReminderScheduler.EXTRA_SECTION, "announcements");
        PendingIntent pi = PendingIntent.getActivity(c, ("ann" + id).hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat_lotus)
            .setLargeIcon(BitmapFactory.decodeResource(c.getResources(), R.mipmap.ic_launcher))
            .setContentTitle(title.isEmpty() ? "AyuBase6" : title)
            .setContentText(msg)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
            .setColor(NotificationArt.REMINDERS.deep)
            .setContentIntent(pi).setAutoCancel(true);
        try { NotificationManagerCompat.from(c).notify(("ann" + id).hashCode(), b.build()); } catch (SecurityException ignored) {}
    }
}
