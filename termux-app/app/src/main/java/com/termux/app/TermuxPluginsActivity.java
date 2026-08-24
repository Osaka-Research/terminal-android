package com.termux.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.settings.SettingsBackupUtils;
import com.termux.app.update.UpdateChecker;
import com.termux.app.update.UpdateInstaller;
import com.termux.app.update.UpdateManifest;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Lists every plugin merged into this app (API/Boot/Widget/Float/Styling/Tasker/GUI) with a
 * one-tap way to open each one's own settings screen, instead of hunting for 7 separate app
 * icons the way the unmerged, upstream Termux layout requires.
 *
 * Launching a plugin's activity is a plain in-process {@link Intent} now (same applicationId),
 * not a cross-app intent -- unlike the old separately-installed layout, this can't fail with
 * "app not installed" for a plugin that's actually merged in.
 */
public class TermuxPluginsActivity extends AppCompatActivity {

    private static class Plugin {
        final int nameRes;
        final int descriptionRes;
        final String activityClassName;

        Plugin(int nameRes, int descriptionRes, String activityClassName) {
            this.nameRes = nameRes;
            this.descriptionRes = descriptionRes;
            this.activityClassName = activityClassName;
        }
    }

    // Each plugin module builds with a non-transitive R class (the AGP 8+ default), so its own
    // string resources (like its app-name label) are only visible through *that module's* R
    // class, not this app module's -- hence the fully-qualified references below rather than
    // importing them all as the same simple name "R".
    private static final List<Plugin> PLUGINS = Arrays.asList(
        new Plugin(com.termux.api.R.string.termux_api_app_name, R.string.plugin_description_api, "com.termux.api.activities.TermuxAPIMainActivity"),
        new Plugin(com.termux.boot.R.string.termux_boot_app_name, R.string.plugin_description_boot, "com.termux.boot.BootActivity"),
        new Plugin(com.termux.widget.R.string.termux_widget_app_name, R.string.plugin_description_widget, "com.termux.widget.activities.TermuxWidgetMainActivity"),
        new Plugin(com.termux.window.R.string.termux_float_app_name, R.string.plugin_description_float, "com.termux.window.TermuxFloatActivity"),
        new Plugin(com.termux.styling.R.string.termux_styling_app_name, R.string.plugin_description_styling, "com.termux.styling.TermuxStyleActivity"),
        new Plugin(com.termux.tasker.R.string.application_name, R.string.plugin_description_tasker, "com.termux.tasker.activities.TermuxTaskerMainActivity"),
        new Plugin(com.termux.gui.R.string.termux_gui_app_name, R.string.plugin_description_gui, "com.termux.gui.GUIConfigActivity")
    );

    private final ActivityResultLauncher<String> mExportLauncher =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::onExportLocationChosen);
    private final ActivityResultLauncher<String[]> mImportLauncher =
        registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportFileChosen);

    private TermuxAppSharedPreferences mPreferences;
    private EditText mUpdatesUrlInput;
    private TextView mUpdatesStatusText;
    private Button mUpdatesInstallButton;
    @Nullable private UpdateManifest mPendingUpdate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux_plugins);
        setTitle(R.string.title_activity_termux_plugins);

        mPreferences = TermuxAppSharedPreferences.build(this);

        LinearLayout list = findViewById(R.id.plugins_list);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);

        for (Plugin plugin : PLUGINS) {
            list.addView(buildRow(plugin, padding));
        }

        findViewById(R.id.plugins_button_export_settings).setOnClickListener(v ->
            mExportLauncher.launch("termux-settings-backup.json"));
        findViewById(R.id.plugins_button_import_settings).setOnClickListener(v ->
            mImportLauncher.launch(new String[]{"application/json"}));

        setUpUpdatesSection();
    }

    private void setUpUpdatesSection() {
        mUpdatesUrlInput = findViewById(R.id.updates_url_input);
        mUpdatesStatusText = findViewById(R.id.updates_status_text);
        mUpdatesInstallButton = findViewById(R.id.updates_button_install);
        CheckBox autoCheckBox = findViewById(R.id.updates_auto_check_checkbox);
        Button checkNowButton = findViewById(R.id.updates_button_check_now);

        if (mPreferences != null) {
            mUpdatesUrlInput.setText(mPreferences.getUpdateCheckUrl());
            autoCheckBox.setChecked(mPreferences.isUpdateAutoCheckEnabled());
        }

        autoCheckBox.setOnCheckedChangeListener((buttonView, checked) -> {
            if (mPreferences != null) mPreferences.setUpdateAutoCheckEnabled(checked);
        });

        checkNowButton.setOnClickListener(v -> {
            String url = mUpdatesUrlInput.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.updates_url_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            if (mPreferences != null) mPreferences.setUpdateCheckUrl(url);

            mUpdatesStatusText.setText(R.string.updates_status_checking);
            mUpdatesInstallButton.setVisibility(View.GONE);
            mPendingUpdate = null;

            UpdateChecker.checkNow(this, url, (manifest, error) -> {
                if (error != null) {
                    mUpdatesStatusText.setText(getString(R.string.updates_status_error, error));
                } else if (manifest == null) {
                    mUpdatesStatusText.setText(R.string.updates_status_up_to_date);
                } else {
                    mPendingUpdate = manifest;
                    mUpdatesStatusText.setText(getString(R.string.updates_status_available, manifest.versionName));
                    mUpdatesInstallButton.setVisibility(View.VISIBLE);
                }
            });
        });

        mUpdatesInstallButton.setOnClickListener(v -> {
            if (mPendingUpdate == null) return;

            if (!UpdateInstaller.canInstallPackages(this)) {
                Toast.makeText(this, R.string.updates_permission_needed, Toast.LENGTH_LONG).show();
                UpdateInstaller.requestInstallPermission(this);
                return;
            }

            UpdateInstaller.startDownload(this, mPendingUpdate);
            Toast.makeText(this, R.string.update_download_title, Toast.LENGTH_SHORT).show();
        });
    }

    private void onExportLocationChosen(@Nullable Uri uri) {
        if (uri == null) return; // user cancelled the picker
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("could not open destination");
            SettingsBackupUtils.export(this, out);
            Toast.makeText(this, R.string.settings_backup_export_done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.settings_backup_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void onImportFileChosen(@Nullable Uri uri) {
        if (uri == null) return; // user cancelled the picker
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("could not open source");
            int restored = SettingsBackupUtils.doImport(this, in);
            Toast.makeText(this, getString(R.string.settings_backup_import_done, restored), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.settings_backup_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private View buildRow(Plugin plugin, int padding) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, padding, 0, padding);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColumnParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textColumn.setLayoutParams(textColumnParams);

        TextView title = new TextView(this);
        title.setText(plugin.nameRes);
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);

        TextView description = new TextView(this);
        description.setText(plugin.descriptionRes);
        description.setTextSize(13);

        textColumn.addView(title);
        textColumn.addView(description);

        Button openButton = new Button(this);
        openButton.setText(R.string.plugin_open);
        openButton.setOnClickListener(v -> openPlugin(plugin));

        row.addView(textColumn);
        row.addView(openButton);
        return row;
    }

    private void openPlugin(Plugin plugin) {
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), plugin.activityClassName);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, plugin.activityClassName + " not found", Toast.LENGTH_SHORT).show();
        }
    }

}
