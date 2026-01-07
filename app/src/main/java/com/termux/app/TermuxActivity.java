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
    }

    /**
     * Set up custom commands button.
     */
    private void setCustomCommandsButton() {
        View customButton = findViewById(R.id.customCommandsButton);
        if (customButton == null)
            return;

        customButton.setOnClickListener(v -> {
            showCustomCommandsDialog();
        });
    }

    /**
     * Show dialog with custom commands list organized by folders.
     */
    private void showCustomCommandsDialog() {
        List<CommandFolder> folders = mCustomCommandManager.getAllFolders();
        List<CustomCommand> rootCommands = mCustomCommandManager.getCommandsInFolder(null);

        // Build display items
        List<String> displayItems = new ArrayList<>();
        List<Object> dataItems = new ArrayList<>(); // Store folder/command objects

        // Menu options
        displayItems.add("➕ 新規コマンド");
        dataItems.add("NEW_COMMAND");
        displayItems.add("📁 新規フォルダ");
        dataItems.add("NEW_FOLDER");
        displayItems.add("📤 エクスポート");
        dataItems.add("EXPORT");
        displayItems.add("📥 インポート");
        dataItems.add("IMPORT");

        // Folders
        for (CommandFolder folder : folders) {
            int count = mCustomCommandManager.getCommandCountInFolder(folder.getId());
            displayItems.add("📁 " + folder.getName() + " (" + count + ")");
            dataItems.add(folder);
        }

        // Root-level commands
        for (CustomCommand cmd : rootCommands) {
            displayItems.add("📄 " + cmd.getName());
            dataItems.add(cmd);
        }

        int totalCount = mCustomCommandManager.getAllCommands().size();
        String[] items = displayItems.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("カスタムコマンド (" + totalCount + ")")
                .setItems(items, (dialog, which) -> {
                    Object selected = dataItems.get(which);
                    if ("NEW_COMMAND".equals(selected)) {
                        showAddEditCommandDialog(null, null);
                    } else if ("NEW_FOLDER".equals(selected)) {
                        showAddFolderDialog();
                    } else if ("EXPORT".equals(selected)) {
                        exportCustomCommands();
                    } else if ("IMPORT".equals(selected)) {
                        importCustomCommands();
                    } else if (selected instanceof CommandFolder) {
                        showFolderContentsDialog((CommandFolder) selected);
                    } else if (selected instanceof CustomCommand) {
                        showCommandActionsDialog((CustomCommand) selected, null);
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    /**
     * Show contents of a folder.
     */
    private void showFolderContentsDialog(CommandFolder folder) {
        List<CustomCommand> commands = mCustomCommandManager.getCommandsInFolder(folder.getId());

        List<String> displayItems = new ArrayList<>();
        displayItems.add("➕ このフォルダにコマンドを追加");
        displayItems.add("✏️ フォルダ名を変更");
        displayItems.add("🗑️ フォルダを削除");

        for (CustomCommand cmd : commands) {
            displayItems.add("📄 " + cmd.getName());
        }

        String[] items = displayItems.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("📁 " + folder.getName())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showAddEditCommandDialog(null, folder.getId());
                    } else if (which == 1) {
                        showRenameFolderDialog(folder);
                    } else if (which == 2) {
                        confirmDeleteFolder(folder);
                    } else {
                        CustomCommand cmd = commands.get(which - 3);
                        showCommandActionsDialog(cmd, folder.getId());
                    }
                })
                .setNegativeButton("戻る", (d, w) -> showCustomCommandsDialog())
                .show();
    }

    /**
     * Show dialog to add a new folder.
     */
    private void showAddFolderDialog() {
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
                        showCustomCommandsDialog();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Show dialog to rename a folder.
     */
    private void showRenameFolderDialog(CommandFolder folder) {
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
                        showCustomCommandsDialog();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Confirm before deleting a folder.
     */
    private void confirmDeleteFolder(CommandFolder folder) {
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
                            showCustomCommandsDialog();
                        } else if (which == 1) {
                            mCustomCommandManager.deleteFolder(folder.getId(), true);
                            showToast("フォルダとコマンドを削除", false);
                            showCustomCommandsDialog();
                        }
                    } else {
                        if (which == 0) {
                            mCustomCommandManager.deleteFolder(folder.getId(), false);
                            showToast("フォルダを削除", false);
                            showCustomCommandsDialog();
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
    private void showAddEditCommandDialog(CustomCommand existing, String folderId) {
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
                })
                .setNegativeButton("キャンセル", null);

        builder.show();
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

            dirNavManager.listDirectories(session, new DirectoryNavigationManager.DirectoryListCallback() {
                @Override
                public void onDirectoriesFound(List<String> directories) {
                    showDirectoryNavigationDialog(directories, dirNavManager);
                }

                @Override
                public void onError(String message) {
                    showToast(message, true);
                }
            });
        });
    }

    /**
     * Shows a dialog with the list of directories.
     * User can select a directory to navigate to.
     */
    private void showDirectoryNavigationDialog(List<String> directories, DirectoryNavigationManager dirNavManager) {
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

        new AlertDialog.Builder(this)
                .setTitle("ディレクトリ選択 (" + (directories.size() - 1) + ")")
                .setItems(dirNames, (dialog, which) -> {
                    String selectedDir = directories.get(which);
                    navigateToDirectory(selectedDir, dirNavManager);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * Navigate to the selected directory by sending cd command.
     */
    private void navigateToDirectory(String dirName, DirectoryNavigationManager dirNavManager) {
        TerminalSession session = getCurrentSession();
        if (session == null) {
            showToast("アクティブなセッションがありません", true);
            return;
        }

        dirNavManager.changeDirectory(session, dirName);
        showToast("移動: " + dirName, false);
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
            TerminalSession session = getCurrentSession();
            if (session == null) {
                showToast("アクティブなセッションがありません", true);
                return;
            }

            showToast("会話履歴を取得中...", false);

            syncManager.listConversations(session, 1, new ConversationSyncManager.ConversationListCallback() {
                @Override
                public void onConversationsFound(List<ConversationSyncManager.ConversationFile> conversations) {
                    showConversationListDialog(conversations, syncManager);
                }

                @Override
                public void onError(String message) {
                    showToast(message, true);
                }
            });
        });
    }

    /**
     * Shows a dialog with the list of conversation files.
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
                    downloadConversation(selected, syncManager);
                })
                .setNegativeButton("キャンセル", null)
                .show();
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

}
