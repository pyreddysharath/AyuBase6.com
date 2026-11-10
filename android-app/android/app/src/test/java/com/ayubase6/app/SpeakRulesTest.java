package com.ayubase6.app;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class SpeakRulesTest {
    @Test public void checksSpeakInTheVoiceLanguage() throws Exception {
        JSONObject it = new JSONObject().put("key", "water").put("title", "Drink Water").put("body", "x").put("at", 600).put("say", "నీళ్ళు తాగండి");
        JSONObject ck = new JSONObject().put("items", new JSONArray().put(it)).put("speak", new JSONObject().put("on", true).put("voice", "te-in-x-tea-local"));
        List<ReminderRules.Due> d = ReminderRules.due(new JSONObject().put("checks", ck), new ReminderRules.Local("2026-11-07", 600, 6), 0);
        assertEquals(1, d.size());
        assertEquals("నీళ్ళు తాగండి", d.get(0).say);
        assertEquals("te-in-x-tea-local", d.get(0).voice);
    }

    @Test public void speakOffSaysNothing() throws Exception {
        JSONObject it = new JSONObject().put("key", "water").put("title", "Drink Water").put("body", "x").put("at", 600).put("say", "నీళ్ళు తాగండి");
        JSONObject ck = new JSONObject().put("items", new JSONArray().put(it));
        List<ReminderRules.Due> d = ReminderRules.due(new JSONObject().put("checks", ck), new ReminderRules.Local("2026-11-07", 600, 6), 0);
        assertEquals("", d.get(0).say);
    }
}
