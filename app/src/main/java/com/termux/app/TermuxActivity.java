package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.ai.NermuxAiClient;
import com.termux.app.ai.NermuxAiChatStore;
import com.termux.app.ai.NermuxAiConfig;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.NermuxWorkspaceActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.shared.shell.ShellUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private boolean mOpenAgentWorkspacePickerAfterStoragePermission;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;
    private static final int REQUEST_AI_WORKSPACE_FOLDER = 4102;
    private static final int MAX_AGENT_WORKSPACE_TREE_ENTRIES = 80;
    private static final int MAX_AGENT_WORKSPACE_CONTEXT_FILES = 8;
    private static final int MAX_AGENT_WORKSPACE_FILE_BYTES = 64000;
    private static final int MAX_AGENT_WORKSPACE_CONTEXT_CHARS = 26000;
    private static final String AGENT_BACKUP_DIR_NAME = ".nermux-agent-backups";

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";
    private static final long UI_MOTION_SHORT_MS = 150L;
    private static final long UI_MOTION_MEDIUM_MS = 260L;
    private static final PathInterpolator UI_MOTION_INTERPOLATOR = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final Pattern AGENT_FILE_HINT_PATTERN = Pattern.compile(
        "([A-Za-z0-9_./-]+\\.(html|htm|css|js|mjs|ts|tsx|jsx|json|py|sh|md|txt|xml|java|kt|gradle|yml|yaml|toml|php|go|rs|c|cpp|h|hpp))",
        Pattern.CASE_INSENSITIVE
    );
    private static final String[] AGENT_PRIORITY_CONTEXT_FILES = {
        "package.json",
        "pnpm-lock.yaml",
        "yarn.lock",
        "package-lock.json",
        "README.md",
        "readme.md",
        "requirements.txt",
        "pyproject.toml",
        "build.gradle",
        "settings.gradle",
        "pom.xml",
        "index.html",
        "server.js",
        "app.js",
        "main.py"
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        startTerminalEntryMotion();

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setAgentButtonView();

        setAgentPanelView();

        setWorkspaceButtonView();

        setPowerCenterButtonView();

        setNewSessionButtonView();

        setToggleKeyboardView();

        setDrawerMotion();

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        refreshAgentProviderStatus();
        refreshAgentWorkspaceStatus();

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) {
            terminalToolbarViewPager.setVisibility(View.VISIBLE);
            terminalToolbarViewPager.setAlpha(1f);
            terminalToolbarViewPager.setTranslationY(0f);
        }

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        animateTerminalToolbarVisibility(terminalToolbarViewPager, showNow);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        attachPressMotion(settingsButton);
        settingsButton.setOnClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setAgentButtonView() {
        ImageButton agentButton = findViewById(R.id.agent_button);
        attachPressMotion(agentButton);
        agentButton.setOnClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
            refreshAgentProviderStatus();
            refreshAgentWorkspaceStatus();
            DrawerLayout drawer = getDrawer();
            drawer.closeDrawers();
            drawer.postDelayed(() -> drawer.openDrawer(Gravity.RIGHT), UI_MOTION_MEDIUM_MS);
        });
        agentButton.setOnLongClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.LONG_PRESS);
            openAiSettings();
            return true;
        });
    }

    private void setAgentPanelView() {
        View closeButton = findViewById(R.id.close_agent_button);
        View clearButton = findViewById(R.id.agent_clear_button);
        View settingsButton = findViewById(R.id.agent_settings_button);
        View sendButton = findViewById(R.id.agent_send_button);
        View fixButton = findViewById(R.id.agent_fix_button);
        View explainButton = findViewById(R.id.agent_explain_button);
        View providerStatus = findViewById(R.id.agent_provider_status);
        View chooseWorkspaceButton = findViewById(R.id.agent_choose_workspace_button);
        View shellWorkspaceButton = findViewById(R.id.agent_shell_workspace_button);
        View openWorkspaceButton = findViewById(R.id.agent_open_workspace_button);
        EditText promptInput = findViewById(R.id.agent_prompt_input);

        attachPressMotion(closeButton);
        attachPressMotion(clearButton);
        attachPressMotion(settingsButton);
        attachPressMotion(sendButton);
        attachPressMotion(fixButton);
        attachPressMotion(explainButton);
        attachPressMotion(providerStatus);
        attachPressMotion(chooseWorkspaceButton);
        attachPressMotion(shellWorkspaceButton);
        attachPressMotion(openWorkspaceButton);

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
                getDrawer().closeDrawer(Gravity.RIGHT);
            });
        }

        if (clearButton != null)
            clearButton.setOnClickListener(v -> confirmClearAgentChat());

        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
                openAiSettings();
            });
        }

        if (providerStatus != null) {
            providerStatus.setOnClickListener(v -> {
                performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
                openAiSettings();
            });
        }

        if (chooseWorkspaceButton != null)
            chooseWorkspaceButton.setOnClickListener(v -> openAgentWorkspacePickerWithStorageSetup());

        if (shellWorkspaceButton != null)
            shellWorkspaceButton.setOnClickListener(v -> useCurrentShellFolderAsAgentWorkspace());

        if (openWorkspaceButton != null)
            openWorkspaceButton.setOnClickListener(v -> openAgentWorkspaceInExplorer());

        if (sendButton != null)
            sendButton.setOnClickListener(v -> sendAgentRequest("answer the user's terminal question"));

        if (fixButton != null)
            fixButton.setOnClickListener(v -> sendAgentRequest("fix the current terminal error"));

        if (explainButton != null)
            explainButton.setOnClickListener(v -> sendAgentRequest("explain the current terminal output"));

        if (promptInput != null) {
            promptInput.setText(NermuxAiChatStore.loadDraft(this));
            promptInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    NermuxAiChatStore.saveDraft(TermuxActivity.this, s == null ? "" : s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        refreshAgentProviderStatus();
        refreshAgentWorkspaceStatus();
        renderAgentChat(false);
    }

    private void openAiSettings() {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        settingsIntent.putExtra(SettingsActivity.EXTRA_OPEN_AI_SETTINGS, true);
        ActivityUtils.startActivity(this, settingsIntent);
    }

    private void openAgentWorkspacePickerWithStorageSetup() {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        if (!PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
            this, PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION, true)) {
            mOpenAgentWorkspacePickerAfterStoragePermission = true;
            setAgentStatus(getString(R.string.msg_ai_storage_permission_needed));
            showToast(getString(R.string.msg_ai_storage_permission_needed), false);
            return;
        }

        TermuxInstaller.setupStorageSymlinks(this);
        openAgentWorkspacePicker();
    }

    private void openAgentWorkspacePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_AI_WORKSPACE_FOLDER);
    }

    private void continueAgentWorkspacePickerAfterStoragePermission() {
        if (!mOpenAgentWorkspacePickerAfterStoragePermission) return;
        mOpenAgentWorkspacePickerAfterStoragePermission = false;

        View rootView = getWindow() == null ? null : getWindow().getDecorView();
        if (rootView == null) return;
        rootView.postDelayed(() -> {
            if (!PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(this, -1, false)) {
                setAgentStatus(getString(R.string.error_ai_workspace_missing));
                return;
            }

            TermuxInstaller.setupStorageSymlinks(this);
            openAgentWorkspacePicker();
        }, UI_MOTION_MEDIUM_MS);
    }

    private void useCurrentShellFolderAsAgentWorkspace() {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        TerminalSession session = getCurrentSession();
        String cwd = session == null ? null : session.getCwd();
        if (TextUtils.isEmpty(cwd)) {
            showToast(getString(R.string.error_ai_workspace_missing), true);
            return;
        }

        File directory = canonicalOrSelf(new File(cwd));
        if (directory == null || !directory.isDirectory()) {
            showToast(getString(R.string.error_ai_workspace_invalid), true);
            return;
        }

        setAgentWorkspace(directory);
    }

    private void openAgentWorkspaceInExplorer() {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        File workspace = getAgentWorkspaceDir();
        if (workspace == null) {
            showToast(getString(R.string.error_ai_workspace_missing), true);
            return;
        }
        ActivityUtils.startActivity(this, NermuxWorkspaceActivity.newInstance(this, workspace.getAbsolutePath()));
    }

    private void sendAgentRequest(@NonNull String task) {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        refreshAgentProviderStatus();
        refreshAgentWorkspaceStatus();

        if (!NermuxAiConfig.hasApiKey(this)) {
            setAgentStatus(getString(R.string.nermux_ai_api_key_missing));
            appendAssistantMessage("Open AI Providers at the bottom of this panel. Choose ChatGPT, Gemini, Groq, OpenRouter, or Custom, then tap \"2. API key\" and paste your key.", null);
            return;
        }

        EditText promptInput = findViewById(R.id.agent_prompt_input);
        String prompt = promptInput == null ? "" : promptInput.getText().toString();
        if (requiresAgentWorkspace(task, prompt) && getAgentWorkspaceDir() == null) {
            setAgentStatus(getString(R.string.error_ai_workspace_missing));
            appendAssistantMessage("Choose a project workspace first. Tap Choose for a shared-storage folder, or Use shell after you cd into the folder you want the agent to edit.", null);
            return;
        }

        String displayPrompt = TextUtils.isEmpty(prompt.trim()) ? task : prompt.trim();
        String terminalContext = getTerminalTranscriptForAgent();
        List<NermuxAiChatStore.Message> previousMessages = NermuxAiChatStore.loadMessages(this);

        NermuxAiChatStore.appendMessage(this, NermuxAiChatStore.Message.user(displayPrompt));
        NermuxAiChatStore.clearDraft(this);
        if (promptInput != null) promptInput.setText("");
        renderAgentChat(true);
        setAgentBusy(true);
        setAgentStatus(getString(R.string.nermux_ai_thinking));

        NermuxAiClient.ask(this, task, terminalContext, prompt, previousMessages, new NermuxAiClient.Callback() {
            @Override
            public void onSuccess(@NonNull String answer) {
                runOnUiThread(() -> {
                    setAgentBusy(false);
                    String command = NermuxAiConfig.shouldAllowRunCommands(TermuxActivity.this)
                        ? extractRunnableCommand(answer)
                        : null;
                    List<NermuxAiChatStore.FileAction> files = extractFileActions(answer);
                    setAgentStatus(NermuxAiConfig.getProviderTitle(NermuxAiConfig.getProvider(TermuxActivity.this)) + " answered");
                    appendAssistantMessage(answer, command, files);
                });
            }

            @Override
            public void onError(@NonNull String message) {
                runOnUiThread(() -> {
                    setAgentBusy(false);
                    setAgentStatus("AI request failed");
                    appendAssistantMessage(message, null);
                });
            }
        });
    }

    @NonNull
    private String getTerminalTranscriptForAgent() {
        TerminalSession session = getCurrentSession();
        String cwd = session == null ? null : session.getCwd();
        String transcript = session == null ? "" : ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        String safeTranscript = transcript == null ? "" : DataUtils.getTruncatedCommandOutput(transcript, 24000, false, true, false).trim();

        StringBuilder context = new StringBuilder();
        File workspace = getAgentWorkspaceDir();
        if (workspace == null) {
            context.append("Agent workspace root: NOT SELECTED. File changes are not allowed until the user chooses a workspace.\n\n");
        } else {
            context.append("Agent workspace root: ").append(workspace.getAbsolutePath()).append('\n');
            context.append("All FILE actions must be relative to this workspace root. Never write outside this folder.\n\n");
            context.append(buildAgentWorkspaceContext(workspace)).append("\n\n");
        }

        context.append("Current terminal working directory: ")
            .append(TextUtils.isEmpty(cwd) ? "unknown" : cwd)
            .append("\n\nRecent terminal transcript:\n")
            .append(safeTranscript);
        return context.toString();
    }

    private boolean requiresAgentWorkspace(@NonNull String task, @Nullable String prompt) {
        String text = (task + " " + (prompt == null ? "" : prompt)).toLowerCase(Locale.US);
        return text.contains("create")
            || text.contains("write")
            || text.contains("make")
            || text.contains("build")
            || text.contains("generate")
            || text.contains("scaffold")
            || text.contains("modify")
            || text.contains("edit")
            || text.contains("implement")
            || text.contains("code")
            || text.contains("webpage")
            || text.contains("website")
            || text.contains("html")
            || text.contains("node")
            || text.contains("project")
            || text.contains("file");
    }

    @NonNull
    private String buildAgentWorkspaceContext(@NonNull File workspace) {
        StringBuilder context = new StringBuilder();
        List<File> entries = new ArrayList<>();
        collectAgentWorkspaceEntries(workspace, workspace, entries, 0);

        context.append("Workspace tree:\n");
        if (entries.isEmpty()) {
            context.append("(empty folder)\n");
        } else {
            for (File entry : entries) {
                context.append(entry.isDirectory() ? "DIR  " : "FILE ")
                    .append(relativeAgentPath(workspace, entry))
                    .append('\n');
            }
        }

        List<File> contentCandidates = buildAgentContentCandidates(workspace, entries);
        int includedFiles = 0;
        int includedChars = 0;
        StringBuilder fileContext = new StringBuilder();
        for (File entry : contentCandidates) {
            if (includedFiles >= MAX_AGENT_WORKSPACE_CONTEXT_FILES || includedChars >= MAX_AGENT_WORKSPACE_CONTEXT_CHARS)
                break;
            if (!entry.isFile() || !shouldIncludeAgentFileContent(entry)) continue;

            byte[] bytes = readAgentFileBytes(entry, MAX_AGENT_WORKSPACE_FILE_BYTES);
            if (bytes == null || looksBinary(bytes)) continue;

            String content = new String(bytes, StandardCharsets.UTF_8);
            int remaining = MAX_AGENT_WORKSPACE_CONTEXT_CHARS - includedChars;
            if (content.length() > remaining) content = content.substring(0, Math.max(0, remaining));
            if (TextUtils.isEmpty(content.trim())) continue;

            fileContext.append("\n--- ")
                .append(relativeAgentPath(workspace, entry))
                .append(" ---\n")
                .append(content)
                .append('\n');
            includedChars += content.length();
            includedFiles++;
        }

        if (fileContext.length() > 0)
            context.append("\nSelected file contents:")
                .append(fileContext);

        return context.toString().trim();
    }

    @NonNull
    private List<File> buildAgentContentCandidates(@NonNull File workspace, @NonNull List<File> entries) {
        List<File> candidates = new ArrayList<>();
        for (String path : AGENT_PRIORITY_CONTEXT_FILES)
            addAgentContentCandidate(workspace, candidates, new File(workspace, path));

        for (File entry : entries)
            addAgentContentCandidate(workspace, candidates, entry);

        return candidates;
    }

    private void addAgentContentCandidate(@NonNull File workspace, @NonNull List<File> candidates, @NonNull File file) {
        File candidate = canonicalOrSelf(file);
        if (candidate == null || !candidate.isFile()) return;
        if (!isInsideDirectory(workspace, candidate)) return;
        for (File existing : candidates) {
            if (existing.equals(candidate)) return;
        }
        candidates.add(candidate);
    }

    private void collectAgentWorkspaceEntries(@NonNull File root, @NonNull File directory, @NonNull List<File> entries, int depth) {
        if (depth > 4 || entries.size() >= MAX_AGENT_WORKSPACE_TREE_ENTRIES) return;

        File[] children = directory.listFiles();
        if (children == null) return;

        Arrays.sort(children, (left, right) -> {
            if (left.isDirectory() != right.isDirectory())
                return left.isDirectory() ? -1 : 1;
            return left.getName().compareToIgnoreCase(right.getName());
        });

        for (File child : children) {
            if (entries.size() >= MAX_AGENT_WORKSPACE_TREE_ENTRIES) return;
            if (shouldSkipAgentWorkspaceEntry(child)) continue;

            File canonicalChild = canonicalOrSelf(child);
            if (canonicalChild == null || !isInsideDirectory(root, canonicalChild)) continue;
            entries.add(canonicalChild);
            if (canonicalChild.isDirectory())
                collectAgentWorkspaceEntries(root, canonicalChild, entries, depth + 1);
        }
    }

    private boolean shouldSkipAgentWorkspaceEntry(@NonNull File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.equals(".git")
            || name.equals(".gradle")
            || name.equals(".idea")
            || name.equals(AGENT_BACKUP_DIR_NAME)
            || name.equals("node_modules")
            || name.equals("build")
            || name.equals("dist")
            || name.equals("out")
            || name.equals(".next")
            || name.equals(".expo")
            || name.equals("__pycache__");
    }

    private boolean shouldIncludeAgentFileContent(@NonNull File file) {
        if (file.length() <= 0 || file.length() > MAX_AGENT_WORKSPACE_FILE_BYTES) return false;

        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".html")
            || name.endsWith(".css")
            || name.endsWith(".js")
            || name.endsWith(".mjs")
            || name.endsWith(".ts")
            || name.endsWith(".tsx")
            || name.endsWith(".jsx")
            || name.endsWith(".json")
            || name.endsWith(".py")
            || name.endsWith(".sh")
            || name.endsWith(".md")
            || name.endsWith(".txt")
            || name.endsWith(".xml")
            || name.endsWith(".java")
            || name.endsWith(".kt")
            || name.endsWith(".gradle")
            || name.endsWith(".yml")
            || name.endsWith(".yaml")
            || name.endsWith(".toml")
            || name.equals("package.json")
            || name.equals("readme")
            || name.equals(".env.example");
    }

    @Nullable
    private byte[] readAgentFileBytes(@NonNull File file, int maxBytes) {
        if (file.length() > maxBytes) return null;

        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (outputStream.size() + read > maxBytes) return null;
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean looksBinary(@NonNull byte[] bytes) {
        int sampleSize = Math.min(bytes.length, 4096);
        for (int i = 0; i < sampleSize; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    @NonNull
    private String relativeAgentPath(@NonNull File root, @NonNull File file) {
        String relative = root.toURI().relativize(file.toURI()).getPath();
        if (TextUtils.isEmpty(relative)) return ".";
        return file.isDirectory() && !relative.endsWith("/") ? relative + "/" : relative;
    }

    @Nullable
    private File getAgentWorkspaceDir() {
        String path = NermuxAiChatStore.loadWorkspacePath(this);
        if (TextUtils.isEmpty(path)) return null;

        File workspace = canonicalOrSelf(new File(path));
        if (workspace == null || !workspace.isDirectory()) {
            NermuxAiChatStore.clearWorkspacePath(this);
            return null;
        }
        return workspace;
    }

    private void setAgentWorkspace(@NonNull File workspace) {
        File canonicalWorkspace = canonicalOrSelf(workspace);
        if (canonicalWorkspace == null || !canonicalWorkspace.isDirectory()) {
            showToast(getString(R.string.error_ai_workspace_invalid), true);
            return;
        }

        NermuxAiChatStore.saveWorkspacePath(this, canonicalWorkspace.getAbsolutePath());
        refreshAgentWorkspaceStatus();
        refreshAgentConversationForWorkspace(false);
        setAgentStatus(getString(R.string.msg_ai_workspace_selected) + ": " + getDisplayAgentPath(canonicalWorkspace));
        showToast(getString(R.string.msg_ai_workspace_selected), false);
    }

    private void refreshAgentConversationForWorkspace(boolean scrollToBottom) {
        EditText promptInput = findViewById(R.id.agent_prompt_input);
        if (promptInput != null)
            promptInput.setText(NermuxAiChatStore.loadDraft(this));
        renderAgentChat(scrollToBottom);
    }

    private void refreshAgentWorkspaceStatus() {
        TextView workspacePath = findViewById(R.id.agent_workspace_path);
        TextView workspaceMeta = findViewById(R.id.agent_workspace_meta);
        if (workspacePath == null && workspaceMeta == null) return;

        File workspace = getAgentWorkspaceDir();
        if (workspace == null) {
            if (workspacePath != null) workspacePath.setText(getString(R.string.nermux_ai_workspace_missing));
            if (workspaceMeta != null) workspaceMeta.setText(getString(R.string.error_ai_workspace_missing));
            return;
        }

        if (workspacePath != null) workspacePath.setText(getDisplayAgentPath(workspace));
        if (workspaceMeta != null) workspaceMeta.setText(workspaceSummary(workspace));
    }

    @NonNull
    private String workspaceSummary(@NonNull File workspace) {
        File[] children = workspace.listFiles();
        int count = children == null ? 0 : children.length;
        String itemText = count == 1 ? "1 item" : count + " items";
        return itemText + " - " + getString(R.string.nermux_ai_workspace_ready);
    }

    @NonNull
    private String getDisplayAgentPath(@NonNull File file) {
        String path = canonicalOrSelf(file).getAbsolutePath();
        String homePath = TermuxConstants.TERMUX_HOME_DIR.getAbsolutePath();
        String prefixPath = TermuxConstants.TERMUX_PREFIX_DIR.getAbsolutePath();
        String filesPath = TermuxConstants.TERMUX_FILES_DIR.getAbsolutePath();
        String externalPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        if (path.equals(homePath)) return "~";
        if (path.startsWith(homePath + File.separator))
            return "~/" + path.substring(homePath.length() + 1);
        if (path.equals(prefixPath)) return "$PREFIX";
        if (path.startsWith(prefixPath + File.separator))
            return "$PREFIX/" + path.substring(prefixPath.length() + 1);
        if (path.equals(externalPath)) return "/sdcard";
        if (path.startsWith(externalPath + File.separator))
            return "/sdcard/" + path.substring(externalPath.length() + 1);
        if (path.startsWith(filesPath + File.separator))
            return "files/" + path.substring(filesPath.length() + 1);
        return path;
    }

    private void persistUriPermission(@NonNull Uri treeUri, int grantedFlags, int permissionFlag) {
        if ((grantedFlags & permissionFlag) == 0) return;
        try {
            getContentResolver().takePersistableUriPermission(treeUri, permissionFlag);
        } catch (Exception ignored) {
            // Some providers do not allow persisted grants; direct shared-storage paths still work below.
        }
    }

    @Nullable
    private File resolveStorageTreeUriToFile(@NonNull Uri treeUri) {
        if (!"com.android.externalstorage.documents".equals(treeUri.getAuthority()))
            return null;

        String documentId;
        try {
            documentId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            return null;
        }

        if (TextUtils.isEmpty(documentId))
            return null;

        String[] parts = documentId.split(":", 2);
        String volume = parts[0];
        String relativePath = parts.length > 1 ? parts[1] : "";

        File baseDir;
        if ("primary".equalsIgnoreCase(volume)) {
            baseDir = Environment.getExternalStorageDirectory();
        } else if ("home".equalsIgnoreCase(volume)) {
            baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        } else {
            baseDir = new File("/storage", volume);
        }

        return TextUtils.isEmpty(relativePath) ? baseDir : new File(baseDir, relativePath);
    }

    @Nullable
    private File canonicalOrSelf(@Nullable File file) {
        if (file == null) return null;
        try {
            return file.getCanonicalFile();
        } catch (Exception e) {
            return file.getAbsoluteFile();
        }
    }

    private boolean isInsideDirectory(@NonNull File directory, @NonNull File file) {
        File canonicalDirectory = canonicalOrSelf(directory);
        File canonicalFile = canonicalOrSelf(file);
        if (canonicalDirectory == null || canonicalFile == null) return false;

        String directoryPath = canonicalDirectory.getAbsolutePath();
        String filePath = canonicalFile.getAbsolutePath();
        return filePath.equals(directoryPath) || filePath.startsWith(directoryPath + File.separator);
    }

    @Nullable
    private String extractRunnableCommand(@NonNull String answer) {
        String[] lines = answer.split("\\r?\\n");
        boolean insideFence = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                insideFence = !insideFence;
                continue;
            }
            if (insideFence) continue;
            if (!trimmed.regionMatches(true, 0, "RUN:", 0, 4)) continue;
            String command = trimmed.substring(4).trim();
            command = trimCommandDecorators(command);
            return TextUtils.isEmpty(command) ? null : command;
        }
        return null;
    }

    @NonNull
    private List<NermuxAiChatStore.FileAction> extractFileActions(@NonNull String answer) {
        List<NermuxAiChatStore.FileAction> files = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        String[] lines = answer.split("\\r?\\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.regionMatches(true, 0, "FILE:", 0, 5)) continue;

            String path = sanitizeAgentFilePath(trimmed.substring(5).trim());
            if (TextUtils.isEmpty(path)) continue;

            int fenceIndex = i + 1;
            while (fenceIndex < lines.length && TextUtils.isEmpty(lines[fenceIndex].trim())) fenceIndex++;
            AgentFenceBlock block = readAgentFenceBlock(lines, fenceIndex);
            if (block == null) continue;

            addAgentFileAction(files, seenPaths, path, block.content);
            i = block.endIndex;
        }

        for (int i = 0; i < lines.length; i++) {
            AgentFenceBlock block = readAgentFenceBlock(lines, i);
            if (block == null) continue;

            String path = inferAgentFilePath(lines, i, block.language, block.content);
            addAgentFileAction(files, seenPaths, path, block.content);
            i = block.endIndex;
        }
        return files;
    }

    @Nullable
    private AgentFenceBlock readAgentFenceBlock(@NonNull String[] lines, int fenceIndex) {
        if (fenceIndex < 0 || fenceIndex >= lines.length) return null;

        String fenceLine = lines[fenceIndex].trim();
        if (!fenceLine.startsWith("```")) return null;

        String language = fenceLine.length() > 3 ? fenceLine.substring(3).trim() : "";
        int languageSpace = language.indexOf(' ');
        if (languageSpace >= 0) language = language.substring(0, languageSpace).trim();

        StringBuilder content = new StringBuilder();
        int contentIndex = fenceIndex + 1;
        while (contentIndex < lines.length && !lines[contentIndex].trim().startsWith("```")) {
            if (contentIndex > fenceIndex + 1) content.append('\n');
            content.append(lines[contentIndex]);
            contentIndex++;
        }

        if (contentIndex >= lines.length) return null;
        return new AgentFenceBlock(language, stripTrailingFenceNewline(content.toString()), contentIndex);
    }

    private void addAgentFileAction(
        @NonNull List<NermuxAiChatStore.FileAction> files,
        @NonNull Set<String> seenPaths,
        @Nullable String path,
        @NonNull String content
    ) {
        String safePath = sanitizeAgentFilePath(path);
        if (TextUtils.isEmpty(safePath) || TextUtils.isEmpty(content.trim())) return;
        if (!seenPaths.add(safePath)) return;
        files.add(new NermuxAiChatStore.FileAction(safePath, stripTrailingFenceNewline(content)));
    }

    @Nullable
    private String inferAgentFilePath(
        @NonNull String[] lines,
        int fenceIndex,
        @Nullable String language,
        @NonNull String content
    ) {
        for (int lookBack = fenceIndex - 1; lookBack >= 0 && lookBack >= fenceIndex - 4; lookBack--) {
            String hintPath = extractAgentPathHint(lines[lookBack]);
            if (!TextUtils.isEmpty(hintPath)) return hintPath;
        }

        String lowerLanguage = language == null ? "" : language.toLowerCase(Locale.US);
        String lowerContent = content.toLowerCase(Locale.US);
        if (lowerLanguage.equals("html") || lowerContent.contains("<!doctype html") || lowerContent.contains("<html"))
            return "index.html";
        if (lowerLanguage.equals("css"))
            return "style.css";
        if (lowerLanguage.equals("javascript") || lowerLanguage.equals("js"))
            return "script.js";
        if (lowerLanguage.equals("typescript") || lowerLanguage.equals("ts"))
            return "main.ts";
        if (lowerLanguage.equals("python") || lowerLanguage.equals("py") || lowerContent.startsWith("#!/usr/bin/env python"))
            return "main.py";
        if (lowerLanguage.equals("java"))
            return "Main.java";
        if (lowerLanguage.equals("kotlin") || lowerLanguage.equals("kt"))
            return "Main.kt";
        if (lowerLanguage.equals("json"))
            return "data.json";
        if (lowerLanguage.equals("markdown") || lowerLanguage.equals("md"))
            return "README.md";
        return null;
    }

    @Nullable
    private String extractAgentPathHint(@Nullable String value) {
        if (TextUtils.isEmpty(value)) return null;

        String cleaned = value.trim();
        while (cleaned.startsWith("#") || cleaned.startsWith("*") || cleaned.startsWith("-"))
            cleaned = cleaned.substring(1).trim();

        Matcher matcher = AGENT_FILE_HINT_PATTERN.matcher(cleaned);
        if (!matcher.find()) return null;
        return sanitizeAgentFilePath(matcher.group(1));
    }

    @Nullable
    private String sanitizeAgentFilePath(@Nullable String path) {
        if (TextUtils.isEmpty(path)) return null;

        String cleaned = trimCommandDecorators(path.trim())
            .replace('\\', '/')
            .replace("\"", "")
            .replace("'", "")
            .trim();

        while (cleaned.startsWith("./")) cleaned = cleaned.substring(2);
        while (cleaned.endsWith(":")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        if (TextUtils.isEmpty(cleaned)
            || cleaned.startsWith("/")
            || cleaned.equals("..")
            || cleaned.startsWith("../")
            || cleaned.contains("/../")
            || cleaned.endsWith("/.."))
            return null;
        return cleaned;
    }

    private static final class AgentFenceBlock {
        @NonNull
        final String language;
        @NonNull
        final String content;
        final int endIndex;

        AgentFenceBlock(@Nullable String language, @NonNull String content, int endIndex) {
            this.language = language == null ? "" : language;
            this.content = content;
            this.endIndex = endIndex;
        }
    }

    @NonNull
    private String stripTrailingFenceNewline(@NonNull String value) {
        String cleaned = value;
        while (cleaned.endsWith("\n\n")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }

    @NonNull
    private String trimCommandDecorators(@NonNull String command) {
        String cleaned = command.trim();
        while (cleaned.startsWith("`")) cleaned = cleaned.substring(1).trim();
        while (cleaned.endsWith("`")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        return cleaned;
    }

    private void confirmRunAiCommand(@NonNull String command) {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        if (TextUtils.isEmpty(command)) {
            showToast(getString(R.string.nermux_ai_no_command), false);
            return;
        }
        if (isDangerousAgentCommand(command)) {
            showToast(getString(R.string.error_ai_command_blocked), true);
            setAgentStatus(getString(R.string.error_ai_command_blocked));
            return;
        }

        String commandToRun = buildAgentRunCommand(command);
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_ai_run_command)
            .setMessage(commandToRun)
            .setPositiveButton(R.string.action_ai_run_suggestion, (dialog, which) -> {
                TerminalSession session = getCurrentSession();
                if (session != null) {
                    session.write(commandToRun + "\n");
                    showToast(getString(R.string.msg_ai_command_sent), false);
                    getDrawer().closeDrawer(Gravity.RIGHT);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private boolean isDangerousAgentCommand(@NonNull String command) {
        String normalized = command.toLowerCase(Locale.US)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replaceAll("\\s+", " ")
            .trim();

        return normalized.contains(":(){")
            || normalized.contains("mkfs")
            || normalized.contains("dd if=")
            || normalized.contains("/dev/block")
            || normalized.contains(" rm -rf /")
            || normalized.startsWith("rm -rf /")
            || normalized.matches(".*\\brm\\s+-[a-z]*r[a-z]*f[a-z]*\\s+(~|/|\\$home|\\$prefix)(\\s|$).*")
            || normalized.matches(".*\\bchmod\\s+-r\\s+777\\s+(/|~|\\$home|\\$prefix)(\\s|$).*")
            || normalized.matches(".*\\bchown\\s+-r\\s+[^ ]+\\s+(/|~|\\$home|\\$prefix)(\\s|$).*");
    }

    @NonNull
    private String buildAgentRunCommand(@NonNull String command) {
        File workspace = getAgentWorkspaceDir();
        if (workspace == null) return command;
        return "cd " + shellQuote(workspace.getAbsolutePath()) + " && " + command;
    }

    @NonNull
    private String shellQuote(@NonNull String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void copyAgentMessage(@NonNull String text) {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        if (TextUtils.isEmpty(text)) return;

        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.title_ai_agent), text));
            showToast(getString(R.string.msg_ai_answer_copied), false);
        }
    }

    private void confirmApplyAiFile(@NonNull NermuxAiChatStore.FileAction file) {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_ai_apply_file)
            .setMessage(buildApplyAiFilesMessage(java.util.Collections.singletonList(file)))
            .setPositiveButton(R.string.action_ai_apply_file, (dialog, which) -> applyAiFile(file))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void confirmApplyAiFiles(@NonNull List<NermuxAiChatStore.FileAction> files) {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        if (files.isEmpty()) return;

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_ai_apply_file)
            .setMessage(buildApplyAiFilesMessage(files))
            .setPositiveButton(R.string.action_ai_apply_all_files, (dialog, which) -> applyAiFiles(files))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    private String buildApplyAiFilesMessage(@NonNull List<NermuxAiChatStore.FileAction> files) {
        StringBuilder message = new StringBuilder(getString(R.string.msg_ai_apply_file));
        File workspace = getAgentWorkspaceDir();
        if (workspace != null) {
            message.append("\n\nWorkspace:\n")
                .append(getDisplayAgentPath(workspace));
        }

        message.append("\n\nFiles:\n");
        for (NermuxAiChatStore.FileAction file : files) {
            message.append(getAgentFileApplyVerb(file))
                .append("  ")
                .append(file.path)
                .append('\n');
        }
        return message.toString().trim();
    }

    @NonNull
    private String getAgentFileApplyVerb(@NonNull NermuxAiChatStore.FileAction file) {
        try {
            return resolveAgentFileTarget(file.path).exists() ? "Update" : "Create";
        } catch (Exception ignored) {
            return "Write";
        }
    }

    private void applyAiFile(@NonNull NermuxAiChatStore.FileAction file) {
        try {
            writeAiFile(file);
            showToast(getString(R.string.msg_ai_file_applied) + ": " + file.path, false);
            setAgentStatus(getString(R.string.msg_ai_file_applied) + ": " + file.path);
        } catch (Exception e) {
            String message = e.getMessage() == null ? file.path : e.getMessage();
            showToast(getString(R.string.error_ai_file_apply_failed, message), true);
            setAgentStatus(getString(R.string.error_ai_file_apply_failed, message));
        }
    }

    private void applyAiFiles(@NonNull List<NermuxAiChatStore.FileAction> files) {
        try {
            for (NermuxAiChatStore.FileAction file : files)
                writeAiFile(file);

            String message = getString(R.string.msg_ai_files_applied, files.size());
            showToast(message, false);
            setAgentStatus(message);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "unknown error" : e.getMessage();
            showToast(getString(R.string.error_ai_file_apply_failed, message), true);
            setAgentStatus(getString(R.string.error_ai_file_apply_failed, message));
        }
    }

    private void writeAiFile(@NonNull NermuxAiChatStore.FileAction file) throws Exception {
        File target = resolveAgentFileTarget(file.path);
        backupExistingAgentFile(target, file.path);

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IllegalStateException("Cannot create " + parent.getAbsolutePath());

        try (FileOutputStream outputStream = new FileOutputStream(target, false)) {
            outputStream.write(file.content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void backupExistingAgentFile(@NonNull File target, @NonNull String relativePath) throws Exception {
        if (!target.exists() || !target.isFile()) return;

        File workspace = getAgentWorkspaceDir();
        if (workspace == null) return;
        File workspaceRoot = workspace.getCanonicalFile();
        if (relativeAgentPath(workspaceRoot, target).startsWith(AGENT_BACKUP_DIR_NAME + "/")) return;

        File backupRoot = new File(new File(workspaceRoot, AGENT_BACKUP_DIR_NAME), String.valueOf(System.currentTimeMillis())).getCanonicalFile();
        File backupTarget = new File(backupRoot, relativePath).getCanonicalFile();
        if (!isInsideDirectory(backupRoot, backupTarget))
            throw new IllegalStateException("Cannot create backup for " + relativePath);

        File parent = backupTarget.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IllegalStateException("Cannot create " + parent.getAbsolutePath());

        copyAgentFile(target, backupTarget);
    }

    private void copyAgentFile(@NonNull File source, @NonNull File target) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(source);
             FileOutputStream outputStream = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1)
                outputStream.write(buffer, 0, read);
        }
    }

    @NonNull
    private File resolveAgentFileTarget(@NonNull String path) throws Exception {
        File workspace = getAgentWorkspaceDir();
        if (workspace == null)
            throw new IllegalStateException(getString(R.string.error_ai_workspace_missing));

        File baseDir = workspace.getCanonicalFile();
        File target = new File(baseDir, path).getCanonicalFile();
        String basePath = baseDir.getPath();
        String targetPath = target.getPath();
        if (!targetPath.equals(basePath) && !targetPath.startsWith(basePath + File.separator))
            throw new IllegalArgumentException("Refusing path outside agent workspace: " + path);
        if (target.isDirectory())
            throw new IllegalArgumentException("Target is a folder: " + path);
        return target;
    }

    private void confirmClearAgentChat() {
        performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_ai_clear_chat)
            .setMessage(R.string.msg_ai_clear_chat)
            .setPositiveButton(R.string.action_ai_clear_chat, (dialog, which) -> {
                NermuxAiChatStore.clearMessages(this);
                NermuxAiChatStore.clearDraft(this);
                EditText promptInput = findViewById(R.id.agent_prompt_input);
                if (promptInput != null) promptInput.setText("");
                renderAgentChat(false);
                setAgentStatus(getString(R.string.msg_ai_chat_cleared));
                showToast(getString(R.string.msg_ai_chat_cleared), false);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void appendAssistantMessage(@NonNull String text, @Nullable String command) {
        appendAssistantMessage(text, command, new ArrayList<>());
    }

    private void appendAssistantMessage(@NonNull String text, @Nullable String command, @NonNull List<NermuxAiChatStore.FileAction> files) {
        NermuxAiChatStore.appendMessage(this, NermuxAiChatStore.Message.assistant(text, command, files));
        renderAgentChat(true);
    }

    private void renderAgentChat(boolean scrollToBottom) {
        LinearLayout chatList = findViewById(R.id.agent_chat_list);
        if (chatList == null) return;

        chatList.removeAllViews();
        List<NermuxAiChatStore.Message> messages = NermuxAiChatStore.loadMessages(this);
        if (messages.isEmpty()) {
            addEmptyAgentMessage(chatList);
        } else {
            for (NermuxAiChatStore.Message message : messages) {
                addAgentMessageView(chatList, message);
            }
        }

        if (scrollToBottom) scrollAgentToBottom();
    }

    private void addEmptyAgentMessage(@NonNull LinearLayout chatList) {
        TextView emptyView = buildAgentTextView(getString(R.string.nermux_ai_empty_chat), 12, R.color.nermux_text_secondary, false);
        emptyView.setBackgroundResource(R.drawable.nermux_agent_card_background);
        emptyView.setPadding(dp(12), dp(12), dp(12), dp(12));
        chatList.addView(emptyView, agentBlockParams(0));
    }

    private void addAgentMessageView(@NonNull LinearLayout chatList, @NonNull NermuxAiChatStore.Message message) {
        boolean isUser = NermuxAiChatStore.ROLE_USER.equals(message.role);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(isUser ? R.drawable.nermux_agent_user_message_background : R.drawable.nermux_agent_card_background);
        card.setPadding(dp(12), dp(11), dp(12), dp(12));

        TextView labelView = buildAgentTextView(
            isUser ? getString(R.string.nermux_ai_user_label) : getString(R.string.nermux_ai_assistant_label),
            10,
            isUser ? R.color.nermux_accent_bright : R.color.nermux_accent_green,
            true
        );
        card.addView(labelView);

        String displayText = isUser ? message.text : cleanAssistantDisplayText(message);
        TextView bodyView = buildAgentTextView(displayText, 12, R.color.nermux_text_primary, false);
        bodyView.setTextIsSelectable(true);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = dp(7);
        card.addView(bodyView, bodyParams);

        if (message.files.size() > 1)
            addApplyAllFilesButton(card, message.files);

        for (NermuxAiChatStore.FileAction file : message.files)
            addFileActionView(card, file);

        if (!TextUtils.isEmpty(message.command))
            addCommandActionView(card, message.command);

        if (!isUser)
            addCopyMessageButton(card, message.text);

        chatList.addView(card, agentBlockParams(10));
    }

    @NonNull
    private String cleanAssistantDisplayText(@NonNull NermuxAiChatStore.Message message) {
        String[] lines = message.text.split("\\r?\\n", -1);
        StringBuilder cleaned = new StringBuilder();
        boolean skippingFileBlock = false;
        boolean skippingFence = false;
        boolean skippingAnyFence = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!skippingFileBlock && trimmed.regionMatches(true, 0, "FILE:", 0, 5)) {
                skippingFileBlock = true;
                skippingFence = false;
                continue;
            }

            if (skippingFileBlock) {
                if (trimmed.startsWith("```")) {
                    if (!skippingFence) {
                        skippingFence = true;
                    } else {
                        skippingFileBlock = false;
                        skippingFence = false;
                    }
                }
                continue;
            }

            if (!message.files.isEmpty() && trimmed.startsWith("```")) {
                skippingAnyFence = !skippingAnyFence;
                continue;
            }
            if (skippingAnyFence) continue;

            if (trimmed.regionMatches(true, 0, "RUN:", 0, 4)) continue;
            if (cleaned.length() > 0 || !TextUtils.isEmpty(trimmed)) cleaned.append(line).append('\n');
        }

        String text = cleaned.toString().trim();
        if (TextUtils.isEmpty(text) && (!message.files.isEmpty() || !TextUtils.isEmpty(message.command)))
            return getString(R.string.nermux_ai_actions_ready);
        return TextUtils.isEmpty(text) ? message.text : text;
    }

    private void addApplyAllFilesButton(@NonNull LinearLayout card, @NonNull List<NermuxAiChatStore.FileAction> files) {
        TextView applyAllButton = buildAgentTextView(getString(R.string.action_ai_apply_all_files), 12, android.R.color.black, true);
        applyAllButton.setGravity(Gravity.CENTER);
        applyAllButton.setClickable(true);
        applyAllButton.setFocusable(true);
        applyAllButton.setBackgroundResource(R.drawable.nermux_agent_run_button_background);
        applyAllButton.setOnClickListener(v -> confirmApplyAiFiles(files));
        attachPressMotion(applyAllButton);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(38)
        );
        params.topMargin = dp(10);
        card.addView(applyAllButton, params);
    }

    private void addFileActionView(@NonNull LinearLayout card, @NonNull NermuxAiChatStore.FileAction file) {
        LinearLayout fileCard = new LinearLayout(this);
        fileCard.setOrientation(LinearLayout.VERTICAL);
        fileCard.setBackgroundResource(R.drawable.nermux_agent_file_background);
        fileCard.setPadding(dp(10), dp(9), dp(10), dp(10));

        TextView label = buildAgentTextView(getString(R.string.nermux_ai_generated_file), 10, R.color.nermux_accent_green, true);
        fileCard.addView(label);

        TextView pathText = buildAgentTextView(file.path, 13, R.color.nermux_text_primary, true);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pathParams.topMargin = dp(7);
        fileCard.addView(pathText, pathParams);

        TextView previewText = buildAgentTextView(filePreview(file.content), 11, R.color.nermux_text_secondary, false);
        previewText.setTextIsSelectable(true);
        previewText.setTypeface(android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        previewParams.topMargin = dp(7);
        fileCard.addView(previewText, previewParams);

        TextView applyButton = buildAgentTextView(getString(R.string.action_ai_apply_file), 12, android.R.color.black, true);
        applyButton.setGravity(Gravity.CENTER);
        applyButton.setClickable(true);
        applyButton.setFocusable(true);
        applyButton.setBackgroundResource(R.drawable.nermux_agent_run_button_background);
        applyButton.setOnClickListener(v -> confirmApplyAiFile(file));
        attachPressMotion(applyButton);
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(38)
        );
        applyParams.topMargin = dp(9);
        fileCard.addView(applyButton, applyParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(10);
        card.addView(fileCard, cardParams);
    }

    @NonNull
    private String filePreview(@NonNull String content) {
        String preview = content.trim();
        if (preview.length() > 420) preview = preview.substring(0, 420).trim() + "\n...";
        return preview;
    }

    private void addCommandActionView(@NonNull LinearLayout card, @NonNull String command) {
        LinearLayout commandCard = new LinearLayout(this);
        commandCard.setOrientation(LinearLayout.VERTICAL);
        commandCard.setBackgroundResource(R.drawable.nermux_agent_command_background);
        commandCard.setPadding(dp(10), dp(9), dp(10), dp(10));

        TextView label = buildAgentTextView(getString(R.string.nermux_ai_suggested_command), 10, R.color.nermux_accent_yellow, true);
        commandCard.addView(label);

        TextView commandText = buildAgentTextView(command, 12, R.color.nermux_text_primary, false);
        commandText.setTextIsSelectable(true);
        commandText.setTypeface(android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        commandParams.topMargin = dp(7);
        commandCard.addView(commandText, commandParams);

        TextView runButton = buildAgentTextView(getString(R.string.action_ai_run_suggestion), 12, android.R.color.black, true);
        runButton.setGravity(Gravity.CENTER);
        runButton.setClickable(true);
        runButton.setFocusable(true);
        runButton.setBackgroundResource(R.drawable.nermux_agent_run_button_background);
        runButton.setOnClickListener(v -> confirmRunAiCommand(command));
        attachPressMotion(runButton);
        LinearLayout.LayoutParams runParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(38)
        );
        runParams.topMargin = dp(9);
        commandCard.addView(runButton, runParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(10);
        card.addView(commandCard, cardParams);
    }

    private void addCopyMessageButton(@NonNull LinearLayout card, @NonNull String text) {
        TextView copyButton = buildAgentTextView(getString(R.string.action_ai_copy_message), 11, R.color.nermux_text_primary, true);
        copyButton.setGravity(Gravity.CENTER);
        copyButton.setClickable(true);
        copyButton.setFocusable(true);
        copyButton.setBackgroundResource(R.drawable.nermux_agent_secondary_button_background);
        copyButton.setOnClickListener(v -> copyAgentMessage(text));
        attachPressMotion(copyButton);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(124), dp(38));
        params.topMargin = dp(10);
        card.addView(copyButton, params);
    }

    @NonNull
    private TextView buildAgentTextView(@NonNull String text, int sp, int colorRes, boolean bold) {
        TextView textView = new TextView(this);
        textView.setIncludeFontPadding(false);
        textView.setText(text);
        textView.setTextColor(ContextCompat.getColor(this, colorRes));
        textView.setTextSize(sp);
        textView.setTypeface(android.graphics.Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
            bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        textView.setLineSpacing(dp(2), 1f);
        return textView;
    }

    @NonNull
    private LinearLayout.LayoutParams agentBlockParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private void refreshAgentProviderStatus() {
        TextView providerStatus = findViewById(R.id.agent_provider_status);
        TextView headerSubtitle = findViewById(R.id.agent_header_subtitle);
        if (providerStatus == null && headerSubtitle == null) return;

        String provider = NermuxAiConfig.getProvider(this);
        String title = NermuxAiConfig.getProviderTitle(provider);
        String model = NermuxAiConfig.getModel(this);
        boolean ready = NermuxAiConfig.hasApiKey(this);
        String status = title + " - " + model + (ready ? " - ready" : " - add key");

        if (providerStatus != null) providerStatus.setText(status);
        if (headerSubtitle != null)
            headerSubtitle.setText(ready ? "Provider ready" : getString(R.string.nermux_ai_waiting));
    }

    private void setAgentStatus(@NonNull String text) {
        TextView statusText = findViewById(R.id.agent_status_text);
        if (statusText != null) statusText.setText(text);
    }

    private void setAgentBusy(boolean busy) {
        View sendButton = findViewById(R.id.agent_send_button);
        View fixButton = findViewById(R.id.agent_fix_button);
        View explainButton = findViewById(R.id.agent_explain_button);
        setAgentActionEnabled(sendButton, !busy);
        setAgentActionEnabled(fixButton, !busy);
        setAgentActionEnabled(explainButton, !busy);
    }

    private void setAgentActionEnabled(@Nullable View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.52f);
    }

    private void scrollAgentToBottom() {
        ScrollView scrollView = findViewById(R.id.agent_scroll);
        if (scrollView != null)
            scrollView.post(() -> {
                View content = scrollView.getChildAt(0);
                if (content != null) scrollView.smoothScrollTo(0, content.getBottom());
            });
    }

    private int dp(int value) {
        return Math.round(dpToPx(value));
    }

    private void setWorkspaceButtonView() {
        ImageButton workspaceButton = findViewById(R.id.workspace_button);
        attachPressMotion(workspaceButton);
        workspaceButton.setOnClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
            TerminalSession currentSession = getCurrentSession();
            String startDirectory = currentSession == null ? null : currentSession.getCwd();
            ActivityUtils.startActivity(this, NermuxWorkspaceActivity.newInstance(this, startDirectory));
        });
    }

    private void setPowerCenterButtonView() {
        ImageButton powerCenterButton = findViewById(R.id.power_center_button);
        attachPressMotion(powerCenterButton);
        powerCenterButton.setOnClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
            ActivityUtils.startActivity(this, NermuxHomeActivity.newInstance(this));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        attachPressMotion(newSessionButton);
        newSessionButton.setOnClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
            mTermuxTerminalSessionActivityClient.addNewSession(false, null);
        });
        newSessionButton.setOnLongClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.LONG_PRESS);
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        View toggleKeyboardButton = findViewById(R.id.toggle_keyboard_button);
        attachPressMotion(toggleKeyboardButton);
        toggleKeyboardButton.setOnClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        toggleKeyboardButton.setOnLongClickListener(v -> {
            performUiHaptic(HapticFeedbackConstants.LONG_PRESS);
            toggleTerminalToolbar();
            return true;
        });
    }

    private void startTerminalEntryMotion() {
        if (mIsActivityRecreated || mTerminalView == null) return;

        mTerminalView.setAlpha(0f);
        mTerminalView.setTranslationY(dpToPx(8));
        mTerminalView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(UI_MOTION_MEDIUM_MS)
            .setInterpolator(UI_MOTION_INTERPOLATOR)
            .start();
    }

    private void setDrawerMotion() {
        DrawerLayout drawerLayout = getDrawer();
        View leftDrawer = findViewById(R.id.left_drawer);
        View agentDrawer = findViewById(R.id.agent_drawer);
        if (drawerLayout == null) return;

        drawerLayout.setScrimColor(ContextCompat.getColor(this, R.color.nermux_drawer_scrim));
        if (leftDrawer != null) {
            leftDrawer.setAlpha(0.92f);
            leftDrawer.setTranslationX(-dpToPx(18));
        }
        if (agentDrawer != null) {
            agentDrawer.setAlpha(0.92f);
            agentDrawer.setTranslationX(dpToPx(18));
        }

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                if (drawerView == leftDrawer) {
                    leftDrawer.setAlpha(0.92f + (slideOffset * 0.08f));
                    leftDrawer.setTranslationX(-dpToPx(18) * (1f - slideOffset));
                } else if (drawerView == agentDrawer) {
                    agentDrawer.setAlpha(0.92f + (slideOffset * 0.08f));
                    agentDrawer.setTranslationX(dpToPx(18) * (1f - slideOffset));
                }
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                if (drawerView == leftDrawer || drawerView == agentDrawer) {
                    drawerView.setAlpha(1f);
                    drawerView.setTranslationX(0f);
                }
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                if (drawerView == leftDrawer) {
                    leftDrawer.setAlpha(0.92f);
                    leftDrawer.setTranslationX(-dpToPx(18));
                } else if (drawerView == agentDrawer) {
                    agentDrawer.setAlpha(0.92f);
                    agentDrawer.setTranslationX(dpToPx(18));
                }
            }
        });
    }

    private void animateTerminalToolbarVisibility(View toolbar, boolean show) {
        if (toolbar == null) return;
        toolbar.animate().cancel();

        float travelDistance = Math.max(toolbar.getHeight(), Math.round(mTerminalToolbarDefaultHeight)) * 0.45f;
        if (travelDistance <= 0) travelDistance = dpToPx(18);

        if (show) {
            toolbar.setVisibility(View.VISIBLE);
            toolbar.setAlpha(0f);
            toolbar.setTranslationY(travelDistance);
            toolbar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(UI_MOTION_MEDIUM_MS)
                .setInterpolator(UI_MOTION_INTERPOLATOR)
                .start();
        } else {
            toolbar.animate()
                .alpha(0f)
                .translationY(travelDistance)
                .setDuration(UI_MOTION_SHORT_MS)
                .setInterpolator(UI_MOTION_INTERPOLATOR)
                .withEndAction(() -> {
                    toolbar.setVisibility(View.GONE);
                    toolbar.setAlpha(1f);
                    toolbar.setTranslationY(0f);
                })
                .start();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachPressMotion(View view) {
        if (view == null) return;

        view.setOnTouchListener((target, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    target.animate()
                        .scaleX(0.94f)
                        .scaleY(0.94f)
                        .alpha(0.86f)
                        .setDuration(UI_MOTION_SHORT_MS)
                        .setInterpolator(UI_MOTION_INTERPOLATOR)
                        .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(UI_MOTION_SHORT_MS)
                        .setInterpolator(UI_MOTION_INTERPOLATOR)
                        .start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }





    @SuppressLint({"RtlHardcoded", "MissingSuperCall"})
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.RIGHT)) {
            getDrawer().closeDrawer(Gravity.RIGHT);
        } else if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }

    public void performUiHaptic(int feedbackConstant) {
        View targetView = mTerminalView != null ? mTerminalView : mTermuxActivityRootView;
        if (targetView == null) targetView = getWindow().getDecorView();
        targetView.performHapticFeedback(feedbackConstant);
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
            continueAgentWorkspacePickerAfterStoragePermission();
            return;
        }

        if (requestCode == REQUEST_AI_WORKSPACE_FOLDER) {
            if (resultCode != RESULT_OK || data == null) return;

            Uri treeUri = data.getData();
            if (treeUri == null) return;

            persistUriPermission(treeUri, data.getFlags(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            persistUriPermission(treeUri, data.getFlags(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            File selectedFolder = resolveStorageTreeUriToFile(treeUri);
            if (selectedFolder == null || !selectedFolder.isDirectory()) {
                showToast(getString(R.string.error_selected_folder_not_supported), true);
                return;
            }

            setAgentWorkspace(selectedFolder);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
            continueAgentWorkspacePickerAfterStoragePermission();
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        mTermuxSessionListViewController.notifyDataSetChanged();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();
        setTerminalToolbarHeight();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
