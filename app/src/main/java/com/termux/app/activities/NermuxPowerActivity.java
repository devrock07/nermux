package com.termux.app.activities;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.StatFs;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.nermux_terminal_background));

        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setPadding(dp(18), dp(18), dp(18), dp(24));
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

        TextView title = new TextView(this);
        title.setText("Power Center");
        title.setTextColor(getColor(R.color.nermux_text_primary));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button close = compactButton("Close");
        close.setOnClickListener(v -> finish());
        row.addView(close);

        mContent.addView(row);
        addSpacer(14);
    }

    private void addStatusSection() {
        addSectionTitle("Status");
        addInfo("Project", mStartDirectory);
        addInfo("Device", getDeviceStatus());
        addInfo("Storage", getStorageStatus());
        addSpacer(12);
    }

    private void addCommandSection() {
        addSectionTitle("Stacks And Tools");
        addCommandButton("Base Dev Stack", "git, ssh, curl, wget, editors, tree, jq, ripgrep",
            "pkg update && pkg install -y git openssh curl wget nano vim tree jq ripgrep");
        addCommandButton("Web / Node Stack", "Node.js LTS, git, TypeScript, pnpm",
            "pkg update && pkg install -y nodejs-lts git && npm install -g typescript pnpm");
        addCommandButton("Python / Bot Stack", "Python, build tools, pip upgrade",
            "pkg update && pkg install -y python clang make git openssl libffi && python -m pip install --upgrade pip wheel");
        addCommandButton("Android Build Stack", "OpenJDK, Gradle, Android platform tools",
            "pkg update && pkg install -y openjdk-17 gradle android-tools aapt apksigner");
        addCommandButton("Network / Security Stack", "nmap, dnsutils, netcat, whois, openssh",
            "pkg update && pkg install -y nmap dnsutils netcat-openbsd whois openssh");
        addCommandButton("Nermux Doctor", "versions, storage, ports, and common tool checks",
            "printf 'Nermux doctor\\n\\n'; uname -a; printf '\\nTools:\\n'; for x in git ssh node npm python pip clang make; do command -v $x >/dev/null && printf '  ok  %s -> %s\\n' $x $(command -v $x) || printf '  miss %s\\n' $x; done; printf '\\nStorage:\\n'; df -h $HOME; printf '\\nPorts:\\n'; (ss -ltnp 2>/dev/null || netstat -tulpn 2>/dev/null || true)");
        addCommandButton("Port Manager", "show listening ports and likely dev servers",
            "printf 'Listening ports\\n\\n'; (ss -ltnp 2>/dev/null || netstat -tulpn 2>/dev/null || lsof -i -P -n 2>/dev/null || true); printf '\\nDev processes\\n\\n'; ps -A | grep -E 'node|python|java|nginx|php|ruby|deno' || true");
        addCommandButton("Export Current Project", "tar.gz current folder to /sdcard/Nermux/exports",
            "mkdir -p /sdcard/Nermux/exports && name=$(basename \"$PWD\") && tar -czf \"/sdcard/Nermux/exports/${name:-project}-$(date +%Y%m%d-%H%M%S).tar.gz\" -C \"$PWD\" . && ls -lh /sdcard/Nermux/exports");
        addCommandButton("Backup Home", "tar.gz $HOME to /sdcard/Nermux/backups",
            "mkdir -p /sdcard/Nermux/backups && tar -czf \"/sdcard/Nermux/backups/home-$(date +%Y%m%d-%H%M%S).tar.gz\" -C \"$HOME\" . && ls -lh /sdcard/Nermux/backups");
        addCommandButton("Generate SSH Key", "ed25519 key at ~/.ssh/id_ed25519",
            "mkdir -p ~/.ssh && chmod 700 ~/.ssh && test -f ~/.ssh/id_ed25519 || ssh-keygen -t ed25519 -C nermux -f ~/.ssh/id_ed25519; printf '\\nPublic key:\\n'; cat ~/.ssh/id_ed25519.pub");

        Button custom = fullButton("Run Custom Command");
        custom.setOnClickListener(v -> showCustomCommandDialog());
        mContent.addView(custom);
        addSpacer(12);
    }

    private void addSshSection() {
        addSectionTitle("SSH Profiles");

        List<String> profiles = getSshProfiles();
        if (profiles.isEmpty()) {
            addInfo("Saved", "No SSH profiles yet.");
        } else {
            for (String profile : profiles) {
                String[] parts = profile.split("\\|", -1);
                if (parts.length != 4) continue;
                Button button = fullButton(parts[0] + "  " + parts[2] + "@" + parts[1] + ":" + parts[3]);
                button.setOnClickListener(v -> runCommand("SSH " + parts[0],
                    "ssh -p " + shellQuote(parts[3]) + " " + shellQuote(parts[2] + "@" + parts[1])));
                button.setOnLongClickListener(v -> {
                    deleteSshProfile(profile);
                    render();
                    return true;
                });
                mContent.addView(button);
            }
        }

        Button add = fullButton("Add SSH Profile");
        add.setOnClickListener(v -> showAddSshProfileDialog());
        mContent.addView(add);
        addSpacer(12);
    }

    private void addSystemSection() {
        addSectionTitle("Runtime Controls");

        Button wakeLock = fullButton("Acquire Wake Lock");
        wakeLock.setOnClickListener(v -> sendWakeAction(TERMUX_SERVICE.ACTION_WAKE_LOCK, "Wake lock acquired"));
        mContent.addView(wakeLock);

        Button wakeUnlock = fullButton("Release Wake Lock");
        wakeUnlock.setOnClickListener(v -> sendWakeAction(TERMUX_SERVICE.ACTION_WAKE_UNLOCK, "Wake lock released"));
        mContent.addView(wakeUnlock);

        addCommandButton("Open Projects Folder", "create and jump to ~/projects",
            "mkdir -p ~/projects && cd ~/projects && pwd && ls -la");
    }

    private void addCommandButton(String title, String subtitle, String command) {
        Button button = fullButton(title + "\n" + subtitle);
        button.setGravity(Gravity.CENTER_VERTICAL);
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
        intent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, mStartDirectory);
        intent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION, String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, shellName);
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ShellCreateMode.ALWAYS.getMode());
        startService(intent);
        startActivity(TermuxActivity.newInstance(this));
        Toast.makeText(this, getString(R.string.msg_power_command_started), Toast.LENGTH_SHORT).show();
    }

    private void sendWakeAction(String action, String message) {
        performHaptic();
        Intent intent = new Intent(this, TermuxService.class).setAction(action);
        startService(intent);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showCustomCommandDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("npm run dev");

        new AlertDialog.Builder(this)
            .setTitle("Run Custom Command")
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Run", (dialog, which) -> {
                String command = input.getText().toString().trim();
                if (!command.isEmpty()) runCommand("Custom", command);
            })
            .show();
    }

    private void showAddSshProfileDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        layout.setPadding(padding, padding, padding, 0);

        EditText name = dialogInput("Name", "VPS");
        EditText host = dialogInput("Host", "example.com");
        EditText user = dialogInput("User", "root");
        EditText port = dialogInput("Port", "22");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);

        layout.addView(name);
        layout.addView(host);
        layout.addView(user);
        layout.addView(port);

        new AlertDialog.Builder(this)
            .setTitle("Add SSH Profile")
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Save", (dialog, which) -> {
                String profileName = cleanField(name.getText().toString(), "SSH");
                String profileHost = cleanField(host.getText().toString(), "");
                String profileUser = cleanField(user.getText().toString(), "root");
                String profilePort = cleanField(port.getText().toString(), "22");
                if (profileHost.isEmpty()) return;
                saveSshProfile(profileName + "|" + profileHost + "|" + profileUser + "|" + profilePort);
                render();
            })
            .show();
    }

    private EditText dialogInput(String hint, String text) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(text);
        input.setSingleLine(true);
        return input;
    }

    private List<String> getSshProfiles() {
        Set<String> profileSet = mPreferences.getStringSet(KEY_SSH_PROFILES, Collections.emptySet());
        List<String> profiles = new ArrayList<>(profileSet);
        Collections.sort(profiles);
        return profiles;
    }

    private void saveSshProfile(String profile) {
        Set<String> profiles = new HashSet<>(mPreferences.getStringSet(KEY_SSH_PROFILES, Collections.emptySet()));
        profiles.add(profile);
        mPreferences.edit().putStringSet(KEY_SSH_PROFILES, profiles).apply();
    }

    private void deleteSshProfile(String profile) {
        Set<String> profiles = new HashSet<>(mPreferences.getStringSet(KEY_SSH_PROFILES, Collections.emptySet()));
        profiles.remove(profile);
        mPreferences.edit().putStringSet(KEY_SSH_PROFILES, profiles).apply();
        Toast.makeText(this, "SSH profile removed", Toast.LENGTH_SHORT).show();
    }

    private void addSectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.nermux_accent_bright));
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setAllCaps(true);
        view.setPadding(0, dp(8), 0, dp(8));
        mContent.addView(view);
    }

    private void addInfo(String label, String value) {
        TextView view = new TextView(this);
        view.setText(label + "\n" + value);
        view.setTextColor(getColor(R.color.nermux_text_secondary));
        view.setTextSize(14);
        view.setPadding(0, dp(4), 0, dp(8));
        mContent.addView(view);
    }

    private Button fullButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(getColor(R.color.nermux_text_primary));
        button.setBackgroundResource(R.drawable.nermux_button_background);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private Button compactButton(String text) {
        Button button = fullButton(text);
        button.setMinWidth(dp(84));
        button.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private void addSpacer(int dp) {
        View spacer = new View(this);
        mContent.addView(spacer, new LinearLayout.LayoutParams(1, dp(dp)));
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

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void performHaptic() {
        getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
