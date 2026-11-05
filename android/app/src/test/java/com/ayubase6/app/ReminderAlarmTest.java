package com.ayubase6.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** Alarms (rules.alarms): due once a day at their minute, only on their days, only when on. */
public class ReminderAlarmTest {
    private static List<ReminderRules.Due> at(String rules, int min, int dow) throws Exception {
        return ReminderRules.due(new JSONObject(rules), new ReminderRules.Local("2026-11-02", min, dow), 0);
    }

    @Test
    public void wakeAlarmRingsAtStartTimeOnChosenDays() throws Exception {
        String r = "{\"alarms\":[{\"id\":\"wake\",\"on\":true,\"at\":390,\"days\":[1,2,3,4,5],\"section\":\"routines\",\"title\":\"Alarm · 6:30 AM\",\"body\":\"Good morning!\"}]}";
        List<ReminderRules.Due> d = at(r, 390, 1);
        assertEquals(1, d.size());
        assertEquals("alarm-wake", d.get(0).key);
        assertEquals("routines", d.get(0).section);
        assertEquals("Alarm · 6:30 AM", d.get(0).title);
        assertTrue(at(r, 391, 1).isEmpty());
        assertTrue(at(r, 390, 0).isEmpty());   // Sunday is not chosen
    }

    @Test
    public void twoAlarmsAtAnyTimeOfDay() throws Exception {
        String r = "{\"alarms\":[{\"id\":\"wake\",\"at\":1320,\"title\":\"Alarm · 10:00 PM\",\"body\":\"x\"},"
            + "{\"id\":\"rem\",\"at\":5,\"days\":[0,6],\"section\":\"alerts\",\"title\":\"Alarm · 12:05 AM\",\"body\":\"y\"}]}";
        assertEquals("alarm-wake", at(r, 1320, 3).get(0).key);
        List<ReminderRules.Due> d = at(r, 5, 6);
        assertEquals(1, d.size());
        assertEquals("alarm-rem", d.get(0).key);
        assertEquals("alerts", d.get(0).section);
        assertTrue(at(r, 5, 2).isEmpty());
    }

    @Test
    public void manyRemindersAlarms() throws Exception {
        String r = "{\"alarms\":[{\"id\":\"rem\",\"at\":420,\"section\":\"alerts\",\"title\":\"Alarm · 7:00 AM\",\"body\":\"x\"},"
            + "{\"id\":\"remkqzxwabc\",\"at\":420,\"section\":\"alerts\",\"title\":\"Gym · 7:00 AM\",\"body\":\"y\"},"
            + "{\"id\":\"remzzzzzzzz\",\"at\":421,\"section\":\"alerts\",\"title\":\"Alarm · 7:01 AM\",\"body\":\"z\"}]}";
        List<ReminderRules.Due> d = at(r, 420, 2);
        assertEquals(2, d.size());
        assertEquals("alarm-rem", d.get(0).key);
        assertEquals("alarm-remkqzxwabc", d.get(1).key);
        assertEquals("Gym · 7:00 AM", d.get(1).title);
        assertEquals("alarm-remzzzzzzzz", at(r, 421, 2).get(0).key);
    }

    @Test
    public void vibrateFlagTravels() throws Exception {
        String r = "{\"alarms\":[{\"id\":\"rem\",\"at\":420,\"section\":\"alerts\",\"title\":\"A\",\"body\":\"x\",\"vibrate\":false},"
            + "{\"id\":\"wake\",\"at\":420,\"title\":\"B\",\"body\":\"y\"}]}";
        List<ReminderRules.Due> d = at(r, 420, 2);
        assertEquals(false, d.get(0).vibrate);
        assertEquals(true, d.get(1).vibrate);
    }

    @Test
    public void offOrBadIdMeansNoAlarm() throws Exception {
        assertTrue(at("{\"alarms\":[{\"id\":\"wake\",\"on\":false,\"at\":390}]}", 390, 1).isEmpty());
        assertTrue(at("{\"alarms\":[{\"id\":\"Wake-1\",\"at\":390}]}", 390, 1).isEmpty());
    }
}
