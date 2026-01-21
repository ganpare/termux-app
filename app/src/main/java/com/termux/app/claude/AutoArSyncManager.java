package com.termux.app.claude;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.termux.app.turso.TursoSyncManager;
import com.termux.terminal.TerminalSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplified AR Sync Manager that polls Turso directly.
 *
 * New architecture:
 * 1. Server-side Python script watches JSONL files and syncs to Turso
 * 2. App only polls Turso for new messages
 * 3. No file downloads or parsing on app side
 */
public class AutoArSyncManager {

    private static final String TAG = "AutoArSyncManager";
    private static final int POLL_INTERVAL_MS = 3000; // 3 seconds
    private static final int MAX_POLL_TIME_MS = 10 * 60 * 1000; // 10 minutes

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isWatching = new AtomicBoolean(false);

    private final TursoSyncManager tursoManager;
    private final Context context;

    private long watchStartTime = 0;
    private int pollCount = 0;
    private String lastDisplayedMessage = null;
    private String currentCwd = null; // Current working directory

    // Thinking animation frames
    private static final String[] THINKING_FRAMES = {
            "考え中 .",
            "考え中 ..",
            "考え中 ...",
            "考え中 ....",
            "考え中 .....",
            "考え中 ......",
    };

    /**
     * Callback interface for sync events.
     */
    public interface SyncCallback {
        void onWatchStarted();

        void onStatusChanged(String status);

        void onNewMessage(String message);

        void onError(String message);

        void onTimeout();

        void onPolling(int count, int remainingSeconds);
    }

    public AutoArSyncManager(Context context) {
        this.context = context;
        this.tursoManager = new TursoSyncManager(context);
    }

    /**
     * Check if currently watching for changes.
     */
    public boolean isWatching() {
        return isWatching.get();
    }

    /**
     * Get the last message that was displayed.
     */
    public String getLastDisplayedMessage() {
        return lastDisplayedMessage;
    }

    /**
     * Start watching for new messages from Turso.
     *
     * @param session  Terminal session to send query to
     * @param query    Query text to send (can be null/empty)
     * @param cwd      Current working directory (optional, if null will attempt to
     *                 get from session)
     * @param callback Callback for sync events
     */
    public void startWatching(TerminalSession session, String query, String cwd, SyncCallback callback) {
        if (isWatching.get()) {
            callback.onError("既に監視中です");
            return;
        }

        if (!tursoManager.isConfigured()) {
            callback.onError("Tursoが設定されていません");
            return;
        }

        // Use provided CWD, or fallback to session's CWD
        currentCwd = cwd;
        if (currentCwd == null || currentCwd.isEmpty()) {
            currentCwd = session.getCwd();
        }

        if (currentCwd == null || currentCwd.isEmpty()) {
            callback.onError("カレントディレクトリを取得できません");
            return;
        }

        Log.d(TAG, "Starting watch for CWD: " + currentCwd);

        isWatching.set(true);
        pollCount = 0;
        watchStartTime = System.currentTimeMillis();
        lastDisplayedMessage = null;

        executor.execute(() -> {
            try {
                // First, fetch the current latest message to set as baseline
                tursoManager.getLatestMessageForCwd(currentCwd, new TursoSyncManager.MessageCallback() {
                    @Override
                    public void onResult(String message) {
                        if (message != null && !message.isEmpty()) {
                            proceedWithBaseline(message);
                        } else {
                            // Fallback: Try to get global latest message if CWD specific one not found
                            Log.d(TAG, "No message found for CWD, trying global fallback");
                            tursoManager.getLastAssistantMessage(new TursoSyncManager.MessageCallback() {
                                @Override
                                public void onResult(String globalMsg) {
                                    if (globalMsg != null) {
                                        Log.d(TAG, "Global fallback message found");
                                    }
                                    proceedWithBaseline(globalMsg);
                                }

                                @Override
                                public void onError(String error) {
                                    Log.w(TAG, "Global fallback failed: " + error);
                                    proceedWithBaseline(null);
                                }
                            });
                        }
                    }

                    private void proceedWithBaseline(String baselineMsg) {
                        if (baselineMsg != null && !baselineMsg.isEmpty()) {
                            lastDisplayedMessage = baselineMsg;
                            Log.d(TAG, "Baseline message found: " +
                                    (baselineMsg.length() > 50 ? baselineMsg.substring(0, 50) + "..." : baselineMsg));
                        }

                        // Then send query and start watching
                        mainHandler.post(() -> {
                            // Send query to terminal if provided
                            if (query != null && !query.isEmpty()) {
                                byte[] data = (query + "\n").getBytes();
                                session.write(data, 0, data.length);
                            }
                            callback.onWatchStarted();

                            // Show existing content immediately if found
                            if (lastDisplayedMessage != null) {
                                callback.onNewMessage(lastDisplayedMessage);
                            }
                        });

                        // Start polling for changes (ON BACKGROUND THREAD)
                        pollForChanges(callback);
                    }

                    @Override
                    public void onError(String error) {
                        // Even if we can't get baseline, start watching
                        Log.w(TAG, "Failed to get baseline message: " + error);
                        proceedWithBaseline(null);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error starting watch: " + e.getMessage());
                postError(callback, "エラー: " + e.getMessage());
                isWatching.set(false);
            }
        });
    }

    /**
     * Stop watching for changes.
     */
    public void stopWatching() {
        isWatching.set(false);
    }

    /**
     * Poll Turso for new messages.
     */
    private void pollForChanges(SyncCallback callback) {
        while (isWatching.get()) {
            try {
                // Check timeout
                long elapsed = System.currentTimeMillis() - watchStartTime;
                if (elapsed > MAX_POLL_TIME_MS) {
                    Log.d(TAG, "Polling timeout");
                    mainHandler.post(callback::onTimeout);
                    isWatching.set(false);
                    break;
                }

                pollCount++;
                int remainingSeconds = (int) ((MAX_POLL_TIME_MS - elapsed) / 1000);

                // Update status with thinking animation
                String status = THINKING_FRAMES[pollCount % THINKING_FRAMES.length];
                mainHandler.post(() -> {
                    callback.onStatusChanged(status);
                    callback.onPolling(pollCount, remainingSeconds);
                });

                // Check Turso for new message (filtered by CWD)
                tursoManager.getLatestMessageForCwd(currentCwd, new TursoSyncManager.MessageCallback() {
                    @Override
                    public void onResult(String message) {
                        if (message != null && !message.isEmpty()) {
                            // Check if message is different from last displayed
                            if (!message.equals(lastDisplayedMessage)) {
                                Log.d(TAG, "New message detected: " +
                                        (message.length() > 50 ? message.substring(0, 50) + "..." : message));

                                lastDisplayedMessage = message;
                                mainHandler.post(() -> callback.onNewMessage(message));
                            }
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Turso poll error: " + error);
                    }
                });

                // Wait before next poll
                Thread.sleep(POLL_INTERVAL_MS);

            } catch (InterruptedException e) {
                Log.d(TAG, "Polling interrupted");
                isWatching.set(false);
                break;
            } catch (Exception e) {
                Log.e(TAG, "Polling error: " + e.getMessage());
                postError(callback, "ポーリングエラー: " + e.getMessage());
                isWatching.set(false);
                break;
            }
        }

        Log.d(TAG, "Polling stopped");
    }

    private void postError(SyncCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }
}
