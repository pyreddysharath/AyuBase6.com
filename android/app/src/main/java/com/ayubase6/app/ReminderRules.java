package com.ayubase6.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Works out which reminders are due at one minute, from the rules the web app builds (ayuPush.buildRules).
 * This is a direct port of the push server's rules.js, so the phone and the server agree on every reminder.
 */
final class ReminderRules {
    private ReminderRules() {}
    private static final Pattern KEY = Pattern.compile("^[a-z]+$");

    static final class Due {
        final String key, title, body, section;
        Due(String key, String title, String body, String section) {
            this.key = key; this.title = title; this.body = body; this.section = section;
        }
    }

    /** Local time of one minute: day as yyyy-mm-dd, minute of the day, day of week (0 = Sunday). */
    static final class Local {
        final String day; final int min; final int dow;
        Local(String day, int min, int dow) { this.day = day; this.min = min; this.dow = dow; }
    }

    private static Double num(JSONObject o, String k) {
        if (o == null || !o.has(k) || o.isNull(k)) return null;
        Object v = o.opt(k);
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            return (Double.isNaN(d) || Double.isInfinite(d)) ? null : d;
        }
        return null;
    }

    private static String str(Object v, int n) {
        String s = v == null || v == JSONObject.NULL ? "" : String.valueOf(v);
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static boolean hasInt(JSONArray a, int v) {
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) {
            Object x = a.opt(i);
            if (x instanceof Number && ((Number) x).doubleValue() == v) return true;
        }
        return false;
    }

    private static boolean hasStr(JSONArray a, String v) {
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) if (v.equals(a.optString(i, null))) return true;
        return false;
    }

    /** "only on the chosen days": an empty or missing list means every day. */
    private static boolean dayAllowed(JSONObject o, int dow) {
        JSONArray days = o.optJSONArray("days");
        return days == null || days.length() == 0 || hasInt(days, dow);
    }

    // Reminders repeat from "from", every N minutes, up to "to" inclusive (overnight windows allowed).
    private static boolean inReminderSlot(JSONObject r, int m) {
        Double a = num(r, "from"), b = num(r, "to"), every = num(r, "every");
        if (every == null || every <= 0) return false;
        double from = a == null ? 0 : a, to = b == null ? 0 : b;
        if (a == null || b == null || from == to) { from = 0; to = 1439; }
        double delta;
        if (from < to) { if (m < from || m > to) return false; delta = m - from; }
        else if (m >= from) delta = m - from;
        else if (m <= to) delta = m + 1440 - from;
        else return false;
        return delta % every == 0;
    }

    static List<Due> due(JSONObject rules, Local local, long epochMin) {
        List<Due> out = new ArrayList<>();
        if (rules == null) return out;
        int m = local.min;

        JSONArray reminders = rules.optJSONArray("reminders");
        if (reminders != null) for (int i = 0; i < reminders.length(); i++) {
            JSONObject r = reminders.optJSONObject(i);
            if (r == null || !KEY.matcher(r.optString("key")).matches()) continue;
            if (!dayAllowed(r, local.dow)) continue;
            if (inReminderSlot(r, m)) out.add(new Due(r.optString("key"), str(r.opt("title"), 80), str(r.opt("body"), 200), "alerts"));
        }

        JSONObject ex = rules.optJSONObject("exercise");
        if (ex != null && hasInt(ex.optJSONArray("weekdays"), local.dow) && hasInt(ex.optJSONArray("times"), m)) {
            out.add(new Due("eyeex", str(ex.opt("title"), 80), str(ex.opt("body"), 200), "alerts"));
        }

        JSONObject ck = rules.optJSONObject("checks");
        JSONArray items = ck == null ? null : ck.optJSONArray("items");
        if (items != null && dayAllowed(ck, local.dow)) {
            JSONArray done = local.day.equals(ck.optString("doneDay")) ? ck.optJSONArray("done") : null;
            String mode = ck.optString("mode");
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null || !KEY.matcher(it.optString("key")).matches() || it.optBoolean("muted", false)) continue;
                Double at = num(it, "at"), end = num(it, "end");
                if (at == null) continue;
                String key = it.optString("key");
                if (m == at) out.add(new Due("routine-" + key, str(it.opt("title"), 80), str(it.opt("body"), 200), "routines"));
                if ("beforeend".equals(mode) && end != null && end - 15 > at && m == end - 15 && !hasStr(done, key)) {
                    out.add(new Due("routine-end-" + key, str(it.opt("title"), 70) + " · ending soon",
                        "This window ends in 15 minutes (" + str(it.opt("window"), 40) + ").", "routines"));
                }
            }
        }

        JSONArray custom = rules.optJSONArray("custom");
        if (custom != null) for (int i = 0; i < custom.length(); i++) {
            JSONObject c = custom.optJSONObject(i);
            if (c == null || c.optString("name").isEmpty()) continue;
            if (!dayAllowed(c, local.dow)) continue;
            boolean isDue = false;
            if ("regular".equals(c.optString("mode"))) {
                Double every = num(c, "every"), anchor = num(c, "anchor");
                long anchorMin = (long) Math.floor((anchor == null ? 0 : anchor) / 60000d);
                long ev = every == null ? 0 : (long) Math.floor(every);
                isDue = ev > 0 && epochMin > anchorMin && (epochMin - anchorMin) % ev == 0;
            } else {
                isDue = hasInt(c.optJSONArray("times"), m);
            }
            if (isDue) out.add(new Due("custom" + str(c.opt("id"), 12).replaceAll("[^A-Za-z0-9]", ""),
                str(c.opt("name"), 80), "It is time for your reminder.", "alerts"));
        }
        return out;
    }
}
