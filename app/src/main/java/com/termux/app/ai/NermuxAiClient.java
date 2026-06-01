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
import java.util.List;
import java.util.Locale;

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
        @NonNull List<NermuxAiChatStore.Message> chatHistory,
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

                String baseUrl = NermuxAiConfig.isGeminiProvider(provider) ? "" : NermuxAiConfig.getBaseUrl(appContext);
                if (!NermuxAiConfig.isGeminiProvider(provider) && TextUtils.isEmpty(baseUrl)) {
                    callback.onError("Add a base URL for the custom OpenAI-compatible provider.");
                    return;
                }

                String answer = callProvider(provider, baseUrl, apiKey, model, prompt, chatHistory);
                if (shouldRetryForActionableFiles(task, userPrompt, answer)) {
                    String repairPrompt = buildRepairPrompt(task, model, contextText, userPrompt, answer);
                    answer = callProvider(provider, baseUrl, apiKey, model, repairPrompt, chatHistory);
                }
                callback.onSuccess(answer);
            } catch (Exception e) {
                String message = e.getMessage();
                callback.onError(TextUtils.isEmpty(message) ? "AI request failed." : message);
            }
        }, "NermuxAiClient").start();
    }

    @NonNull
    private static String callProvider(
        @NonNull String provider,
        @NonNull String baseUrl,
        @NonNull String apiKey,
        @NonNull String model,
        @NonNull String prompt,
        @NonNull List<NermuxAiChatStore.Message> chatHistory
    ) throws Exception {
        if (NermuxAiConfig.isGeminiProvider(provider))
            return callGemini(apiKey, model, prompt, chatHistory);
        return callOpenAiCompatible(baseUrl, apiKey, model, prompt, chatHistory);
    }

    @NonNull
    private static String callOpenAiCompatible(
        @NonNull String baseUrl,
        @NonNull String apiKey,
        @NonNull String model,
        @NonNull String prompt,
        @NonNull List<NermuxAiChatStore.Message> chatHistory
    ) throws Exception {
        URL url = new URL(baseUrl + "/chat/completions");
        JSONObject body = new JSONObject();
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
            .put("role", "system")
            .put("content", buildSystemPrompt()));

        for (NermuxAiChatStore.Message message : recentHistory(chatHistory)) {
            String role = NermuxAiChatStore.ROLE_ASSISTANT.equals(message.role) ? "assistant" : "user";
            messages.put(new JSONObject()
                .put("role", role)
                .put("content", message.text));
        }

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
        @NonNull String prompt,
        @NonNull List<NermuxAiChatStore.Message> chatHistory
    ) throws Exception {
        String encodedKey = URLEncoder.encode(apiKey, "UTF-8");
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + encodedKey);

        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();

        for (NermuxAiChatStore.Message message : recentHistory(chatHistory)) {
            JSONArray historyParts = new JSONArray();
            historyParts.put(new JSONObject().put("text", message.text));
            contents.put(new JSONObject()
                .put("role", NermuxAiChatStore.ROLE_ASSISTANT.equals(message.role) ? "model" : "user")
                .put("parts", historyParts));
        }

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

        prompt.append("Return a concise answer.\n");
        prompt.append("If the user asks you to create, write, build, generate, scaffold, or modify code, you MUST return real file actions for the selected agent workspace. Do not tell them to open nano, vim, vi, or any editor.\n");
        prompt.append("Do not answer coding requests with manual editing steps unless the user explicitly asks for manual steps.\n");
        prompt.append("For every file you want Nermux to create or replace, use exactly this format:\n");
        prompt.append("FILE: relative/path\n```language\nfull file contents\n```\n");
        prompt.append("Use paths relative to the selected workspace root only. Include complete file contents, not fragments or placeholders.\n");
        prompt.append("Do not use absolute paths or ../ in FILE paths. Nermux backs up existing files before replacing them.\n");
        prompt.append("After file actions, add a short explanation. If a command should be run after files are applied, include exactly one safest non-destructive command on a separate line as RUN: command.");
        return prompt.toString();
    }

    @NonNull
    private static String buildSystemPrompt() {
        return "You are Nermux Agent, a careful mobile terminal and coding assistant inside an Android terminal app. "
            + "Diagnose shell errors, missing packages, failed builds, and project issues from the provided context. "
            + "Prefer small reversible fixes. Never suggest destructive commands such as rm -rf, mkfs, dd, recursive chmod/chown on broad paths, or wiping project files. Do not invent files you cannot see. "
            + "For coding tasks, act like a local coding agent inside the selected workspace: create complete file actions with FILE: blocks instead of suggesting editors or manual copy-paste. "
            + "When suggesting a terminal command, put one approved candidate on a line starting with RUN:.";
    }

    @NonNull
    private static String buildRepairPrompt(
        @NonNull String task,
        @NonNull String model,
        @Nullable String terminalContext,
        @Nullable String userPrompt,
        @NonNull String previousAnswer
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(buildPrompt(task, model, terminalContext, userPrompt));
        prompt.append("\n\nYour previous answer was not actionable inside Nermux because it did not provide file actions.\n");
        prompt.append("Regenerate the answer now using FILE blocks. Do not suggest nano, vim, vi, cat > file, or manual editing.\n");
        prompt.append("If the user asked for a simple webpage, create FILE: index.html with complete HTML, CSS, and JavaScript in that file unless they asked for separate files.\n");
        prompt.append("Previous answer:\n```text\n")
            .append(trimContext(previousAnswer))
            .append("\n```");
        return prompt.toString();
    }

    private static boolean shouldRetryForActionableFiles(
        @NonNull String task,
        @Nullable String userPrompt,
        @NonNull String answer
    ) {
        if (!isActionableCodingRequest(task, userPrompt)) return false;
        if (containsFileAction(answer) || containsLikelyCodeFence(answer)) return false;

        String lower = answer.toLowerCase(Locale.US);
        return lower.contains("nano ")
            || lower.contains("vim ")
            || lower.contains(" vi ")
            || lower.contains("open an editor")
            || lower.contains("open the editor")
            || lower.contains("create an html file")
            || lower.contains("create a file")
            || lower.contains("save the file")
            || lower.contains("you'll need to create")
            || lower.contains("you need to create")
            || lower.length() > 0;
    }

    private static boolean isActionableCodingRequest(@NonNull String task, @Nullable String userPrompt) {
        String prompt = userPrompt == null ? "" : userPrompt.trim();
        if (TextUtils.isEmpty(prompt)) return false;

        String lower = (task + " " + prompt).toLowerCase(Locale.US);
        return lower.contains("create")
            || lower.contains("write")
            || lower.contains("make")
            || lower.contains("build")
            || lower.contains("generate")
            || lower.contains("scaffold")
            || lower.contains("modify")
            || lower.contains("edit")
            || lower.contains("implement")
            || lower.contains("code")
            || lower.contains("webpage")
            || lower.contains("website")
            || lower.contains("html")
            || lower.contains("script")
            || lower.contains("component")
            || lower.contains("file");
    }

    private static boolean containsFileAction(@NonNull String answer) {
        return answer.toLowerCase(Locale.US).contains("file:") && answer.contains("```");
    }

    private static boolean containsLikelyCodeFence(@NonNull String answer) {
        String lower = answer.toLowerCase(Locale.US);
        return lower.contains("```html")
            || lower.contains("```css")
            || lower.contains("```javascript")
            || lower.contains("```js")
            || lower.contains("```python")
            || lower.contains("```java")
            || lower.contains("```kotlin")
            || lower.contains("```json");
    }

    @NonNull
    private static String trimContext(@Nullable String value) {
        if (TextUtils.isEmpty(value)) return "";
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_CONTEXT_CHARS) return trimmed;
        return trimmed.substring(trimmed.length() - MAX_CONTEXT_CHARS);
    }

    @NonNull
    private static List<NermuxAiChatStore.Message> recentHistory(@NonNull List<NermuxAiChatStore.Message> chatHistory) {
        int start = Math.max(0, chatHistory.size() - 12);
        return chatHistory.subList(start, chatHistory.size());
    }

    private interface HeaderWriter {
        void write(@NonNull HttpURLConnection connection);
    }
}
