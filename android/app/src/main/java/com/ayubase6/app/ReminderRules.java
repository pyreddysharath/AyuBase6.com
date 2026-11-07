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
    private static final java.util.regex.Pattern ALARM_ID = java.util.regex.Pattern.compile("[a-z]{1,12}");

    static final class Due {
        final String key, title, body, section;
        boolean vibrate = true;   // Vibrate switch in the app (Checks, Health Breaks, each Personal Reminder); missing means on
        Due(String key, String title, String body, String section) {
            this.key = key; this.title = title; this.body = body; this.section = section;
        }
        String sound = "";        // a tone of the app (Checks → Notify Me → Sound); empty = the phone's notification sound
        String say = "", voice = "";   // Speak: what to read out (empty = nothing) and the voice
        Due vib(JSONObject o) {
            if (o != null) {
                vibrate = o.optBoolean("vibrate", true); String t = o.optString("sound", ""); sound = t.matches("^[a-z]{1,12}$") ? t : "";
                JSONObject sp = o.optJSONObject("speak");
                if (sp != null && sp.optBoolean("on", false)) {
                    String tx = sp.optString("text", "").trim();
                    say = (tx.isEmpty() ? title : tx); if (say.length() > 160) say = say.substring(0, 160);
                    voice = sp.optString("voice", "");
                }
            }
            return this;
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

    // Reminders repeat every N minutes inside the hours "from" to "to" inclusive (overnight windows allowed).
    // With "anchor" (ms, set by the phone app when the reminder was turned on or changed) the count starts from
    // that moment; without it (the push server's rules) it starts at "from".
    private static boolean inReminderSlot(JSONObject r, int m, long epochMin) {
        Double a = num(r, "from"), b = num(r, "to"), every = num(r, "every");
        if (every == null || every <= 0) return false;
        double from = a == null ? 0 : a, to = b == null ? 0 : b;
        if (a == null || b == null || from == to) { from = 0; to = 1439; }
        double delta;
        if (from < to) { if (m < from || m > to) return false; delta = m - from; }
        else if (m >= from) delta = m - from;
        else if (m <= to) delta = m + 1440 - from;
        else return false;
        Double anchor = num(r, "anchor");
        if (anchor != null) {
            long anchorMin = (long) Math.floor(anchor / 60000d), ev = (long) Math.floor(every);
            return ev > 0 && epochMin > anchorMin && (epochMin - anchorMin) % ev == 0;
        }
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
            if (inReminderSlot(r, m, epochMin)) out.add(new Due(r.optString("key"), str(r.opt("title"), 80), str(r.opt("body"), 200), "alerts").vib(r));
        }

        JSONObject ex = rules.optJSONObject("exercise");
        if (ex != null && hasInt(ex.optJSONArray("weekdays"), local.dow) && hasInt(ex.optJSONArray("times"), m)) {
            out.add(new Due("eyeex", str(ex.opt("title"), 80), str(ex.opt("body"), 200), "alerts").vib(ex));
        }

        // Alarms (the phone app only; not in the server rules): Checks → Alarm ("wake", at the start time, on the Checks days)
        // and Reminders → Alarm (any number: "rem" or "rem" + 8 letters, each with its own time and days). Key "alarm-<id>".
        JSONArray alarms = rules.optJSONArray("alarms");
        for (int i = 0; alarms != null && i < alarms.length(); i++) {
            JSONObject al = alarms.optJSONObject(i);
            if (al == null || !al.optBoolean("on", true) || !ALARM_ID.matcher(al.optString("id")).matches()) continue;
            if (!dayAllowed(al, local.dow)) continue;
            Double at = num(al, "at");
            if (at != null && at == m) out.add(new Due("alarm-" + al.optString("id"), str(al.opt("title"), 80), str(al.opt("body"), 200),
                "alerts".equals(al.optString("section")) ? "alerts" : "routines").vib(al));
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
                if (m == at) out.add(new Due("routine-" + key, str(it.opt("title"), 80), str(it.opt("body"), 200), "routines").vib(ck));
                if ("beforeend".equals(mode) && end != null && end - 15 > at && m == end - 15 && !hasStr(done, key)) {
                    out.add(new Due("routine-end-" + key, str(it.opt("title"), 70) + " · ending soon",
                        "This window ends in 15 minutes (" + str(it.opt("window"), 40) + ").", "routines").vib(ck));
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
            } else if ("dates".equals(c.optString("mode"))) {
                isDue = hasInt(c.optJSONArray("times"), m) && onDate(c, local.day);
            } else {
                isDue = hasInt(c.optJSONArray("times"), m);
            }
            if (isDue) out.add(new Due("custom" + str(c.opt("id"), 12).replaceAll("[^A-Za-z0-9]", ""),
                str(c.opt("name"), 80), "It is time for your reminder.", "alerts").vib(c));
        }
        return out;
    }

    /** Personal Reminders "On chosen dates": once (date), weekly (its days), monthly (days of the month), yearly (date).
     *  A day the month does not have (31, or 29 Feb) is the month's last day. day is "yyyy-MM-dd". */
    static boolean onDate(JSONObject c, String day) {
        int y, mo, d;
        try { y = Integer.parseInt(day.substring(0, 4)); mo = Integer.parseInt(day.substring(5, 7)); d = Integer.parseInt(day.substring(8, 10)); }
        catch (Exception e) { return false; }
        int last = java.time.YearMonth.of(y, mo).lengthOfMonth();
        String sched = c.optString("sched", "once");
        switch (sched) {
            case "weekly": return true;   // the days are checked with "days"
            case "monthly": {
                JSONArray md = c.optJSONArray("mdays");
                if (md == null) return false;
                for (int i = 0; i < md.length(); i++) { int v = md.optInt(i, 0); if (v >= 1 && v <= 31 && Math.min(v, last) == d) return true; }
                return false;
            }
            case "yearly": {
                String dt = c.optString("date", "");
                if (dt.length() < 10) return false;
                try { int m2 = Integer.parseInt(dt.substring(5, 7)), d2 = Integer.parseInt(dt.substring(8, 10));
                      return m2 == mo && Math.min(d2, last) == d; } catch (Exception e) { return false; }
            }
            default: return day.equals(c.optString("date", ""));   // once
        }
    }
}
