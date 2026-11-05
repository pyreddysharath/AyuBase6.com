package com.ayubase6.app;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * Backs up the app's data copy (written by the web page through the plugin) to the person's Google account,
 * using Android's key/value backup. Android restores it when the app is installed again.
 */
public class AyuBackupAgent extends BackupAgentHelper {
    static final String PREFS = "ayu_backup";

    @Override
    public void onCreate() {
        addHelper("data", new SharedPreferencesBackupHelper(this, PREFS));
    }
}
