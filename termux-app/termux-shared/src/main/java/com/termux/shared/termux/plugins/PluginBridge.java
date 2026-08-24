package com.termux.shared.termux.plugins;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets merged plugins call each other directly instead of only working standalone. Before the
 * merge this wasn't really possible without going through cross-app IPC per pair of plugins;
 * now that api/boot/widget/float/styling/tasker/gui all run in this one process, one shared
 * registry is enough for any of them (or a shell script, via {@code PluginBridgeReceiver}) to
 * invoke an action another plugin exposes.
 *
 * This is deliberately a thin, generic {@link Bundle}-in/{@link Bundle}-out registry rather than
 * typed per-plugin interfaces -- adding a new bridge action shouldn't require a new interface
 * and a new dependency between two plugin modules that otherwise don't know about each other.
 */
public class PluginBridge {

    public interface Action {
        @NonNull Bundle execute(@NonNull Bundle args);
    }

    private static final String LOG_TAG = "PluginBridge";

    private static final Map<String, Action> ACTIONS = new ConcurrentHashMap<>();

    /** @param pluginId short id like "boot", "widget", "api" -- not the Android package name. */
    public static void register(@NonNull String pluginId, @NonNull String action, @NonNull Action handler) {
        String key = key(pluginId, action);
        ACTIONS.put(key, handler);
        Logger.logDebug(LOG_TAG, "registered: " + key);
    }

    @NonNull
    public static Bundle call(@NonNull String pluginId, @NonNull String action, @Nullable Bundle args) {
        Action handler = ACTIONS.get(key(pluginId, action));
        Bundle result = new Bundle();
        if (handler == null) {
            result.putBoolean("ok", false);
            result.putString("error", "no such plugin action: " + key(pluginId, action));
            return result;
        }
        try {
            return handler.execute(args != null ? args : new Bundle());
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "action \"" + key(pluginId, action) + "\" threw", e);
            result.putBoolean("ok", false);
            result.putString("error", e.toString());
            return result;
        }
    }

    /** Sorted, for a stable listing action (see PluginBridgeReceiver). */
    @NonNull
    public static List<String> listActions() {
        return new ArrayList<>(new TreeMap<>(ACTIONS).keySet());
    }

    private static String key(String pluginId, String action) {
        return pluginId + ":" + action;
    }

}
