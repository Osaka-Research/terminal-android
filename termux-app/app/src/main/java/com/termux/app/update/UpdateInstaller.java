package com.termux.app.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;

/**
 * Handles the "yes, get this update" half of the flow: requests the unknown-sources permission
 * if needed, then queues the APK download via {@link DownloadManager}. The actual install
 * prompt is triggered later by {@link UpdateDownloadReceiver} once the download finishes and its
 * checksum (if the manifest provided one) checks out.
 */
public class UpdateInstaller {

    private static final String LOG_TAG = "UpdateInstaller";

    static final String STATE_PREFS_NAME = "termux_update_download_state";
    static final String KEY_DOWNLOAD_ID = "download_id";
    static final String KEY_EXPECTED_SHA256 = "expected_sha256";

    public static boolean canInstallPackages(@NonNull Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.getPackageManager().canRequestPackageInstalls();
    }

    /** Sends the user to the per-app "install unknown apps" toggle. There's no result callback
     * for this in a way that works reliably across OEMs, so the caller should just let the user
     * tap the update button again after granting it. */
    public static void requestInstallPermission(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** @return the DownloadManager download id, or -1 if enqueueing failed. */
    public static long startDownload(@NonNull Context context, @NonNull UpdateManifest manifest) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) return -1;

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(manifest.apkUrl))
                .setTitle("Termux update " + manifest.versionName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "termux-update.apk")
                .setMimeType("application/vnd.android.package-archive");

            long downloadId = downloadManager.enqueue(request);

            SharedPreferences state = context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE);
            state.edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_EXPECTED_SHA256, manifest.sha256) // may be null -- receiver treats that as "unverified"
                .apply();

            return downloadId;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "failed to enqueue update download", e);
            return -1;
        }
    }

}
