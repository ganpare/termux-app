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
            android.util.Log.e("ClaudeChatParser", "File not found: " + filePath);
            return comments;
        }

        android.util.Log.d("ClaudeChatParser", "Parsing file: " + filePath + " (size: " + file.length() + " bytes)");

        int totalLines = 0;
        int assistantLines = 0;
        int textBlocks = 0;

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

                    assistantLines++;

                    if (!event.has("message")) {
                        android.util.Log.d("ClaudeChatParser", "Assistant event missing 'message' field");
                        continue;
                    }

                    JSONObject message = event.getJSONObject("message");
                    if (!message.has("content")) {
                        android.util.Log.d("ClaudeChatParser", "Message missing 'content' field");
                        continue;
                    }

                    JSONArray contentArray = message.getJSONArray("content");
                    for (int i = 0; i < contentArray.length(); i++) {
                        JSONObject contentItem = contentArray.getJSONObject(i);

                        // Extract only text content, ignoring tool_use
                        if (contentItem.has("type") && contentItem.getString("type").equals("text")) {
                            textBlocks++;
                            if (contentItem.has("text")) {
                                String text = contentItem.getString("text");
                                comments.add(text);
                                android.util.Log.d("ClaudeChatParser", "Found text block #" + textBlocks + " (length: " + text.length() + ")");
                            }
                        }
                    }

                } catch (JSONException e) {
                    // Skip malformed lines
                    android.util.Log.w("ClaudeChatParser", "Malformed JSON line " + totalLines + ": " + e.getMessage());
                    continue;
                }
            }
        } catch (IOException e) {
            android.util.Log.e("ClaudeChatParser", "IO Error: " + e.getMessage());
            e.printStackTrace();
        }

        android.util.Log.i("ClaudeChatParser", "Parsing complete: " + totalLines + " total lines, " +
            assistantLines + " assistant lines, " + textBlocks + " text blocks, " + comments.size() + " comments");

        return comments;
    }

    /**
     * Gets the latest assistant comment from the JSONL file.
     * Returns null if no assistant comment is found.
     *
     * @param filePath The absolute path to the local .jsonl file.
     * @return The latest assistant comment text, or null if not found.
     */
    public static String getLatestAgentComment(String filePath) {
        List<String> comments = getAgentComments(filePath);
        if (comments.isEmpty()) {
            return null;
        }
        return comments.get(comments.size() - 1);
    }
}
