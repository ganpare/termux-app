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
 *    a. Get current latest file timestamp from HTTP server
 *    b. Send query to terminal
 *    c. Start polling (every 5 seconds)
 *    d. Show "考え中..." on AR glasses (moving position each poll)
 *    e. When timestamp changes → download new file → display on AR
 *    f. Timeout after 10 minutes
 */
public class AutoArSyncManager {

    private static final int POLL_INTERVAL_MS = 5000; // 5 seconds
    private static final int MAX_POLL_TIME_MS = 10 * 60 * 1000; // 10 minutes
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isWatching = new AtomicBoolean(false);

    private String serverHost;
    private int serverPort;
    private double initialMtime = 0;
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
        void onPolling(int count, int maxSeconds);
        void onFileChanged();
        void onSyncComplete(String content);
        void onTimeout();
        void onError(String message);
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
     * @param session Terminal session to send query to
     * @param query Query text to send
     * @param callback Callback for sync events
     */
    public void startWatching(TerminalSession session, String query, SyncCallback callback) {
        if (isWatching.get()) {
            callback.onError("既に監視中です");
            return;
        }

        isWatching.set(true);
        pollCount = 0;
        watchStartTime = System.currentTimeMillis();

        executor.execute(() -> {
            try {
                // Step 1: Get initial timestamp
                JSONObject info = fetchLatestInfo();
                if (info == null) {
                    postError(callback, "サーバーに接続できません");
                    isWatching.set(false);
                    return;
                }
                
                initialMtime = info.getDouble("mtime");
                
                // Step 2: Send query to terminal
                mainHandler.post(() -> {
                    String fullQuery = query + "\n";
                    byte[] data = fullQuery.getBytes();
                    session.write(data, 0, data.length);
                    callback.onWatchStarted();
                });

                // Step 3: Start polling
                pollForChanges(callback);

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

        // Show thinking animation on AR
        showThinkingOnAr(pollCount);

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
                    // File changed!
                    isWatching.set(false);
                    mainHandler.post(callback::onFileChanged);
                    
                    // Download and display
                    downloadAndDisplay(callback);
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
                public void onSuccess() {}
                @Override
                public void onFailure(String error) {}
            },
            EvenG1Constants.NEW_TEXT_SCREEN,
            pageNum,
            maxPolls
        );
    }

    private void downloadAndDisplay(SyncCallback callback) {
        executor.execute(() -> {
            try {
                // Download latest file
                ClaudeHistoryHttpClient client = new ClaudeHistoryHttpClient(serverHost, serverPort);
                client.downloadLatest(new ClaudeHistoryHttpClient.DownloadCallback() {
                    @Override
                    public void onSuccess(String localPath) {
                        // Parse and get latest comment
                        List<String> comments = ClaudeChatParser.getAgentComments(localPath);
                        if (comments.isEmpty()) {
                            postError(callback, "コメントが見つかりません");
                            return;
                        }

                        String latestComment = comments.get(comments.size() - 1);
                        
                        // Display on AR
                        displayOnAr(latestComment, callback);
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

    private void displayOnAr(String content, SyncCallback callback) {
        if (!EvenG1Manager.getInstance().isConnected()) {
            postError(callback, "ARグラスが接続されていません");
            return;
        }

        // Create pager and display
        com.termux.app.eveng1.ArTextPager pager = new com.termux.app.eveng1.ArTextPager(
            EvenG1Manager.getInstance(),
            mainHandler,
            content
        );

        pager.sendCurrentPage(new EvenG1Protocol.TextSendCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> callback.onSyncComplete(content));
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
