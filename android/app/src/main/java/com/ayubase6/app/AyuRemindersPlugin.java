package com.ayubase6.app;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

/**
 * Bridge between the web app and the phone's reminders (see src/android-bridge.js).
 *   setRules({rules})          store the reminder rules and set the next alarm (rules null = all off)
 *   show({key,title,body,...})  show a notification now (tests, previews, the eye timer)
 *   cancel({key})               remove a notification
 *   checkPermissions / requestPermissions   notifications (Android 13+ asks), plus exact-alarm state
 *   openExactAlarmSettings / openNotificationSettings / openBatterySettings
 *   getStatus()                 next alarm time and permission state
 * Event "reminderTap" {key, section}: a notification was tapped.
 */
@CapacitorPlugin(
    name = "AyuReminders",
    permissions = { @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS }) }
)
public class AyuRemindersPlugin extends Plugin {

    @Override
    public void load() {
        ReminderScheduler.ensureChannels(getContext());
        if (getActivity() != null) deliverTap(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        deliverTap(intent);
    }

    private void deliverTap(Intent intent) {
        if (intent == null || !intent.hasExtra(ReminderScheduler.EXTRA_SECTION)) return;
        JSObject d = new JSObject();
        d.put("key", intent.getStringExtra(ReminderScheduler.EXTRA_KEY));
        d.put("section", intent.getStringExtra(ReminderScheduler.EXTRA_SECTION));
        intent.removeExtra(ReminderScheduler.EXTRA_SECTION);
        intent.removeExtra(ReminderScheduler.EXTRA_KEY);
        notifyListeners("reminderTap", d, true);
    }

    private String notificationState() {
        boolean enabled = NotificationManagerCompat.from(getContext()).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return enabled ? "granted" : "denied";
        PermissionState s = getPermissionState("notifications");
        if (s == PermissionState.GRANTED) return enabled ? "granted" : "denied";
        return s == PermissionState.DENIED ? "denied" : "prompt";
    }

    private boolean batteryUnrestricted() {
        PowerManager pm = (PowerManager) getContext().getSystemService(android.content.Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
    }

    private JSObject status() {
        JSObject r = new JSObject();
        r.put("notifications", notificationState());
        r.put("exactAlarm", ReminderScheduler.canExact(getContext()));
        r.put("batteryUnrestricted", batteryUnrestricted());
        long next = ReminderScheduler.nextAt(getContext());
        if (next > 0) r.put("next", next); else r.put("next", JSONObject.NULL);
        r.put("rules", ReminderScheduler.loadRules(getContext()) != null);
        return r;
    }

    @Override
    @PluginMethod
    public void checkPermissions(PluginCall call) { call.resolve(status()); }

    @Override
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "notificationsCallback");
        } else {
            call.resolve(status());
        }
    }

    @PermissionCallback
    private void notificationsCallback(PluginCall call) { call.resolve(status()); }

    @PluginMethod
    public void getStatus(PluginCall call) { call.resolve(status()); }

    @PluginMethod
    public void setRules(PluginCall call) {
        JSONObject rules = call.getObject("rules", null);
        ReminderScheduler.saveRules(getContext(), rules);
        long next = ReminderScheduler.reschedule(getContext());
        JSObject r = new JSObject();
        if (next > 0) r.put("next", next); else r.put("next", JSONObject.NULL);
        r.put("exact", ReminderScheduler.canExact(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void show(PluginCall call) {
        String key = call.getString("key", "note");
        String color = call.getString("color", "");
        // A colour is sent only when the notification belongs to the open section (the Help test); otherwise its own section decides.
        NotificationArt.Theme theme;
        try { theme = (color != null && color.matches("^#[0-9a-fA-F]{6}$")) ? NotificationArt.fromColor(android.graphics.Color.parseColor(color)) : NotificationArt.forSection(call.getString("section", "")); }
        catch (Exception e) { theme = NotificationArt.REMINDERS; }
        ReminderScheduler.show(getContext(), key, call.getString("title", "AyuBase6"), call.getString("body", ""),
            Boolean.TRUE.equals(call.getBoolean("silent", false)), theme, call.getString("section", ""), call.getString("tapKey", ""));
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        ReminderScheduler.cancel(getContext(), call.getString("key", ""));
        call.resolve();
    }

    private void openSettings(PluginCall call, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
        } catch (Exception e) {
            Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
            details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { getActivity().startActivity(details); } catch (Exception ignored) {}
        }
        call.resolve();
    }

    @PluginMethod
    public void openExactAlarmSettings(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openSettings(call, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getContext().getPackageName())));
        } else call.resolve();
    }

    @PluginMethod
    public void openNotificationSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
        openSettings(call, i);
    }

    @PluginMethod
    public void openBatterySettings(PluginCall call) {
        openSettings(call, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }
}
