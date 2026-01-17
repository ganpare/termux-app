package com.termux.app.turso;

import android.content.Context;
import android.util.Log;
import com.termux.app.ai.AiSettingsManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.json.JSONArray;
import org.json.JSONObject;

public class TursoSyncManager {
    private static final String TAG = "TursoSyncManager";
    private final TursoClient client;
    private final ExecutorService executor;

    public TursoSyncManager(Context context) {
        AiSettingsManager settings = new AiSettingsManager(context);
        String dbUrl = settings.getTursoDbUrl();
        String authToken = settings.getTursoAuthToken();
        if (!dbUrl.isEmpty() && !authToken.isEmpty()) {
            this.client = new TursoClient(dbUrl, authToken);
        } else {
            this.client = null;
        }
        this.executor = Executors.newSingleThreadExecutor();
    }

    public TursoSyncManager(String dbUrl, String authToken) {
        if (!dbUrl.isEmpty() && !authToken.isEmpty()) {
            this.client = new TursoClient(dbUrl, authToken);
        } else {
            this.client = null;
        }
        this.executor = Executors.newSingleThreadExecutor();
    }

    public boolean isConfigured() {
        return client != null;
    }

    public void initializeDb(Runnable onComplete) {
        if (!isConfigured())
            return;

        executor.execute(() -> {
            try {
                // Table 1: raw_jsonl_lines
                client.executeSync(
                        "CREATE TABLE IF NOT EXISTS raw_jsonl_lines (session_id TEXT NOT NULL, line_no INTEGER NOT NULL, ts TEXT, uuid TEXT, type TEXT, raw_json TEXT NOT NULL, PRIMARY KEY (session_id, line_no));",
                        new ArrayList<>());
                client.executeSync("CREATE INDEX IF NOT EXISTS idx_raw_session_ts ON raw_jsonl_lines(session_id, ts);",
                        new ArrayList<>());

                // Table 2: turns
                client.executeSync(
                        "CREATE TABLE IF NOT EXISTS turns (turn_id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, user_uuid TEXT NOT NULL, ts TEXT, cwd TEXT, git_branch TEXT, user_text TEXT NOT NULL, UNIQUE(session_id, user_uuid));",
                        new ArrayList<>());
                client.executeSync("CREATE INDEX IF NOT EXISTS idx_turns_session_ts ON turns(session_id, ts);",
                        new ArrayList<>());

                // Table 3: assistant_texts
                client.executeSync(
                        "CREATE TABLE IF NOT EXISTS assistant_texts (assistant_text_id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, turn_id INTEGER, assistant_uuid TEXT, parent_uuid TEXT, ts TEXT, model TEXT, stop_reason TEXT, part_index INTEGER NOT NULL, text TEXT NOT NULL, FOREIGN KEY(turn_id) REFERENCES turns(turn_id) ON DELETE SET NULL);",
                        new ArrayList<>());
                client.executeSync(
                        "CREATE INDEX IF NOT EXISTS idx_asst_turn_order ON assistant_texts(turn_id, assistant_text_id);",
                        new ArrayList<>());

                if (onComplete != null)
                    onComplete.run();
            } catch (Exception e) {
                Log.e(TAG, "DB Init Error: " + e.getMessage());
            }
        });
    }

    public void sync(String filePath, Runnable onComplete) {
        if (!isConfigured())
            return;

        executor.execute(() -> {
            performSync(filePath);
            if (onComplete != null)
                onComplete.run();
        });
    }

    private void performSync(String filePath) {
        File file = new File(filePath);
        if (!file.exists())
            return;
        String sessionId = file.getName();

        try {
            // 1. Get max line number
            int startLine = 0;
            List<Object> args = new ArrayList<>();
            args.add(sessionId);
            TursoResponse.ResultData res = client
                    .executeSync("SELECT MAX(line_no) as max_line FROM raw_jsonl_lines WHERE session_id = ?", args);
            if (res != null && res.rows != null && res.rows.size() > 0) {
                startLine = valAsInt(res.rows.get(0).getAsJsonArray().get(0));
            }

            // 2. Fetch UUID map
            Map<String, Long> uuidToTurnId = new HashMap<>();
            res = client.executeSync("SELECT user_uuid, turn_id FROM turns WHERE session_id = ?", args);
            if (res != null && res.rows != null) {
                for (JsonElement rowEl : res.rows) {
                    try {
                        com.google.gson.JsonArray row = rowEl.getAsJsonArray();
                        String uuid = valToString(row.get(0));
                        long id = valAsLong(row.get(1));
                        uuidToTurnId.put(uuid, id);
                    } catch (Exception e) {
                    }
                }
            }

            // 3. Process file
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            String line;
            int lineNo = 0;

            while ((line = br.readLine()) != null) {
                lineNo++;
                if (lineNo <= startLine)
                    continue;
                if (line.trim().isEmpty())
                    continue;

                JSONObject json = new JSONObject(line);
                String uuid = json.optString("uuid");
                String type = json.optString("type");
                String ts = json.optString("created_at");
                if (ts.isEmpty())
                    ts = json.optString("timestamp");

                // Check duplicates (insurance, though startLine check should cover it)
                // Actually startLine is efficient.

                // Insert Raw
                List<Object> rawArgs = new ArrayList<>();
                rawArgs.add(sessionId);
                rawArgs.add(lineNo);
                rawArgs.add(ts);
                rawArgs.add(uuid);
                rawArgs.add(type);
                rawArgs.add(line);
                client.executeSync(
                        "INSERT INTO raw_jsonl_lines (session_id, line_no, ts, uuid, type, raw_json) VALUES (?, ?, ?, ?, ?, ?)",
                        rawArgs);

                // Process Turn
                if ("user".equals(type)) {
                    JSONObject message = json.optJSONObject("message");
                    if (message != null) {
                        String text = "";
                        JSONArray content = message.optJSONArray("content");
                        if (content != null) {
                            for (int i = 0; i < content.length(); i++) {
                                JSONObject part = content.optJSONObject(i);
                                if ("text".equals(part.optString("type"))) {
                                    text = part.optString("text");
                                } else if (part.has("content") && part.opt("content") instanceof String) {
                                    // Fallback for different content structure
                                    text = part.optString("content");
                                }
                            }
                        } else if (message.has("content") && message.opt("content") instanceof String) {
                            text = message.optString("content");
                        }

                        if (!text.isEmpty()) {
                            // Extract metadata if available - prioritize top-level
                            String cwd = json.optString("cwd", null);
                            String gitBranch = json.optString("gitBranch", null);
                            if (gitBranch == null)
                                gitBranch = json.optString("git_branch", null);

                            // Fallback to metadata
                            JSONObject metadata = json.optJSONObject("metadata");
                            if (metadata != null) {
                                if (cwd == null && metadata.has("cwd"))
                                    cwd = metadata.optString("cwd", null);
                                if ((gitBranch == null || gitBranch.equals("null")) && metadata.has("gitBranch"))
                                    gitBranch = metadata.optString("gitBranch", null);
                                if ((gitBranch == null || gitBranch.equals("null")) && metadata.has("git_branch"))
                                    gitBranch = metadata.optString("git_branch", null);
                            }

                            // Fallback to context
                            if (cwd == null || gitBranch == null || gitBranch.equals("null")) {
                                JSONObject context = json.optJSONObject("context");
                                if (context != null) {
                                    if (cwd == null)
                                        cwd = context.optString("cwd", null);
                                    if (gitBranch == null || gitBranch.equals("null")) {
                                        gitBranch = context.optString("gitBranch", null);
                                        if (gitBranch == null)
                                            gitBranch = context.optString("git_branch", null);
                                    }
                                }
                            }

                            if (cwd != null)
                                Log.d(TAG, "Extracted CWD: " + cwd);
                            if (gitBranch != null)
                                Log.d(TAG, "Extracted Branch: " + gitBranch);

                            List<Object> turnArgs = new ArrayList<>();
                            turnArgs.add(sessionId);
                            turnArgs.add(uuid);
                            turnArgs.add(ts);
                            turnArgs.add(cwd); // Can be null
                            turnArgs.add(gitBranch); // Can be null
                            turnArgs.add(text);

                            // Insert and get ID
                            TursoResponse.ResultData turnRes = client.executeSync(
                                    "INSERT INTO turns (session_id, user_uuid, ts, cwd, git_branch, user_text) VALUES (?, ?, ?, ?, ?, ?) RETURNING turn_id",
                                    turnArgs);
                            if (turnRes != null && turnRes.rows != null && turnRes.rows.size() > 0) {
                                long turnId = valAsLong(turnRes.rows.get(0).getAsJsonArray().get(0));
                                uuidToTurnId.put(uuid, turnId);
                                Log.d(TAG, "Inserted turn: " + turnId + " for uuid: " + uuid);
                            }
                        }
                    }
                }

                // Process Assistant
                if ("assistant".equals(type)) {
                    JSONObject message = json.optJSONObject("message");
                    String stopReason = "";
                    String model = "";

                    if (message != null) {
                        stopReason = message.optString("stop_reason");
                        JSONArray content = message.optJSONArray("content");
                        if (content != null) {
                            String pUuid = json.optString("parentUuid", "");
                            if (pUuid.isEmpty())
                                pUuid = json.optString("parent_uuid", "");
                            if (pUuid.isEmpty())
                                pUuid = json.optString("parent_message_uuid", "");

                            long turnId = 0;
                            if (uuidToTurnId.containsKey(pUuid)) {
                                turnId = uuidToTurnId.get(pUuid);
                            } else {
                                // Fallback: find latest turn in this session
                                List<Object> lastTurnArgs = new ArrayList<>();
                                lastTurnArgs.add(sessionId);
                                TursoResponse.ResultData lastTurnRes = client.executeSync(
                                        "SELECT turn_id FROM turns WHERE session_id = ? ORDER BY turn_id DESC LIMIT 1",
                                        lastTurnArgs);
                                if (lastTurnRes != null && lastTurnRes.rows != null && lastTurnRes.rows.size() > 0) {
                                    turnId = valAsLong(lastTurnRes.rows.get(0).getAsJsonArray().get(0));
                                    Log.d(TAG, "Inferred turnId " + turnId + " for assistant uuid " + uuid);
                                }
                            }

                            for (int i = 0; i < content.length(); i++) {
                                JSONObject part = content.optJSONObject(i);
                                String textToInsert = null;

                                String typePart = part.optString("type");
                                if ("text".equals(typePart)) {
                                    textToInsert = part.optString("text");
                                } else if ("tool_use".equals(typePart)) {
                                    // Skip tool_use for cleaner AR view as requested by user
                                    // String toolName = part.optString("name");
                                    // JSONObject input = part.optJSONObject("input");
                                    // textToInsert = String.format("[Tool: %s input=%s]", toolName, input != null ?
                                    // input.toString() : "{}");
                                }

                                if (textToInsert != null) {
                                    List<Object> asstArgs = new ArrayList<>();
                                    asstArgs.add(sessionId);
                                    // turn_id (nullable)
                                    if (turnId > 0)
                                        asstArgs.add(turnId);
                                    else
                                        asstArgs.add(null);
                                    asstArgs.add(uuid);
                                    asstArgs.add(pUuid);
                                    asstArgs.add(ts);
                                    asstArgs.add(model);
                                    asstArgs.add(stopReason);
                                    asstArgs.add(i); // part_index
                                    asstArgs.add(textToInsert);

                                    client.executeSync(
                                            "INSERT INTO assistant_texts (session_id, turn_id, assistant_uuid, parent_uuid, ts, model, stop_reason, part_index, text) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                            asstArgs);
                                }
                            }
                        }
                    }
                }
            }
            br.close();
        } catch (Exception e) {
            Log.e(TAG, "Sync process failed", e);
        }
    }

    public interface MessageCallback {
        void onResult(String message);

        void onError(String error);
    }

    public void getLastAssistantMessage(String sessionId, MessageCallback callback) {
        if (!isConfigured()) {
            callback.onError("Database not configured");
            return;
        }

        executor.execute(() -> {
            try {
                // Query latest message for this session
                // Order by turn_id DESC, part_index ASC (to get the latest turn, then combine
                // parts)
                // But simplified: get latest assistant_text_id
                // Better: Get all texts for the latest turn found in assistant_texts for this
                // session

                String sql = "SELECT text FROM assistant_texts WHERE session_id = ? ORDER BY assistant_text_id DESC LIMIT 1";
                // Note: This gets the very last part of the last message.
                // If message has multiple parts, we might want to combine them?
                // For now, let's assume we want the full text of the latest turn.

                // Revised query to get all parts of the latest assistant turn:
                // 1. Find latest turn_id for assistant
                // 2. Select text where turn_id = ... order by part_index

                // Let's stick to simple latest text for now as per "latest agent response
                // text".
                // If "limit 1", it might be partial.
                // Let's try to get the last message's full content.

                List<Object> args = new ArrayList<>();
                args.add(sessionId);

                // Find the latest assistant_uuid or turn_id
                String findLatestSql = "SELECT turn_id FROM assistant_texts WHERE session_id = ? ORDER BY assistant_text_id DESC LIMIT 1";
                TursoResponse.ResultData res = client.executeSync(findLatestSql, args);

                if (res != null && res.rows != null && res.rows.size() > 0) {
                    long turnId = valAsLong(res.rows.get(0).getAsJsonArray().get(0));

                    if (turnId > 0) {
                        // Fetch all parts for this turn
                        String fetchTextSql = "SELECT text FROM assistant_texts WHERE turn_id = ? ORDER BY part_index ASC";
                        List<Object> fetchArgs = new ArrayList<>();
                        fetchArgs.add(turnId);

                        TursoResponse.ResultData textRes = client.executeSync(fetchTextSql, fetchArgs);
                        if (textRes != null && textRes.rows != null) {
                            StringBuilder sb = new StringBuilder();
                            for (JsonElement rowEl : textRes.rows) {
                                com.google.gson.JsonArray row = rowEl.getAsJsonArray();
                                String textPart = valToString(row.get(0));
                                if (textPart != null) {
                                    sb.append(textPart);
                                }
                            }
                            callback.onResult(sb.toString());
                        } else {
                            callback.onResult(null);
                        }
                    } else {
                        callback.onResult(null);
                    }
                } else {
                    // Fallback if turn_id is null (unlikely with this schema but possible)
                    // Just get the single latest text
                    String simpleSql = "SELECT text FROM assistant_texts WHERE session_id = ? ORDER BY assistant_text_id DESC LIMIT 1";
                    TursoResponse.ResultData simpleRes = client.executeSync(simpleSql, args);
                    if (simpleRes != null && simpleRes.rows != null && simpleRes.rows.size() > 0) {
                        callback.onResult(valToString(simpleRes.rows.get(0).getAsJsonArray().get(0)));
                    } else {
                        callback.onResult(null);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching last message", e);
                callback.onError("Exception: " + e.getMessage());
            }
        });
    }

    private String valToString(JsonElement el) {
        if (el == null || el.isJsonNull())
            return null;
        if (el.isJsonPrimitive())
            return el.getAsString();
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("value"))
                return obj.get("value").getAsString();
        }
        return el.toString();
    }

    private long valAsLong(JsonElement el) {
        if (el == null || el.isJsonNull())
            return 0;
        if (el.isJsonPrimitive())
            return el.getAsLong();
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("value")) {
                try {
                    return Long.parseLong(obj.get("value").getAsString());
                } catch (Exception e) {
                }
            }
        }
        return 0;
    }

    private int valAsInt(JsonElement el) {
        return (int) valAsLong(el);
    }

    public interface HistoryCallback {
        void onResult(List<String> historyItems);

        void onError(String error);
    }

    public void getConversationHistory(String sessionId, HistoryCallback callback) {
        if (!isConfigured()) {
            callback.onError("Database not configured");
            return;
        }

        executor.execute(() -> {
            try {
                List<String> historyItems = new ArrayList<>();
                List<Object> args = new ArrayList<>();
                args.add(sessionId);

                // 1. Fetch all turns (User messages)
                String turnsSql = "SELECT turn_id, user_text FROM turns WHERE session_id = ? ORDER BY turn_id ASC";
                TursoResponse.ResultData turnsRes = client.executeSync(turnsSql, args);

                // 2. Fetch all assistant texts
                String asstSql = "SELECT turn_id, text FROM assistant_texts WHERE session_id = ? ORDER BY turn_id ASC, part_index ASC";
                TursoResponse.ResultData asstRes = client.executeSync(asstSql, args);

                Map<Long, StringBuilder> asstMap = new HashMap<>();
                if (asstRes != null && asstRes.rows != null) {
                    for (JsonElement rowEl : asstRes.rows) {
                        try {
                            com.google.gson.JsonArray row = rowEl.getAsJsonArray();
                            long turnId = valAsLong(row.get(0));
                            String text = valToString(row.get(1));

                            if (!asstMap.containsKey(turnId)) {
                                asstMap.put(turnId, new StringBuilder());
                            }
                            asstMap.get(turnId).append(text);
                        } catch (Exception e) {
                        }
                    }
                }

                if (turnsRes != null && turnsRes.rows != null) {
                    for (JsonElement rowEl : turnsRes.rows) {
                        try {
                            com.google.gson.JsonArray row = rowEl.getAsJsonArray();
                            long turnId = valAsLong(row.get(0));
                            String userText = valToString(row.get(1));

                            // Add User Text
                            historyItems.add("👤 User:\n" + userText);

                            // Add Assistant Text if exists
                            if (asstMap.containsKey(turnId)) {
                                historyItems.add("🤖 Assistant:\n" + asstMap.get(turnId).toString());
                            }
                        } catch (Exception e) {
                        }
                    }
                }

                callback.onResult(historyItems);

            } catch (Exception e) {
                Log.e(TAG, "Error fetching history", e);
                callback.onError("Exception: " + e.getMessage());
            }
        });
    }
}
