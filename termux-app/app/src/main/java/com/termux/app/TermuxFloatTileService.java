package com.termux.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile that jumps straight to the Termux:Float floating terminal from outside
 * the app -- e.g. mid-way through using another app, without switching away from it first. Only
 * meaningful now that Float is guaranteed to be present (it's merged in, not a separately
 * installed app that might not be there).
 */
public class TermuxFloatTileService extends TileService {

    @Override
    @SuppressWarnings("deprecation") // startActivityAndCollapse(Intent) is deprecated on 34+ in favor of the PendingIntent overload, not removed -- still needed for the pre-34 branch below
    public void onClick() {
        super.onClick();

        Intent intent = new Intent();
        intent.setClassName(getPackageName(), "com.termux.window.TermuxFloatActivity");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

}
