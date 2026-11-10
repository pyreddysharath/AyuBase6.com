package com.ayubase6.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/** Clock notification buttons (Pause, Resume, +1 min, Lap, Reset) and the timer reaching zero. */
public class ClockReceiver extends BroadcastReceiver {
    static final String PREFIX = "com.ayubase6.app.CLOCK.";

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction() == null ? "" : intent.getAction();
        if (!a.startsWith(PREFIX)) return;
        handle(context.getApplicationContext(), a.substring(PREFIX.length()), System.currentTimeMillis());
    }

    static synchronized void handle(Context c, String a, long now) {
        JSONObject t = ClockNotifier.load(c, "timer"), s = ClockNotifier.load(c, "stopwatch");
        boolean ring = false;
        switch (a) {
            case "t_pause": ClockKit.timerPause(t, now); break;
            case "t_resume": ClockKit.timerResume(t, now); break;
            case "t_reset": ClockKit.timerReset(t, now); AlarmService.stopIfKey("timer"); break;
            case "t_add": ClockKit.timerAdd(t, now, 60000L); break;
            case "t_fire": ring = ClockKit.timerFire(t, now); break;
            case "s_pause": ClockKit.swPause(s, now); break;
            case "s_resume": ClockKit.swStart(s, now); break;
            case "s_lap": ClockKit.swLap(s, now); break;
            case "s_reset": ClockKit.swReset(s, now); break;
            default: return;
        }
        if (a.startsWith("t_")) ClockNotifier.save(c, "timer", t); else ClockNotifier.save(c, "stopwatch", s);
        ClockNotifier.refresh(c);
        if (ring) ringTimer(c, t);
        AyuRemindersPlugin.clockChanged();
    }

    /** Rings like an alarm, with the timer's sound and vibrate choice, for a short time (repeating timers keep counting). */
    static void ringTimer(Context c, JSONObject t) {
        try {
            long len = ClockKit.len(t);
            boolean more = "running".equals(ClockKit.run(t));
            int sec = more ? (int) Math.max(5, Math.min(30, len / 3000)) : 60;
            JSONObject cfg = new JSONObject().put("tone", t.optString("tone", "rise")).put("vibrate", t.optBoolean("vibrate", true))
                .put("ringSec", sec).put("snoozeMax", 0).put("section", "alerts").put("keys", "stop");
            String body = "Time's up" + (more ? " · next alert in " + ClockKit.hms(len) + " (" + ClockNotifier.roundText(t) + ")" : "");
            AlarmService.startWith(c, "timer", ClockNotifier.timerTitle(t).replace("⏱ ", ""), body, cfg);
        } catch (Exception ignored) {}
    }
}
