package com.termux.app.claude;

import android.os.Handler;
import android.os.Looper;

import com.termux.app.eveng1.EvenG1Manager;
import com.termux.app.eveng1.EvenG1Protocol;
import com.termux.app.eveng1.EvenG1Constants;
import com.termux.terminal.TerminalSession;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages automatic AR synchronization.
 * 
 * Flow:
 * 1. User inputs query in dedicated input field
 * 2. On "Send→AR" button press:
 * a. Get current latest file timestamp from HTTP server
 * b. Send query to terminal
 * c. Start polling (every 5 seconds)
 * d. Show "考え中..." on AR glasses (moving position each poll)
 * e. When timestamp changes → download new file → display on AR
 * f. Timeout after 10 minutes
 */
public class AutoArSyncManager {

    private static final int POLL_INTERVAL_MS = 5000; // 5 seconds
    private static final int MAX_POLL_TIME_MS = 10 * 60 * 1000; // 10 minutes
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isWatching = new AtomicBoolean(false);
    private boolean hasDisplayedContent = false;

    private String serverHost;
    private int serverPort;
    private double initialMtime = 0;
    private String initialLatestComment = null; // 初期の最新assistantメッセージ
    private long watchStartTime = 0;
    private int pollCount = 0;

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

        void onFileChanged();

        void onStatusChanged(String status);

        void onSyncComplete(String filePath, String content);

        void onError(String message);

        void onTimeout();

        void onPolling(int count, int maxSeconds);
    }

    public AutoArSyncManager(String host, int port) {
        this.serverHost = host;
        this.serverPort = port;
    }

    /**
     * Check if currently watching for changes.
     */
    public boolean isWatching() {
        return isWatching.get();
    }

    /**
     * Start watching for file changes and sync to AR.
     * 
     * @param session  Terminal session to send query to
     * @param query    Query text to send
     * @param callback Callback for sync events
     */
    public void startWatching(TerminalSession session, String query, SyncCallback callback) {
        if (isWatching.get()) {
            callback.onError("既に監視中です");
            return;
        }

        isWatching.set(true);
        isWatching.set(true);
        hasDisplayedContent = false;
        pollCount = 0;
        watchStartTime = System.currentTimeMillis();

        executor.execute(() -> {
            try {
                // Step 1: Get initial timestamp and latest assistant comment
                JSONObject info = fetchLatestInfo();
                if (info == null) {
                    postError(callback, "サーバーに接続できません");
                    isWatching.set(false);
                    return;
                }

                initialMtime = info.getDouble("mtime");

                // Get initial latest assistant comment to compare later
                ClaudeHistoryHttpClient client = new ClaudeHistoryHttpClient(serverHost, serverPort);
                client.downloadLatest(new ClaudeHistoryHttpClient.DownloadCallback() {
                    @Override
                    public void onSuccess(String localPath) {
                        ClaudeChatParser.AgentComment comment = ClaudeChatParser.getLatestAgentComment(localPath);
                        initialLatestComment = comment != null ? comment.text : null;

                        android.util.Log.d("AutoArSync", "Initial latest comment: " +
                                (initialLatestComment != null
                                        ? initialLatestComment.substring(0, Math.min(50, initialLatestComment.length()))
                                                + "..."
                                        : "null"));

                        // Step 2: Send query to terminal
                        mainHandler.post(() -> {
                            String fullQuery = query + "\n";
                            byte[] data = fullQuery.getBytes();
                            session.write(data, 0, data.length);
                            callback.onWatchStarted();
                        });

                        // Step 3: Start polling
                        callback.onStatusChanged("サーバー監視を開始します...");
                        pollForChanges(callback);
                    }

                    @Override
                    public void onError(String message) {
                        // Even if we can't get initial comment, start polling anyway
                        android.util.Log.w("AutoArSync", "Failed to get initial comment: " + message);
                        initialLatestComment = null;

                        mainHandler.post(() -> {
                            String fullQuery = query + "\n";
                            byte[] data = fullQuery.getBytes();
                            session.write(data, 0, data.length);
                            callback.onWatchStarted();
                        });

                        pollForChanges(callback);
                    }
                });

            } catch (Exception e) {
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
     * Notify that content has been displayed externally (e.g. manual AR view).
     * This stops the "Thinking..." animation.
     */
    public void notifyContentDisplayed() {
        this.hasDisplayedContent = true;
    }

    private void pollForChanges(SyncCallback callback) {
        if (!isWatching.get()) {
            return;
        }

        // Check timeout
        long elapsed = System.currentTimeMillis() - watchStartTime;
        if (elapsed >= MAX_POLL_TIME_MS) {
            isWatching.set(false);
            mainHandler.post(callback::onTimeout);
            return;
        }

        pollCount++;
        int remainingSeconds = (int) ((MAX_POLL_TIME_MS - elapsed) / 1000);

        // Notify polling progress
        final int currentPollCount = pollCount;
        mainHandler.post(() -> callback.onPolling(currentPollCount, remainingSeconds));

        // Show thinking animation on AR (only if no content displayed yet)
        if (!hasDisplayedContent) {
            showThinkingOnAr(pollCount);
        }

        executor.execute(() -> {
            try {
                JSONObject info = fetchLatestInfo();
                if (info == null) {
                    // Server error, but continue polling
                    scheduleNextPoll(callback);
                    return;
                }

                double currentMtime = info.getDouble("mtime");

                if (currentMtime > initialMtime) {
                    // File changed! Check if assistant message actually changed
                    checkAndDisplayIfNew(callback);
                } else {
                    // No change, continue polling
                    scheduleNextPoll(callback);
                }
            } catch (Exception e) {
                // Continue polling on error
                scheduleNextPoll(callback);
            }
        });
    }

    private void scheduleNextPoll(SyncCallback callback) {
        if (!isWatching.get()) {
            return;
        }

        mainHandler.postDelayed(() -> {
            executor.execute(() -> pollForChanges(callback));
        }, POLL_INTERVAL_MS);
    }

    private void showThinkingOnAr(int pollCount) {
        if (!EvenG1Manager.getInstance().isConnected()) {
            return;
        }

        // Cycle through animation frames
        String frame = THINKING_FRAMES[pollCount % THINKING_FRAMES.length];

        // Use page number to show progress (1/120 = 5sec/10min)
        int maxPolls = MAX_POLL_TIME_MS / POLL_INTERVAL_MS;
        int pageNum = Math.min(pollCount, maxPolls);

        EvenG1Protocol.sendText(
                EvenG1Manager.getInstance(),
                frame,
                mainHandler,
                new EvenG1Protocol.TextSendCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(String error) {
                    }
                },
                EvenG1Constants.NEW_TEXT_SCREEN,
                pageNum,
                maxPolls);
    }

    /**
     * Check if assistant message has changed, and display if it has.
     */
    private void checkAndDisplayIfNew(SyncCallback callback) {
        executor.execute(() -> {
            try {
                // Download latest file
                ClaudeHistoryHttpClient client = new ClaudeHistoryHttpClient(serverHost, serverPort);
                client.downloadLatest(new ClaudeHistoryHttpClient.DownloadCallback() {
                    @Override
                    public void onSuccess(String localPath) {
                        // Get latest assistant comment
                        ClaudeChatParser.AgentComment latestComment = ClaudeChatParser.getLatestAgentComment(localPath);
                        String currentLatestText = latestComment != null ? latestComment.text : null;

                        if (currentLatestText == null) {
                            // No assistant comment found, continue polling
                            android.util.Log.d("AutoArSync", "No assistant comment found, continuing to poll");
                            scheduleNextPoll(callback);
                            return;
                        }

                        // Compare with initial comment
                        if (initialLatestComment == null) {
                            // First successful fetch after failed initialization (or brand new chat).
                            // Assume this is the baseline and do NOT display yet to avoid showing old chat.
                            android.util.Log.d("AutoArSync", "Initializing baseline comment: " +
                                    (currentLatestText != null
                                            ? currentLatestText.substring(0, Math.min(20, currentLatestText.length()))
                                            : "null"));
                            initialLatestComment = currentLatestText;

                            // Continue polling for actual new changes
                            scheduleNextPoll(callback);
                            return;
                        }

                        if (!currentLatestText.equals(initialLatestComment)) {
                            // Assistant message has changed!
                            android.util.Log.d("AutoArSync", "Assistant message changed!");

                            // Check stop reason
                            boolean isFinal = "end_turn".equals(latestComment.stopReason);
                            boolean isEmpty = currentLatestText.trim().isEmpty();

                            if (!isEmpty) {
                                hasDisplayedContent = true;
                            } else {
                                android.util.Log.d("AutoArSync", "New message is empty");
                            }

                            if (isFinal) {
                                isWatching.set(false);
                                mainHandler.post(callback::onFileChanged); // Notify finish
                            } else {
                                // Update tracker and continue polling
                                initialLatestComment = currentLatestText;
                                // Update initialMtime
                                try {
                                    JSONObject info = fetchLatestInfo();
                                    if (info != null) {
                                        initialMtime = info.getDouble("mtime");
                                    }
                                } catch (Exception e) {
                                }

                                // Notify about update so we can sync to Turso
                                mainHandler.post(() -> callback.onSyncComplete(localPath, currentLatestText));

                                scheduleNextPoll(callback);
                            }

                        } else {
                            // Assistant message hasn't changed (probably just user message added)
                            android.util.Log.d("AutoArSync", "Assistant message unchanged, continuing to poll");
                            // Update initialMtime to avoid re-checking the same change
                            try {
                                JSONObject info = fetchLatestInfo();
                                if (info != null) {
                                    initialMtime = info.getDouble("mtime");
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                            scheduleNextPoll(callback);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Continue polling on download error
                        android.util.Log.w("AutoArSync", "Download error: " + message);
                        scheduleNextPoll(callback);
                    }
                });
            } catch (Exception e) {
                // Continue polling on error
                android.util.Log.w("AutoArSync", "Error checking for new message: " + e.getMessage());
                scheduleNextPoll(callback);
            }
        });
    }

    public void downloadAndDisplay(SyncCallback callback) {
        executor.execute(() -> {
            try {
                // Download latest file
                ClaudeHistoryHttpClient client = new ClaudeHistoryHttpClient(serverHost, serverPort);
                client.downloadLatest(new ClaudeHistoryHttpClient.DownloadCallback() {
                    @Override
                    public void onSuccess(String localPath) {
                        // Parse and get latest comment
                        List<ClaudeChatParser.AgentComment> comments = ClaudeChatParser.getAgentComments(localPath);
                        if (comments.isEmpty()) {
                            postError(callback, "コメントが見つかりません");
                            return;
                        }

                        ClaudeChatParser.AgentComment latestComment = comments.get(comments.size() - 1);

                        // Notify sync complete (TermuxActivity will handle sync to Turso and AR
                        // display)
                        mainHandler.post(() -> callback.onSyncComplete(localPath, latestComment.text));
                    }

                    @Override
                    public void onError(String message) {
                        postError(callback, "ダウンロードエラー: " + message);
                    }
                });
            } catch (Exception e) {
                postError(callback, "エラー: " + e.getMessage());
            }
        });
    }

    private void displayOnAr(String localPath, String content, SyncCallback callback) {
        if (!EvenG1Manager.getInstance().isConnected()) {
            postError(callback, "ARグラスが接続されていません");
            return;
        }

        // Create pager and display
        com.termux.app.eveng1.ArTextPager pager = new com.termux.app.eveng1.ArTextPager(
                EvenG1Manager.getInstance(),
                mainHandler,
                content);

        pager.sendCurrentPage(new EvenG1Protocol.TextSendCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> callback.onSyncComplete(localPath, content));
            }

            @Override
            public void onFailure(String error) {
                postError(callback, "AR送信エラー: " + error);
            }

        });
    }

    private JSONObject fetchLatestInfo() {
        try {
            URL url = new URL("http://" + serverHost + ":" + serverPort + "/api/latest-info");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");

            try {
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    return null;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                return new JSONObject(sb.toString());
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void postError(SyncCallback callback, String message) {
        isWatching.set(false);
        mainHandler.post(() -> callback.onError(message));
    }
}
