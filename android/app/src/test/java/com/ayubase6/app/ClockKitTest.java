package com.ayubase6.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ClockKitTest {
    private JSONObject timer(long ms, int times) throws Exception { return new JSONObject().put("ms", ms).put("times", times); }

    @Test public void timerOnce() throws Exception {
        JSONObject t = timer(60000, 1);
        ClockKit.timerStart(t, 1000);
        assertEquals(61000, t.getLong("endAt"));
        assertFalse(ClockKit.timerFire(t, 30000));
        assertTrue(ClockKit.timerFire(t, 61000));
        assertEquals("idle", t.getString("run"));
    }

    @Test public void timerRepeatsThenStops() throws Exception {
        JSONObject t = timer(10000, 3);
        ClockKit.timerStart(t, 0);
        assertTrue(ClockKit.timerFire(t, 10000)); assertEquals(2, t.getInt("round")); assertEquals(20000, t.getLong("endAt"));
        assertTrue(ClockKit.timerFire(t, 20000)); assertEquals(3, t.getInt("round"));
        assertTrue(ClockKit.timerFire(t, 30000)); assertEquals("idle", t.getString("run"));
        assertFalse(ClockKit.timerFire(t, 40000));
    }

    @Test public void timerUntilStopped() throws Exception {
        JSONObject t = timer(10000, 0);
        ClockKit.timerStart(t, 0);
        for (int i = 1; i <= 20; i++) assertTrue(ClockKit.timerFire(t, i * 10000L));
        assertEquals("running", t.getString("run"));
        assertEquals(21, t.getInt("round"));
        assertTrue(ClockKit.timerFire(t, 1_000_000)); assertTrue(t.getLong("endAt") > 1_000_000);
    }

    @Test public void pauseResumeAdd() throws Exception {
        JSONObject t = timer(60000, 1);
        ClockKit.timerStart(t, 0);
        ClockKit.timerPause(t, 20000); assertEquals("paused", t.getString("run")); assertEquals(40000, t.getLong("left"));
        ClockKit.timerAdd(t, 25000, 60000); assertEquals(100000, t.getLong("left"));
        ClockKit.timerResume(t, 30000); assertEquals(130000, t.getLong("endAt"));
        ClockKit.timerReset(t, 31000); assertEquals("idle", t.getString("run")); assertEquals(60000, t.getLong("left"));
    }

    @Test public void stopwatch() throws Exception {
        JSONObject s = new JSONObject();
        ClockKit.swStart(s, 1000);
        ClockKit.swLap(s, 6000);
        ClockKit.swPause(s, 11000); assertEquals(10000, ClockKit.swElapsed(s, 50000));
        ClockKit.swLap(s, 12000); assertEquals(1, s.getJSONArray("laps").length());
        ClockKit.swStart(s, 20000); assertEquals(15000, ClockKit.swElapsed(s, 25000));
        ClockKit.swReset(s, 26000); assertEquals(0, ClockKit.swElapsed(s, 30000)); assertEquals(0, s.getJSONArray("laps").length());
    }

    @Test public void format() {
        assertEquals("04:12", ClockKit.hms(252000));
        assertEquals("1:00:00", ClockKit.hms(3600000));
        assertEquals("00:01", ClockKit.hms(1));
    }
}
