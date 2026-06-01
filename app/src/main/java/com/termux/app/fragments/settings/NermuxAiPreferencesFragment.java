package com.termux.app.fragments.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.NermuxSystemBars;
import com.termux.app.ai.NermuxAiConfig;

public class NermuxAiPreferencesFragment extends NermuxPreferenceFragment {

    private Preference mProviderPreference;
    private Preference mApiKeyPreference;
    private Preference mModelPreference;
    private Preference mBaseUrlPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(NermuxAiConfig.PREFS_NAME);
        setPreferencesFromResource(R.xml.nermux_ai_preferences, rootKey);

        mProviderPreference = findPreference(NermuxAiConfig.KEY_PROVIDER);
        mApiKeyPreference = findPreference(NermuxAiConfig.KEY_API_KEY);
        mModelPreference = findPreference(NermuxAiConfig.KEY_MODEL);
        mBaseUrlPreference = findPreference(NermuxAiConfig.KEY_BASE_URL);

        ensureDefaults();
        configureProvider();
        configureTextPreference(mApiKeyPreference, NermuxAiConfig.KEY_API_KEY,
            R.string.nermux_ai_api_key_title, R.string.nermux_ai_api_key_dialog_message, true, InputType.TYPE_TEXT_VARIATION_PASSWORD);
        configureTextPreference(mModelPreference, NermuxAiConfig.KEY_MODEL,
            R.string.nermux_ai_model_title, R.string.nermux_ai_model_dialog_message, false, InputType.TYPE_TEXT_VARIATION_NORMAL);
        configureTextPreference(mBaseUrlPreference, NermuxAiConfig.KEY_BASE_URL,
            R.string.nermux_ai_base_url_title, R.string.nermux_ai_base_url_dialog_message, false, InputType.TYPE_TEXT_VARIATION_URI);
        configureSwitch(NermuxAiConfig.KEY_INCLUDE_TERMINAL_CONTEXT);
        configureSwitch(NermuxAiConfig.KEY_ALLOW_RUN_COMMANDS);
        refreshSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) NermuxSystemBars.hideNavigationBar(getActivity());
        refreshSummaries();
    }

    private void configureProvider() {
        if (mProviderPreference == null) return;
        mProviderPreference.setOnPreferenceClickListener(preference -> {
            showProviderDialog();
            return true;
        });
    }

    private void configureTextPreference(@Nullable Preference preference, @NonNull String key,
                                         int titleRes, int messageRes, boolean secret, int textVariation) {
        if (preference == null) return;

        preference.setOnPreferenceClickListener(clicked -> {
            showTextDialog(key, titleRes, messageRes, secret, textVariation);
            return true;
        });
    }

    private void showProviderDialog() {
        Context context = getContext();
        if (context == null) return;

        String[] entries = getResources().getStringArray(R.array.nermux_ai_provider_entries);
        String[] values = getResources().getStringArray(R.array.nermux_ai_provider_values);
        String current = prefs().getString(NermuxAiConfig.KEY_PROVIDER, NermuxAiConfig.PROVIDER_OPENAI);
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(context)
            .setTitle(R.string.nermux_ai_provider_title)
            .setSingleChoiceItems(entries, selectedIndex, (dialog, which) -> {
                if (which < 0 || which >= values.length) return;
                saveProvider(values[which]);
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showTextDialog(@NonNull String key, int titleRes, int messageRes, boolean secret, int textVariation) {
        Context context = getContext();
        if (context == null) return;

        EditText editText = new EditText(context);
        editText.setSingleLine(true);
        editText.setSelectAllOnFocus(true);
        editText.setTextColor(getResources().getColor(R.color.nermux_text_primary));
        editText.setHintTextColor(getResources().getColor(R.color.nermux_text_muted));
        editText.setText(currentEditableValue(key));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | textVariation);
        if (secret) editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editText.setSelection(editText.getText().length());

        int horizontalPadding = dp(22);
        int topPadding = dp(8);
        FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);
        wrapper.addView(editText, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setView(wrapper)
            .setPositiveButton(android.R.string.ok, (d, which) -> saveTextValue(key, editText.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            Window window = dialog.getWindow();
            if (window != null)
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    private void saveProvider(@NonNull String provider) {
        if (!isKnownProvider(provider)) provider = NermuxAiConfig.PROVIDER_OPENAI;

        SharedPreferences.Editor editor = prefs().edit()
            .putString(NermuxAiConfig.KEY_PROVIDER, provider)
            .putString(NermuxAiConfig.KEY_MODEL, NermuxAiConfig.getDefaultModel(provider));
        if (!NermuxAiConfig.PROVIDER_CUSTOM_OPENAI.equals(provider))
            editor.remove(NermuxAiConfig.KEY_BASE_URL);
        editor.apply();

        refreshSummaries();
    }

    private void saveTextValue(@NonNull String key, @NonNull String rawValue) {
        String value = rawValue.trim();
        if (NermuxAiConfig.KEY_BASE_URL.equals(key)) value = trimTrailingSlash(value);
        if (NermuxAiConfig.KEY_MODEL.equals(key) && TextUtils.isEmpty(value))
            value = NermuxAiConfig.getDefaultModel(currentProvider());

        prefs().edit().putString(key, value).apply();
        refreshSummaries();
    }

    private String currentEditableValue(@NonNull String key) {
        if (NermuxAiConfig.KEY_MODEL.equals(key))
            return NermuxAiConfig.getModel(requireContext());
        if (NermuxAiConfig.KEY_BASE_URL.equals(key)) {
            String value = prefs().getString(key, "");
            return value == null ? "" : value;
        }
        String value = prefs().getString(key, "");
        return value == null ? "" : value;
    }

    private void ensureDefaults() {
        String provider = currentProvider();
        SharedPreferences.Editor editor = prefs().edit();
        boolean changed = false;

        if (!isKnownProvider(provider)) {
            provider = NermuxAiConfig.PROVIDER_OPENAI;
            editor.putString(NermuxAiConfig.KEY_PROVIDER, provider);
            changed = true;
        }
        if (TextUtils.isEmpty(prefs().getString(NermuxAiConfig.KEY_MODEL, ""))) {
            editor.putString(NermuxAiConfig.KEY_MODEL, NermuxAiConfig.getDefaultModel(provider));
            changed = true;
        }
        if (changed) editor.apply();
    }

    private void refreshSummaries() {
        Context context = getContext();
        if (context == null) return;

        String provider = currentProvider();
        if (mProviderPreference != null)
            mProviderPreference.setSummary(NermuxAiConfig.getProviderTitle(provider));
        if (mApiKeyPreference != null) {
            String key = prefs().getString(NermuxAiConfig.KEY_API_KEY, "");
            mApiKeyPreference.setSummary(TextUtils.isEmpty(key)
                ? getString(R.string.nermux_ai_api_key_missing)
                : getString(R.string.nermux_ai_api_key_configured));
        }
        if (mModelPreference != null)
            mModelPreference.setSummary(NermuxAiConfig.getModel(context));
        if (mBaseUrlPreference != null) {
            boolean custom = NermuxAiConfig.PROVIDER_CUSTOM_OPENAI.equals(provider);
            mBaseUrlPreference.setVisible(custom);
            String value = prefs().getString(NermuxAiConfig.KEY_BASE_URL, "");
            mBaseUrlPreference.setSummary(TextUtils.isEmpty(value)
                ? getString(R.string.nermux_ai_base_url_summary)
                : value);
        }
    }

    private void configureSwitch(@NonNull String key) {
        SwitchPreferenceCompat switchPreference = findPreference(key);
        if (switchPreference == null) return;
    }

    private SharedPreferences prefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    @NonNull
    private String currentProvider() {
        return prefs().getString(NermuxAiConfig.KEY_PROVIDER, NermuxAiConfig.PROVIDER_OPENAI);
    }

    private boolean isKnownProvider(@Nullable String provider) {
        if (provider == null) return false;
        String[] values = getResources().getStringArray(R.array.nermux_ai_provider_values);
        for (String value : values) {
            if (value.equals(provider)) return true;
        }
        return false;
    }

    @NonNull
    private String trimTrailingSlash(@NonNull String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
