package com.termux.app;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.activities.NermuxWorkspaceActivity;
import com.termux.app.activities.SettingsActivity;
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
    private View mBootstrapOverlay;
    private TextView mBootstrapStatusView;
    private final List<TextView> mBootstrapStepViews = new ArrayList<>();
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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.nermux_terminal_background));

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

        root.addView(scrollView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mBootstrapOverlay = createBootstrapOverlay();
        root.addView(mBootstrapOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View createBootstrapOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(214, 12, 14, 17));
        overlay.setVisibility(View.GONE);
        overlay.setAlpha(0f);
        overlay.setClickable(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(round(Color.rgb(21, 25, 30), dp(24), color(R.color.nermux_outline), dp(1)));

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setOrientation(LinearLayout.HORIZONTAL);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        hero.addView(progressBar, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(16), 0, 0, 0);
        copy.addView(text("Preparing shell", 16, R.color.nermux_text_primary, true));
        mBootstrapStatusView = text("Checking runtime files", 13, R.color.nermux_text_secondary, false);
        copy.addView(mBootstrapStatusView, blockParams(0, 5, 0, 0));
        hero.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(hero);

        mBootstrapStepViews.clear();
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setPadding(dp(60), dp(12), 0, 0);
        String[] labels = { "Files", "Folders", "Packages", "Ready" };
        for (String label : labels) {
            TextView step = text("- " + label, 12, R.color.nermux_text_muted, false);
            step.setTypeface(Typeface.MONOSPACE);
            mBootstrapStepViews.add(step);
            steps.addView(step, blockParams(0, 4, 0, 0));
        }
        card.addView(steps);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        cardParams.setMargins(dp(18), dp(18), dp(18), dp(26));
        overlay.addView(card, cardParams);
        return overlay;
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

        ImageButton palette = new ImageButton(this);
        palette.setImageResource(R.drawable.ic_search);
        palette.setColorFilter(color(R.color.nermux_text_primary));
        palette.setBackground(round(Color.rgb(18, 22, 27), dp(19), color(R.color.nermux_outline), dp(1)));
        palette.setContentDescription(getString(R.string.action_command_palette));
        palette.setPadding(dp(10), dp(10), dp(10), dp(10));
        palette.setOnClickListener(v -> showCommandPalette());
        attachPressFeedback(palette);
        LinearLayout.LayoutParams paletteParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        paletteParams.setMargins(0, 0, dp(8), 0);
        row.addView(palette, paletteParams);

        TextView shell = text("Shell", 14, R.color.nermux_accent_bright, true);
        shell.setGravity(Gravity.CENTER);
        shell.setIncludeFontPadding(false);
        shell.setBackground(round(Color.rgb(18, 22, 27), dp(19), color(R.color.nermux_outline), dp(1)));
        shell.setOnClickListener(v -> openTerminal());
        attachPressFeedback(shell);
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
        attachPressFeedback(card);

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
        icon.setColorFilter(iconTintForAccent(section.color));
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
            showRunSheet(item);
            return true;
        });
        attachPressFeedback(row);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView command = text(item.command, 14, R.color.nermux_text_primary, true);
        command.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        labels.addView(command);
        labels.addView(text(item.subtitle, 12, R.color.nermux_text_secondary, false),
            blockParams(0, 5, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView status = statusPill(statusForItem(item));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
        statusParams.setMargins(dp(8), 0, dp(8), 0);
        row.addView(status, statusParams);

        ImageButton run = new ImageButton(this);
        run.setImageResource(R.drawable.ic_play_arrow);
        run.setColorFilter(iconTintForAccent(accent));
        run.setBackground(oval(color(accent)));
        run.setPadding(dp(10), dp(10), dp(10), dp(10));
        run.setContentDescription(getString(R.string.action_run_in_terminal));
        run.setOnClickListener(v -> runCommand(item));
        run.setOnLongClickListener(v -> {
            showRunSheet(item);
            return true;
        });
        attachPressFeedback(run);
        row.addView(run, new LinearLayout.LayoutParams(dp(38), dp(38)));

        ImageButton copy = new ImageButton(this);
        copy.setImageResource(R.drawable.ic_content_copy);
        copy.setColorFilter(color(R.color.nermux_text_muted));
        copy.setBackground(round(Color.TRANSPARENT, dp(16), 0, 0));
        copy.setPadding(dp(10), dp(10), dp(10), dp(10));
        copy.setContentDescription("Copy command");
        copy.setOnClickListener(v -> copyCommand(item.command));
        attachPressFeedback(copy);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        copyParams.setMargins(dp(8), 0, 0, 0);
        row.addView(copy, copyParams);

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

    private TextView statusPill(CommandStatus status) {
        TextView pill = text(status.label, 10, status.textColor, true);
        pill.setGravity(Gravity.CENTER);
        pill.setIncludeFontPadding(false);
        pill.setMinWidth(dp(58));
        pill.setPadding(dp(9), 0, dp(9), 0);
        pill.setBackground(round(status.fillColor, dp(14), status.strokeColor, dp(1)));
        return pill;
    }

    private CommandStatus statusForItem(CommandItem item) {
        if (!mBootstrapReady)
            return new CommandStatus("CHECK", R.color.nermux_text_secondary, Color.rgb(24, 27, 31), color(R.color.nermux_outline));
        if (item.probeTool == null)
            return new CommandStatus("READY", R.color.nermux_text_secondary, Color.rgb(24, 27, 31), color(R.color.nermux_outline));

        boolean installed = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR, item.probeTool).canExecute();
        if (installed)
            return new CommandStatus("OK", R.color.nermux_accent_green, Color.rgb(19, 34, 31), color(R.color.nermux_accent_green));
        return new CommandStatus("MISS", R.color.nermux_accent_yellow, Color.rgb(38, 32, 18), color(R.color.nermux_accent_yellow));
    }

    private void showRunSheet(CommandItem item) {
        Dialog dialog = new Dialog(this);
        LinearLayout sheet = createSheetContainer();
        sheet.addView(text("Run command", 20, R.color.nermux_text_primary, true));
        sheet.addView(text("Choose how Nermux should launch it.", 13, R.color.nermux_text_secondary, false),
            blockParams(0, 5, 0, 0));

        TextView command = text(item.command, 14, R.color.nermux_accent_green, true);
        command.setTypeface(Typeface.MONOSPACE);
        command.setPadding(dp(14), dp(12), dp(14), dp(12));
        command.setBackground(round(color(R.color.nermux_surface_deep), dp(12), color(R.color.nermux_outline), dp(1)));
        sheet.addView(command, blockParams(0, 16, 0, 0));

        sheet.addView(sheetAction("Run now", "Open a new shell session and execute it.", R.drawable.ic_play_arrow,
            () -> {
                dialog.dismiss();
                runCommand(item);
            }), blockParams(0, 14, 0, 0));
        sheet.addView(sheetAction("Copy command", "Put the command on clipboard.", R.drawable.ic_content_copy,
            () -> {
                copyCommand(item.command);
                dialog.dismiss();
            }), blockParams(0, 8, 0, 0));
        sheet.addView(sheetAction("Open shell", "Jump to terminal without running anything.", R.drawable.ic_terminal,
            () -> {
                dialog.dismiss();
                openTerminal();
            }), blockParams(0, 8, 0, 0));

        dialog.setContentView(sheet);
        showDialog(dialog, Gravity.BOTTOM);
    }

    private void showCommandPalette() {
        Dialog dialog = new Dialog(this);
        LinearLayout palette = createSheetContainer();
        palette.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(text("Command palette", 20, R.color.nermux_text_primary, true),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView hintPill = text("RUN / OPEN / COPY", 10, R.color.nermux_text_muted, true);
        hintPill.setGravity(Gravity.CENTER);
        hintPill.setIncludeFontPadding(false);
        hintPill.setPadding(dp(9), 0, dp(9), 0);
        hintPill.setBackground(round(Color.rgb(24, 27, 31), dp(12), color(R.color.nermux_outline), dp(1)));
        header.addView(hintPill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)));
        palette.addView(header);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(color(R.color.nermux_text_primary));
        input.setHintTextColor(color(R.color.nermux_text_muted));
        input.setHint("Search commands or screens");
        input.setTextSize(15);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(10), 0, 0, 0);
        input.setMinHeight(dp(48));
        input.setIncludeFontPadding(false);
        input.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setPadding(dp(14), 0, dp(14), 0);
        searchBar.setMinimumHeight(dp(50));
        searchBar.setBackground(round(Color.rgb(14, 17, 21), dp(16), color(R.color.nermux_outline_strong), dp(1)));

        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(color(R.color.nermux_accent_bright));
        searchBar.addView(searchIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        searchBar.addView(input, new LinearLayout.LayoutParams(0, dp(50), 1));
        palette.addView(searchBar, blockParams(0, 14, 0, 0));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        palette.addView(results, blockParams(0, 14, 0, 0));

        Runnable[] render = new Runnable[1];
        render[0] = () -> renderPaletteResults(results, dialog, input.getText().toString());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render[0].run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.setContentView(palette);
        showDialog(dialog, Gravity.TOP);
        render[0].run();
        input.requestFocus();
        Window window = dialog.getWindow();
        if (window != null)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void renderPaletteResults(LinearLayout results, Dialog dialog, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        results.removeAllViews();

        List<PaletteAction> actions = new ArrayList<>();
        actions.add(new PaletteAction("Open shell", "Jump into the terminal", R.drawable.ic_terminal, this::openTerminal));
        actions.add(new PaletteAction("Open workspace", "Browse files and edit code", R.drawable.ic_folder,
            () -> startActivity(NermuxWorkspaceActivity.newInstance(this, TermuxConstants.TERMUX_HOME_DIR_PATH))));
        actions.add(new PaletteAction("Open settings", "Tune Nermux preferences", R.drawable.ic_settings,
            () -> startActivity(new Intent(this, SettingsActivity.class))));

        for (CommandSection section : mSections) {
            for (CommandItem item : section.items) {
                actions.add(new PaletteAction(item.title, item.command, section.icon, () -> showRunSheet(item)));
            }
        }

        int shown = 0;
        for (PaletteAction action : actions) {
            if (!query.isEmpty() && !action.matches(query)) continue;
            shown++;
            results.addView(sheetAction(action.title, action.subtitle, action.icon, () -> {
                dialog.dismiss();
                action.run();
            }), blockParams(0, shown == 1 ? 0 : 8, 0, 0));
            if (shown == 7) break;
        }

        if (shown == 0) {
            TextView empty = text("No match", 14, R.color.nermux_text_secondary, true);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            results.addView(empty);
        }
    }

    private LinearLayout sheetAction(String title, String subtitle, @DrawableRes int iconRes, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(13), dp(10), dp(13), dp(10));
        row.setBackground(round(color(R.color.nermux_surface_deep), dp(14), color(R.color.nermux_outline), dp(1)));
        row.setOnClickListener(v -> {
            performHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
            action.run();
        });
        attachPressFeedback(row);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color(R.color.nermux_accent_bright));
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setBackground(oval(Color.rgb(26, 37, 48)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text(title, 14, R.color.nermux_text_primary, true));
        TextView subtitleView = text(subtitle, 12, R.color.nermux_text_secondary, false);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(subtitleView, blockParams(0, 4, 0, 0));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private LinearLayout createSheetContainer() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(20), dp(18), dp(20));
        sheet.setBackground(round(Color.rgb(21, 24, 29), dp(22), color(R.color.nermux_outline), dp(1)));
        return sheet;
    }

    private void showDialog(Dialog dialog, int gravity) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(gravity);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.width = getResources().getDisplayMetrics().widthPixels - dp(24);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.y = gravity == Gravity.TOP ? dp(18) : dp(12);
        window.setAttributes(params);
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
        if (mBootstrapReady) {
            hideBootstrapOverlay();
            return;
        }

        showBootstrapOverlay("Checking runtime files");
        if (mBootstrapStarted) return;

        mBootstrapStarted = true;
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
            mBootstrapReady = true;
            hideBootstrapOverlay();
            renderCommands(mSearchInput == null ? "" : mSearchInput.getText().toString());
            CommandItem pendingCommand = mPendingRunCommand;
            mPendingRunCommand = null;

            if (pendingCommand != null) {
                executeCommand(pendingCommand);
            } else if (mOpenTerminalWhenReady) {
                mOpenTerminalWhenReady = false;
                startTerminalNow();
            }
        }, this::showBootstrapOverlay);
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

    private void showBootstrapOverlay(String message) {
        runOnUiThread(() -> {
            if (mBootstrapStatusView != null) mBootstrapStatusView.setText(message);
            updateBootstrapSteps(message);
            if (mBootstrapOverlay == null || mBootstrapOverlay.getVisibility() == View.VISIBLE) return;

            mBootstrapOverlay.setVisibility(View.VISIBLE);
            mBootstrapOverlay.animate().alpha(1f).setDuration(160).start();
        });
    }

    private void updateBootstrapSteps(String message) {
        int active = 0;
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("folder")) active = 1;
        else if (lower.contains("extract") || lower.contains("package") || lower.contains("link")) active = 2;
        else if (lower.contains("ready") || lower.contains("final")) active = 3;

        for (int i = 0; i < mBootstrapStepViews.size(); i++) {
            TextView step = mBootstrapStepViews.get(i);
            String label = step.getText().toString().replace("+ ", "").replace("> ", "").replace("- ", "");
            if (i < active) {
                step.setText("+ " + label);
                step.setTextColor(color(R.color.nermux_accent_green));
            } else if (i == active) {
                step.setText("> " + label);
                step.setTextColor(color(R.color.nermux_accent_bright));
            } else {
                step.setText("- " + label);
                step.setTextColor(color(R.color.nermux_text_muted));
            }
        }
    }

    private void hideBootstrapOverlay() {
        runOnUiThread(() -> {
            if (mBootstrapOverlay == null || mBootstrapOverlay.getVisibility() != View.VISIBLE) return;

            mBootstrapOverlay.animate().alpha(0f).setDuration(140).withEndAction(() -> {
                if (mBootstrapOverlay != null) mBootstrapOverlay.setVisibility(View.GONE);
            }).start();
        });
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

    private int iconTintForAccent(@ColorRes int accent) {
        return Color.WHITE;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachPressFeedback(View view) {
        view.setOnTouchListener((v, event) -> {
            if (!v.isEnabled()) return false;

            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.86f).setDuration(80).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
            }
            return false;
        });
    }

    @SuppressLint("MissingPermission")
    private void performHaptic(int feedbackConstant) {
        getWindow().getDecorView().performHapticFeedback(feedbackConstant);
    }

    private static List<CommandSection> createSections() {
        return Arrays.asList(
            new CommandSection("UPDATE", R.drawable.ic_refresh, R.color.nermux_accent,
                "Keeps your package index and installed packages current.",
                new CommandItem("Update packages", "pkg update", "Refresh package index", "pkg"),
                new CommandItem("Upgrade packages", "pkg upgrade", "Upgrade installed packages", "pkg")),

            new CommandSection("STORAGE", R.drawable.ic_folder, R.color.nermux_accent_yellow,
                "",
                new CommandItem("Setup storage", "termux-setup-storage", "Grant storage permission", "termux-setup-storage"),
                new CommandItem("Make projects", "mkdir -p ~/projects", "Create projects folder"),
                new CommandItem("Enter projects", "cd ~/projects", "Move into projects folder")),

            new CommandSection("PACKAGES", R.drawable.ic_add, R.color.nermux_accent_green,
                "",
                new CommandItem("Core tools", "pkg install git openssh curl wget", "Git, SSH, curl, and wget", "git"),
                new CommandItem("Editors", "pkg install nano vim", "Install terminal editors", "vim"),
                new CommandItem("Node.js", "pkg install nodejs-lts", "Install Node.js LTS", "node"),
                new CommandItem("Python build", "pkg install python clang make", "Python and native build tools", "python")),

            new CommandSection("QUICK START", R.drawable.ic_terminal, R.color.nermux_accent_purple,
                "",
                new CommandItem("Python server", "python -m http.server 8080", "Serve current folder", "python"),
                new CommandItem("Node project", "npm init -y && npm install", "Create a Node.js project", "npm"),
                new CommandItem("Python venv", "python -m venv .venv", "Create virtual environment", "python"),
                new CommandItem("Activate venv", "source .venv/bin/activate", "Activate Python environment")),

            new CommandSection("FIXES", R.drawable.ic_settings, R.color.nermux_warning,
                "",
                new CommandItem("Change mirror", "termux-change-repo", "Pick a faster package mirror", "termux-change-repo"),
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
        final String probeTool;

        CommandItem(String title, String command, String subtitle) {
            this(title, command, subtitle, null);
        }

        CommandItem(String title, String command, String subtitle, String probeTool) {
            this.title = title;
            this.command = command;
            this.subtitle = subtitle;
            this.probeTool = probeTool;
        }

        boolean matches(String query) {
            return title.toLowerCase(Locale.ROOT).contains(query) ||
                command.toLowerCase(Locale.ROOT).contains(query) ||
                subtitle.toLowerCase(Locale.ROOT).contains(query);
        }
    }

    private static class CommandStatus {
        final String label;
        final int textColor;
        final int fillColor;
        final int strokeColor;

        CommandStatus(String label, @ColorRes int textColor, int fillColor, int strokeColor) {
            this.label = label;
            this.textColor = textColor;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
        }
    }

    private static class PaletteAction {
        final String title;
        final String subtitle;
        final int icon;
        final Runnable action;

        PaletteAction(String title, String subtitle, @DrawableRes int icon, Runnable action) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.action = action;
        }

        boolean matches(String query) {
            return title.toLowerCase(Locale.ROOT).contains(query) ||
                subtitle.toLowerCase(Locale.ROOT).contains(query);
        }

        void run() {
            action.run();
        }
    }
}
