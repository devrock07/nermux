package com.termux.app.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

public final class NermuxAiConfig {

    public static final String PREFS_NAME = "nermux_ai";

    public static final String KEY_PROVIDER = "provider";
    public static final String KEY_API_KEY = "api_key";
    public static final String KEY_MODEL = "model";
    public static final String KEY_BASE_URL = "base_url";
    public static final String KEY_INCLUDE_TERMINAL_CONTEXT = "include_terminal_context";
    public static final String KEY_ALLOW_RUN_COMMANDS = "allow_run_commands";

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_GEMINI = "gemini";
    public static final String PROVIDER_GROQ = "groq";
    public static final String PROVIDER_OPENROUTER = "openrouter";
    public static final String PROVIDER_CUSTOM_OPENAI = "custom_openai";

    private NermuxAiConfig() {}

    @NonNull
    public static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public static String getProvider(@NonNull Context context) {
        return prefs(context).getString(KEY_PROVIDER, PROVIDER_OPENAI);
    }

    @NonNull
    public static String getApiKey(@NonNull Context context) {
        return prefs(context).getString(KEY_API_KEY, "");
    }

    @NonNull
    public static String getModel(@NonNull Context context) {
        String provider = getProvider(context);
        String model = prefs(context).getString(KEY_MODEL, "");
        return TextUtils.isEmpty(model) ? getDefaultModel(provider) : model;
    }

    @NonNull
    public static String getBaseUrl(@NonNull Context context) {
        String provider = getProvider(context);
        String customUrl = prefs(context).getString(KEY_BASE_URL, "");
        if (PROVIDER_CUSTOM_OPENAI.equals(provider) && !TextUtils.isEmpty(customUrl))
            return trimTrailingSlash(customUrl);
        return getDefaultBaseUrl(provider);
    }

    public static boolean shouldIncludeTerminalContext(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_INCLUDE_TERMINAL_CONTEXT, true);
    }

    public static boolean shouldAllowRunCommands(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_ALLOW_RUN_COMMANDS, true);
    }

    public static boolean hasApiKey(@NonNull Context context) {
        return !TextUtils.isEmpty(getApiKey(context).trim());
    }

    @NonNull
    public static String getProviderTitle(@NonNull String provider) {
        switch (provider) {
            case PROVIDER_GEMINI:
                return "Gemini";
            case PROVIDER_GROQ:
                return "Groq";
            case PROVIDER_OPENROUTER:
                return "OpenRouter";
            case PROVIDER_CUSTOM_OPENAI:
                return "Custom";
            case PROVIDER_OPENAI:
            default:
                return "ChatGPT";
        }
    }

    @NonNull
    public static String getDefaultModel(@NonNull String provider) {
        switch (provider) {
            case PROVIDER_GEMINI:
                return "gemini-2.5-flash";
            case PROVIDER_GROQ:
                return "llama-3.3-70b-versatile";
            case PROVIDER_OPENROUTER:
                return "openai/gpt-4.1-mini";
            case PROVIDER_CUSTOM_OPENAI:
                return "gpt-4.1-mini";
            case PROVIDER_OPENAI:
            default:
                return "gpt-4.1-mini";
        }
    }

    @NonNull
    public static String getDefaultBaseUrl(@NonNull String provider) {
        switch (provider) {
            case PROVIDER_GROQ:
                return "https://api.groq.com/openai/v1";
            case PROVIDER_OPENROUTER:
                return "https://openrouter.ai/api/v1";
            case PROVIDER_CUSTOM_OPENAI:
                return "";
            case PROVIDER_OPENAI:
            default:
                return "https://api.openai.com/v1";
        }
    }

    public static boolean isGeminiProvider(@NonNull String provider) {
        return PROVIDER_GEMINI.equals(provider);
    }

    @NonNull
    private static String trimTrailingSlash(@NonNull String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }
}
