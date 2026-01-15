package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.byobu.ByobuSessionManager;
import com.termux.app.byobu.ByobuCommandHelper;
import com.termux.app.customcmd.CommandFolder;
import com.termux.app.customcmd.CustomCommand;
import com.termux.app.customcmd.CustomCommandManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import com.termux.app.ssh.SshConfigManager;
import com.termux.app.ssh.SshConnectionConfig;
import com.termux.app.activities.SshConnectionsActivity;
import com.termux.app.dirnav.DirectoryNavigationManager;
import com.termux.app.claude.ConversationSyncManager;
import com.termux.app.claude.ClaudeHistoryHttpClient;
import com.termux.app.claude.AutoArSyncManager;

import android.os.Handler;
import android.os.Looper;
import com.termux.app.eveng1.EvenG1ConfigManager;
import com.termux.app.eveng1.EvenG1Manager;
import com.termux.app.eveng1.EvenG1DevicePair;
import com.termux.app.eveng1.ArTextPager;
import com.termux.app.eveng1.EvenG1Protocol;
import com.termux.app.claude.ClaudeChatParser;
import com.termux.app.media.MediaControlManager;
import com.termux.app.ai.AiSettingsManager;
import com.termux.app.turso.TursoSyncManager;
import com.termux.app.ai.LlmClient;
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
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * The connection to the {@link TermuxService}. Requested in
     * {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored
     * in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in {@link TermuxActivity} that displays the
     * terminal.
     */
    TerminalView mTerminalView;

    /**
     * The {@link TerminalViewClient} interface implementation to allow for
     * communication between
     * {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     * The {@link TerminalSessionClient} interface implementation to allow for
     * communication between
     * {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Activity result launcher for exporting custom commands.
     */
    private ActivityResultLauncher<String> mExportLauncher;

    /**
     * Activity result launcher for importing custom commands.
     */
    private ActivityResultLauncher<String[]> mImportLauncher;

    /**
     * Activity result launcher for exporting AI prompt templates.
     */
    private ActivityResultLauncher<String> mAiTemplateExportLauncher;

    /**
     * Activity result launcher for importing AI prompt templates.
     */
    private ActivityResultLauncher<String[]> mAiTemplateImportLauncher;

    /**
     * Activity result launcher for selecting Claude history JSONL file.
     */
    private ActivityResultLauncher<String[]> mClaudeHistoryLauncher;

    /**
     * Current ArTextPager for Claude history display on AR glasses.
     */
    private com.termux.app.eveng1.ArTextPager mCurrentArPager;

    /**
     * Cached CustomCommandManager instance for import/export operations.
     */
    private CustomCommandManager mCustomCommandManager;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the
     * {@link TermuxActivity}.
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
     * The {@link TermuxActivity} broadcast receiver for various things like
     * terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in
     * {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the
     * foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should
     * probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after
     * receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was
     * changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;
    private float mTerminalToolbarDefaultHeight;

    private long mLastToastTime = 0;
    private final long TOAST_COOLDOWN_MS = 2000;

    // Conversation History Navigation
    private java.util.List<String> mCurrentConversationHistory = null;
    private int mCurrentHistoryIndex = -1;

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_AI_ASSISTANT_ID = 20;
    private static final int CONTEXT_MENU_HELP_ID = 21;
    private static final int CONTEXT_MENU_SETTINGS_ID = 22;
    private static final int CONTEXT_MENU_REPORT_ID = 23;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

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

        // Initialize custom commands manager and activity result launchers
        initCustomCommandsLaunchers();

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal
        // applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue
            // running activity code
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

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setNewSessionButtonView();

        setToggleKeyboardView();

        // Set custom commands button
        setCustomCommandsButton();

        // Set byobu sessions button
        setByobuSessionsButton();

        // Set byobu commands help button
        setByobuCommandsButton();

        // Set SSH buttons
        setSshButtons();

        // Set directory navigation button
        setDirectoryNavigationButton();

        // Set Claude conversation sync button
        setClaudeSyncButton();

        // Set AI Assistant button
        setAiAssistantButton();

        // Set EVEN G1 AR Glasses buttons
        setEvenG1Buttons();

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to
            // it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link
            // #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                    getString(e.getMessage() != null && e.getMessage().contains("app is in background")
                            ? R.string.error_termux_service_start_failed_bg
                            : R.string.error_termux_service_start_failed_general),
                    true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify
        // apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState)
            return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();

        // Ensure default AI agent commands exist
        if (mCustomCommandManager == null) {
            mCustomCommandManager = new CustomCommandManager(this);
        }
        mCustomCommandManager.ensureDefaultCommands();

        mMediaControlManager = new MediaControlManager(this);

        mAiSettingsManager = new AiSettingsManager(this);
        mLlmClient = new LlmClient(mAiSettingsManager.getBaseUrlForProvider(mAiSettingsManager.getProvider()));
        mTursoSyncManager = new TursoSyncManager(this);
        mTursoSyncManager.initializeDb(null);
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState)
            return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // Check if a crash happened on last run of the app or if a plugin crashed and
        // show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState)
            return;

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

        if (mIsInvalidState)
            return;

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
     * {@link #bindService(Intent, ServiceConnection, int)} in
     * {@link #onCreate(Bundle)} which will cause a call to this
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
                    if (mTermuxService == null)
                        return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION,
                                    false);
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
            // If termux was started from launcher "New session" shortcut and activity is
            // recreated,
            // then the original intent will be re-delivered, resulting in a new session
            // being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient
                        .setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
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

        // Set activity night mode. If NightMode.SYSTEM is set, then android will
        // automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed
        // so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal,
                marginVertical);
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
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this,
                mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
                mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar())
            terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(
                new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null)
            return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
                (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0
                        : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length)
                *
                mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null)
            return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar)
                : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null)
            return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty())
                savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                    R.string.action_create_named_session_confirm,
                    text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                    R.string.action_new_session_failsafe,
                    text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                    -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }

    /**
     * Set up the byobu commands help button.
     * Shows a list of byobu commands that can be copied to clipboard.
     */
    private void setByobuCommandsButton() {
        View commandsButton = findViewById(R.id.byobuCommandsButton);
        if (commandsButton == null)
            return;

        commandsButton.setOnClickListener(v -> showByobuCommandsDialog());
    }

    /**
     * Shows a dialog with byobu commands organized by category.
     */
    private void showByobuCommandsDialog() {
        List<ByobuCommandHelper.ByobuCommand> commands = ByobuCommandHelper.getCommandList();
        List<String> categories = ByobuCommandHelper.getCategories();

        // Create a list of command strings for display
        List<String> commandStrings = new ArrayList<>();
        final List<ByobuCommandHelper.ByobuCommand> commandList = new ArrayList<>(); // Store actual commands
        String currentCategory = "";

        for (ByobuCommandHelper.ByobuCommand cmd : commands) {
            if (!cmd.getCategory().equals(currentCategory)) {
                currentCategory = cmd.getCategory();
                commandStrings.add("\n【" + currentCategory + "】");
                commandList.add(null); // Placeholder for category header
            }
            commandStrings.add(cmd.getDescription() + ":\n  " + cmd.getCommand());
            commandList.add(cmd);
        }

        String[] commandArray = commandStrings.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Byobu Commands")
                .setItems(commandArray, (dialog, which) -> {
                    // Skip category headers
                    if (which >= commandList.size() || commandList.get(which) == null) {
                        return;
                    }

                    ByobuCommandHelper.ByobuCommand selectedCmd = commandList.get(which);

                    // Handle different command types
                    if (selectedCmd.getType() == ByobuCommandHelper.CommandType.FUNCTION_KEY) {
                        // Send function key escape sequence
                        sendFunctionKey(selectedCmd.getEscapeSequence(), selectedCmd.getDescription());
                    } else if (selectedCmd.getType() == ByobuCommandHelper.CommandType.INFO_ONLY) {
                        // Just show info, don't execute
                        showToast(selectedCmd.getDescription(), false);
                    } else {
                        // Normal shell command
                        String commandTemplate = selectedCmd.getCommand();
                        if (ByobuCommandHelper.hasVariables(commandTemplate)) {
                            // Prompt for variables
                            promptForVariablesAndExecute(commandTemplate, selectedCmd.getDescription());
                        } else {
                            // Execute command directly
                            executeByobuCommand(commandTemplate);
                        }
                    }
                })
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Prompt user for variable values and execute the command.
     */
    private void promptForVariablesAndExecute(@NonNull String commandTemplate, @NonNull String description) {
        List<String> variables = ByobuCommandHelper.extractVariables(commandTemplate);
        if (variables.isEmpty()) {
            executeByobuCommand(commandTemplate);
            return;
        }

        // Collect variable values recursively
        final java.util.Map<String, String> variableValues = new java.util.HashMap<>();
        promptForVariableRecursive(commandTemplate, description, variables, 0, variableValues);
    }

    /**
     * Recursively prompt for each variable.
     */
    private void promptForVariableRecursive(@NonNull String commandTemplate, @NonNull String description,
            @NonNull List<String> variables, int index,
            @NonNull final Map<String, String> variableValues) {
        if (index >= variables.size()) {
            // All variables collected, execute command
            String finalCommand = ByobuCommandHelper.replaceVariables(commandTemplate, variableValues);
            executeByobuCommand(finalCommand);
            return;
        }

        String variableName = variables.get(index);
        String prompt = ByobuCommandHelper.getVariablePrompt(variableName);

        // Create a custom dialog for better title display
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setSingleLine();
        input.setHint(prompt);

        builder.setTitle(description)
                .setMessage(prompt)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        variableValues.put(variableName, value);
                        promptForVariableRecursive(commandTemplate, description, variables, index + 1, variableValues);
                    } else {
                        showToast("値を入力してください", false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Send a function key (escape sequence) to the terminal.
     */
    private void sendFunctionKey(@NonNull String escapeSequence, @NonNull String description) {
        TerminalSession session = getCurrentSession();
        if (session == null || !session.isRunning()) {
            showToast("アクティブなターミナルセッションがありません", true);
            return;
        }

        // Send escape sequence directly (no newline)
        byte[] data = escapeSequence.getBytes();
        session.write(data, 0, data.length);

        showToast("送信: " + description, false);
    }

    /**
     * Execute a byobu command by sending it to the terminal.
     */
    private void executeByobuCommand(@NonNull String command) {
        TerminalSession session = getCurrentSession();
        if (session == null || !session.isRunning()) {
            showToast("アクティブなターミナルセッションがありません", true);
            return;
        }

        // Send command to terminal (add newline at the end)
        byte[] data = (command + "\n").getBytes();
        session.write(data, 0, data.length);

        showToast("実行: " + command, false);
    }

    /**
     * Set up the byobu sessions button.
     * When clicked, it sends a marker-wrapped command to list byobu sessions,
     * parses the output, and shows a selection dialog.
     */
    private void setByobuSessionsButton() {
        View sessionsButton = findViewById(R.id.byobuSessionsButton);
        if (sessionsButton == null)
            return;

        ByobuSessionManager byobuManager = new ByobuSessionManager();

        sessionsButton.setOnClickListener(v -> {
            TerminalSession session = getCurrentSession();
            if (session == null) {
                showToast("No active session", true);
                return;
            }

            showToast("Fetching byobu sessions...", false);

            byobuManager.listSessions(session, new ByobuSessionManager.SessionListCallback() {
                @Override
                public void onSessionsFound(List<String> sessions) {
                    showByobuSessionsDialog(sessions, byobuManager);
                }

                @Override
                public void onError(String message) {
                    if (message != null && message.contains("No byobu sessions found")) {
                        // If no sessions exist, prompt to create a new one
                        promptForNewSession(byobuManager);
                    } else {
                        showToast(message, true);
                    }
                }
            });
        });
    }

    /**
     * Shows a dialog with the list of byobu sessions.
     * User can select a session and then choose an action.
     */
    private void showByobuSessionsDialog(List<String> sessions, ByobuSessionManager byobuManager) {
        // Add "New Session" option at the top
        List<String> options = new ArrayList<>();
        options.add("➕ 新規セッションを作成");
        for (String s : sessions) {
            options.add("📺 " + s);
        }

        String[] optionArray = options.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Byobu Sessions (" + sessions.size() + ")")
                .setItems(optionArray, (dialog, which) -> {
                    if (which == 0) {
                        // New session
                        promptForNewSession(byobuManager);
                    } else {
                        // Selected existing session
                        String selectedSession = sessions.get(which - 1);
                        showSessionActionsDialog(selectedSession, byobuManager);
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    /**
     * Prompt for new session name and create it.
     */
    private void promptForNewSession(ByobuSessionManager byobuManager) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setSingleLine();
        input.setHint("セッション名 (例: dev, work)");

        builder.setTitle("新規Byobuセッション")
                .setMessage("セッション名を入力してください:")
                .setView(input)
                .setPositiveButton("作成して接続", (dialog, which) -> {
                    String sessionName = input.getText().toString().trim();
                    if (!sessionName.isEmpty()) {
                        TerminalSession session = getCurrentSession();
                        if (session != null) {
                            // Use -d to create detached session first, then attach
                            // This avoids "sessions should be nested" warning
                            // Note: If already in a byobu session, detach first to avoid nested sessions
                            String command = "if [ -n \"$TMUX\" ]; then byobu detach 2>/dev/null; fi; byobu new-session -d -s '"
                                    + sessionName + "' && sleep 0.5 && byobu attach -t '" + sessionName + "'\n";
                            byte[] data = command.getBytes();
                            session.write(data, 0, data.length);
                            showToast("セッション作成・接続: " + sessionName, false);
                        }
                    } else {
                        showToast("セッション名を入力してください", false);
                    }
                })
                .setNeutralButton("作成のみ", (dialog, which) -> {
                    String sessionName = input.getText().toString().trim();
                    if (!sessionName.isEmpty()) {
                        TerminalSession session = getCurrentSession();
                        if (session != null) {
                            // Create session in detached mode (background)
                            String command = "byobu new-session -d -s '" + sessionName + "'\n";
                            byte[] data = command.getBytes();
                            session.write(data, 0, data.length);
                            showToast("セッション作成（バックグラウンド）: " + sessionName, false);
                        }
                    } else {
                        showToast("セッション名を入力してください", false);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Show actions available for a selected session.
     */
    private void showSessionActionsDialog(String sessionName, ByobuSessionManager byobuManager) {
        String[] actions = {
                "🔗 接続 (attach)",
                "🗑️ 終了 (kill)",
                "📋 セッション名をコピー"
        };

        new AlertDialog.Builder(this)
                .setTitle("セッション: " + sessionName)
                .setItems(actions, (dialog, which) -> {
                    TerminalSession session = getCurrentSession();
                    if (session == null) {
                        showToast("アクティブなセッションがありません", true);
                        return;
                    }

                    switch (which) {
                        case 0: // Attach
                            byobuManager.attachToSession(session, sessionName);
                            showToast("接続中: " + sessionName, false);
                            break;
                        case 1: // Kill
                            confirmKillSession(sessionName, byobuManager);
                            break;
                        case 2: // Copy to clipboard
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("session name", sessionName);
                            clipboard.setPrimaryClip(clip);
                            showToast("コピー: " + sessionName, false);
                            break;
                    }
                })
                .setNegativeButton("戻る", null)
                .show();
    }

    /**
     * Confirm before killing a session.
     */
    private void confirmKillSession(String sessionName, ByobuSessionManager byobuManager) {
        new AlertDialog.Builder(this)
                .setTitle("セッション終了の確認")
                .setMessage("セッション「" + sessionName + "」を終了しますか？\n\n⚠️ この操作は取り消せません。")
                .setPositiveButton("終了", (dialog, which) -> {
                    TerminalSession session = getCurrentSession();
                    if (session != null) {
                        String command = "byobu kill-session -t " + sessionName + "\n";
                        byte[] data = command.getBytes();
                        session.write(data, 0, data.length);
                        showToast("セッション終了: " + sessionName, false);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Initialize custom commands ActivityResultLaunchers for import/export.
     */
    private void initCustomCommandsLaunchers() {
        mCustomCommandManager = new CustomCommandManager(this);

        // Export launcher - creates a JSON file
        mExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(),
                uri -> {
                    if (uri != null) {
                        try {
                            OutputStream outputStream = getContentResolver().openOutputStream(uri);
                            if (outputStream != null) {
                                mCustomCommandManager.exportToFile(outputStream);
                                outputStream.close();
                                showToast("エクスポート完了", false);
                            }
                        } catch (Exception e) {
                            showToast("エクスポート失敗: " + e.getMessage(), true);
                        }
                    }
                });

        // Import launcher - opens a JSON file
        mImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        handleImportFile(uri);
                    }
                });

        // AI prompt template export launcher
        mAiTemplateExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(),
                uri -> {
                    if (uri != null) {
                        try {
                            OutputStream outputStream = getContentResolver().openOutputStream(uri);
                            if (outputStream != null) {
                                new AiSettingsManager(this).exportTemplatesToFile(outputStream);
                                outputStream.close();
                                showToast("テンプレートをエクスポートしました", false);
                            }
                        } catch (Exception e) {
                            showToast("テンプレートのエクスポート失敗: " + e.getMessage(), true);
                        }
                    }
                });

        // AI prompt template import launcher
        mAiTemplateImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        handleAiTemplateImportFile(uri);
                    }
                });

        // Claude history launcher - opens JSONL files for AR display
        mClaudeHistoryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        handleClaudeHistoryFile(uri);
                    }
                });
    }

    /**
     * Set up custom commands button.
     */
    private void setCustomCommandsButton() {
        View customButton = findViewById(R.id.customCommandsButton);
        if (customButton == null)
            return;

        customButton.setOnClickListener(v -> {
            showCustomCommandsDialog(false);
        });

        customButton.setOnLongClickListener(v -> {
            showCustomCommandsDialog(true);
            return true;
        });
    }

    /**
     * Show dialog with custom commands list organized by folders.
     */
    private void showCustomCommandsDialog(boolean manageMode) {
        List<CommandFolder> folders = mCustomCommandManager.getAllFolders();
        List<CustomCommand> rootCommands = mCustomCommandManager.getCommandsInFolder(null);

        // Build display items
        List<String> displayItems = new ArrayList<>();
        List<Object> dataItems = new ArrayList<>(); // Store folder/command objects

        // Menu options
        if (manageMode) {
            displayItems.add("➕ 新規コマンド");
            dataItems.add("NEW_COMMAND");
            displayItems.add("📁 新規フォルダ");
            dataItems.add("NEW_FOLDER");
            displayItems.add("📤 エクスポート");
            dataItems.add("EXPORT");
            displayItems.add("📥 インポート");
            dataItems.add("IMPORT");
            displayItems.add("◀️ 実行モードに戻る");
            dataItems.add("EXIT_MANAGE");
        } else {
            displayItems.add("⚙️ コマンド管理・編集...");
            dataItems.add("ENTER_MANAGE");
        }

        // Folders
        for (CommandFolder folder : folders) {
            int count = mCustomCommandManager.getCommandCountInFolder(folder.getId());
            displayItems.add("📁 " + folder.getName() + " (" + count + ")");
            dataItems.add(folder);
        }

        // Root-level commands
        for (CustomCommand cmd : rootCommands) {
            displayItems.add(manageMode ? "🔧 " + cmd.getName() : "📄 " + cmd.getName());
            dataItems.add(cmd);
        }

        int totalCount = mCustomCommandManager.getAllCommands().size();
        String[] items = displayItems.toArray(new String[0]);
        String title = manageMode ? "カスタムコマンド管理 (" + totalCount + ")" : "カスタムコマンド (" + totalCount + ")";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> {
                    Object selected = dataItems.get(which);
                    if ("NEW_COMMAND".equals(selected)) {
                        showAddEditCommandDialog(null, null, true);
                    } else if ("NEW_FOLDER".equals(selected)) {
                        showAddFolderDialog(true);
                    } else if ("EXPORT".equals(selected)) {
                        exportCustomCommands();
                    } else if ("IMPORT".equals(selected)) {
                        importCustomCommands();
                    } else if ("ENTER_MANAGE".equals(selected)) {
                        showCustomCommandsDialog(true);
                    } else if ("EXIT_MANAGE".equals(selected)) {
                        showCustomCommandsDialog(false);
                    } else if (selected instanceof CommandFolder) {
                        showFolderContentsDialog((CommandFolder) selected, manageMode);
                    } else if (selected instanceof CustomCommand) {
                        if (manageMode) {
                            showCommandActionsDialog((CustomCommand) selected, null);
                        } else {
                            executeCustomCommand((CustomCommand) selected);
                        }
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void showCustomCommandsDialog() {
        showCustomCommandsDialog(false);
    }

    /**
     * Show contents of a folder.
     */
    private void showFolderContentsDialog(CommandFolder folder, boolean manageMode) {
        List<CustomCommand> commands = mCustomCommandManager.getCommandsInFolder(folder.getId());

        List<String> displayItems = new ArrayList<>();
        if (manageMode) {
            displayItems.add("➕ このフォルダにコマンドを追加");
            displayItems.add("✏️ フォルダ名を変更");
            displayItems.add("🗑️ フォルダを削除");
        }

        for (CustomCommand cmd : commands) {
            displayItems.add(manageMode ? "🔧 " + cmd.getName() : "📄 " + cmd.getName());
        }

        String[] items = displayItems.toArray(new String[0]);
        String title = (manageMode ? "📁 [管理] " : "📁 ") + folder.getName();

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> {
                    if (manageMode) {
                        if (which == 0) {
                            showAddEditCommandDialog(null, folder.getId(), true);
                        } else if (which == 1) {
                            showRenameFolderDialog(folder, true);
                        } else if (which == 2) {
                            confirmDeleteFolder(folder, true);
                        } else {
                            CustomCommand cmd = commands.get(which - 3);
                            showCommandActionsDialog(cmd, folder.getId());
                        }
                    } else {
                        CustomCommand cmd = commands.get(which);
                        executeCustomCommand(cmd);
                    }
                })
                .setNegativeButton("戻る", (d, w) -> showCustomCommandsDialog(manageMode))
                .show();
    }

    /**
     * Show dialog to add a new folder.
     */
    private void showAddFolderDialog(boolean manageMode) {
        final EditText input = new EditText(this);
        input.setHint("フォルダ名");
        input.setSingleLine();
        input.setPadding(48, 24, 48, 0);

        new AlertDialog.Builder(this)
                .setTitle("新規フォルダ")
                .setView(input)
                .setPositiveButton("作成", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        mCustomCommandManager.createFolder(name);
                        showToast("フォルダを作成: " + name, false);
                        showCustomCommandsDialog(manageMode);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Show dialog to rename a folder.
     */
    private void showRenameFolderDialog(CommandFolder folder, boolean manageMode) {
        final EditText input = new EditText(this);
        input.setText(folder.getName());
        input.setSingleLine();
        input.setPadding(48, 24, 48, 0);

        new AlertDialog.Builder(this)
                .setTitle("フォルダ名を変更")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        mCustomCommandManager.updateFolder(folder.getId(), name);
                        showToast("フォルダ名を変更: " + name, false);
                        showCustomCommandsDialog(manageMode);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Confirm before deleting a folder.
     */
    private void confirmDeleteFolder(CommandFolder folder, boolean manageMode) {
        int cmdCount = mCustomCommandManager.getCommandCountInFolder(folder.getId());

        String message = "フォルダ「" + folder.getName() + "」を削除しますか？";
        if (cmdCount > 0) {
            message += "\n\n" + cmdCount + "個のコマンドがあります。";
        }

        String[] options = cmdCount > 0
                ? new String[] { "フォルダのみ削除（コマンドはルートへ移動）", "フォルダとコマンドを削除", "キャンセル" }
                : new String[] { "削除", "キャンセル" };

        new AlertDialog.Builder(this)
                .setTitle("フォルダを削除")
                .setMessage(message)
                .setItems(options, (dialog, which) -> {
                    if (cmdCount > 0) {
                        if (which == 0) {
                            mCustomCommandManager.deleteFolder(folder.getId(), false);
                            showToast("フォルダを削除（コマンドは保持）", false);
                            showCustomCommandsDialog(manageMode);
                        } else if (which == 1) {
                            mCustomCommandManager.deleteFolder(folder.getId(), true);
                            showToast("フォルダとコマンドを削除", false);
                            showCustomCommandsDialog(manageMode);
                        }
                    } else {
                        if (which == 0) {
                            mCustomCommandManager.deleteFolder(folder.getId(), false);
                            showToast("フォルダを削除", false);
                            showCustomCommandsDialog(manageMode);
                        }
                    }
                })
                .show();
    }

    /**
     * Show actions dialog for a selected custom command.
     */
    private void showCommandActionsDialog(CustomCommand cmd, String currentFolderId) {
        List<String> actionsList = new ArrayList<>();
        actionsList.add("▶️ 実行");
        actionsList.add("✏️ 編集");
        actionsList.add("📋 コピー");
        actionsList.add("📁 フォルダを移動");
        actionsList.add("🗑️ 削除");

        String[] actions = actionsList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(cmd.getName())
                .setMessage("コマンド: " + cmd.getCommand())
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0: // Execute
                            executeCustomCommand(cmd);
                            break;
                        case 1: // Edit
                            showAddEditCommandDialog(cmd, cmd.getFolderId());
                            break;
                        case 2: // Copy
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Custom Command", cmd.getCommand());
                            clipboard.setPrimaryClip(clip);
                            showToast("コピーしました: " + cmd.getCommand(), false);
                            break;
                        case 3: // Move to folder
                            showMoveToFolderDialog(cmd);
                            break;
                        case 4: // Delete
                            confirmDeleteCommand(cmd);
                            break;
                    }
                })
                .setNegativeButton("戻る", null)
                .show();
    }

    /**
     * Show dialog to move a command to a different folder.
     */
    private void showMoveToFolderDialog(CustomCommand cmd) {
        List<CommandFolder> folders = mCustomCommandManager.getAllFolders();

        List<String> options = new ArrayList<>();
        List<String> folderIds = new ArrayList<>();

        options.add("📂 ルート（フォルダなし）");
        folderIds.add(null);

        for (CommandFolder folder : folders) {
            options.add("📁 " + folder.getName());
            folderIds.add(folder.getId());
        }

        String[] items = options.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("移動先を選択")
                .setItems(items, (dialog, which) -> {
                    String targetFolderId = folderIds.get(which);
                    mCustomCommandManager.moveCommandToFolder(cmd.getId(), targetFolderId);
                    showToast("移動しました", false);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Execute a custom command.
     */
    private void executeCustomCommand(CustomCommand cmd) {
        TerminalSession session = getCurrentSession();
        if (session == null) {
            showToast("アクティブなセッションがありません", true);
            return;
        }

        String command = cmd.getCommand();
        if (!command.endsWith("\n")) {
            command += "\n";
        }
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);
        showToast("実行: " + cmd.getName(), false);
    }

    /**
     * Show dialog to add or edit a custom command.
     */
    private void showAddEditCommandDialog(CustomCommand existing, String folderId, boolean manageMode) {
        boolean isEdit = existing != null;

        // Create dialog view
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("コマンド名 (例: サーバー接続)");
        nameInput.setSingleLine();
        if (isEdit)
            nameInput.setText(existing.getName());
        layout.addView(nameInput);

        final EditText cmdInput = new EditText(this);
        cmdInput.setHint("コマンド (例: ssh user@host)");
        cmdInput.setMinLines(2);
        cmdInput.setMaxLines(5);
        if (isEdit)
            cmdInput.setText(existing.getCommand());
        layout.addView(cmdInput);

        // Folder selector
        List<CommandFolder> folders = mCustomCommandManager.getAllFolders();
        final android.widget.Spinner folderSpinner = new android.widget.Spinner(this);

        List<String> folderOptions = new ArrayList<>();
        List<String> folderIds = new ArrayList<>();
        folderOptions.add("(フォルダなし)");
        folderIds.add(null);

        int selectedIndex = 0;
        String targetFolderId = isEdit ? existing.getFolderId() : folderId;

        for (int i = 0; i < folders.size(); i++) {
            CommandFolder f = folders.get(i);
            folderOptions.add(f.getName());
            folderIds.add(f.getId());
            if (f.getId().equals(targetFolderId)) {
                selectedIndex = i + 1;
            }
        }

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, folderOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        folderSpinner.setAdapter(adapter);
        folderSpinner.setSelection(selectedIndex);
        layout.addView(folderSpinner);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isEdit ? "コマンドを編集" : "新規コマンドを追加")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String command = cmdInput.getText().toString().trim();
                    String selectedFolderId = folderIds.get(folderSpinner.getSelectedItemPosition());

                    if (name.isEmpty() || command.isEmpty()) {
                        showToast("名前とコマンドを入力してください", true);
                        return;
                    }

                    if (isEdit) {
                        mCustomCommandManager.updateCommand(existing.getId(), name, command, selectedFolderId);
                        showToast("更新しました: " + name, false);
                    } else {
                        mCustomCommandManager.saveCommand(name, command, selectedFolderId);
                        showToast("追加しました: " + name, false);
                    }
                    showCustomCommandsDialog(manageMode);
                })
                .setNegativeButton("キャンセル", null);

        builder.show();
    }

    private void showAddEditCommandDialog(CustomCommand existing, String folderId) {
        showAddEditCommandDialog(existing, folderId, false);
    }

    /**
     * Confirm before deleting a command.
     */
    private void confirmDeleteCommand(CustomCommand cmd) {
        new AlertDialog.Builder(this)
                .setTitle("削除の確認")
                .setMessage("コマンド「" + cmd.getName() + "」を削除しますか？")
                .setPositiveButton("削除", (dialog, which) -> {
                    mCustomCommandManager.deleteCommand(cmd.getId());
                    showToast("削除しました: " + cmd.getName(), false);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Export custom commands to a JSON file.
     */
    private void exportCustomCommands() {
        String filename = "termux_commands_" + System.currentTimeMillis() + ".json";
        mExportLauncher.launch(filename);
    }

    /**
     * Import custom commands from a JSON file.
     */
    private void importCustomCommands() {
        mImportLauncher.launch(new String[] { "application/json", "*/*" });
    }

    /**
     * Test Turso connection and initialize database schema.
     */
    public void testTursoConnection(String url, String token) {
        showToast("接続テスト中...", false);
        new Thread(() -> {
            try {
                // Initialize a temporary manager to test connection
                final com.termux.app.turso.TursoSyncManager tempManager = new com.termux.app.turso.TursoSyncManager(url,
                        token);
                tempManager.initializeDb(null);
                runOnUiThread(() -> {
                    showToast("接続成功: テーブル作成完了", false);
                    mTursoSyncManager = tempManager; // Use this valid manager
                });
            } catch (Exception e) {
                runOnUiThread(() -> showToast("接続エラー: " + e.getMessage(), true));
            }
        }).start();
    }

    /**
     * Export AI prompt templates to a JSON file.
     */
    public void exportAiTemplates() {
        String filename = "termux_ai_templates_" + System.currentTimeMillis() + ".json";
        mAiTemplateExportLauncher.launch(filename);
    }

    /**
     * Import AI prompt templates from a JSON file.
     */
    public void importAiTemplates() {
        mAiTemplateImportLauncher.launch(new String[] { "application/json", "*/*" });
    }

    /**
     * Handle imported file.
     */
    private void handleImportFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                showToast("ファイルを開けませんでした", true);
                return;
            }

            CustomCommandManager.ImportData data = mCustomCommandManager.parseImportFile(inputStream);
            inputStream.close();

            // Show import options dialog
            showImportOptionsDialog(data);
        } catch (Exception e) {
            showToast("インポート失敗: " + e.getMessage(), true);
        }
    }

    /**
     * Handle imported AI prompt template file.
     */
    private void handleAiTemplateImportFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                showToast("ファイルを開けませんでした", true);
                return;
            }

            AiSettingsManager settingsManager = new AiSettingsManager(this);
            AiSettingsManager.TemplateImportData data = settingsManager.parseTemplateImportFile(inputStream);
            inputStream.close();

            showAiTemplateImportOptionsDialog(settingsManager, data);
        } catch (Exception e) {
            showToast("テンプレートのインポート失敗: " + e.getMessage(), true);
        }
    }

    /**
     * Show import options dialog (merge, replace, or clear and import).
     */
    private void showImportOptionsDialog(CustomCommandManager.ImportData data) {
        String message = "インポート内容:\n" +
                "• フォルダ: " + data.folders.size() + "個\n" +
                "• コマンド: " + data.commands.size() + "個\n\n" +
                "インポート方法を選択してください:";

        String[] options = {
                "マージ（既存を残して追加のみ）",
                "上書き（重複は置換）",
                "すべてクリアしてインポート",
                "キャンセル"
        };

        new AlertDialog.Builder(this)
                .setTitle("インポート")
                .setMessage(message)
                .setItems(options, (dialog, which) -> {
                    CustomCommandManager.ImportMode mode;
                    switch (which) {
                        case 0:
                            mode = CustomCommandManager.ImportMode.MERGE;
                            break;
                        case 1:
                            mode = CustomCommandManager.ImportMode.REPLACE;
                            break;
                        case 2:
                            mode = CustomCommandManager.ImportMode.CLEAR_AND_IMPORT;
                            break;
                        default:
                            return;
                    }

                    CustomCommandManager.ImportResult result = mCustomCommandManager.importData(data, mode);
                    showToast("インポート完了\n" + result.toString(), false);
                    showCustomCommandsDialog();
                })
                .show();
    }

    /**
     * Show import options dialog for AI prompt templates.
     */
    private void showAiTemplateImportOptionsDialog(AiSettingsManager settingsManager,
            AiSettingsManager.TemplateImportData data) {
        String message = "インポート内容:\n" +
                "• テンプレート: " + data.templates.size() + "個\n\n" +
                "インポート方法を選択してください:";

        String[] options = {
                "マージ（既存を残して追加のみ）",
                "上書き（重複は置換）",
                "すべてクリアしてインポート",
                "キャンセル"
        };

        new AlertDialog.Builder(this)
                .setTitle("テンプレートをインポート")
                .setMessage(message)
                .setItems(options, (dialog, which) -> {
                    AiSettingsManager.TemplateImportMode mode;
                    switch (which) {
                        case 0:
                            mode = AiSettingsManager.TemplateImportMode.MERGE;
                            break;
                        case 1:
                            mode = AiSettingsManager.TemplateImportMode.REPLACE;
                            break;
                        case 2:
                            mode = AiSettingsManager.TemplateImportMode.CLEAR_AND_IMPORT;
                            break;
                        default:
                            return;
                    }

                    AiSettingsManager.TemplateImportResult result = settingsManager.importTemplates(data, mode);
                    showToast("テンプレートをインポートしました\n" + result.toString(), false);
                })
                .show();
    }

    /**
     * Set up SSH connection and management buttons.
     */
    private void setSshButtons() {
        SshConfigManager sshConfigManager = new SshConfigManager(this);

        // SSH Connect button - shows list of saved connections
        View sshConnectButton = findViewById(R.id.sshConnectButton);
        if (sshConnectButton != null) {
            sshConnectButton.setOnClickListener(v -> {
                List<SshConnectionConfig> configs = sshConfigManager.loadConfigs();
                if (configs.isEmpty()) {
                    showToast("No SSH connections configured. Use 'SSH設定' to add one.", true);
                    return;
                }

                String[] configNames = new String[configs.size()];
                for (int i = 0; i < configs.size(); i++) {
                    configNames[i] = configs.get(i).toString();
                }

                new AlertDialog.Builder(this)
                        .setTitle("SSH Connections")
                        .setItems(configNames, (dialog, which) -> {
                            SshConnectionConfig selectedConfig = configs.get(which);
                            connectToSsh(selectedConfig);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // SSH Manage button - opens SSH connections management activity
        View sshManageButton = findViewById(R.id.sshManageButton);
        if (sshManageButton != null) {
            sshManageButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, SshConnectionsActivity.class);
                startActivity(intent);
            });
        }
    }

    /**
     * Creates a new terminal session and connects via SSH.
     */
    private void connectToSsh(SshConnectionConfig config) {
        if (mTermuxService == null || mTermuxTerminalSessionActivityClient == null) {
            showToast("Service not ready", true);
            return;
        }

        // Save SSH host for Claude HTTP server connection
        mCurrentSshHost = config.getHost();

        String sshCommand = config.buildSshCommand();

        // Create a new terminal session with the SSH command
        // The session will execute the SSH command automatically
        String sessionName = config.getName() != null ? config.getName() : config.getHost();

        try {
            mTermuxTerminalSessionActivityClient.addNewSession(false, sessionName);

            // Wait a bit for the session to be created, then send SSH command
            new android.os.Handler().postDelayed(() -> {
                TerminalSession session = getCurrentSession();
                if (session != null) {
                    // Send the SSH command
                    byte[] data = (sshCommand + "\n").getBytes();
                    session.write(data, 0, data.length);
                    showToast("Connecting to " + config.getHost() + "...", false);
                }
            }, 500);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage("TermuxActivity", "Failed to create SSH session", e);
            showToast("Failed to create SSH session", true);
        }
    }

    /**
     * Set up EVEN G1 AR Glasses connection and management buttons.
     */
    private void setEvenG1Buttons() {
        EvenG1ConfigManager configManager = new EvenG1ConfigManager(this);
        EvenG1Manager g1Manager = EvenG1Manager.getInstance();

        // G1 Connect button - shows scan or saved connections
        View g1ConnectButton = findViewById(R.id.evenG1ConnectButton);
        if (g1ConnectButton != null) {
            g1ConnectButton.setOnClickListener(v -> {
                // Check Bluetooth permissions
                if (!checkBluetoothPermissions()) {
                    requestBluetoothPermissions();
                    return;
                }

                // Initialize manager if needed
                g1Manager.initialize(getApplicationContext(), new EvenG1Manager.ConnectionCallback() {
                    @Override
                    public void onDeviceFound(@NonNull String channelNumber, @NonNull String leftName,
                            @NonNull String rightName) {
                        runOnUiThread(() -> {
                            showToast("Found G1 Channel " + channelNumber, false);
                        });
                    }

                    @Override
                    public void onConnected(@NonNull EvenG1DevicePair pair) {
                        runOnUiThread(() -> {
                            showToast("Connected to EVEN G1!", false);
                            // Show test send dialog
                            showG1TestSendDialog();
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        runOnUiThread(() -> {
                            showToast("Disconnected from EVEN G1", true);
                        });
                    }

                    @Override
                    public void onConnectionFailed(@NonNull String error) {
                        runOnUiThread(() -> {
                            showToast("Connection failed: " + error, true);
                        });
                    }

                    @Override
                    public void onDataReceived(boolean isLeft, @NonNull byte[] data) {
                        // Handle received data from glasses (for future implementation)
                        Logger.logDebug("TermuxActivity", "Received data from " +
                                (isLeft ? "left" : "right") + ": " + data.length + " bytes");
                    }
                });

                // Show connection dialog
                showG1ConnectionDialog(configManager, g1Manager);
            });
        }

        // G1 Manage button removed - functionality integrated into G1 Connect dialog
    }

    /**
     * Check if Bluetooth permissions are granted.
     */
    private boolean checkBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            return checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    &&
                    checkSelfPermission(
                            android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 and below
            return checkSelfPermission(
                    android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Request Bluetooth permissions.
     */
    private void requestBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            requestPermissions(new String[] {
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
            }, 1001);
        } else {
            // Android 11 and below
            requestPermissions(new String[] {
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, 1001);
        }
    }

    /**
     * Show G1 connection dialog.
     */
    private void showG1ConnectionDialog(com.termux.app.eveng1.EvenG1ConfigManager configManager,
            com.termux.app.eveng1.EvenG1Manager g1Manager) {

        // Check current connection status
        boolean isConnected = g1Manager.isConnected();
        com.termux.app.eveng1.EvenG1ConfigManager.SavedG1Device savedDevice = configManager.getSavedDevice();

        // Create dialog options
        List<String> options = new ArrayList<>();

        if (isConnected) {
            // Connected state options
            options.add("📤 Send Text to G1");
            options.add("📄 Claude履歴を読み込む");
            options.add("💾 Save this G1 for quick connect");
            options.add("🔌 Disconnect");
            options.add("🔄 Reconnect (disconnect & scan)");
        } else {
            // Disconnected state options
            if (savedDevice != null) {
                options.add("📱 Connect to " + savedDevice.toString());
            }
            options.add("🔍 Scan for new devices");
            if (savedDevice != null) {
                options.add("🗑️ Clear saved device");
            }
        }

        String title = isConnected ? "EVEN G1 (Connected ✓)" : "EVEN G1 Connection";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (isConnected) {
                        // Connected state options
                        switch (which) {
                            case 0: // Send text
                                showG1TestSendDialog();
                                break;
                            case 1: // Load Claude history
                                openClaudeHistoryFilePicker();
                                break;
                            case 2: // Save current device
                                com.termux.app.eveng1.EvenG1DevicePair pair = g1Manager.getConnectedPair();
                                if (pair != null) {
                                    configManager.saveCurrentDevice(pair);
                                    showToast("G1 saved for quick connect!", false);
                                }
                                break;
                            case 3: // Disconnect
                                g1Manager.disconnect();
                                showToast("Disconnected from EVEN G1", false);
                                break;
                            case 4: // Reconnect
                                g1Manager.disconnect();
                                showToast("Disconnected. Scanning...", false);
                                startG1Scan(g1Manager);
                                break;
                        }
                    } else {
                        // Disconnected state options
                        if (savedDevice != null) {
                            switch (which) {
                                case 0: // Connect to saved device
                                    showToast("Connecting to saved G1...", false);
                                    g1Manager.connectToSavedDevice(savedDevice);
                                    break;
                                case 1: // Scan for new devices
                                    startG1Scan(g1Manager);
                                    break;
                                case 2: // Clear saved device
                                    configManager.clearSavedDevice();
                                    showToast("Saved device cleared", false);
                                    break;
                            }
                        } else {
                            // No saved device - only scan option
                            if (which == 0) {
                                startG1Scan(g1Manager);
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Start G1 device scan and show results.
     */
    private void startG1Scan(com.termux.app.eveng1.EvenG1Manager g1Manager) {
        showToast("Scanning for EVEN G1 devices...", false);
        g1Manager.startScan();

        // Show scanning dialog with discovered devices
        new android.os.Handler().postDelayed(() -> {
            List<com.termux.app.eveng1.EvenG1Device> devices = g1Manager.getDiscoveredDevices();
            List<String> channels = new ArrayList<>();
            for (com.termux.app.eveng1.EvenG1Device device : devices) {
                String channel = device.getChannelNumber();
                if (!channels.contains(channel)) {
                    channels.add(channel);
                }
            }

            if (channels.isEmpty()) {
                showToast(
                        "No EVEN G1 devices found. Make sure glasses are out of case and not connected to other apps.",
                        true);
                g1Manager.stopScan();
                return;
            }

            g1Manager.stopScan();

            // Show channel selection
            String[] channelOptions = new String[channels.size()];
            for (int i = 0; i < channels.size(); i++) {
                channelOptions[i] = "Channel " + channels.get(i);
            }

            new AlertDialog.Builder(this)
                    .setTitle("Select EVEN G1 Channel")
                    .setItems(channelOptions, (dlg, idx) -> {
                        String selectedChannel = channels.get(idx);
                        g1Manager.connect(selectedChannel);
                        showToast("Connecting to Channel " + selectedChannel + "...", false);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }, 5000); // 5 second scan
    }

    /**
     * Show G1 test send dialog.
     */
    private void showG1TestSendDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter text to display on G1");
        input.setText("Hello from Termux!");

        new AlertDialog.Builder(this)
                .setTitle("Send Text to EVEN G1 (5秒表示)")
                .setView(input)
                .setPositiveButton("Send", (dialog, which) -> {
                    String text = input.getText().toString();
                    if (!text.isEmpty()) {
                        com.termux.app.eveng1.EvenG1Manager manager = com.termux.app.eveng1.EvenG1Manager.getInstance();
                        android.os.Handler handler = new android.os.Handler();

                        com.termux.app.eveng1.EvenG1Protocol.sendText(manager, text, handler,
                                new com.termux.app.eveng1.EvenG1Protocol.TextSendCallback() {
                                    @Override
                                    public void onSuccess() {
                                        runOnUiThread(() -> showToast("Text sent! (5秒後に消えます)", false));

                                        // Exit to dashboard after 5 seconds
                                        handler.postDelayed(() -> {
                                            byte[] exitPacket = com.termux.app.eveng1.EvenG1Protocol
                                                    .createExitToDashboardPacket();
                                            manager.sendData(exitPacket);
                                        }, 5000);
                                    }

                                    @Override
                                    public void onFailure(@NonNull String error) {
                                        runOnUiThread(() -> showToast("Send failed: " + error, true));
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Opens file picker to select Claude history JSONL file.
     */
    private void openClaudeHistoryFilePicker() {
        if (mClaudeHistoryLauncher != null) {
            mClaudeHistoryLauncher.launch(new String[] { "*/*" });
        } else {
            showToast("ファイル選択機能が初期化されていません", true);
        }
    }

    /**
     * Handles selected Claude history JSONL file.
     */
    private void handleClaudeHistoryFile(android.net.Uri uri) {
        try {
            // Read file content
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                showToast("ファイルを開けませんでした", true);
                return;
            }

            // Create temp file to pass to parser
            java.io.File tempFile = new java.io.File(getCacheDir(), "claude_history_temp.jsonl");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.close();
            inputStream.close();

            // Parse the file
            List<com.termux.app.claude.ClaudeChatParser.AgentComment> comments = com.termux.app.claude.ClaudeChatParser
                    .getAgentComments(tempFile.getAbsolutePath());

            // Clean up temp file
            tempFile.delete();

            if (comments.isEmpty()) {
                showToast("Claudeのコメントが見つかりませんでした", true);
                return;
            }

            // Combine all comments into one text
            StringBuilder fullText = new StringBuilder();
            for (int i = 0; i < comments.size(); i++) {
                if (i > 0) {
                    fullText.append("\n\n---\n\n");
                }
                fullText.append(comments.get(i).text);
            }

            // Create pager and show dialog
            com.termux.app.eveng1.EvenG1Manager manager = com.termux.app.eveng1.EvenG1Manager.getInstance();
            if (!manager.isConnected()) {
                showToast("G1に接続されていません", true);
                return;
            }

            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            mCurrentArPager = new com.termux.app.eveng1.ArTextPager(manager, handler, fullText.toString());

            showToast(comments.size() + "件のコメント, " + mCurrentArPager.getTotalPages() + "ページ", false);
            showArPagerDialog();

        } catch (Exception e) {
            showToast("ファイル読み込みエラー: " + e.getMessage(), true);
        }
    }

    /**
     * Shows AR pager dialog with page navigation controls.
     */
    private void showArPagerDialog() {
        if (mCurrentArPager == null) {
            showToast("ページャーが初期化されていません", true);
            return;
        }

        // Send current page first
        mCurrentArPager.sendCurrentPage(new com.termux.app.eveng1.EvenG1Protocol.TextSendCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> showPageNavigationDialog());
            }

            @Override
            public void onFailure(@NonNull String error) {
                runOnUiThread(() -> showToast("送信失敗: " + error, true));
            }
        });
    }

    /**
     * Shows page navigation dialog for AR display.
     */
    private void showPageNavigationDialog() {
        if (mCurrentArPager == null)
            return;

        String title = "📖 ページ " + mCurrentArPager.getCurrentPageNum() + " / " + mCurrentArPager.getTotalPages();

        List<String> options = new ArrayList<>();
        if (mCurrentArPager.hasPrev()) {
            options.add("⬅️ 前のページ");
        }
        if (mCurrentArPager.hasNext()) {
            options.add("➡️ 次のページ");
        }
        options.add("🔄 現在のページを再送信");
        options.add("❌ 閉じる (ダッシュボードに戻る)");

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String selected = options.get(which);
                    if (selected.startsWith("⬅️")) {
                        // Previous page
                        mCurrentArPager.prevPage(new com.termux.app.eveng1.EvenG1Protocol.TextSendCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> showPageNavigationDialog());
                            }

                            @Override
                            public void onFailure(@NonNull String error) {
                                runOnUiThread(() -> showToast("送信失敗: " + error, true));
                            }
                        });
                    } else if (selected.startsWith("➡️")) {
                        // Next page
                        mCurrentArPager.nextPage(new com.termux.app.eveng1.EvenG1Protocol.TextSendCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> showPageNavigationDialog());
                            }

                            @Override
                            public void onFailure(@NonNull String error) {
                                runOnUiThread(() -> showToast("送信失敗: " + error, true));
                            }
                        });
                    } else if (selected.startsWith("🔄")) {
                        // Resend current page
                        mCurrentArPager.sendCurrentPage(new com.termux.app.eveng1.EvenG1Protocol.TextSendCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> showPageNavigationDialog());
                            }

                            @Override
                            public void onFailure(@NonNull String error) {
                                runOnUiThread(() -> showToast("送信失敗: " + error, true));
                            }
                        });
                    } else {
                        // Close - exit to dashboard
                        com.termux.app.eveng1.EvenG1Manager manager = com.termux.app.eveng1.EvenG1Manager.getInstance();
                        byte[] exitPacket = com.termux.app.eveng1.EvenG1Protocol.createExitToDashboardPacket();
                        manager.sendData(exitPacket);
                        mCurrentArPager = null;
                        showToast("ARディスプレイを終了しました", false);
                    }
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Set up directory navigation button.
     */
    private void setDirectoryNavigationButton() {
        View dirNavButton = findViewById(R.id.dirNavButton);
        if (dirNavButton == null)
            return;

        DirectoryNavigationManager dirNavManager = new DirectoryNavigationManager();

        dirNavButton.setOnClickListener(v -> {
            TerminalSession session = getCurrentSession();
            if (session == null) {
                showToast("アクティブなセッションがありません", true);
                return;
            }

            showToast("ディレクトリ取得中...", false);

            // Use new browse method for file explorer-like navigation
            dirNavManager.browseDirectories(session, new DirectoryNavigationManager.DirectoryBrowseCallback() {
                @Override
                public void onResult(String currentPath, List<String> directories) {
                    showDirectoryBrowserDialog(currentPath, directories, dirNavManager);
                }

                @Override
                public void onError(String message) {
                    showToast(message, true);
                }
            });
        });
    }

    /**
     * Shows a file explorer-like dialog for directory navigation.
     * User can browse directories and press "決定" to stay at current location.
     */
    private void showDirectoryBrowserDialog(String currentPath, List<String> directories,
            DirectoryNavigationManager dirNavManager) {
        // Format directory names with icons
        String[] dirNames = new String[directories.size()];
        for (int i = 0; i < directories.size(); i++) {
            String dirName = directories.get(i);
            if (dirName.equals("..")) {
                dirNames[i] = "⬆️ .. (親ディレクトリ)";
            } else {
                dirNames[i] = "📁 " + dirName;
            }
        }

        // Shorten path for display if too long
        String displayPath = currentPath;
        if (displayPath.length() > 35) {
            displayPath = "..." + displayPath.substring(displayPath.length() - 32);
        }

        new AlertDialog.Builder(this)
                .setTitle("📂 " + displayPath)
                .setItems(dirNames, (dialog, which) -> {
                    String selectedDir = directories.get(which);
                    TerminalSession session = getCurrentSession();
                    if (session == null) {
                        showToast("セッションがありません", true);
                        return;
                    }

                    showToast("移動中...", false);

                    // Navigate and show new directory listing
                    dirNavManager.navigateAndBrowse(session, selectedDir,
                            new DirectoryNavigationManager.DirectoryBrowseCallback() {
                                @Override
                                public void onResult(String newPath, List<String> newDirectories) {
                                    // Show dialog again with new location
                                    showDirectoryBrowserDialog(newPath, newDirectories, dirNavManager);
                                }

                                @Override
                                public void onError(String message) {
                                    showToast(message, true);
                                }
                            });
                })
                .setPositiveButton("✓ ここに決定", (dialog, which) -> {
                    showToast("現在地: " + currentPath, false);
                })
                .setNegativeButton("キャンセル", (dialog, which) -> {
                    // Go back to original directory? For now just close
                })
                .show();
    }

    /**
     * Set up Claude conversation sync button.
     */
    private void setClaudeSyncButton() {
        View claudeButton = findViewById(R.id.claudeSyncButton);
        if (claudeButton == null)
            return;

        ConversationSyncManager syncManager = new ConversationSyncManager();

        claudeButton.setOnClickListener(v -> {
            // Show source selection dialog
            new AlertDialog.Builder(this)
                    .setTitle("AI / Claude メニュー")
                    .setItems(new String[] {
                            "✨ AI Assistant (LLM)",
                            "🤖 Claude→AR (自動同期)",
                            "👁️ AR Viewを開く",
                            "🔄 サーバー再起動"
                    }, (dialog, which) -> {
                        if (which == 0) {
                            showAiInteractionDialog();
                            return;
                        }
                        if (which == 1) {
                            // Auto AR sync
                            showAutoArSyncDialog();
                            return;
                        }
                        if (which == 2) {
                            // Open AR View
                            openArViewIfAvailable();
                            return;
                        }
                        if (which == 3) {
                            // Restart server
                            restartClaudeServer();
                            return;
                        }
                    })
                    .setNegativeButton("キャンセル", null)
                    .show();
        });
    }

    /**
     * Set up AI Assistant button.
     */
    private void setAiAssistantButton() {
        View aiButton = findViewById(R.id.aiAssistantButton);
        if (aiButton == null)
            return;

        aiButton.setOnClickListener(v -> showAiInteractionDialog());
    }

    // Claude history server settings
    private static final int CLAUDE_SERVER_PORT = 8765;
    private String mCurrentSshHost = ""; // SSH host for HTTP server connection

    // AutoArSyncManager instance
    private AutoArSyncManager mAutoArSyncManager;
    private MediaControlManager mMediaControlManager; // For media button control
    private AlertDialog mActiveArDialog; // Track active AR dialog
    private AiSettingsManager mAiSettingsManager;
    private LlmClient mLlmClient;
    private TursoSyncManager mTursoSyncManager;

    /**
     * Shows dialog for automatic AR sync.
     * User inputs query, app sends to terminal and watches for file changes.
     */
    private void showAutoArSyncDialog() {
        if (mCurrentSshHost == null || mCurrentSshHost.isEmpty()) {
            showToast("SSH接続が必要です", true);
            return;
        }

        TerminalSession session = getCurrentSession();
        if (session == null) {
            showToast("アクティブなセッションがありません", true);
            return;
        }

        if (!EvenG1Manager.getInstance().isConnected()) {
            showToast("ARグラスが接続されていません", true);
            return;
        }

        // Check if already watching
        if (mAutoArSyncManager != null && mAutoArSyncManager.isWatching()) {
            new AlertDialog.Builder(this)
                    .setTitle("監視中")
                    .setMessage("現在ファイル変更を監視中です。\n停止しますか？")
                    .setPositiveButton("停止", (d, w) -> {
                        mAutoArSyncManager.stopWatching();
                        showToast("監視を停止しました", false);
                    })
                    .setNegativeButton("継続", null)
                    .show();
            return;
        }

        // Create input dialog
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Claudeに送るクエリを入力...");
        input.setMinLines(3);
        input.setGravity(android.view.Gravity.TOP);

        // Add padding
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(padding, padding / 2, padding, 0);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("🤖 Claude→AR 自動同期")
                .setMessage("クエリを送信後、Claudeの応答を自動でARに表示します\n(最大10分間監視)")
                .setView(container)
                .setPositiveButton("送信→AR", (dialog, which) -> {
                    String query = input.getText().toString().trim();
                    if (query.isEmpty()) {
                        showToast("クエリを入力してください", true);
                        return;
                    }
                    startAutoArSync(session, query);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Starts automatic AR sync process.
     */
    private void startAutoArSync(TerminalSession session, String query) {
        mAutoArSyncManager = new AutoArSyncManager(mCurrentSshHost, CLAUDE_SERVER_PORT);

        mAutoArSyncManager.startWatching(session, query, new AutoArSyncManager.SyncCallback() {
            @Override
            public void onWatchStarted() {
                showToast("クエリ送信、監視開始...", false);
            }

            @Override
            public void onPolling(int count, int remainingSeconds) {
                // Update status (optional: could show in a persistent notification)
                android.util.Log.d("AutoArSync", "Polling #" + count + ", remaining: " + remainingSeconds + "s");
            }

            @Override
            public void onFileChanged() {
                showToast("ファイル更新検出！ダウンロード中...", false);
                // When file changed successfully, we usually want to show the content.
                // The onSyncComplete will handle the Turso sync and then call
                // showAutoArPagerDialog.
            }

            @Override
            public void onStatusChanged(String status) {
                android.util.Log.d("AutoArSync", "Status: " + status);
            }

            @Override
            public void onSyncComplete(String filePath, String content) {
                showToast("AR同期中...", false);
                // Sync to Turso (Always sync)
                if (mTursoSyncManager != null) {
                    mTursoSyncManager.sync(filePath, () -> {
                        // After sync completes, fetch latest message from Turso
                        mTursoSyncManager.getLastAssistantMessage(new java.io.File(filePath).getName(),
                                new com.termux.app.turso.TursoSyncManager.MessageCallback() {
                                    @Override
                                    public void onResult(String message) {
                                        runOnUiThread(() -> {
                                            if (message != null && !message.trim().isEmpty()) {
                                                showAutoArPagerDialog(message);
                                                showToast("AR表示完了 (from Turso)", false);
                                            } else {
                                                android.util.Log.d("AutoArSync", "Turso returned empty message");
                                            }
                                        });
                                    }

                                    @Override
                                    public void onError(String error) {
                                        runOnUiThread(() -> showToast("Turso取得エラー: " + error, true));
                                    }
                                });
                    });
                }
            }

            @Override
            public void onTimeout() {
                showToast("タイムアウト（10分経過）", true);
            }

            @Override
            public void onError(String message) {
                showToast("エラー: " + message, true);
            }
        });
    }

    /**
     * Shows AR pager dialog after auto sync completes.
     */
    private void showAutoArPagerDialog(String content) {
        android.util.Log.d("AutoArSync", "showAutoArPagerDialog called with: "
                + (content != null ? content.substring(0, Math.min(20, content.length())) : "null"));
        // Reuse existing dialog if visible
        if (mActiveArDialog != null && mActiveArDialog.isShowing()) {
            android.util.Log.d("AutoArSync", "Reusing existing dialog: " + mActiveArDialog);
            if (mCurrentArPager != null) {
                mCurrentArPager.updateText(content);
                mCurrentArPager.sendCurrentPage(null); // Send new content immediately
                updateArDialogUI(mActiveArDialog, mCurrentArPager);
            }
            return;
        }
        android.util.Log.d("AutoArSync", "Creating NEW dialog");

        ArTextPager pager = new ArTextPager(
                EvenG1Manager.getInstance(),
                new Handler(Looper.getMainLooper()),
                content);
        showArControllerDialog(pager);
    }

    /**
     * Opens AR View for the latest conversation history with Turso synchronization.
     */
    /**
     * Opens AR View after allowing user to select a session from the server.
     */
    private void openArViewIfAvailable() {
        if (mCurrentSshHost == null || mCurrentSshHost.isEmpty()) {
            showToast("SSHホストが設定されていません", true);
            return;
        }

        showToast("セッション一覧を取得中...", false);

        ClaudeHistoryHttpClient client = new ClaudeHistoryHttpClient(mCurrentSshHost, CLAUDE_SERVER_PORT);
        client.listCurrentFiles(new ClaudeHistoryHttpClient.FileListCallback() {
            @Override
            public void onSuccess(String cwd, String project,
                    java.util.List<ClaudeHistoryHttpClient.RemoteFile> files) {
                runOnUiThread(() -> {
                    if (files.isEmpty()) {
                        showToast("会話履歴が見つかりません", true);
                        return;
                    }
                    showRemoteConversationListDialog(files, client);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showToast("一覧取得エラー: " + message, true));
            }
        });
    }

    private void showRemoteConversationListDialog(java.util.List<ClaudeHistoryHttpClient.RemoteFile> files,
            ClaudeHistoryHttpClient client) {
        String[] fileNames = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            fileNames[i] = "📄 " + files.get(i).displayName;
        }

        new AlertDialog.Builder(this)
                .setTitle("AR表示するセッションを選択 (" + files.size() + ")")
                .setItems(fileNames, (dialog, which) -> {
                    ClaudeHistoryHttpClient.RemoteFile selected = files.get(which);
                    showToast(selected.name + " をダウンロード中...", false);
                    client.downloadFile(selected, new ClaudeHistoryHttpClient.DownloadCallback() {
                        @Override
                        public void onSuccess(String localPath) {
                            processAndShowArView(localPath);
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> showToast("ダウンロードエラー: " + message, true));
                        }
                    });
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void processAndShowArView(String localPath) {
        // Get latest message
        com.termux.app.claude.ClaudeChatParser.AgentComment comment = com.termux.app.claude.ClaudeChatParser
                .getLatestAgentComment(localPath);

        if (comment != null && !comment.text.trim().isEmpty()) {
            runOnUiThread(() -> {
                // Sync to Turso (Always sync)
                if (mTursoSyncManager != null) {
                    mTursoSyncManager.sync(localPath, () -> {
                        // After sync completes, fetch FULL history
                        mTursoSyncManager.getConversationHistory(new java.io.File(localPath).getName(),
                                new com.termux.app.turso.TursoSyncManager.HistoryCallback() {
                                    @Override
                                    public void onResult(java.util.List<String> historyItems) {
                                        runOnUiThread(() -> {
                                            if (historyItems != null && !historyItems.isEmpty()) {
                                                mCurrentConversationHistory = historyItems;
                                                mCurrentHistoryIndex = historyItems.size() - 1;
                                                String latestMessage = historyItems.get(mCurrentHistoryIndex);

                                                showAutoArPagerDialog(latestMessage);
                                                showToast("AR表示完了 (履歴: " + historyItems.size() + "件)", false);
                                            } else {
                                                // Fallback to local parsing
                                                mCurrentConversationHistory = null;
                                                mCurrentHistoryIndex = -1;
                                                showAutoArPagerDialog(comment.text);
                                                showToast("AR表示完了 (ローカル)", false);
                                            }
                                        });
                                    }

                                    @Override
                                    public void onError(String error) {
                                        runOnUiThread(() -> {
                                            showToast("Turso履歴取得エラー: " + error, true);
                                            // Fallback to local parsing
                                            mCurrentConversationHistory = null;
                                            mCurrentHistoryIndex = -1;
                                            showAutoArPagerDialog(comment.text);
                                        });
                                    }
                                });
                    });
                } else {
                    mCurrentConversationHistory = null;
                    mCurrentHistoryIndex = -1;
                    showAutoArPagerDialog(comment.text);
                }
            });
        } else {
            runOnUiThread(() -> showToast("履歴が見つかりません", true));
        }
    }

    /**
     * Shows dialog to connect to HTTP server.
     * Uses SSH host automatically if available.
     */
    private void showHttpServerDialog(ConversationSyncManager syncManager) {
        if (mCurrentSshHost == null || mCurrentSshHost.isEmpty()) {
            showToast("SSH接続が必要です。SSHボタンで接続してください。", true);
            return;
        }

        connectToClaudeServer(mCurrentSshHost);
    }

    /**
     * Restart Claude history server on SSH host.
     */
    private void restartClaudeServer() {
        if (mCurrentSshHost == null || mCurrentSshHost.isEmpty()) {
            showToast("SSH接続が必要です", true);
            return;
        }

        TerminalSession session = getCurrentSession();
        if (session == null) {
            showToast("アクティブなセッションがありません", true);
            return;
        }

        // Confirm restart
        new AlertDialog.Builder(this)
                .setTitle("サーバー再起動")
                .setMessage("HTTPサーバーを再起動しますか？\n\nホスト: " + mCurrentSshHost + "\nポート: " + CLAUDE_SERVER_PORT)
                .setPositiveButton("再起動", (dialog, which) -> {
                    // Send restart command to terminal
                    String command = "pkill -f claude-history-server.py; sleep 1; nohup python3 ~/.local/bin/claude-history-server.py > /dev/null 2>&1 &\n";
                    byte[] data = command.getBytes();
                    session.write(data, 0, data.length);
                    showToast("サーバー再起動コマンドを送信しました", false);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Connect to Claude history server.
     */
    private void connectToClaudeServer(String host) {
        showToast("サーバーに接続中...", false);

        ClaudeHistoryHttpClient client = new ClaudeHistoryHttpClient(host, CLAUDE_SERVER_PORT);
        client.checkHealth(new ClaudeHistoryHttpClient.HealthCallback() {
            @Override
            public void onSuccess(String cwd) {
                showToast("接続成功!", false);
                showHttpServerOptionsDialog(client, cwd);
            }

            @Override
            public void onError(String message) {
                showToast("接続エラー: " + message, true);
            }
        });
    }

    /**
     * Connect to HTTP server and show options.
     */
    private void connectToHttpServer(String host, int port) {
        showToast("サーバーに接続中...", false);

        ClaudeHistoryHttpClient client = new ClaudeHistoryHttpClient(host, port);
        client.checkHealth(new ClaudeHistoryHttpClient.HealthCallback() {
            @Override
            public void onSuccess(String cwd) {
                showHttpServerOptionsDialog(client, cwd);
            }

            @Override
            public void onError(String message) {
                showToast("接続エラー: " + message, true);
            }
        });
    }

    /**
     * Show options after connecting to HTTP server.
     */
    private void showHttpServerOptionsDialog(ClaudeHistoryHttpClient client, String serverCwd) {
        new AlertDialog.Builder(this)
                .setTitle("HTTP接続成功\n" + serverCwd)
                .setItems(new String[] {
                        "🚀 最新ファイルをダウンロード",
                        "📋 ファイル一覧を表示"
                }, (dialog, which) -> {
                    if (which == 0) {
                        downloadLatestViaHttp(client);
                    } else {
                        listFilesViaHttp(client);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Download latest file via HTTP.
     */
    private void downloadLatestViaHttp(ClaudeHistoryHttpClient client) {
        showToast("最新ファイルをダウンロード中...", false);

        client.downloadLatest(new ClaudeHistoryHttpClient.DownloadCallback() {
            @Override
            public void onSuccess(String localPath) {
                showToast("ダウンロード完了!", false);
            }

            @Override
            public void onError(String message) {
                showToast("エラー: " + message, true);
            }
        });
    }

    /**
     * List files via HTTP and show selection dialog.
     */
    private void listFilesViaHttp(ClaudeHistoryHttpClient client) {
        showToast("ファイル一覧を取得中...", false);

        client.listCurrentFiles(new ClaudeHistoryHttpClient.FileListCallback() {
            @Override
            public void onSuccess(String cwd, String project, List<ClaudeHistoryHttpClient.RemoteFile> files) {
                if (files.isEmpty()) {
                    showToast("ファイルが見つかりません", true);
                    return;
                }
                showHttpFileListDialog(client, files);
            }

            @Override
            public void onError(String message) {
                showToast("エラー: " + message, true);
            }
        });
    }

    /**
     * Show file list dialog for HTTP downloads.
     */
    private void showHttpFileListDialog(ClaudeHistoryHttpClient client,
            List<ClaudeHistoryHttpClient.RemoteFile> files) {
        String[] fileNames = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            fileNames[i] = "💬 " + files.get(i).displayName;
        }

        new AlertDialog.Builder(this)
                .setTitle("会話ファイル (" + files.size() + "件)")
                .setItems(fileNames, (dialog, which) -> {
                    ClaudeHistoryHttpClient.RemoteFile selected = files.get(which);
                    downloadFileViaHttp(client, selected);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Download specific file via HTTP.
     */
    private void downloadFileViaHttp(ClaudeHistoryHttpClient client, ClaudeHistoryHttpClient.RemoteFile file) {
        showToast("ダウンロード中: " + file.name, false);

        client.downloadFile(file, new ClaudeHistoryHttpClient.DownloadCallback() {
            @Override
            public void onSuccess(String localPath) {
                showToast("ダウンロード完了!", false);
            }

            @Override
            public void onError(String message) {
                showToast("エラー: " + message, true);
            }
        });
    }

    /**
     * Shows a dialog with the list of conversation files (remote).
     * User can select a file to download.
     */
    private void showConversationListDialog(List<ConversationSyncManager.ConversationFile> conversations,
            ConversationSyncManager syncManager) {
        // Format conversation names with icons
        String[] convNames = new String[conversations.size()];
        for (int i = 0; i < conversations.size(); i++) {
            convNames[i] = "💬 " + conversations.get(i).displayName;
        }

        new AlertDialog.Builder(this)
                .setTitle("Claude 会話履歴 (" + conversations.size() + ")")
                .setItems(convNames, (dialog, which) -> {
                    ConversationSyncManager.ConversationFile selected = conversations.get(which);

                    // Show action options
                    new AlertDialog.Builder(TermuxActivity.this)
                            .setTitle(selected.filename)
                            .setItems(new String[] { "ダウンロードして保存" }, (subDialog, subWhich) -> {
                                downloadConversation(selected, syncManager);
                            })
                            .show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Shows a dialog with the list of local conversation files.
     * User can select a file to send to AR glasses.
     */
    private void showLocalConversationListDialog(List<ConversationSyncManager.ConversationFile> conversations) {
        // Format conversation names with icons
        String[] convNames = new String[conversations.size()];
        for (int i = 0; i < conversations.size(); i++) {
            convNames[i] = "💬 " + conversations.get(i).displayName;
        }

        new AlertDialog.Builder(this)
                .setTitle("ローカルClaude履歴 (" + conversations.size() + ")")
                .setMessage("AR表示機能は削除されました。\nClaude→AR自動同期を使用してください。")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showArControllerDialog(ArTextPager pager) {
        try {
            if (pager == null || pager.getTotalPages() == 0) {
                showToast("表示するコンテンツがありません", true);
                return;
            }

            // mCurrentArPagerを保持（再オープン可能にするため）
            mCurrentArPager = pager;

            // Start Media Session for background control
            mMediaControlManager.start(new MediaControlManager.MediaControlCallback() {
                @Override
                public void onNext() {
                    runOnUiThread(() -> {
                        try {
                            pager.nextPage(null);
                            updateArDialogUI(mActiveArDialog, pager);
                        } catch (Exception e) {
                            showToast("次ページエラー: " + e.getMessage(), true);
                        }
                    });
                }

                @Override
                public void onPrevious() {
                    runOnUiThread(() -> {
                        try {
                            pager.prevPage(null);
                            updateArDialogUI(mActiveArDialog, pager);
                        } catch (Exception e) {
                            showToast("前ページエラー: " + e.getMessage(), true);
                        }
                    });
                }
            });

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Custom Layout Construction
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            layout.setPadding(padding, padding, padding, padding);

            // Text View in ScrollView
            android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
            android.widget.TextView textView = new android.widget.TextView(this);
            textView.setTextSize(16);
            textView.setTag("msg_text"); // Tag for finding later
            scrollView.addView(textView);

            android.widget.LinearLayout.LayoutParams scrollParams = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    0, 1.0f); // Weight 1 to take available space
            layout.addView(scrollView, scrollParams);

            // Buttons Layout
            android.widget.LinearLayout btnLayout = new android.widget.LinearLayout(this);
            btnLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            btnLayout.setGravity(android.view.Gravity.CENTER);
            btnLayout.setPadding(0, padding, 0, 0);

            // Previous Turn Button
            android.widget.Button btnPrevTurn = new android.widget.Button(this);
            btnPrevTurn.setText("<< 前ログ");
            btnPrevTurn.setOnClickListener(v -> navigateTurn(-1));
            btnPrevTurn.setEnabled(mCurrentConversationHistory != null && mCurrentHistoryIndex > 0);
            btnPrevTurn.setTag("btn_prev_turn");
            btnLayout.addView(btnPrevTurn);

            // Prev Page Button
            android.widget.Button btnPrevPage = new android.widget.Button(this);
            btnPrevPage.setText("< 前項");
            btnPrevPage.setOnClickListener(v -> {
                pager.prevPage(null);
                updateArDialogUI(mActiveArDialog, pager);
            });
            btnLayout.addView(btnPrevPage);

            // Next Page Button
            android.widget.Button btnNextPage = new android.widget.Button(this);
            btnNextPage.setText("次項 >");
            btnNextPage.setOnClickListener(v -> {
                pager.nextPage(null);
                updateArDialogUI(mActiveArDialog, pager);
            });
            btnLayout.addView(btnNextPage);

            // Next Turn Button
            android.widget.Button btnNextTurn = new android.widget.Button(this);
            btnNextTurn.setText("次ログ >>");
            btnNextTurn.setOnClickListener(v -> navigateTurn(1));
            btnNextTurn.setEnabled(mCurrentConversationHistory != null
                    && mCurrentHistoryIndex < (mCurrentConversationHistory.size() - 1));
            btnNextTurn.setTag("btn_next_turn");
            btnLayout.addView(btnNextTurn);

            layout.addView(btnLayout);

            builder.setView(layout);
            builder.setTitle("AR View (" + pager.getCurrentPageNum() + "/" + pager.getTotalPages() + ")");
            builder.setNeutralButton("閉じる", (dialog, which) -> {
                dialog.dismiss();
            });

            builder.setOnDismissListener(dialog -> {
                mMediaControlManager.stop();
                mActiveArDialog = null;
            });

            AlertDialog dialog = builder.create();
            mActiveArDialog = dialog; // Keep reference

            // Initial UI Update
            textView.setText("現在の表示:\n\n" + pager.getCurrentPageText());

            dialog.show();

        } catch (Exception e) {
            showToast("ダイアログ表示エラー: " + e.getMessage(), true);
        }
    }

    private void navigateTurn(int direction) {
        if (mCurrentConversationHistory == null)
            return;

        int newIndex = mCurrentHistoryIndex + direction;
        if (newIndex >= 0 && newIndex < mCurrentConversationHistory.size()) {
            mCurrentHistoryIndex = newIndex;
            String newText = mCurrentConversationHistory.get(newIndex);
            showAutoArPagerDialog(newText);
        }
    }

    private void updateArDialogUI(AlertDialog dialog, ArTextPager pager) {
        if (dialog == null || !dialog.isShowing())
            return;

        dialog.setTitle("AR View (" + pager.getCurrentPageNum() + "/" + pager.getTotalPages() + ")");

        android.widget.TextView textView = dialog
                .findViewById(dialog.getContext().getResources().getIdentifier("msg_text", "id", getPackageName()));
        // Since we didn't use XML ID, we iterate user view helper/tags or just traverse
        // Simpler: findViewWithTag
        android.view.View rootView = dialog.findViewById(android.R.id.custom); // This gets the FrameLayout wrapping our
                                                                               // view
        if (rootView != null) {
            android.widget.TextView tv = rootView.findViewWithTag("msg_text");
            if (tv != null) {
                tv.setText("現在の表示:\n\n" + pager.getCurrentPageText());
            }

            android.view.View btnPrevTurn = rootView.findViewWithTag("btn_prev_turn");
            if (btnPrevTurn != null) {
                btnPrevTurn.setEnabled(mCurrentConversationHistory != null && mCurrentHistoryIndex > 0);
            }

            android.view.View btnNextTurn = rootView.findViewWithTag("btn_next_turn");
            if (btnNextTurn != null) {
                btnNextTurn.setEnabled(mCurrentConversationHistory != null
                        && mCurrentHistoryIndex < (mCurrentConversationHistory.size() - 1));
            }
        }
    }

    /**
     * Downloads the selected conversation file.
     */
    private void downloadConversation(ConversationSyncManager.ConversationFile conversation,
            ConversationSyncManager syncManager) {
        TerminalSession session = getCurrentSession();
        if (session == null) {
            showToast("アクティブなセッションがありません", true);
            return;
        }

        showToast("ダウンロード中: " + conversation.filename, false);

        syncManager.downloadFile(session, conversation, new ConversationSyncManager.FileDownloadCallback() {
            @Override
            public void onDownloadComplete(String localPath) {
                showToast("保存完了: " + localPath, false);
            }

            @Override
            public void onError(String message) {
                showToast("エラー: " + message, true);
            }
        });
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
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
        if (text == null || text.isEmpty())
            return;
        if (mLastToast != null)
            mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null)
            return;

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
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE,
                getResources().getString(R.string.action_kill_process, getCurrentSession().getPid()))
                .setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on)
                .setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
        menu.add(Menu.NONE, CONTEXT_MENU_AI_ASSISTANT_ID, Menu.NONE, "AI Assistant");
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
            case CONTEXT_MENU_AI_ASSISTANT_ID:
                showAiInteractionDialog();
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
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss
        // instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null)
            return;

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
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
                TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                    .setPositiveButton(R.string.action_styling_install,
                            (dialog, which) -> ActivityUtils.startActivity(this,
                                    new Intent(Intent.ACTION_VIEW,
                                            Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
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
     * For processes to access primary external storage (/sdcard,
     * /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or
     * MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android
     * 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if (PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
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
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode
                + ", data: " + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "
                + Arrays.toString(permissions) + ", grantResults: " + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
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
        if (intent == null)
            return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;

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
        // It will destroy the activity, including all stored variables and views, and
        // onCreate()
        // will be called again. Extra keys input text, terminal sessions and
        // transcripts will be preserved.
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

    /**
     * Shows the AI Interaction Dialog.
     */
    public void showAiInteractionDialog() {
        new com.termux.app.ai.AiInteractionDialog(this, mAiSettingsManager, mLlmClient).show();
    }

    /**
     * Sends AI response string to AR glasses and shows the controller dialog.
     */
    public void sendAiResponseToAr(String responseText) {
        if (responseText == null || responseText.isEmpty())
            return;

        runOnUiThread(() -> {
            boolean isConnected = com.termux.app.eveng1.EvenG1Manager.getInstance().isConnected();
            if (!isConnected) {
                showToast("AR Not Connected, but creating pager.", true);
            }

            // Create Pager
            com.termux.app.eveng1.EvenG1Manager manager = com.termux.app.eveng1.EvenG1Manager.getInstance();
            android.os.Handler handler = new android.os.Handler();
            com.termux.app.eveng1.ArTextPager pager = new com.termux.app.eveng1.ArTextPager(manager, handler,
                    responseText);

            // Send first page if connected
            if (isConnected) {
                pager.sendCurrentPage(new com.termux.app.eveng1.EvenG1Protocol.TextSendCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(String error) {
                        showToast("AR Send Failed: " + error, true);
                    }
                });
            }

            // Show Controller
            showArControllerDialog(pager);
        });
    }

}
