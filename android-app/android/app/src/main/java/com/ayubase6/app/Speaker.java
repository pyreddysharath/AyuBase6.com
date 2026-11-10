package com.ayubase6.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Speak: reads a reminder out loud with the phone's text-to-speech (the voice chosen in the app, or the phone's default). */
final class Speaker {
    private Speaker() {}

    /** Speaks and waits until done (at most about 12 s). Call off the main thread. */
    static void say(Context c, String text, String voiceName) { say(c, text, voiceName, ""); }

    /** lang (e.g. "te"): used when the voice is not on the phone (or none was chosen). */
    static void say(Context c, String text, String voiceName, String lang) {
        if (text == null || text.trim().isEmpty()) return;
        final CountDownLatch ready = new CountDownLatch(1), done = new CountDownLatch(1);
        final TextToSpeech[] t = new TextToSpeech[1];
        final int[] status = {TextToSpeech.ERROR};
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> t[0] = new TextToSpeech(c.getApplicationContext(), s -> { status[0] = s; ready.countDown(); }));
        try {
            if (!ready.await(5, TimeUnit.SECONDS) || status[0] != TextToSpeech.SUCCESS || t[0] == null) { stop(main, t[0]); return; }
            TextToSpeech tts = t[0];
            tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            if (!pick(tts, voiceName) && lang != null && !lang.isEmpty()) {
                try { tts.setLanguage(Locale.forLanguageTag(lang)); } catch (Exception ignored) {}
            }
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { done.countDown(); }
                @Override public void onError(String id) { done.countDown(); }
            });
            Thread.sleep(700);   // after the notification sound
            tts.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, null, "ayu-say");
            done.await(12, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        } finally { stop(main, t[0]); }
    }

    private static boolean pick(TextToSpeech tts, String voiceName) {
        try {
            if (voiceName != null && !voiceName.isEmpty() && tts.getVoices() != null)
                for (Voice v : tts.getVoices()) if (v.getName().equals(voiceName)) { tts.setVoice(v); return true; }
        } catch (Exception ignored) {}
        return false;
    }

    private static void stop(Handler main, TextToSpeech tts) { if (tts != null) main.post(() -> { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {} }); }

    /** The phone's installed voices: name, language, a label, and male / female when the voice name says so. */
    static void voices(Context c, java.util.function.Consumer<org.json.JSONArray> back) {
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
            final TextToSpeech[] t = new TextToSpeech[1];
            t[0] = new TextToSpeech(c.getApplicationContext(), s -> {
                org.json.JSONArray out = new org.json.JSONArray();
                try {
                    if (s == TextToSpeech.SUCCESS && t[0].getVoices() != null) for (Voice v : t[0].getVoices()) {
                        // only voices that can speak now: not "not installed" (speech data still to download)
                        if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                        Locale l = v.getLocale(); String n = v.getName(), low = n.toLowerCase(Locale.US);
                        String g = low.contains("female") ? "Female" : low.contains("male") ? "Male" : "";
                        out.put(new org.json.JSONObject().put("name", n).put("lang", l.toLanguageTag())
                            .put("label", l.getDisplayName(Locale.ENGLISH)).put("gender", g).put("net", v.isNetworkConnectionRequired()));
                    }
                } catch (Exception ignored) {}
                back.accept(out);
                try { t[0].shutdown(); } catch (Exception ignored) {}
            });
        });
    }
}
