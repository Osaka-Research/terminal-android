package com.termux.shared.logger;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * In-memory ring buffer of recent log lines from every plugin merged into this app (api/boot/
 * widget/float/styling/tasker/gui all route through {@link Logger}, which is the single place
 * that feeds this). Before the merge, seeing what a specific plugin logged meant `adb logcat`
 * filtered to that plugin's own process -- most users never had that available. This makes
 * "why didn't my boot script run" / "why did the API call fail" answerable from inside the app.
 *
 * Crashes already have their own dedicated file ({@link com.termux.shared.termux.crash.TermuxCrashUtils})
 * and viewer (ReportActivity) -- this is for everything else Logger.log*() emits.
 */
public class LogHistory {

    public static class Entry {
        public final long timestamp;
        public final int priority;
        public final String tag;
        public final String message;

        Entry(long timestamp, int priority, String tag, String message) {
            this.timestamp = timestamp;
            this.priority = priority;
            this.tag = tag;
            this.message = message;
        }
    }

    private static final int MAX_ENTRIES = 500;

    private static final ArrayDeque<Entry> sEntries = new ArrayDeque<>(MAX_ENTRIES);

    public static synchronized void add(int priority, String tag, String message) {
        if (sEntries.size() >= MAX_ENTRIES)
            sEntries.removeFirst();
        sEntries.addLast(new Entry(System.currentTimeMillis(), priority, tag, message));
    }

    /** Newest first. */
    @NonNull
    public static synchronized List<Entry> getEntries() {
        List<Entry> list = new ArrayList<>(sEntries);
        java.util.Collections.reverse(list);
        return list;
    }

    public static synchronized void clear() {
        sEntries.clear();
    }

    @NonNull
    public static String formatAsText() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder();
        for (Entry e : getEntries()) {
            sb.append(sdf.format(new Date(e.timestamp)))
                .append(' ').append(priorityLabel(e.priority))
                .append('/').append(e.tag)
                .append(": ").append(e.message)
                .append('\n');
        }
        return sb.toString();
    }

    private static char priorityLabel(int priority) {
        switch (priority) {
            case android.util.Log.ERROR: return 'E';
            case android.util.Log.WARN: return 'W';
            case android.util.Log.INFO: return 'I';
            case android.util.Log.DEBUG: return 'D';
            case android.util.Log.VERBOSE: return 'V';
            default: return '?';
        }
    }

}
