package com.termux.app.update;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.BuildConfig;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fetches and parses the update manifest, off the main thread. No Play Store, no in-app update
 * API -- this is the same "check a JSON file, compare version codes" approach F-Droid clients
 * and Obtainium use for sideloaded APKs.
 */
public class UpdateChecker {

    private static final String LOG_TAG = "UpdateChecker";

    /** How often {@link #autoCheckIfDue} actually hits the network, at most. */
    private static final long AUTO_CHECK_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface Callback {
        /** {@code manifest} is null if already up to date or the check failed (see logs/toast for why). */
        @MainThread
        void onResult(@Nullable UpdateManifest manifest, @Nullable String error);
    }

    /** Runs a check unconditionally, right now, and reports back on the main thread. */
    public static void checkNow(@NonNull Context context, @NonNull String manifestUrl, @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.submit(() -> {
            String error = null;
            UpdateManifest manifest = null;
            try {
                manifest = fetch(manifestUrl);
                if (manifest != null && manifest.versionCode <= BuildConfig.VERSION_CODE) {
                    manifest = null; // already up to date
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "update check failed", e);
                error = e.getMessage();
            }

            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(appContext);
            if (preferences != null) preferences.setUpdateLastCheckedAt(System.currentTimeMillis());

            UpdateManifest finalManifest = manifest;
            String finalError = error;
            MAIN_HANDLER.post(() -> callback.onResult(finalManifest, finalError));
        });
    }

    /**
     * Checks only if auto-check is enabled, a URL is configured, and it's been at least
     * {@link #AUTO_CHECK_INTERVAL_MILLIS} since the last check. Meant to be called cheaply and
     * often (e.g. every app start) -- it no-ops instantly unless actually due.
     */
    public static void autoCheckIfDue(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null || !preferences.isUpdateAutoCheckEnabled()) return;

        String url = preferences.getUpdateCheckUrl();
        if (url == null || url.isEmpty()) return;

        if (System.currentTimeMillis() - preferences.getUpdateLastCheckedAt() < AUTO_CHECK_INTERVAL_MILLIS) return;

        checkNow(context, url, (manifest, error) -> {
            if (manifest != null) {
                UpdateNotifier.notifyUpdateAvailable(context, manifest);
            }
        });
    }

    @WorkerThread
    @NonNull
    private static UpdateManifest fetch(@NonNull String manifestUrl) throws Exception {
        if (!manifestUrl.startsWith("https://"))
            throw new IllegalArgumentException("update check URL must be https:// (got: " + manifestUrl + ")");

        URL url = new URL(manifestUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);

        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK)
                throw new IllegalStateException("update manifest fetch failed: HTTP " + code);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            String apkUrl = json.getString("apk_url");
            if (!apkUrl.startsWith("https://"))
                throw new IllegalArgumentException("apk_url must be https:// (got: " + apkUrl + ")");

            return new UpdateManifest(
                json.getInt("version_code"),
                json.optString("version_name", ""),
                apkUrl,
                json.has("sha256") ? json.getString("sha256") : null,
                json.has("notes") ? json.getString("notes") : null
            );
        } finally {
            connection.disconnect();
        }
    }

}
