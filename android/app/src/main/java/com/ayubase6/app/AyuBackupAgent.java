package com.ayubase6.app;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

/**
 * Backs up the app's data copy (written by the web page through the plugin) to the person's Google account,
 * using Android's key/value backup. Android restores it when the app is installed again.
 * Each time Android takes the backup, the time is noted (not backed up itself), so the app can show "Last backed up".
 */
public class AyuBackupAgent extends BackupAgentHelper {
    static final String PREFS = "ayu_backup", META = "ayu_backup_meta";

    @Override
    public void onCreate() {
        addHelper("data", new SharedPreferencesBackupHelper(this, PREFS));
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
        super.onBackup(oldState, data, newState);
        getSharedPreferences(META, MODE_PRIVATE).edit().putLong("last", System.currentTimeMillis()).commit();
    }
}
