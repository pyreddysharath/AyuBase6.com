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

    private static java.lang.ref.WeakReference<AyuRemindersPlugin> current = new java.lang.ref.WeakReference<>(null);

    /** A notification button was used: tell the open app to fetch and apply it (Done ticks, Turn off switches off). */
    static void actionsWaiting() {
        AyuRemindersPlugin p = current.get();
        if (p != null) p.notifyListeners("reminderAction", new JSObject(), true);
    }

    @PluginMethod
    public void takeActions(PluginCall call) {
        JSObject r = new JSObject();
        r.put("actions", ReminderScheduler.takePending(getContext()));
        call.resolve(r);
    }

    @Override
    public void load() {
        current = new java.lang.ref.WeakReference<>(this);
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
        try {
            android.content.pm.PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            r.put("versionCode", Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode);
            r.put("versionName", pi.versionName);
        } catch (Exception ignored) {}
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
        ReminderScheduler.keepPendingInForce(getContext(), rules);
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

    // ── Settings pages for a permission: as soon as it is allowed there, the app comes back by itself ──
    private static final int REQ_SETTINGS = 7101;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable watcher;

    private void openAndReturn(PluginCall call, Intent intent, java.util.function.BooleanSupplier allowed) {
        if (allowed.getAsBoolean()) { call.resolve(status()); return; }
        // Opened in this app's task (no NEW_TASK) and for a result, so the app can close the page again.
        try { getActivity().startActivityForResult(intent, REQ_SETTINGS); }
        catch (Exception e) {
            try { getActivity().startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName())), REQ_SETTINGS); }
            catch (Exception ignored) { call.resolve(status()); return; }
        }
        if (watcher != null) main.removeCallbacks(watcher);
        final long until = android.os.SystemClock.uptimeMillis() + 3 * 60 * 1000;
        watcher = new Runnable() {
            @Override public void run() {
                if (allowed.getAsBoolean()) {
                    watcher = null;
                    try { getActivity().finishActivity(REQ_SETTINGS); } catch (Exception ignored) {}
                    // If the page lives in another task, bring the app to the front instead.
                    main.postDelayed(() -> {
                        try {
                            if (!getActivity().hasWindowFocus()) {
                                getActivity().startActivity(new Intent(getContext(), MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK));
                            }
                        } catch (Exception ignored) {}
                    }, 350);
                    new Thread(() -> ReminderScheduler.reschedule(getContext()), "ayu-perm").start();
                    notifyListeners("permissionsChanged", status());
                    return;
                }
                if (android.os.SystemClock.uptimeMillis() < until) main.postDelayed(this, 400);
                else watcher = null;
            }
        };
        main.postDelayed(watcher, 600);
        call.resolve(status());
    }

    @PluginMethod
    public void openExactAlarmSettings(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openAndReturn(call, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getContext().getPackageName())),
                () -> ReminderScheduler.canExact(getContext()));
        } else call.resolve(status());
    }

    @PluginMethod
    public void openNotificationSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
        openAndReturn(call, i, () -> NotificationManagerCompat.from(getContext()).areNotificationsEnabled());
    }

    /** Opens a web address in the phone's browser (e.g. the new app version to download). Only https. */
    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url", "");
        if (url == null || !url.startsWith("https://")) { call.reject("https only"); return; }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject("No browser"); }
    }

    /**
     * Saves a file the app made (the Backup file, PDFs): to Downloads on Android 10+, otherwise through the share sheet.
     * The web view cannot download the page's own files, so the bridge hands them here.
     */
    @PluginMethod
    public void saveFile(PluginCall call) {
        String name = call.getString("name", "AyuBase6.json").replaceAll("[\\\\/:*?\"<>|]", "_");
        String text = call.getString("text", ""), b64 = call.getString("base64", null);
        String mime = call.getString("mime", "application/json");
        byte[] data = b64 != null ? android.util.Base64.decode(b64, android.util.Base64.DEFAULT) : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        JSObject r = new JSObject();
        r.put("name", name);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContext().getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new java.io.IOException("no uri");
                try (java.io.OutputStream os = getContext().getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new java.io.IOException("no stream");
                    os.write(data);
                }
                r.put("where", "downloads");
            } else {
                java.io.File f = new java.io.File(getContext().getCacheDir(), name);
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) { os.write(data); }
                Uri uri = androidx.core.content.FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
                Intent send = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getActivity().startActivity(Intent.createChooser(send, "Save " + name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                r.put("where", "share");
            }
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Could not save the file");
        }
    }

    // ── Checks alarm ringtones ──
    private android.media.MediaPlayer preview;
    private final android.os.Handler previewStop = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Keeps the person's own ringtone (one) in the app's private storage. */
    @PluginMethod
    public void saveTone(PluginCall call) {
        String b64 = call.getString("base64", "");
        try {
            byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            if (data.length == 0 || data.length > 8 * 1024 * 1024) { call.reject("Choose a sound file under 8 MB"); return; }
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(AlarmService.ownTone(getContext()))) { os.write(data); }
            call.resolve();
        } catch (Exception e) { call.reject("Could not save the sound"); }
    }

    /** Plays a ringtone for a few seconds: an app tone id, "phone" (the phone's default alarm) or "own". */
    @PluginMethod
    public void previewTone(PluginCall call) {
        stopPreviewNow();
        String tone = call.getString("tone", "rise");
        android.net.Uri uri;
        if ("phone".equals(tone)) {
            uri = android.media.RingtoneManager.getActualDefaultRingtoneUri(getContext(), android.media.RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE);
        } else if ("own".equals(tone) && AlarmService.ownTone(getContext()).exists()) {
            uri = android.net.Uri.fromFile(AlarmService.ownTone(getContext()));
        } else uri = AlarmService.rawTone(getContext(), tone);
        try {
            preview = new android.media.MediaPlayer();
            preview.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            preview.setDataSource(getContext(), uri);
            preview.setLooping(!"phone".equals(tone) && !"own".equals(tone));
            preview.prepare(); preview.start();
            previewStop.postDelayed(this::stopPreviewNow, 6000);
            call.resolve();
        } catch (Exception e) { stopPreviewNow(); call.reject("Could not play this sound"); }
    }

    @PluginMethod
    public void stopPreview(PluginCall call) { stopPreviewNow(); call.resolve(); }

    private void stopPreviewNow() {
        previewStop.removeCallbacksAndMessages(null);
        try { if (preview != null) { preview.stop(); preview.release(); } } catch (Exception ignored) {}
        preview = null;
        notifyListeners("previewEnded", new JSObject());
    }

    @PluginMethod
    public void openBatterySettings(PluginCall call) {
        openSettings(call, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }
}
