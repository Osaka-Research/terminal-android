package com.termux.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE;

import java.io.File;

/**
 * Drives the separately-installed Termux:X11 companion app (built as the ":lorie-app" module here,
 * "sharedUid" flavor -- see settings.gradle) from inside this app, instead of the user manually
 * running `pkg install termux-x11-nightly`, `termux-x11 :0 &`, and tapping the X11 app icon
 * themselves every time.
 *
 * Three moving pieces, each checked independently since any of them can be missing:
 *   1. The companion APK (com.termux.x11) -- gives us {@link #openDesktop} and, once installed
 *      with a matching signature, same-process access to its MainActivity singleton.
 *   2. The native X server binary (~/../usr/bin/termux-x11, from the termux-x11-nightry pkg) --
 *      there is no APK for this, it's a Linux package; {@link #installNativeBinary} shells out
 *      to `pkg install` for it via the same RUN_COMMAND path boot scripts already use.
 *   3. An actual running session -- {@link #isSessionRunning} reflects into MainActivity's
 *      static getInstance(), which is only non-null once the server has connected to it.
 */
public class TermuxX11Controller {

    private static final String LOG_TAG = "TermuxX11Controller";

    private static final String X11_PACKAGE = "com.termux.x11";
    private static final String X11_MAIN_ACTIVITY = "com.termux.x11.MainActivity";
    private static final String X11_ACTION_STOP = "com.termux.x11.ACTION_STOP";

    public static boolean isCompanionAppInstalled(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(X11_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isNativeServerInstalled() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "termux-x11").exists();
    }

    /**
     * Whether a session is actually connected right now. Uses reflection because :app has no
     * compile-time dependency on :lorie-app -- they're separate APKs and this only resolves to
     * anything once both are installed and (via the shared sharedUserId) actually running in
     * this same process.
     */
    public static boolean isSessionRunning() {
        try {
            Class<?> mainActivityClass = Class.forName(X11_MAIN_ACTIVITY);
            Object instance = mainActivityClass.getMethod("getInstance").invoke(null);
            return instance != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static void installNativeServer(@NonNull Context context) {
        runCommand(context, TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/pkg",
            new String[]{"install", "-y", "termux-x11-nightly"});
    }

    public static void startServer(@NonNull Context context) {
        runCommand(context, TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/termux-x11",
            new String[]{":0"});
    }

    public static void stopServer(@NonNull Context context) {
        Intent intent = new Intent(X11_ACTION_STOP);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public static void openDesktop(@NonNull Context context) {
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), X11_MAIN_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Termux:X11 companion app not installed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Runs an executable via TermuxService's RUN_COMMAND path, the same intent contract
     * third-party apps use (see termux-shared's RUN_COMMAND_SERVICE) -- calling it here just
     * means the caller and the target are the same app.
     */
    private static void runCommand(@NonNull Context context, @NonNull String executable, @NonNull String[] arguments) {
        if (!new File(executable).exists()) {
            Logger.logWarn(LOG_TAG, "runCommand: executable does not exist: " + executable);
            Toast.makeText(context, executable + " not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND);
        intent.setClassName(context.getPackageName(), TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE_NAME);
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, executable);
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, arguments);
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_RUNNER, "app-shell");

        try {
            context.startForegroundService(intent);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "failed to run " + executable, e);
        }
    }

}
