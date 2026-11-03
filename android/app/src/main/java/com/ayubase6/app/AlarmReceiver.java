package com.ayubase6.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The reminder alarm rang: show what is due and set the next alarm (off the main thread). */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final long minute = intent.getLongExtra(ReminderScheduler.EXTRA_MINUTE, 0);
        final Context app = context.getApplicationContext();
        final PendingResult done = goAsync();
        new Thread(() -> {
            try { ReminderScheduler.onAlarm(app, minute); }
            finally { done.finish(); }
        }, "ayu-alarm").start();
    }
}
