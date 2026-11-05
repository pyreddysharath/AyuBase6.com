package com.ayubase6.app;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Timer and stopwatch states (the same rules as the web page). Pure logic on JSON, so it can be unit tested.
 *  timer:     name, ms (length), times (1 = once, n = n alerts, 0 = until stopped), tone, vibrate,
 *             run ("idle" | "running" | "paused"), endAt, left (ms, when paused), round (1-based), fired, v (last change)
 *  stopwatch: run ("idle" | "running" | "paused"), startAt, acc (ms before the current run), laps [total ms], v
 */
final class ClockKit {
    private ClockKit() {}

    static long len(JSONObject t) { return Math.max(1000L, t.optLong("ms", 300000L)); }
    static int times(JSONObject t) { return Math.max(0, t.optInt("times", 1)); }
    static String run(JSONObject o) { return o == null ? "idle" : o.optString("run", "idle"); }

    private static void put(JSONObject o, String k, Object v) { try { o.put(k, v); } catch (Exception ignored) {} }

    static void timerStart(JSONObject t, long now) {
        put(t, "run", "running"); put(t, "round", 1); put(t, "endAt", now + len(t)); put(t, "left", len(t)); put(t, "v", now);
    }
    static void timerPause(JSONObject t, long now) {
        if (!"running".equals(run(t))) return;
        put(t, "left", Math.max(0, t.optLong("endAt") - now)); put(t, "run", "paused"); put(t, "v", now);
    }
    static void timerResume(JSONObject t, long now) {
        if (!"paused".equals(run(t))) return;
        put(t, "endAt", now + Math.max(1000L, t.optLong("left", len(t)))); put(t, "run", "running"); put(t, "v", now);
    }
    static void timerReset(JSONObject t, long now) {
        put(t, "run", "idle"); put(t, "round", 0); put(t, "endAt", 0); put(t, "left", len(t)); put(t, "v", now);
    }
    static void timerAdd(JSONObject t, long now, long ms) {
        if ("running".equals(run(t))) put(t, "endAt", t.optLong("endAt") + ms);
        else if ("paused".equals(run(t))) put(t, "left", t.optLong("left") + ms);
        else return;
        put(t, "v", now);
    }
    /** The countdown reached zero. Returns true when it should alert; then starts the next round or stops. */
    static boolean timerFire(JSONObject t, long now) {
        if (!"running".equals(run(t))) return false;
        long end = t.optLong("endAt");
        if (now < end - 1500) return false;           // early (the alarm is set again for endAt)
        int n = times(t), round = Math.max(1, t.optInt("round", 1));
        put(t, "fired", now); put(t, "v", now);
        if (n == 0 || round < n) {
            long next = end + len(t);
            while (next <= now) next += len(t);       // the phone was off: carry on from now
            put(t, "round", round + 1); put(t, "endAt", next);
        } else {
            put(t, "run", "idle"); put(t, "round", 0); put(t, "endAt", 0); put(t, "left", len(t));
        }
        return true;
    }

    static long swElapsed(JSONObject s, long now) {
        long acc = s.optLong("acc", 0);
        return "running".equals(run(s)) ? acc + Math.max(0, now - s.optLong("startAt", now)) : acc;
    }
    static void swStart(JSONObject s, long now) {
        if ("running".equals(run(s))) return;
        if ("idle".equals(run(s))) { put(s, "acc", 0); put(s, "laps", new JSONArray()); }
        put(s, "startAt", now); put(s, "run", "running"); put(s, "v", now);
    }
    static void swPause(JSONObject s, long now) {
        if (!"running".equals(run(s))) return;
        put(s, "acc", swElapsed(s, now)); put(s, "run", "paused"); put(s, "v", now);
    }
    static void swLap(JSONObject s, long now) {
        if (!"running".equals(run(s))) return;
        JSONArray l = s.optJSONArray("laps"); if (l == null) l = new JSONArray();
        if (l.length() >= 99) return;
        l.put(swElapsed(s, now)); put(s, "laps", l); put(s, "v", now);
    }
    static void swReset(JSONObject s, long now) {
        put(s, "run", "idle"); put(s, "acc", 0); put(s, "startAt", 0); put(s, "laps", new JSONArray()); put(s, "v", now);
    }

    /** "04:12" or "1:04:12" */
    static String hms(long ms) {
        long s = Math.max(0, (ms + 999) / 1000), h = s / 3600, m = (s % 3600) / 60, x = s % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, x) : String.format(java.util.Locale.US, "%02d:%02d", m, x);
    }
}
