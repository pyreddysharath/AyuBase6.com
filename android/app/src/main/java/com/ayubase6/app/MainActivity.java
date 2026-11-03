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
            getBridge().getWebView().getSettings().setTextZoom(100);
        }
    }
}
