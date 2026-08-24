package com.termux.app.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Registered dynamically (not in the manifest) from {@code TermuxApplication.onCreate()} --
 * {@code DownloadManager.ACTION_DOWNLOAD_COMPLETE} is one of the broadcasts Android's
 * background-execution limits (since Oreo) don't guarantee delivery to manifest-declared
 * receivers for, so a static {@code <receiver>} entry here would silently never fire on some
 * OS versions. Runtime registration always works as long as this process is alive, which for
 * Termux -- generally kept alive by its own foreground TermuxService -- is the common case.
 */
public class UpdateDownloadReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "UpdateDownloadReceiver";

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;

        long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (completedId == -1) return;

        SharedPreferences state = context.getSharedPreferences(UpdateInstaller.STATE_PREFS_NAME, Context.MODE_PRIVATE);
        long expectedId = state.getLong(UpdateInstaller.KEY_DOWNLOAD_ID, -1);
        if (expectedId == -1 || completedId != expectedId) return; // some other app's download, not ours

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) return;

        if (!wasDownloadSuccessful(downloadManager, completedId)) {
            Logger.logError(LOG_TAG, "update download did not complete successfully");
            Toast.makeText(context, "Update download failed", Toast.LENGTH_SHORT).show();
            state.edit().clear().apply();
            return;
        }

        Uri fileUri = downloadManager.getUriForDownloadedFile(completedId);
        if (fileUri == null) {
            Logger.logError(LOG_TAG, "downloaded update has no content uri");
            state.edit().clear().apply();
            return;
        }

        String expectedSha256 = state.getString(UpdateInstaller.KEY_EXPECTED_SHA256, null);
        if (expectedSha256 != null) {
            String actualSha256 = sha256(context, fileUri);
            if (actualSha256 == null || !actualSha256.equalsIgnoreCase(expectedSha256)) {
                Logger.logError(LOG_TAG, "update checksum mismatch -- refusing to install (expected "
                    + expectedSha256 + ", got " + actualSha256 + ")");
                Toast.makeText(context, "Update checksum did not match -- not installing", Toast.LENGTH_LONG).show();
                state.edit().clear().apply();
                return;
            }
        } else {
            Logger.logWarn(LOG_TAG, "update manifest had no sha256 -- installing unverified");
        }

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(fileUri, "application/vnd.android.package-archive");
        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(installIntent);

        state.edit().clear().apply();
    }

    private static boolean wasDownloadSuccessful(DownloadManager downloadManager, long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) return false;
            int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            return statusColumn >= 0 && cursor.getInt(statusColumn) == DownloadManager.STATUS_SUCCESSFUL;
        }
    }

    private static String sha256(Context context, Uri fileUri) {
        try (InputStream in = context.getContentResolver().openInputStream(fileUri)) {
            if (in == null) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "failed to hash downloaded update", e);
            return null;
        }
    }

}
