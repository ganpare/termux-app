package com.termux.app.claude;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Claude Code JSONL history files to extract relevant information for AR
 * display.
 */
public class ClaudeChatParser {

    public static class AgentComment {
        public String text;
        public String stopReason;

        public AgentComment(String text, String stopReason) {
            this.text = text;
            this.stopReason = stopReason;
        }
    }

    /**
     * Extracts all text comments made by the assistant (Claude) from the given
     * JSONL file.
     * Use this to get the "voice" of the agent without tool execution logs.
     *
     * @param filePath The absolute path to the local .jsonl file.
     * @return A list of AgentComment objects.
     */
    public static List<AgentComment> getAgentComments(String filePath) {
        List<AgentComment> comments = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            android.util.Log.e("ClaudeChatParser", "File not found: " + filePath);
            return comments;
        }

        int totalLines = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                totalLines++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    JSONObject event = new JSONObject(line);

                    // Filter for assistant events
                    if (!event.has("type") || !event.getString("type").equals("assistant")) {
                        continue;
                    }

                    if (!event.has("message")) {
                        continue;
                    }

                    JSONObject message = event.getJSONObject("message");
                    String stopReason = message.optString("stop_reason", "");

                    if (!message.has("content")) {
                        continue;
                    }

                    JSONArray contentArray = message.getJSONArray("content");
                    StringBuilder sb = new StringBuilder();
                    boolean hasText = false;

                    for (int i = 0; i < contentArray.length(); i++) {
                        JSONObject contentItem = contentArray.getJSONObject(i);

                        // Extract only text content, ignoring tool_use
                        if (contentItem.has("type") && contentItem.getString("type").equals("text")) {
                            if (contentItem.has("text")) {
                                String text = contentItem.getString("text");
                                sb.append(text);
                                hasText = true;
                            }
                        } else if (contentItem.has("type") && contentItem.getString("type").equals("tool_use")) {
                            if (contentItem.has("name")) {
                                sb.append("\n[Tool: ").append(contentItem.getString("name")).append("]");
                                hasText = true;
                            }
                        }
                    }

                    if (hasText || !stopReason.isEmpty()) {
                        comments.add(new AgentComment(sb.toString(), stopReason));
                    }

                } catch (JSONException e) {
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return comments;
    }

    /**
     * Gets the latest assistant comment from the JSONL file.
     * Returns null if no assistant comment is found.
     *
     * @param filePath The absolute path to the local .jsonl file.
     * @return The latest assistant AgentComment, or null if not found.
     */
    public static AgentComment getLatestAgentComment(String filePath) {
        List<AgentComment> comments = getAgentComments(filePath);
        if (comments.isEmpty()) {
            return null;
        }
        return comments.get(comments.size() - 1);
    }
}
