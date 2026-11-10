package com.ayubase6.app;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Every-N-minutes reminders count from the moment they were turned on ("anchor"), inside their hours. */
public class ReminderAnchorTest {
    private static List<Integer> dueMinutes(String rulesJson, long dayStartMin) throws Exception {
        JSONObject rules = new JSONObject(rulesJson);
        List<Integer> out = new ArrayList<>();
        for (int m = 0; m < 1440; m++) {
            if (!ReminderRules.due(rules, new ReminderRules.Local("2026-11-03", m, 2), dayStartMin + m).isEmpty()) out.add(m);
        }
        return out;
    }

    @Test
    public void countsFromTurnOnTime() throws Exception {
        long day = 29894400L;                        // 3 Nov 2026 00:00 UTC, in minutes
        long anchorMs = (day + 607) * 60000L + 30000; // turned on at 10:07:30
        String r = "{\"reminders\":[{\"key\":\"drink\",\"title\":\"Water\",\"body\":\"x\",\"every\":20,\"from\":540,\"to\":720,\"anchor\":" + anchorMs + "}]}";
        List<Integer> got = dueMinutes(r, day);
        List<Integer> want = new ArrayList<>();
        for (int m = 627; m <= 720; m += 20) want.add(m);   // 10:27, 10:47 ... 11:47 (not 9:00, 9:20 ...)
        assertEquals(want, got);
    }

    @Test
    public void withoutAnchorStartsAtWindowStart() throws Exception {
        String r = "{\"reminders\":[{\"key\":\"drink\",\"title\":\"Water\",\"body\":\"x\",\"every\":60,\"from\":540,\"to\":720}]}";
        List<Integer> want = new ArrayList<>();
        for (int m = 540; m <= 720; m += 60) want.add(m);
        assertEquals(want, dueMinutes(r, 29894400L));
    }
}
