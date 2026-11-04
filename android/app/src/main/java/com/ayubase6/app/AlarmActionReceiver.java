package com.ayubase6.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Snooze / Stop / ✓ Done on the ringing alarm's notification. */
public class AlarmActionReceiver extends BroadcastReceiver {
    static final String EX_ACTION = "ayu_alarm_action";

    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmService s = AlarmService.current();
        if (s == null) return;
        String a = intent.getStringExtra(EX_ACTION);
        if ("snooze".equals(a)) s.snooze();
        else if ("done".equals(a)) s.done();
        else s.stopAll();
    }
}
