package com.termux.app;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.shared.shell.command.ExecutionCommand.ShellCreateMode;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class NermuxHomeActivity extends AppCompatActivity {

    private final List<CommandSection> mSections = createSections();
    private LinearLayout mCommandContainer;
    private EditText mSearchInput;
    private boolean mBootstrapReady;
    private boolean mBootstrapStarted;
    private boolean mOpenTerminalWhenReady;
    private CommandItem mPendingRunCommand;

    public static Intent newInstance(@NonNull Context context) {
        return new Intent(context, NermuxHomeActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(color(R.color.nermux_terminal_background));
        window.setNavigationBarColor(color(R.color.nermux_terminal_background));

        setContentView(createContentView());
        renderCommands("");

        ensureBootstrap();
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(color(R.color.nermux_terminal_background));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(42), dp(22), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            content.setPadding(dp(22), insets.getSystemWindowInsetTop() + dp(32), dp(22), dp(28));
            return insets;
        });

        content.addView(createTitleRow());
        content.addView(text("Quickstart commands for a fresh shell.", 14, R.color.nermux_text_secondary, false),
            blockParams(0, 6, 0, 0));
        content.addView(createSearchBox(), blockParams(0, 18, 0, 0));
        content.addView(createHeroCard(), blockParams(0, 16, 0, 0));

        mCommandContainer = new LinearLayout(this);
        mCommandContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(mCommandContainer, blockParams(0, 10, 0, 0));
        return scrollView;
    }

    private View createTitleRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        ImageView wordmark = new ImageView(this);
        wordmark.setImageResource(R.drawable.nermux_wordmark);
        wordmark.setAdjustViewBounds(true);
        wordmark.setScaleType(ImageView.ScaleType.FIT_START);
        row.addView(wordmark, new LinearLayout.LayoutParams(0, dp(46), 1));

        TextView shell = text("Shell", 14, R.color.nermux_accent_bright, true);
        shell.setGravity(Gravity.CENTER);
        shell.setIncludeFontPadding(false);
        shell.setBackground(round(Color.rgb(18, 22, 27), dp(19), color(R.color.nermux_outline), dp(1)));
        shell.setOnClickListener(v -> openTerminal());
        row.addView(shell, new LinearLayout.LayoutParams(dp(78), dp(38)));
        return row;
    }

    private View createSearchBox() {
        mSearchInput = new EditText(this);
        mSearchInput.setSingleLine(true);
        mSearchInput.setTextSize(15);
        mSearchInput.setTextColor(color(R.color.nermux_text_primary));
        mSearchInput.setHintTextColor(color(R.color.nermux_text_muted));
        mSearchInput.setHint("Search commands");
        mSearchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mSearchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        mSearchInput.setPadding(dp(14), 0, dp(14), 0);
        mSearchInput.setMinHeight(dp(46));
        mSearchInput.setBackground(round(color(R.color.nermux_surface_deep), dp(23), 0, 0));

        Drawable searchIcon = ContextCompat.getDrawable(this, R.drawable.ic_search);
        if (searchIcon != null) {
            searchIcon = searchIcon.mutate();
            searchIcon.setTint(color(R.color.nermux_text_muted));
            mSearchInput.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);
            mSearchInput.setCompoundDrawablePadding(dp(10));
        }

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderCommands(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        return mSearchInput;
    }

    private View createHeroCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round(Color.rgb(17, 21, 26), dp(24), 0, 0));
        card.setOnClickListener(v -> openTerminal());

        FrameLayout iconShell = new FrameLayout(this);
        iconShell.setBackground(oval(color(R.color.nermux_accent)));

        ImageView terminal = new ImageView(this);
        terminal.setImageResource(R.drawable.ic_terminal);
        terminal.setColorFilter(color(R.color.nermux_text_primary));
        terminal.setPadding(dp(12), dp(12), dp(12), dp(12));
        iconShell.addView(terminal, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        card.addView(iconShell, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, dp(10), 0);
        copy.addView(text("Ready to go", 15, R.color.nermux_text_primary, true));
        copy.addView(text("Start with update, then install packages.", 13, R.color.nermux_text_secondary, false),
            blockParams(0, 3, 0, 0));
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = text(">", 25, R.color.nermux_text_muted, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(48)));
        return card;
    }

    private void renderCommands(String rawQuery) {
        if (mCommandContainer == null) return;

        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        mCommandContainer.removeAllViews();

        int visibleSections = 0;
        for (CommandSection section : mSections) {
            List<CommandItem> visibleItems = new ArrayList<>();
            for (CommandItem item : section.items) {
                if (query.isEmpty() || item.matches(query) || section.title.toLowerCase(Locale.ROOT).contains(query)) {
                    visibleItems.add(item);
                }
            }

            if (!visibleItems.isEmpty()) {
                visibleSections++;
                addSection(section, visibleItems);
            }
        }

        if (visibleSections == 0) {
            TextView empty = text("No commands found", 15, R.color.nermux_text_secondary, true);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(round(Color.rgb(18, 22, 27), dp(20), color(R.color.nermux_outline), dp(1)));
            empty.setPadding(dp(18), dp(26), dp(18), dp(26));
            mCommandContainer.addView(empty, blockParams(0, 18, 0, 0));
        }
    }

    private void addSection(CommandSection section, List<CommandItem> visibleItems) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(4), 0, 0, 0);

        ImageView icon = new ImageView(this);
        icon.setImageResource(section.icon);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        icon.setBackground(oval(color(section.color)));
        header.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));

        TextView label = text(section.title, 12, R.color.nermux_text_muted, true);
        label.setLetterSpacing(0.08f);
        header.addView(label, blockParams(10, 0, 0, 0));
        mCommandContainer.addView(header, blockParams(0, 24, 0, 8));

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setClipToOutline(true);
        group.setBackground(round(Color.rgb(18, 22, 27), dp(20), Color.rgb(36, 42, 48), dp(1)));

        for (int i = 0; i < visibleItems.size(); i++) {
            CommandItem item = visibleItems.get(i);
            group.addView(createCommandRow(item, section.color));
            if (i < visibleItems.size() - 1) group.addView(divider());
        }

        mCommandContainer.addView(group);

        if (section.note != null && !section.note.isEmpty()) {
            mCommandContainer.addView(text(section.note, 12, R.color.nermux_text_muted, false),
                blockParams(4, 8, 4, 0));
        }
    }

    private View createCommandRow(CommandItem item, @ColorRes int accent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMinimumHeight(dp(66));
        row.setPadding(dp(16), dp(10), dp(10), dp(10));
        row.setOnClickListener(v -> copyCommand(item.command));
        row.setOnLongClickListener(v -> {
            runCommand(item);
            return true;
        });

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView command = text(item.command, 14, R.color.nermux_text_primary, true);
        command.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        labels.addView(command);
        labels.addView(text(item.subtitle, 12, R.color.nermux_text_secondary, false),
            blockParams(0, 5, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton copy = new ImageButton(this);
        copy.setImageResource(R.drawable.ic_content_copy);
        copy.setColorFilter(color(R.color.nermux_text_muted));
        copy.setBackground(round(Color.TRANSPARENT, dp(16), 0, 0));
        copy.setPadding(dp(10), dp(10), dp(10), dp(10));
        copy.setContentDescription("Copy command");
        copy.setOnClickListener(v -> copyCommand(item.command));
        row.addView(copy, new LinearLayout.LayoutParams(dp(44), dp(44)));

        return row;
    }

    private void copyCommand(String command) {
        performHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Nermux command", command));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void runCommand(CommandItem item) {
        performHaptic(HapticFeedbackConstants.LONG_PRESS);
        if (mBootstrapReady) {
            executeCommand(item);
            return;
        }

        mPendingRunCommand = item;
        ensureBootstrap();
        Toast.makeText(this, "Preparing shell", Toast.LENGTH_SHORT).show();
    }

    private void openTerminal() {
        if (mBootstrapReady) {
            startTerminalNow();
            return;
        }

        mOpenTerminalWhenReady = true;
        ensureBootstrap();
        Toast.makeText(this, "Preparing shell", Toast.LENGTH_SHORT).show();
    }

    private void ensureBootstrap() {
        if (mBootstrapReady || mBootstrapStarted) return;

        mBootstrapStarted = true;
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
            mBootstrapReady = true;
            CommandItem pendingCommand = mPendingRunCommand;
            mPendingRunCommand = null;

            if (pendingCommand != null) {
                executeCommand(pendingCommand);
            } else if (mOpenTerminalWhenReady) {
                mOpenTerminalWhenReady = false;
                startTerminalNow();
            }
        });
    }

    private void executeCommand(CommandItem item) {
        mOpenTerminalWhenReady = false;
        File executable = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR, "sh");
        if (!executable.canExecute()) executable = new File("/system/bin/sh");

        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE,
            new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(executable.getAbsolutePath()).build());
        intent.setClass(this, TermuxService.class);
        intent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[] { "-lc", item.command });
        intent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, TermuxConstants.TERMUX_HOME_DIR_PATH);
        intent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION,
            String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, item.title);
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ShellCreateMode.ALWAYS.getMode());
        startService(intent);
        startTerminalNow();
        Toast.makeText(this, getString(R.string.msg_power_command_started), Toast.LENGTH_SHORT).show();
    }

    private void startTerminalNow() {
        startActivity(TermuxActivity.newInstance(this));
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(Color.rgb(35, 40, 46));
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return view;
    }

    private TextView title(String value, int sp, @ColorRes int color, boolean bold) {
        TextView text = text(value, sp, color, bold);
        text.setLetterSpacing(0f);
        return text;
    }

    private TextView text(String value, int sp, @ColorRes int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color(color));
        text.setIncludeFontPadding(true);
        text.setGravity(Gravity.START);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private LinearLayout.LayoutParams blockParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private GradientDrawable round(int fill, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable oval(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    private int color(@ColorRes int resId) {
        return ContextCompat.getColor(this, resId);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressLint("MissingPermission")
    private void performHaptic(int feedbackConstant) {
        getWindow().getDecorView().performHapticFeedback(feedbackConstant);
    }

    private static List<CommandSection> createSections() {
        return Arrays.asList(
            new CommandSection("UPDATE", R.drawable.ic_refresh, R.color.nermux_accent,
                "Keeps your package index and installed packages current.",
                new CommandItem("Update packages", "pkg update", "Refresh package index"),
                new CommandItem("Upgrade packages", "pkg upgrade", "Upgrade installed packages")),

            new CommandSection("STORAGE", R.drawable.ic_folder, R.color.nermux_accent_yellow,
                "",
                new CommandItem("Setup storage", "termux-setup-storage", "Grant storage permission"),
                new CommandItem("Make projects", "mkdir -p ~/projects", "Create projects folder"),
                new CommandItem("Enter projects", "cd ~/projects", "Move into projects folder")),

            new CommandSection("PACKAGES", R.drawable.ic_add, R.color.nermux_accent_green,
                "",
                new CommandItem("Core tools", "pkg install git openssh curl wget", "Git, SSH, curl, and wget"),
                new CommandItem("Editors", "pkg install nano vim", "Install terminal editors"),
                new CommandItem("Node.js", "pkg install nodejs-lts", "Install Node.js LTS"),
                new CommandItem("Python build", "pkg install python clang make", "Python and native build tools")),

            new CommandSection("QUICK START", R.drawable.ic_terminal, R.color.nermux_accent_purple,
                "",
                new CommandItem("Python server", "python -m http.server 8080", "Serve current folder"),
                new CommandItem("Node project", "npm init -y && npm install", "Create a Node.js project"),
                new CommandItem("Python venv", "python -m venv .venv", "Create virtual environment"),
                new CommandItem("Activate venv", "source .venv/bin/activate", "Activate Python environment")),

            new CommandSection("FIXES", R.drawable.ic_settings, R.color.nermux_warning,
                "",
                new CommandItem("Change mirror", "termux-change-repo", "Pick a faster package mirror"),
                new CommandItem("Doctor", "command -v git node python clang make", "Check common dev tools"))
        );
    }

    private static class CommandSection {
        final String title;
        final int icon;
        final int color;
        final String note;
        final List<CommandItem> items;

        CommandSection(String title, @DrawableRes int icon, @ColorRes int color, String note, CommandItem... items) {
            this.title = title;
            this.icon = icon;
            this.color = color;
            this.note = note;
            this.items = Arrays.asList(items);
        }
    }

    private static class CommandItem {
        final String title;
        final String command;
        final String subtitle;

        CommandItem(String title, String command, String subtitle) {
            this.title = title;
            this.command = command;
            this.subtitle = subtitle;
        }

        boolean matches(String query) {
            return title.toLowerCase(Locale.ROOT).contains(query) ||
                command.toLowerCase(Locale.ROOT).contains(query) ||
                subtitle.toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
