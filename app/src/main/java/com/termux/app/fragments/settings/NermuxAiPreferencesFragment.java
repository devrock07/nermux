package com.termux.app.fragments.settings;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.ai.NermuxAiConfig;

public class NermuxAiPreferencesFragment extends NermuxPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(NermuxAiConfig.PREFS_NAME);
        setPreferencesFromResource(R.xml.nermux_ai_preferences, rootKey);

        configureProvider();
        configureApiKey();
        configureModel();
        configureBaseUrl();
        configureSwitch(NermuxAiConfig.KEY_INCLUDE_TERMINAL_CONTEXT);
        configureSwitch(NermuxAiConfig.KEY_ALLOW_RUN_COMMANDS);
    }

    private void configureProvider() {
        ListPreference providerPreference = findPreference(NermuxAiConfig.KEY_PROVIDER);
        if (providerPreference == null) return;

        providerPreference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        if (TextUtils.isEmpty(providerPreference.getValue()))
            providerPreference.setValue(NermuxAiConfig.PROVIDER_OPENAI);

        providerPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String provider = String.valueOf(newValue);
            EditTextPreference modelPreference = findPreference(NermuxAiConfig.KEY_MODEL);
            if (modelPreference != null)
                modelPreference.setText(NermuxAiConfig.getDefaultModel(provider));

            EditTextPreference baseUrlPreference = findPreference(NermuxAiConfig.KEY_BASE_URL);
            if (baseUrlPreference != null) {
                baseUrlPreference.setVisible(NermuxAiConfig.PROVIDER_CUSTOM_OPENAI.equals(provider));
                if (!NermuxAiConfig.PROVIDER_CUSTOM_OPENAI.equals(provider))
                    baseUrlPreference.setText(NermuxAiConfig.getDefaultBaseUrl(provider));
            }
            return true;
        });
    }

    private void configureApiKey() {
        EditTextPreference apiKeyPreference = findPreference(NermuxAiConfig.KEY_API_KEY);
        if (apiKeyPreference == null) return;

        apiKeyPreference.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(true);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        });
        apiKeyPreference.setSummaryProvider(preference -> {
            String value = ((EditTextPreference) preference).getText();
            return TextUtils.isEmpty(value) ? getString(R.string.nermux_ai_api_key_missing) : getString(R.string.nermux_ai_api_key_configured);
        });
    }

    private void configureModel() {
        EditTextPreference modelPreference = findPreference(NermuxAiConfig.KEY_MODEL);
        if (modelPreference == null) return;

        modelPreference.setOnBindEditTextListener(EditText::setSingleLine);
        if (TextUtils.isEmpty(modelPreference.getText())) {
            String provider = getPreferenceManager().getSharedPreferences()
                .getString(NermuxAiConfig.KEY_PROVIDER, NermuxAiConfig.PROVIDER_OPENAI);
            modelPreference.setText(NermuxAiConfig.getDefaultModel(provider));
        }
        modelPreference.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
    }

    private void configureBaseUrl() {
        EditTextPreference baseUrlPreference = findPreference(NermuxAiConfig.KEY_BASE_URL);
        if (baseUrlPreference == null) return;

        String provider = getPreferenceManager().getSharedPreferences()
            .getString(NermuxAiConfig.KEY_PROVIDER, NermuxAiConfig.PROVIDER_OPENAI);

        baseUrlPreference.setVisible(NermuxAiConfig.PROVIDER_CUSTOM_OPENAI.equals(provider));
        baseUrlPreference.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(true);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        });
        baseUrlPreference.setSummaryProvider(preference -> {
            String value = ((EditTextPreference) preference).getText();
            return TextUtils.isEmpty(value) ? getString(R.string.nermux_ai_base_url_summary) : value;
        });
    }

    private void configureSwitch(@NonNull String key) {
        SwitchPreferenceCompat switchPreference = findPreference(key);
        if (switchPreference == null) return;
    }
}
