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

    /**
     * Extracts all text comments made by the assistant (Claude) from the given
     * JSONL file.
     * Use this to get the "voice" of the agent without tool execution logs.
     *
     * @param filePath The absolute path to the local .jsonl file.
     * @return A list of strings, where each string is a text comment from the
     *         assistant.
     *         Returns an empty list if file not found or parsing fails.
     */
    public static List<String> getAgentComments(String filePath) {
        List<String> comments = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            return comments;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
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
                    if (!message.has("content")) {
                        continue;
                    }

                    JSONArray contentArray = message.getJSONArray("content");
                    for (int i = 0; i < contentArray.length(); i++) {
                        JSONObject contentItem = contentArray.getJSONObject(i);

                        // Extract only text content, ignoring tool_use
                        if (contentItem.has("type") && contentItem.getString("type").equals("text")) {
                            if (contentItem.has("text")) {
                                comments.add(contentItem.getString("text"));
                            }
                        }
                    }

                } catch (JSONException e) {
                    // Skip malformed lines
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return comments;
    }
}
