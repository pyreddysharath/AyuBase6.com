package com.ayubase6.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AyuRemindersPlugin.class);
        super.onCreate(savedInstanceState);
        // Text at the same size as the website: the phone's font-size setting does not enlarge the app's text.
        // (Pinch-zoom still works.) To follow the phone again, remove this line.
        if (getBridge() != null && getBridge().getWebView() != null) {
            android.webkit.WebView wv = getBridge().getWebView();
            wv.getSettings().setTextZoom(100);
            // Ads: the website's AdSense runs in the app through Google's WebView API for Ads.
            wv.getSettings().setJavaScriptEnabled(true);
            wv.getSettings().setDomStorageEnabled(true);
            wv.getSettings().setMediaPlaybackRequiresUserGesture(false);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
            try { com.google.android.gms.ads.MobileAds.registerWebView(wv); } catch (Throwable ignored) {}
        }
        try { AnnouncementCheck.schedule(this); } catch (Exception ignored) {}
    }

    // Announcements: no notification while the app is in use (the banner shows in the app)
    @Override public void onResume() { super.onResume(); AnnouncementCheck.appVisible = true; }
    @Override public void onPause() { AnnouncementCheck.appVisible = false; super.onPause(); }
}
