package com.termux.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Exports/imports the SharedPreferences of every merged plugin as one JSON file, so setting up a
 * new device (or restoring after a reinstall) is "import one file" instead of separately
 * reconfiguring termux.properties-equivalent settings in 7 different plugin UIs.
 *
 * Deliberately NOT Android's Auto Backup / a BackupAgent: this app keeps {@code allowBackup=false}
 * on purpose (upstream Termux's own choice -- most of ~/ is executable binaries and a package
 * cache tied to this specific device's ABI, which auto-restoring onto a different device or
 * Android version would corrupt, not fix). Settings are a tiny, genuinely portable subset of
 * that, so they get an explicit, user-triggered export/import instead of blanket auto-backup.
 *
 * Termux:GUI's settings are excluded on purpose too -- they're stored in
 * {@code EncryptedSharedPreferences} under a device-bound key, so an exported copy could never
 * be decrypted again on a different device or after a reinstall anyway.
 */
public class SettingsBackupUtils {

    private static final String[] PREFERENCE_FILE_BASENAMES = {
        TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
        TermuxConstants.TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
        TermuxConstants.TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
        TermuxConstants.TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
        TermuxConstants.TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
        TermuxConstants.TERMUX_TASKER_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
        TermuxConstants.TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
    };

    public static void export(@NonNull Context context, @NonNull OutputStream out) throws IOException, JSONException {
        JSONObject root = new JSONObject();
        for (String basename : PREFERENCE_FILE_BASENAMES) {
            SharedPreferences prefs = context.getSharedPreferences(basename, Context.MODE_PRIVATE);
            JSONObject entries = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                entries.put(entry.getKey(), toJson(entry.getValue()));
            }
            root.put(basename, entries);
        }

        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(root.toString(2));
        }
    }

    /** @return number of preference files restored. */
    public static int doImport(@NonNull Context context, @NonNull InputStream in) throws IOException, JSONException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
        }
        JSONObject root = new JSONObject(sb.toString());

        int restoredFiles = 0;
        Iterator<String> basenames = root.keys();
        while (basenames.hasNext()) {
            String basename = basenames.next();
            JSONObject entries = root.getJSONObject(basename);
            SharedPreferences prefs = context.getSharedPreferences(basename, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Iterator<String> keys = entries.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                putFromJson(editor, key, entries.get(key));
            }
            editor.apply();
            restoredFiles++;
        }
        return restoredFiles;
    }

    private static Object toJson(Object value) throws JSONException {
        if (value instanceof Set) {
            JSONArray array = new JSONArray();
            for (Object item : (Set<?>) value) array.put(item);
            return array;
        }
        return value; // String/Boolean/Integer/Long/Float all serialize fine as-is
    }

    private static void putFromJson(SharedPreferences.Editor editor, String key, Object value) throws JSONException {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            Set<String> set = new HashSet<>();
            for (int i = 0; i < array.length(); i++) set.add(array.getString(i));
            editor.putStringSet(key, set);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Double) {
            editor.putFloat(key, ((Double) value).floatValue());
        } else {
            editor.putString(key, String.valueOf(value));
        }
    }

}
