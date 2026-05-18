package com.termux.app.activities;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.StatFs;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.shell.command.ExecutionCommand.ShellCreateMode;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NermuxPowerActivity extends AppCompatActivity {

    private static final String EXTRA_START_DIR = "com.termux.app.power.START_DIR";
    private static final String PREFS_NAME = "nermux_power";
    private static final String KEY_SSH_PROFILES = "ssh_profiles";

    private String mStartDirectory;
    private LinearLayout mContent;
    private SharedPreferences mPreferences;

    public static Intent newInstance(Context context, @Nullable String startDirectory) {
        Intent intent = new Intent(context, NermuxPowerActivity.class);
        intent.putExtra(EXTRA_START_DIR, startDirectory);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStartDirectory = getIntent().getStringExtra(EXTRA_START_DIR);
        if (TextUtils.isEmpty(mStartDirectory)) mStartDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;
        mPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        getWindow().setStatusBarColor(color(R.color.nermux_terminal_background));
        getWindow().setNavigationBarColor(color(R.color.nermux_terminal_background));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(color(R.color.nermux_terminal_background));

        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(mContent, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scrollView);
        render();
    }

    private void render() {
        mContent.removeAllViews();

        addHeader();
        addStatusSection();
        addCommandSection();
        addSshSection();
        addSystemSection();
    }

    private void addHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("Power Center", 27, R.color.nermux_text_primary, true);
        TextView subtitle = text("Stacks, SSH, ports, backups, and runtime controls", 13, R.color.nermux_text_secondary, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        titleColumn.addView(title);
        titleColumn.addView(subtitle);
        row.addView(titleColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button close = compactButton("Close");
        close.setOnClickListener(v -> finish());
        row.addView(close);

        mContent.addView(row);
        addSpacer(16);
    }

    private void addStatusSection() {
        addSectionTitle("Status");
        addInfo("Project", getWorkingDirectory());
        addInfo("Device", getDeviceStatus());
        addInfo("Storage", getStorageStatus());
        addSpacer(12);
    }

    private void addCommandSection() {
        addSectionTitle("Stacks And Tools");
        addCommandButton("Base Dev Stack", "Git, SSH, curl, editors, tree, jq, ripgrep",
            "pkg update && pkg install -y git openssh curl wget nano vim tree jq ripgrep");
        addCommandButton("Web / Node Stack", "Node.js LTS, git, TypeScript, pnpm",
            "pkg update && pkg install -y nodejs-lts git && npm install -g typescript pnpm");
        addCommandButton("Python / Bot Stack", "Python, clang/make, OpenSSL, pip tools",
            "pkg update && pkg install -y python clang make git openssl libffi && python -m pip install --upgrade pip wheel");
        addCommandButton("Android Build Stack", "JDK, Gradle, platform tools, signing helpers",
            "pkg update && pkg install -y openjdk-17 gradle android-tools && pkg install -y aapt apksigner || true");
        addCommandButton("Network / Security Stack", "nmap, DNS tools, whois, SSH, netcat fallback",
            "pkg update && pkg install -y nmap dnsutils whois openssh && (pkg install -y netcat-openbsd || pkg install -y netcat || true)");
        addCommandButton("Nermux Doctor", "Versions, storage, ports, missing tools",
            "printf 'Nermux doctor\\n\\n'; uname -a; printf '\\nTools:\\n'; for x in git ssh node npm python pip clang make; do command -v $x >/dev/null && printf '  ok  %s -> %s\\n' $x $(command -v $x) || printf '  miss %s\\n' $x; done; printf '\\nStorage:\\n'; df -h $HOME; printf '\\nPorts:\\n'; (ss -ltnp 2>/dev/null || netstat -tulpn 2>/dev/null || true)");
        addCommandButton("Port Manager", "Show listeners and likely dev server processes",
            "printf 'Listening ports\\n\\n'; (ss -ltnp 2>/dev/null || netstat -tulpn 2>/dev/null || lsof -i -P -n 2>/dev/null || true); printf '\\nDev processes\\n\\n'; ps -A | grep -E 'node|python|java|nginx|php|ruby|deno' || true");
        addCommandButton("Export Current Project", "Archive this folder to shared storage",
            "mkdir -p /sdcard/Nermux/exports && name=$(basename \"$PWD\") && tar -czf \"/sdcard/Nermux/exports/${name:-project}-$(date +%Y%m%d-%H%M%S).tar.gz\" -C \"$PWD\" . && ls -lh /sdcard/Nermux/exports");
        addCommandButton("Backup Home", "Archive $HOME to shared storage",
            "mkdir -p /sdcard/Nermux/backups && tar -czf \"/sdcard/Nermux/backups/home-$(date +%Y%m%d-%H%M%S).tar.gz\" -C \"$HOME\" . && ls -lh /sdcard/Nermux/backups");
        addCommandButton("Generate SSH Key", "Create or print ~/.ssh/id_ed25519.pub",
            "mkdir -p ~/.ssh && chmod 700 ~/.ssh && test -f ~/.ssh/id_ed25519 || ssh-keygen -t ed25519 -C nermux -f ~/.ssh/id_ed25519; printf '\\nPublic key:\\n'; cat ~/.ssh/id_ed25519.pub");

        Button custom = fullButton("Run Custom Command\nLaunch any command in a fresh terminal session");
        custom.setOnClickListener(v -> showCustomCommandDialog());
        mContent.addView(custom);
        addSpacer(12);
    }

    private void addSshSection() {
        addSectionTitle("SSH Profiles");

        List<String> rawProfiles = getSshProfiles();
        if (rawProfiles.isEmpty()) {
            addInfo("Saved", "No SSH profiles yet. Add a host once, then connect in one tap.");
        } else {
            for (String rawProfile : rawProfiles) {
                SshProfile profile = SshProfile.parse(rawProfile);
                if (profile == null) continue;
                Button button = fullButton(profile.name + "\n" + profile.user + "@" + profile.host + ":" + profile.port);
                button.setOnClickListener(v -> connectSsh(profile));
                button.setOnLongClickListener(v -> {
                    showSshProfileActions(rawProfile, profile);
                    return true;
                });
                mContent.addView(button);
            }
        }

        Button add = primaryButton("Add SSH Profile");
        add.setOnClickListener(v -> showSshProfileDialog(null, null));
        mContent.addView(add);
        addSpacer(12);
    }

    private void addSystemSection() {
        addSectionTitle("Runtime Controls");

        addCommandButton("Setup Shared Storage", "Grant access for backups, exports, and phone folders",
            "termux-setup-storage");

        Button wakeLock = fullButton("Acquire Wake Lock\nKeep long-running bots and servers alive");
        wakeLock.setOnClickListener(v -> sendWakeAction(TERMUX_SERVICE.ACTION_WAKE_LOCK, "Wake lock acquired"));
        mContent.addView(wakeLock);

        Button wakeUnlock = fullButton("Release Wake Lock\nReturn to normal battery behavior");
        wakeUnlock.setOnClickListener(v -> sendWakeAction(TERMUX_SERVICE.ACTION_WAKE_UNLOCK, "Wake lock released"));
        mContent.addView(wakeUnlock);

        addCommandButton("Open Projects Folder", "Create and jump to ~/projects",
            "mkdir -p ~/projects && cd ~/projects && pwd && ls -la");
    }

    private void addCommandButton(String title, String subtitle, String command) {
        Button button = fullButton(title + "\n" + subtitle);
        button.setOnClickListener(v -> runCommand(title, command));
        mContent.addView(button);
    }

    private void runCommand(String shellName, String command) {
        performHaptic();

        File executable = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR, "sh");
        if (!executable.canExecute()) executable = new File("/system/bin/sh");

        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE,
            new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(executable.getAbsolutePath()).build());
        intent.setClass(this, TermuxService.class);
        intent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[] { "-lc", command });
        intent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, getWorkingDirectory());
        intent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION, String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, shellName);
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ShellCreateMode.ALWAYS.getMode());
        startService(intent);
        startActivity(TermuxActivity.newInstance(this));
        Toast.makeText(this, getString(R.string.msg_power_command_started), Toast.LENGTH_SHORT).show();
    }

    private void connectSsh(SshProfile profile) {
        if (profile.host.isEmpty() || profile.user.isEmpty() || parsePort(profile.port) < 1) {
            showToast("Edit this SSH profile before connecting.");
            return;
        }

        runCommand("SSH " + profile.name,
            "ssh -p " + shellQuote(profile.port) + " " + shellQuote(profile.user + "@" + profile.host));
    }

    private void sendWakeAction(String action, String message) {
        performHaptic();
        Intent intent = new Intent(this, TermuxService.class).setAction(action);
        startService(intent);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showCustomCommandDialog() {
        EditText input = powerInput("Command", "npm run dev", "npm run dev");
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        showPowerDialog("Run Custom Command", "Runs in " + getWorkingDirectory(), input,
            "Cancel", null,
            "Run", dialog -> {
                String command = input.getText().toString().trim();
                if (command.isEmpty()) {
                    showToast("Command cannot be empty");
                    return;
                }
                dialog.dismiss();
                runCommand("Custom", command);
            });
    }

    private void showSshProfileActions(String rawProfile, SshProfile profile) {
        LinearLayout body = verticalPanel(0);
        body.addView(dialogSummary(profile.user + "@" + profile.host + ":" + profile.port));
        addPanelSpacer(body, 14);

        final Dialog[] actionDialog = new Dialog[1];
        body.addView(dialogAction("Connect", true, v -> {
            if (actionDialog[0] != null) actionDialog[0].dismiss();
            connectSsh(profile);
        }));
        body.addView(dialogAction("Edit", false, v -> {
            if (actionDialog[0] != null) actionDialog[0].dismiss();
            showSshProfileDialog(rawProfile, profile);
        }));
        body.addView(dialogAction("Delete", false, v -> {
            if (actionDialog[0] != null) actionDialog[0].dismiss();
            confirmDeleteSshProfile(rawProfile, profile);
        }));

        actionDialog[0] = showPowerDialog("SSH Profile", profile.name, body, "Close", null, null, null);
    }

    private void showSshProfileDialog(@Nullable String existingRawProfile, @Nullable SshProfile existingProfile) {
        LinearLayout body = verticalPanel(0);

        EditText name = powerInput("Profile name", "VPS", existingProfile == null ? "VPS" : existingProfile.name);
        EditText host = powerInput("Host / IP", "example.com", existingProfile == null ? "" : existingProfile.host);
        EditText user = powerInput("User", "root", existingProfile == null ? "root" : existingProfile.user);
        EditText port = powerInput("Port", "22", existingProfile == null ? "22" : existingProfile.port);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);

        body.addView(fieldBlock("Name", name));
        body.addView(fieldBlock("Host", host));
        body.addView(fieldBlock("User", user));
        body.addView(fieldBlock("Port", port));

        showPowerDialog(existingProfile == null ? "Add SSH Profile" : "Edit SSH Profile",
            "Saved locally on this device", body,
            "Cancel", null,
            "Save", dialog -> {
                SshProfile profile = buildProfile(name, host, user, port);
                if (profile == null) return;
                saveSshProfile(existingRawProfile, profile);
                dialog.dismiss();
                render();
            });
    }

    private SshProfile buildProfile(EditText nameInput, EditText hostInput, EditText userInput, EditText portInput) {
        String name = cleanField(nameInput.getText().toString(), "SSH");
        String host = cleanField(hostInput.getText().toString(), "");
        String user = cleanField(userInput.getText().toString(), "root");
        String port = cleanField(portInput.getText().toString(), "22");

        if (host.contains("@")) {
            int atIndex = host.lastIndexOf('@');
            if (atIndex > 0 && atIndex < host.length() - 1) {
                user = host.substring(0, atIndex).trim();
                host = host.substring(atIndex + 1).trim();
            }
        }

        boolean valid = true;
        if (host.isEmpty() || containsWhitespace(host)) {
            hostInput.setError("Enter a valid host");
            valid = false;
        }
        if (user.isEmpty() || containsWhitespace(user)) {
            userInput.setError("Enter a valid user");
            valid = false;
        }

        int portNumber = parsePort(port);
        if (portNumber < 1 || portNumber > 65535) {
            portInput.setError("Use 1-65535");
            valid = false;
        }

        if (!valid) return null;
        if (name.isEmpty()) name = user + "@" + host;
        return new SshProfile(name, host, user, String.valueOf(portNumber));
    }

    private void confirmDeleteSshProfile(String rawProfile, SshProfile profile) {
        TextView message = dialogSummary("Delete " + profile.name + "?\n\nThis only removes the saved shortcut. It does not touch SSH keys.");
        showPowerDialog("Delete SSH Profile", profile.user + "@" + profile.host, message,
            "Cancel", null,
            "Delete", dialog -> {
                deleteSshProfile(rawProfile);
                dialog.dismiss();
                render();
            });
    }

    private List<String> getSshProfiles() {
        Set<String> profileSet = mPreferences.getStringSet(KEY_SSH_PROFILES, Collections.emptySet());
        List<String> profiles = new ArrayList<>(profileSet);
        Collections.sort(profiles, (left, right) -> {
            SshProfile leftProfile = SshProfile.parse(left);
            SshProfile rightProfile = SshProfile.parse(right);
            String leftName = leftProfile == null ? left : leftProfile.name;
            String rightName = rightProfile == null ? right : rightProfile.name;
            return leftName.compareToIgnoreCase(rightName);
        });
        return profiles;
    }

    private void saveSshProfile(@Nullable String oldRawProfile, SshProfile profile) {
        Set<String> profiles = new HashSet<>(mPreferences.getStringSet(KEY_SSH_PROFILES, Collections.emptySet()));
        if (oldRawProfile != null) profiles.remove(oldRawProfile);
        profiles.add(profile.encode());
        mPreferences.edit().putStringSet(KEY_SSH_PROFILES, profiles).apply();
        showToast("SSH profile saved");
    }

    private void deleteSshProfile(String profile) {
        Set<String> profiles = new HashSet<>(mPreferences.getStringSet(KEY_SSH_PROFILES, Collections.emptySet()));
        profiles.remove(profile);
        mPreferences.edit().putStringSet(KEY_SSH_PROFILES, profiles).apply();
        showToast("SSH profile removed");
    }

    private Dialog showPowerDialog(String title, @Nullable String subtitle, View body,
                                 @Nullable String negativeText, @Nullable DialogAction negativeAction,
                                 @Nullable String positiveText, @Nullable DialogAction positiveAction) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = verticalPanel(dp(18));
        panel.setBackgroundResource(R.drawable.nermux_power_dialog_background);

        TextView titleView = text(title, 20, R.color.nermux_text_primary, true);
        panel.addView(titleView);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subtitleView = text(subtitle, 13, R.color.nermux_text_secondary, false);
            subtitleView.setPadding(0, dp(6), 0, dp(12));
            panel.addView(subtitleView);
        } else {
            addPanelSpacer(panel, 10);
        }

        panel.addView(body);
        addPanelSpacer(panel, 16);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        if (!TextUtils.isEmpty(negativeText)) {
            TextView negative = dialogButton(negativeText, false);
            negative.setOnClickListener(v -> {
                if (negativeAction == null) dialog.dismiss();
                else negativeAction.run(dialog);
            });
            actions.addView(negative);
        }

        if (!TextUtils.isEmpty(positiveText)) {
            TextView positive = dialogButton(positiveText, true);
            positive.setOnClickListener(v -> {
                if (positiveAction == null) dialog.dismiss();
                else positiveAction.run(dialog);
            });
            actions.addView(positive);
        }

        panel.addView(actions);
        dialog.setContentView(panel);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(32);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
        dialog.show();
        return dialog;
    }

    private LinearLayout fieldBlock(String label, EditText input) {
        LinearLayout block = verticalPanel(0);
        TextView labelView = text(label, 12, R.color.nermux_accent_bright, true);
        labelView.setPadding(dp(2), 0, 0, dp(6));
        block.addView(labelView);
        block.addView(input);
        addPanelSpacer(block, 12);
        return block;
    }

    private EditText powerInput(String label, String hint, String text) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(text);
        input.setSingleLine(true);
        input.setTextColor(color(R.color.nermux_text_primary));
        input.setHintTextColor(color(R.color.nermux_text_muted));
        input.setTextSize(15);
        input.setSelectAllOnFocus(false);
        input.setBackgroundResource(R.drawable.nermux_power_input_background);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setContentDescription(label);
        return input;
    }

    private View dialogAction(String text, boolean primary, View.OnClickListener listener) {
        TextView action = dialogButton(text, primary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, 0, 0, dp(10));
        action.setLayoutParams(params);
        action.setGravity(Gravity.CENTER);
        action.setOnClickListener(listener);
        return action;
    }

    private TextView dialogButton(String text, boolean primary) {
        TextView button = text(text, 14, primary ? android.R.color.black : R.color.nermux_text_primary, true);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(dp(88));
        button.setMinHeight(dp(44));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackgroundResource(primary ? R.drawable.nermux_power_primary_button_background : R.drawable.nermux_power_button_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void addSectionTitle(String text) {
        TextView view = text(text.toUpperCase(Locale.ROOT), 13, R.color.nermux_accent_bright, true);
        view.setPadding(0, dp(10), 0, dp(8));
        mContent.addView(view);
    }

    private void addInfo(String label, String value) {
        LinearLayout panel = verticalPanel(dp(14));
        panel.setBackgroundResource(R.drawable.nermux_power_card_background);
        panel.addView(text(label, 12, R.color.nermux_accent_bright, true));
        TextView valueView = text(value, 14, R.color.nermux_text_secondary, false);
        valueView.setPadding(0, dp(5), 0, 0);
        panel.addView(valueView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        mContent.addView(panel, params);
    }

    private Button fullButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(color(R.color.nermux_text_primary));
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        button.setMinHeight(dp(66));
        button.setMinWidth(0);
        button.setIncludeFontPadding(false);
        button.setLineSpacing(dp(2), 1f);
        button.setBackgroundResource(R.drawable.nermux_power_button_background);
        button.setPadding(dp(16), dp(11), dp(16), dp(11));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private Button primaryButton(String text) {
        Button button = fullButton(text);
        button.setTextColor(color(android.R.color.black));
        button.setGravity(Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setMinHeight(dp(54));
        button.setBackgroundResource(R.drawable.nermux_power_primary_button_background);
        return button;
    }

    private Button compactButton(String text) {
        Button button = primaryButton(text);
        button.setMinWidth(dp(82));
        button.setMinHeight(dp(44));
        button.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private TextView text(String text, int sp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color(colorRes));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private int color(int colorRes) {
        return ContextCompat.getColor(this, colorRes);
    }

    private TextView dialogSummary(String value) {
        TextView view = text(value, 14, R.color.nermux_text_secondary, false);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private LinearLayout verticalPanel(int padding) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);
        return layout;
    }

    private void addSpacer(int dp) {
        View spacer = new View(this);
        mContent.addView(spacer, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private void addPanelSpacer(LinearLayout panel, int dp) {
        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private String getWorkingDirectory() {
        File directory = new File(mStartDirectory);
        if (directory.isDirectory() && directory.canRead()) return directory.getAbsolutePath();
        return TermuxConstants.TERMUX_HOME_DIR_PATH;
    }

    private String getDeviceStatus() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager != null) manager.getMemoryInfo(info);
        long totalMemory = info.totalMem / 1024 / 1024;
        long availableMemory = info.availMem / 1024 / 1024;
        return Runtime.getRuntime().availableProcessors() + " CPU cores, " + availableMemory + " MB free / " + totalMemory + " MB RAM";
    }

    private String getStorageStatus() {
        File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        StatFs statFs = new StatFs(home.exists() ? home.getAbsolutePath() : getFilesDir().getAbsolutePath());
        long available = statFs.getAvailableBytes() / 1024 / 1024;
        long total = statFs.getTotalBytes() / 1024 / 1024;
        return available + " MB free / " + total + " MB total";
    }

    private String cleanField(String value, String fallback) {
        value = value == null ? "" : value.trim().replace("|", "");
        return value.isEmpty() ? fallback : value;
    }

    private boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return true;
        }
        return false;
    }

    private int parsePort(String port) {
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void performHaptic() {
        getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface DialogAction {
        void run(Dialog dialog);
    }

    private static final class SshProfile {
        final String name;
        final String host;
        final String user;
        final String port;

        SshProfile(String name, String host, String user, String port) {
            this.name = name;
            this.host = host;
            this.user = user;
            this.port = port;
        }

        String encode() {
            return name + "|" + host + "|" + user + "|" + port;
        }

        @Nullable
        static SshProfile parse(String raw) {
            if (raw == null) return null;
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 4) return null;
            return new SshProfile(parts[0], parts[1], parts[2], parts[3]);
        }
    }
}
