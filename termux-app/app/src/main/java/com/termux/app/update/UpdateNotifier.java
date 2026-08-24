package com.termux.app.update;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxPluginsActivity;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.termux.TermuxConstants;

/** Posts (or clears) the "update available" notification. Tapping it opens the Plugins screen's
 * Updates section, where the actual download+install is a deliberate, explicit tap -- this
 * mechanism never downloads or installs anything on its own. */
public class UpdateNotifier {

    public static void notifyUpdateAvailable(@NonNull Context context, @NonNull UpdateManifest manifest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtils.setupNotificationChannel(context, TermuxConstants.TERMUX_UPDATE_NOTIFICATION_CHANNEL_ID,
                TermuxConstants.TERMUX_UPDATE_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        }

        Intent contentIntent = new Intent(context, TermuxPluginsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = context.getString(R.string.update_notification_text, manifest.versionName);

        Notification.Builder builder = NotificationUtils.geNotificationBuilder(context,
            TermuxConstants.TERMUX_UPDATE_NOTIFICATION_CHANNEL_ID, Notification.PRIORITY_DEFAULT,
            context.getString(R.string.update_notification_title), text, manifest.notes,
            pendingIntent, null, NotificationUtils.NOTIFICATION_MODE_SOUND);
        if (builder == null) return;

        builder.setSmallIcon(R.drawable.ic_service_notification);
        builder.setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null)
            notificationManager.notify(TermuxConstants.TERMUX_UPDATE_NOTIFICATION_ID, builder.build());
    }

    public static void clear(@NonNull Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null)
            notificationManager.cancel(TermuxConstants.TERMUX_UPDATE_NOTIFICATION_ID);
    }

}
