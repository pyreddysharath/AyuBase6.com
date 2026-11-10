package com.ayubase6.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ReminderDatesTest {
    private JSONObject c(String sched) throws Exception {
        return new JSONObject().put("id", "1").put("name", "Pay rent").put("mode", "dates").put("sched", sched).put("times", new JSONArray().put(540));
    }
    private int due(JSONObject c, String day, int dow) throws Exception {
        JSONObject rules = new JSONObject().put("custom", new JSONArray().put(c));
        return ReminderRules.due(rules, new ReminderRules.Local(day, 540, dow), 0).size();
    }

    @Test public void once() throws Exception {
        JSONObject x = c("once").put("date", "2026-11-12");
        assertEquals(1, due(x, "2026-11-12", 4));
        assertEquals(0, due(x, "2026-11-13", 5));
        assertEquals(0, due(x, "2027-11-12", 5));
    }

    @Test public void weekly() throws Exception {
        JSONObject x = c("weekly").put("days", new JSONArray().put(1).put(3));
        assertEquals(1, due(x, "2026-11-09", 1));
        assertEquals(0, due(x, "2026-11-10", 2));
    }

    @Test public void monthlyLastDay() throws Exception {
        JSONObject x = c("monthly").put("mdays", new JSONArray().put(31).put(15));
        assertEquals(1, due(x, "2026-11-30", 1));   // November has no 31st
        assertEquals(0, due(x, "2026-11-29", 0));
        assertEquals(1, due(x, "2026-12-31", 4));
        assertEquals(0, due(x, "2026-12-30", 3));
        assertEquals(1, due(x, "2027-02-28", 0));
        assertEquals(1, due(x, "2026-11-15", 0));
    }

    @Test public void yearly() throws Exception {
        JSONObject x = c("yearly").put("date", "2024-02-29");
        assertEquals(1, due(x, "2027-02-28", 0));
        assertEquals(1, due(x, "2028-02-29", 2));
        assertEquals(0, due(x, "2028-02-28", 1));
        JSONObject y = c("yearly").put("date", "2026-11-07");
        assertTrue(ReminderRules.onDate(y, "2030-11-07"));
        assertFalse(ReminderRules.onDate(y, "2030-11-08"));
    }

    @Test public void wrongTimeNotDue() throws Exception {
        JSONObject x = c("once").put("date", "2026-11-12");
        JSONObject rules = new JSONObject().put("custom", new JSONArray().put(x));
        assertEquals(0, ReminderRules.due(rules, new ReminderRules.Local("2026-11-12", 541, 4), 0).size());
    }
}
