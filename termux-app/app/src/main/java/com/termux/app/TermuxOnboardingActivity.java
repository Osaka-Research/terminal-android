package com.termux.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * First-run permission wizard. Termux used to be several separately-installed apps, each asking
 * for its own permissions the first time you touched that specific feature (open Termux:Float,
 * get a permission dialog; run a termux-api command, get another one; ...). Merged into one app,
 * all of that can be surfaced up front instead, in one place, with a plain-language reason for
 * each ask -- and skippable, since none of these are required just to get a shell.
 */
public class TermuxOnboardingActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE = PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;
    private static final int REQUEST_NOTIFICATIONS = 3000;
    private static final int REQUEST_DISPLAY_OVER_APPS = PermissionUtils.REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION;
    private static final int REQUEST_BATTERY = PermissionUtils.REQUEST_DISABLE_BATTERY_OPTIMIZATIONS;

    private TermuxAppSharedPreferences mPreferences;

    /** Returns {@code true} if the wizard was shown (caller should not proceed with its own
     * onCreate()), {@code false} if onboarding is already done and the caller can continue. */
    public static boolean showIfNeeded(@NonNull AppCompatActivity from) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(from);
        if (preferences != null && preferences.isOnboardingCompleted())
            return false;

        from.startActivity(new Intent(from, TermuxOnboardingActivity.class));
        from.finish();
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        mPreferences = TermuxAppSharedPreferences.build(this);

        findViewById(R.id.onboarding_button_storage).setOnClickListener(v -> requestStorage());
        findViewById(R.id.onboarding_button_notifications).setOnClickListener(v -> requestNotifications());
        findViewById(R.id.onboarding_button_display_over_apps).setOnClickListener(v -> requestDisplayOverApps());
        findViewById(R.id.onboarding_button_battery).setOnClickListener(v -> requestBattery());
        findViewById(R.id.onboarding_button_continue).setOnClickListener(v -> finishOnboarding());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Overlay/battery grants happen in the system Settings app, which doesn't reliably
        // deliver onActivityResult() on every OEM skin -- re-check everything on resume instead.
        refreshStatuses();
    }

    private void requestStorage() {
        if (PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(this, REQUEST_STORAGE, false)) {
            TermuxInstaller.setupStorageSymlinks(this);
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionUtils.requestPermission(this, Manifest.permission.POST_NOTIFICATIONS, REQUEST_NOTIFICATIONS);
        }
    }

    private void requestDisplayOverApps() {
        PermissionUtils.requestDisplayOverOtherAppsPermission(this, REQUEST_DISPLAY_OVER_APPS);
    }

    private void requestBattery() {
        PermissionUtils.requestDisableBatteryOptimizations(this, REQUEST_BATTERY);
    }

    private void finishOnboarding() {
        if (mPreferences != null) mPreferences.setOnboardingCompleted(true);
        startActivity(new Intent(this, TermuxActivity.class));
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            // Legacy (pre-R) storage permission comes back through this callback rather than
            // onActivityResult(); MANAGE_EXTERNAL_STORAGE (R+) comes back through onActivityResult().
            if (PermissionUtils.checkStoragePermission(this, true))
                TermuxInstaller.setupStorageSymlinks(this);
        }
        refreshStatuses();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_STORAGE && PermissionUtils.checkStoragePermission(this, false)) {
            TermuxInstaller.setupStorageSymlinks(this);
        }
        refreshStatuses();
    }

    private void refreshStatuses() {
        setRowStatus(R.id.onboarding_status_storage, R.id.onboarding_button_storage,
            PermissionUtils.checkStoragePermission(this, PermissionUtils.isLegacyExternalStoragePossible(this)));

        boolean notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || PermissionUtils.checkPermission(this, Manifest.permission.POST_NOTIFICATIONS);
        setRowStatus(R.id.onboarding_status_notifications, R.id.onboarding_button_notifications, notificationsGranted);

        setRowStatus(R.id.onboarding_status_display_over_apps, R.id.onboarding_button_display_over_apps,
            PermissionUtils.checkDisplayOverOtherAppsPermission(this));

        setRowStatus(R.id.onboarding_status_battery, R.id.onboarding_button_battery,
            PermissionUtils.checkIfBatteryOptimizationsDisabled(this));
    }

    private void setRowStatus(int statusViewId, int buttonViewId, boolean granted) {
        TextView statusView = findViewById(statusViewId);
        statusView.setText(granted ? R.string.onboarding_status_granted : R.string.onboarding_status_not_granted);

        Button button = findViewById(buttonViewId);
        button.setEnabled(!granted);
    }

}
