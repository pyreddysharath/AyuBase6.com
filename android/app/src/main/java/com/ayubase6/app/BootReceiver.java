package com.ayubase6.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Sets the reminder alarm again after a restart, an app update, a clock or time-zone change,
 * and when "Alarms & reminders" is allowed (so the alarm becomes exact).
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final PendingResult done = goAsync();
        new Thread(() -> {
            try { ReminderScheduler.reschedule(app); try { ClockNotifier.refresh(app); } catch (Exception ignored) {} try { AnnouncementCheck.schedule(app); } catch (Exception ignored) {} try { UpdateCheck.schedule(app); } catch (Exception ignored) {} }
            finally { done.finish(); }
        }, "ayu-boot").start();
    }
}
