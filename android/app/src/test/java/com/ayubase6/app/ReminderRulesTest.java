package com.ayubase6.app;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** The phone must agree with the push server (rules.js) on every minute of a sample week (parity.json). */
public class ReminderRulesTest {
    @Test
    public void matchesServerRules() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("parity.json");
        String text = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        JSONObject fx = new JSONObject(text);
        JSONObject rules = fx.getJSONObject("rules"), expected = fx.getJSONObject("expected");
        long start = fx.getLong("start");
        int minutes = fx.getInt("minutes"), checked = 0;
        for (long t = start; t < start + minutes; t++) {
            ZonedDateTime z = Instant.ofEpochMilli(t * 60000L).atZone(ZoneOffset.UTC);
            ReminderRules.Local local = new ReminderRules.Local(
                String.format(Locale.US, "%04d-%02d-%02d", z.getYear(), z.getMonthValue(), z.getDayOfMonth()),
                z.getHour() * 60 + z.getMinute(), z.getDayOfWeek().getValue() % 7);
            List<ReminderRules.Due> got = ReminderRules.due(rules, local, t);
            JSONArray want = expected.optJSONArray(String.valueOf(t));
            StringBuilder g = new StringBuilder(), w = new StringBuilder();
            for (ReminderRules.Due d : got) g.append(d.key).append('|').append(d.title).append('|').append(d.body).append('|').append(d.section).append('\n');
            if (want != null) for (int i = 0; i < want.length(); i++) {
                JSONObject o = want.getJSONObject(i);
                w.append(o.getString("key")).append('|').append(o.getString("title")).append('|').append(o.getString("body")).append('|').append(o.getString("section")).append('\n');
            }
            assertEquals("minute " + t + " (" + local.day + " " + local.min + ")", w.toString(), g.toString());
            if (want != null) checked++;
        }
        assertEquals(expected.length(), checked);
    }
}
