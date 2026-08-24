package com.termux.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.logger.LogHistory;

/**
 * Shows the last {@code LogHistory.MAX_ENTRIES} log lines from every merged plugin, newest
 * first. See {@link LogHistory} for why this exists.
 */
public class TermuxLogViewerActivity extends AppCompatActivity {

    private TextView mTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);
        setTitle(R.string.title_activity_log_viewer);

        mTextView = findViewById(R.id.log_viewer_text);

        findViewById(R.id.log_viewer_button_share).setOnClickListener(v -> share());
        findViewById(R.id.log_viewer_button_clear).setOnClickListener(v -> {
            LogHistory.clear();
            refresh();
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String text = LogHistory.formatAsText();
        mTextView.setText(text.isEmpty() ? getString(R.string.log_viewer_empty) : text);
    }

    private void share() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, LogHistory.formatAsText());
        startActivity(Intent.createChooser(intent, getString(R.string.log_viewer_action_share)));
    }

}
