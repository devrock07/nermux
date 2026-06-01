package com.termux.app.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NermuxAiChatStore {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private static final String PREFS_NAME = "nermux_ai_chat";
    private static final String KEY_MESSAGES = "messages";
    private static final String KEY_DRAFT = "draft";
    private static final String KEY_WORKSPACE_PATH = "workspace_path";
    private static final int MAX_MESSAGES = 80;

    private NermuxAiChatStore() {}

    @NonNull
    public static List<Message> loadMessages(@NonNull Context context) {
        return loadMessages(context, loadWorkspacePath(context));
    }

    @NonNull
    public static List<Message> loadMessages(@NonNull Context context, @Nullable String workspacePath) {
        String key = scopedKey(KEY_MESSAGES, workspacePath);
        String raw = prefs(context).getString(key, null);
        if (raw == null && TextUtils.isEmpty(workspacePath))
            raw = prefs(context).getString(KEY_MESSAGES, "[]");
        if (raw == null) raw = "[]";

        List<Message> messages = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Message message = Message.fromJson(object);
                if (message != null) messages.add(message);
            }
        } catch (Exception ignored) {
            clearMessages(context, workspacePath);
        }
        return messages;
    }

    public static void appendMessage(@NonNull Context context, @NonNull Message message) {
        appendMessage(context, loadWorkspacePath(context), message);
    }

    public static void appendMessage(@NonNull Context context, @Nullable String workspacePath, @NonNull Message message) {
        List<Message> messages = loadMessages(context, workspacePath);
        messages.add(message);
        trim(messages);
        saveMessages(context, workspacePath, messages);
    }

    public static void clearMessages(@NonNull Context context) {
        clearMessages(context, loadWorkspacePath(context));
    }

    public static void clearMessages(@NonNull Context context, @Nullable String workspacePath) {
        SharedPreferences.Editor editor = prefs(context).edit().remove(scopedKey(KEY_MESSAGES, workspacePath));
        if (TextUtils.isEmpty(workspacePath)) editor.remove(KEY_MESSAGES);
        editor.apply();
    }

    public static void saveDraft(@NonNull Context context, @Nullable String draft) {
        saveDraft(context, loadWorkspacePath(context), draft);
    }

    public static void saveDraft(@NonNull Context context, @Nullable String workspacePath, @Nullable String draft) {
        prefs(context).edit().putString(scopedKey(KEY_DRAFT, workspacePath), draft == null ? "" : draft).apply();
    }

    @NonNull
    public static String loadDraft(@NonNull Context context) {
        return loadDraft(context, loadWorkspacePath(context));
    }

    @NonNull
    public static String loadDraft(@NonNull Context context, @Nullable String workspacePath) {
        String value = prefs(context).getString(scopedKey(KEY_DRAFT, workspacePath), null);
        if (value == null && TextUtils.isEmpty(workspacePath))
            value = prefs(context).getString(KEY_DRAFT, "");
        return value == null ? "" : value;
    }

    public static void clearDraft(@NonNull Context context) {
        clearDraft(context, loadWorkspacePath(context));
    }

    public static void clearDraft(@NonNull Context context, @Nullable String workspacePath) {
        SharedPreferences.Editor editor = prefs(context).edit().remove(scopedKey(KEY_DRAFT, workspacePath));
        if (TextUtils.isEmpty(workspacePath)) editor.remove(KEY_DRAFT);
        editor.apply();
    }

    public static void saveWorkspacePath(@NonNull Context context, @Nullable String path) {
        prefs(context).edit().putString(KEY_WORKSPACE_PATH, path == null ? "" : path).apply();
    }

    @NonNull
    public static String loadWorkspacePath(@NonNull Context context) {
        return prefs(context).getString(KEY_WORKSPACE_PATH, "");
    }

    public static void clearWorkspacePath(@NonNull Context context) {
        prefs(context).edit().remove(KEY_WORKSPACE_PATH).apply();
    }

    private static void saveMessages(@NonNull Context context, @Nullable String workspacePath, @NonNull List<Message> messages) {
        JSONArray array = new JSONArray();
        for (Message message : messages) {
            array.put(message.toJson());
        }
        prefs(context).edit().putString(scopedKey(KEY_MESSAGES, workspacePath), array.toString()).apply();
    }

    private static void trim(@NonNull List<Message> messages) {
        while (messages.size() > MAX_MESSAGES) messages.remove(0);
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String scopedKey(@NonNull String prefix, @Nullable String workspacePath) {
        if (TextUtils.isEmpty(workspacePath))
            return prefix + "_workspace_none";
        return prefix + "_workspace_" + sha256(workspacePath.trim());
    }

    @NonNull
    private static String sha256(@NonNull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes)
                builder.append(String.format(Locale.US, "%02x", current & 0xff));
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static final class Message {
        @NonNull
        public final String role;
        @NonNull
        public final String text;
        @Nullable
        public final String command;
        @NonNull
        public final List<FileAction> files;
        public final long timestamp;

        private Message(@NonNull String role, @NonNull String text, @Nullable String command, @NonNull List<FileAction> files, long timestamp) {
            this.role = role;
            this.text = text;
            this.command = command;
            this.files = files;
            this.timestamp = timestamp;
        }

        @NonNull
        public static Message user(@NonNull String text) {
            return new Message(ROLE_USER, text, null, new ArrayList<>(), System.currentTimeMillis());
        }

        @NonNull
        public static Message assistant(@NonNull String text, @Nullable String command, @NonNull List<FileAction> files) {
            return new Message(ROLE_ASSISTANT, text, command, files, System.currentTimeMillis());
        }

        @Nullable
        private static Message fromJson(@NonNull JSONObject object) {
            String role = object.optString("role", "");
            String text = object.optString("text", "");
            if (TextUtils.isEmpty(role) || TextUtils.isEmpty(text)) return null;

            String command = object.optString("command", "");
            List<FileAction> files = new ArrayList<>();
            JSONArray fileArray = object.optJSONArray("files");
            if (fileArray != null) {
                for (int i = 0; i < fileArray.length(); i++) {
                    JSONObject fileObject = fileArray.optJSONObject(i);
                    if (fileObject == null) continue;
                    FileAction file = FileAction.fromJson(fileObject);
                    if (file != null) files.add(file);
                }
            }
            return new Message(role, text, TextUtils.isEmpty(command) ? null : command, files, object.optLong("timestamp", System.currentTimeMillis()));
        }

        @NonNull
        private JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("role", role);
                object.put("text", text);
                object.put("timestamp", timestamp);
                if (!TextUtils.isEmpty(command)) object.put("command", command);
                if (!files.isEmpty()) {
                    JSONArray fileArray = new JSONArray();
                    for (FileAction file : files) fileArray.put(file.toJson());
                    object.put("files", fileArray);
                }
            } catch (Exception ignored) {
            }
            return object;
        }
    }

    public static final class FileAction {
        @NonNull
        public final String path;
        @NonNull
        public final String content;

        public FileAction(@NonNull String path, @NonNull String content) {
            this.path = path;
            this.content = content;
        }

        @Nullable
        private static FileAction fromJson(@NonNull JSONObject object) {
            String path = object.optString("path", "");
            String content = object.optString("content", "");
            if (TextUtils.isEmpty(path)) return null;
            return new FileAction(path, content);
        }

        @NonNull
        private JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("path", path);
                object.put("content", content);
            } catch (Exception ignored) {
            }
            return object;
        }
    }
}
