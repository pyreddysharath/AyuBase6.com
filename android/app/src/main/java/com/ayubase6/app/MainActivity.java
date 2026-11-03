package com.ayubase6.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AyuRemindersPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
