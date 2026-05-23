package com.termux.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.fragments.settings.NermuxPreferenceFragment;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

public class SettingsActivity extends AppCompatActivity {

    private static final String UPSTREAM_TERMUX_APP_URL = "https://github.com/termux/termux-app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        int systemBarColor = ContextCompat.getColor(this, R.color.nermux_terminal_background);
        getWindow().setStatusBarColor(systemBarColor);
        getWindow().setNavigationBarColor(systemBarColor);

        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new RootPreferencesFragment())
                .commit();
        }

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(R.string.action_open_settings);

        Toolbar toolbar = findViewById(com.termux.shared.R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundResource(R.drawable.nermux_workspace_header_background);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.nermux_text_primary));
            toolbar.setSubtitleTextColor(ContextCompat.getColor(this, R.color.nermux_text_secondary));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class RootPreferencesFragment extends NermuxPreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null) return;

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            configureTermuxAPIPreference(context);
            configureTermuxFloatPreference(context);
            configureTermuxTaskerPreference(context);
            configureTermuxWidgetPreference(context);
            configureAboutPreference(context);
            configureDonatePreference(context);
        }

        private void configureTermuxAPIPreference(@NonNull Context context) {
            Preference termuxAPIPreference = findPreference("termux_api");
            if (termuxAPIPreference != null) {
                TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxAPIPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxFloatPreference(@NonNull Context context) {
            Preference termuxFloatPreference = findPreference("termux_float");
            if (termuxFloatPreference != null) {
                TermuxFloatAppSharedPreferences preferences = TermuxFloatAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxFloatPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxTaskerPreference(@NonNull Context context) {
            Preference termuxTaskerPreference = findPreference("termux_tasker");
            if (termuxTaskerPreference != null) {
                TermuxTaskerAppSharedPreferences preferences = TermuxTaskerAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxTaskerPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxWidgetPreference(@NonNull Context context) {
            Preference termuxWidgetPreference = findPreference("termux_widget");
            if (termuxWidgetPreference != null) {
                TermuxWidgetAppSharedPreferences preferences = TermuxWidgetAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxWidgetPreference.setVisible(preferences != null);
            }
        }

        private void configureAboutPreference(@NonNull Context context) {
            Preference aboutPreference = findPreference("about");
            if (aboutPreference != null) {
                aboutPreference.setOnPreferenceClickListener(preference -> {
                    new Thread() {
                        @Override
                        public void run() {
                            String title = context.getString(R.string.about_preference_title);

                            StringBuilder aboutString = new StringBuilder();
                            aboutString.append("# Nermux\n\n");
                            aboutString.append("Nermux is an independent Termux-powered clone/fork focused on a cleaner blue UI, smoother session controls, haptics, and a built-in workspace editor/file explorer.\n\n");
                            aboutString.append("The project keeps the Termux runtime model so existing packages and shell workflows still make sense, while the app experience is being rebuilt for Nermux.\n\n");
                            aboutString.append("## Runtime note\n\n");
                            aboutString.append("The Android package id currently remains `com.termux` because the bootstrap packages are compiled for `/data/data/com.termux/files/usr`. A full `com.nermux` migration needs a rebuilt bootstrap/package ecosystem.\n\n");
                            aboutString.append("## Support Nermux\n\n");
                            aboutString.append("Litecoin donations help support the fork:\n\n`").append(TermuxConstants.TERMUX_DONATE_LTC_ADDRESS).append("`\n\n");
                            aboutString.append("## Upstream credit\n\n");
                            aboutString.append("Nermux is based on Termux. Upstream app source: ").append(UPSTREAM_TERMUX_APP_URL).append("\n\n");
                            aboutString.append("## License\n\n");
                            aboutString.append("Nermux follows the upstream Termux app license: GPLv3-only for the app/root project, with documented exceptions for bundled/shared libraries. Original Termux copyright and attribution remain intact.\n\n");
                            aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                            aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true));

                            String userActionName = UserAction.ABOUT.getName();

                            ReportInfo reportInfo = new ReportInfo(userActionName,
                                TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                            reportInfo.setReportString(aboutString.toString());
                            reportInfo.setReportSaveFileLabelAndPath(userActionName,
                                Environment.getExternalStorageDirectory() + "/" +
                                    FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

                            ReportActivity.startReportActivity(context, reportInfo);
                        }
                    }.start();

                    return true;
                });
            }
        }

        private void configureDonatePreference(@NonNull Context context) {
            Preference donatePreference = findPreference("donate");
            if (donatePreference != null) {
                donatePreference.setVisible(true);

                donatePreference.setOnPreferenceClickListener(preference -> {
                    new Thread() {
                        @Override
                        public void run() {
                            String title = context.getString(R.string.donate_preference_title);

                            StringBuilder donateString = new StringBuilder();
                            donateString.append("# Support Nermux\n\n");
                            donateString.append("Nermux is a Termux clone/fork with a blue, cleaner app experience and a native workspace editor.\n\n");
                            donateString.append("Litecoin donation address:\n\n`").append(TermuxConstants.TERMUX_DONATE_LTC_ADDRESS).append("`\n\n");
                            donateString.append("Thanks for supporting the fork and the work needed to polish the terminal, settings, sessions, and editor flow.\n\n");
                            donateString.append("## Upstream\n\n");
                            donateString.append("Nermux is based on Termux and keeps upstream credit intact: ").append(UPSTREAM_TERMUX_APP_URL).append("\n");

                            String userActionName = "donate";

                            ReportInfo reportInfo = new ReportInfo(userActionName,
                                TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                            reportInfo.setReportString(donateString.toString());
                            reportInfo.setReportSaveFileLabelAndPath(userActionName,
                                Environment.getExternalStorageDirectory() + "/" +
                                    FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

                            ReportActivity.startReportActivity(context, reportInfo);
                        }
                    }.start();
                    return true;
                });
            }
        }
    }

}
