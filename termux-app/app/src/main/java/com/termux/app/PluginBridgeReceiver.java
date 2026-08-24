package com.termux.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.plugins.PluginBridge;

import java.util.TreeSet;

/**
 * External entry point into {@link PluginBridge}: lets a shell command reach it the same way
 * termux-api commands reach {@code TermuxApiReceiver} --
 *
 *   am broadcast -n com.termux/.app.PluginBridgeReceiver \
 *       --es plugin boot --es action runNow
 *
 * With no "plugin"/"action" extras, replies with the list of registered actions instead (useful
 * for discovering what's available without reading source).
 */
public class PluginBridgeReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "PluginBridgeReceiver";

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String pluginId = intent.getStringExtra("plugin");
        String action = intent.getStringExtra("action");

        if (pluginId == null || action == null) {
            String listing = String.join("\n", new TreeSet<>(PluginBridge.listActions()));
            Logger.logInfo(LOG_TAG, "no plugin/action extras given; registered actions:\n" + listing);
            if (isOrderedBroadcast()) {
                setResultCode(1);
                setResultData(listing);
            }
            return;
        }

        Bundle args = intent.getExtras() != null ? intent.getExtras() : new Bundle();
        Bundle result = PluginBridge.call(pluginId, action, args);

        Logger.logInfo(LOG_TAG, pluginId + ":" + action + " -> " + result);
        if (isOrderedBroadcast()) {
            boolean ok = result.getBoolean("ok", false);
            setResultCode(ok ? 0 : 1);
            setResultData(result.toString());
        }
    }

}
