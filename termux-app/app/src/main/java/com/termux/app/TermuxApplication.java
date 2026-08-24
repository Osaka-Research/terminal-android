package com.termux.app;

import android.app.Application;
import android.content.Context;

import com.termux.BuildConfig;
import com.termux.shared.errors.Error;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.am.TermuxAmSocketServer;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.theme.TermuxThemeUtils;

public class TermuxApplication extends Application {

    private static final String LOG_TAG = "TermuxApplication";

    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();

        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(this);

        // Set log config for the app
        setLogConfig(context);

        Logger.logDebug("Starting Application");

        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);

        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(context);

        // Init app wide shell manager
        TermuxShellManager shellManager = TermuxShellManager.init(context);

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode());

        // termux-api/boot/widget/float are merged into this app instead of being separately
        // installed, so their own Application subclasses (TermuxAPIApplication etc.) never run.
        // Most of what they did there (crash handler, night mode, log tag) is already covered
        // above -- the one piece with no equivalent here is termux-api's socket listener, which
        // TermuxApiReceiver/ResultReturner depend on to answer `termux-api` client requests.
        com.termux.api.util.ResultReturner.setContext(this);
        com.termux.api.SocketListener.createSocketListener(this);

        // Termux:GUI's own Application subclass (com.termux.gui.App) never runs for the same
        // reason -- it stores itself as a static singleton other GUI code depends on, so that
        // has to be set explicitly too.
        com.termux.gui.App.initForEmbeddedHost(this);

        registerPluginBridgeActions();

        androidx.core.content.ContextCompat.registerReceiver(this, new com.termux.app.update.UpdateDownloadReceiver(),
            new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        com.termux.app.update.UpdateChecker.autoCheckIfDue(this);

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo(LOG_TAG, "Termux files directory is accessible");

            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n" + error);
                return;
            }

            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
        } else {
            Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n" + error);
        }

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this);

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this);
        }
    }

    /**
     * Registers the concrete {@link com.termux.shared.termux.plugins.PluginBridge} actions this
     * build ships out of the box. Any plugin can add more of its own at any point (not just
     * here) by calling {@code PluginBridge.register(...)} -- these two are just the ones wired
     * up so far, to prove the mechanism end-to-end rather than leave it as unused infrastructure.
     */
    private void registerPluginBridgeActions() {
        com.termux.shared.termux.plugins.PluginBridge.register("boot", "runNow", args -> {
            String summary = com.termux.boot.BootReceiver.runBootScripts(this);
            android.os.Bundle result = new android.os.Bundle();
            result.putBoolean("ok", true);
            result.putString("summary", summary);
            return result;
        });

        com.termux.shared.termux.plugins.PluginBridge.register("widget", "refresh", args -> {
            com.termux.widget.TermuxWidgetProvider.sendIntentToRefreshAllWidgets(this, LOG_TAG);
            android.os.Bundle result = new android.os.Bundle();
            result.putBoolean("ok", true);
            return result;
        });
    }

    public static void setLogConfig(Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);

        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
    }

}
