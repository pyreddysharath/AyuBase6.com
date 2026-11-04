package com.ayubase6.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** Checks → Alarm: due once a day at its minute, only on the Checks days, only when on. */
public class ReminderAlarmTest {
    private static List<ReminderRules.Due> at(String rules, int min, int dow) throws Exception {
        return ReminderRules.due(new JSONObject(rules), new ReminderRules.Local("2026-11-02", min, dow), 0);
    }

    @Test
    public void ringsAtWakeUpTimeOnChosenDays() throws Exception {
        String r = "{\"checks\":{\"items\":[],\"days\":[1,2,3,4,5],\"alarm\":{\"on\":true,\"at\":390,\"title\":\"Alarm · 6:30 AM\",\"body\":\"Good morning!\"}}}";
        List<ReminderRules.Due> d = at(r, 390, 1);
        assertEquals(1, d.size());
        assertEquals("alarm", d.get(0).key);
        assertEquals("Alarm · 6:30 AM", d.get(0).title);
        assertTrue(at(r, 391, 1).isEmpty());
        assertTrue(at(r, 390, 0).isEmpty());   // Sunday is not chosen
    }

    @Test
    public void offMeansNoAlarm() throws Exception {
        assertTrue(at("{\"checks\":{\"items\":[],\"alarm\":{\"on\":false,\"at\":390}}}", 390, 1).isEmpty());
    }
}
