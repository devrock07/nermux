package com.termux.app.ai;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class NermuxAiClient {

    public interface Callback {
        void onSuccess(@NonNull String answer);
        void onError(@NonNull String message);
    }

    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int MAX_CONTEXT_CHARS = 14000;

    private NermuxAiClient() {}

    public static void ask(
        @NonNull Context context,
        @NonNull String task,
        @Nullable String terminalContext,
        @Nullable String userPrompt,
        @NonNull Callback callback
    ) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                String apiKey = NermuxAiConfig.getApiKey(appContext).trim();
                if (TextUtils.isEmpty(apiKey)) {
                    callback.onError("Add an API key in AI Providers first.");
                    return;
                }

                String provider = NermuxAiConfig.getProvider(appContext);
                String model = NermuxAiConfig.getModel(appContext);
                String contextText = NermuxAiConfig.shouldIncludeTerminalContext(appContext) ? terminalContext : "";
                String prompt = buildPrompt(task, model, contextText, userPrompt);

                if (NermuxAiConfig.isGeminiProvider(provider)) {
                    callback.onSuccess(callGemini(apiKey, model, prompt));
                } else {
                    String baseUrl = NermuxAiConfig.getBaseUrl(appContext);
                    if (TextUtils.isEmpty(baseUrl)) {
                        callback.onError("Add a base URL for the custom OpenAI-compatible provider.");
                        return;
                    }
                    callback.onSuccess(callOpenAiCompatible(baseUrl, apiKey, model, prompt));
                }
            } catch (Exception e) {
                String message = e.getMessage();
                callback.onError(TextUtils.isEmpty(message) ? "AI request failed." : message);
            }
        }, "NermuxAiClient").start();
    }

    @NonNull
    private static String callOpenAiCompatible(
        @NonNull String baseUrl,
        @NonNull String apiKey,
        @NonNull String model,
        @NonNull String prompt
    ) throws Exception {
        URL url = new URL(baseUrl + "/chat/completions");
        JSONObject body = new JSONObject();
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
            .put("role", "system")
            .put("content", buildSystemPrompt()));
        messages.put(new JSONObject()
            .put("role", "user")
            .put("content", prompt));

        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.2);

        String response = postJson(url, body.toString(), connection -> {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
        });

        JSONObject json = new JSONObject(response);
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0)
            throw new IllegalStateException("Provider returned no choices.");

        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null)
            throw new IllegalStateException("Provider response did not include a message.");

        String content = message.optString("content", "").trim();
        if (TextUtils.isEmpty(content))
            throw new IllegalStateException("Provider returned an empty answer.");

        return content;
    }

    @NonNull
    private static String callGemini(
        @NonNull String apiKey,
        @NonNull String model,
        @NonNull String prompt
    ) throws Exception {
        String encodedKey = URLEncoder.encode(apiKey, "UTF-8");
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + encodedKey);

        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", buildSystemPrompt() + "\n\n" + prompt));
        contents.put(new JSONObject()
            .put("role", "user")
            .put("parts", parts));
        body.put("contents", contents);
        body.put("generationConfig", new JSONObject().put("temperature", 0.2));

        String response = postJson(url, body.toString(), connection -> {
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
        });

        JSONObject json = new JSONObject(response);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0)
            throw new IllegalStateException("Gemini returned no candidates.");

        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        JSONArray responseParts = content == null ? null : content.optJSONArray("parts");
        if (responseParts == null || responseParts.length() == 0)
            throw new IllegalStateException("Gemini response did not include text.");

        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < responseParts.length(); i++) {
            String text = responseParts.getJSONObject(i).optString("text", "");
            if (!TextUtils.isEmpty(text)) answer.append(text);
        }

        String answerText = answer.toString().trim();
        if (TextUtils.isEmpty(answerText))
            throw new IllegalStateException("Gemini returned an empty answer.");

        return answerText;
    }

    @NonNull
    private static String postJson(
        @NonNull URL url,
        @NonNull String body,
        @NonNull HeaderWriter headerWriter
    ) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        headerWriter.write(connection);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bytes);
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readStream(stream);
        connection.disconnect();

        if (code < 200 || code >= 300) {
            String compactResponse = response == null ? "" : response.trim();
            if (compactResponse.length() > 360) compactResponse = compactResponse.substring(0, 360) + "...";
            throw new IllegalStateException("Provider error " + code + ": " + compactResponse);
        }

        return response == null ? "" : response;
    }

    @NonNull
    private static String readStream(@Nullable InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String buildPrompt(
        @NonNull String task,
        @NonNull String model,
        @Nullable String terminalContext,
        @Nullable String userPrompt
    ) {
        String cleanContext = trimContext(terminalContext);
        String cleanPrompt = userPrompt == null ? "" : userPrompt.trim();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Task: ").append(task).append('\n');
        prompt.append("Model profile: ").append(model).append('\n');
        prompt.append("User request: ").append(TextUtils.isEmpty(cleanPrompt) ? "Help with the current terminal state." : cleanPrompt).append("\n\n");

        if (!TextUtils.isEmpty(cleanContext)) {
            prompt.append("Recent terminal transcript:\n```text\n")
                .append(cleanContext)
                .append("\n```\n\n");
        }

        prompt.append("Return a concise answer. If a command should be run, include exactly one safest command on a separate line as RUN: command.");
        return prompt.toString();
    }

    @NonNull
    private static String buildSystemPrompt() {
        return "You are Nermux Agent, a careful mobile terminal and coding assistant inside an Android terminal app. "
            + "Diagnose shell errors, missing packages, failed builds, and project issues from the provided context. "
            + "Prefer small reversible fixes. Warn before destructive actions. Do not invent files you cannot see. "
            + "When suggesting a terminal command, put one approved candidate on a line starting with RUN:.";
    }

    @NonNull
    private static String trimContext(@Nullable String value) {
        if (TextUtils.isEmpty(value)) return "";
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_CONTEXT_CHARS) return trimmed;
        return trimmed.substring(trimmed.length() - MAX_CONTEXT_CHARS);
    }

    private interface HeaderWriter {
        void write(@NonNull HttpURLConnection connection);
    }
}
