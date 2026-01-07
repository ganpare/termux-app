package com.termux.app.claude;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.termux.shared.shell.ShellUtils;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages Claude Code conversation history synchronization via terminal.
 *
 * This class provides functionality to:
 * - List Claude Code conversation files from remote server
 * - Download conversation files via base64 encoding
 * - Save them locally in Termux storage
 */
public class ConversationSyncManager {

    // Unique markers for conversation list
    public static final String LIST_MARKER_START = "__CONV_LIST_START_9mQ7wR2__";
    public static final String LIST_MARKER_END = "__CONV_LIST_END_9mQ7wR2__";

    // Unique markers for file content
    public static final String FILE_MARKER_START = "__CONV_FILE_START_9mQ7wR2__";
    public static final String FILE_MARKER_END = "__CONV_FILE_END_9mQ7wR2__";

    // Delay before reading terminal buffer (ms)
    private static final int READ_DELAY_MS = 800; // Longer for file transfers

    // Local storage directory
    private static final String STORAGE_DIR = "/data/data/com.termux/files/home/claude-history";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Callback interface for conversation list results.
     */
    public interface ConversationListCallback {
        void onConversationsFound(List<ConversationFile> conversations);

        void onError(String message);
    }

    /**
     * Callback interface for file download results.
     */
    public interface FileDownloadCallback {
        void onDownloadComplete(String localPath);

        void onError(String message);
    }

    /**
     * Represents a conversation file with metadata.
     */
    public static class ConversationFile {
        public String path;
        public String filename;
        public long size;
        public String displayName;

        public ConversationFile(String path, long size) {
            this.path = path;
            this.size = size;
            this.filename = new File(path).getName();

            // Format display name: filename (size)
            String sizeStr = formatFileSize(size);
            this.displayName = filename + " (" + sizeStr + ")";
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024)
                return bytes + "B";
            if (bytes < 1024 * 1024)
                return (bytes / 1024) + "KB";
            return String.format(Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Generates the command to list conversation files with metadata.
     */
    public String getListConversationsCommand() {
        // Get current working directory and convert to Claude project format
        // Use stat to get timestamp, filename, and size, then sort by timestamp
        // descending
        // Use @@@ for xargs placeholder to avoid conflict with {} in find -exec and %
        // in stat format
        return "echo '" + LIST_MARKER_START + "' && " +
                "pwd | sed 's|^/||;s|/|-|g' | sed 's|^|-|' | xargs -I @@@ find ~/.claude/projects/@@@ -name '*.jsonl' -type f -exec stat -c \"%Y %n %s\" {} + 2>/dev/null | "
                +
                "sort -rn | cut -d' ' -f2- && " +
                "echo '" + LIST_MARKER_END + "'\n";
    }

    /**
     * Generates the command to download a conversation file via base64.
     */
    public String getDownloadFileCommand(String remotePath) {
        // Escape single quotes in path
        String escapedPath = remotePath.replace("'", "'\\''");
        return "echo '" + FILE_MARKER_START + "' && " +
                "base64 < '" + escapedPath + "' && " +
                "echo '" + FILE_MARKER_END + "'\n";
    }

    /**
     * Lists available conversation files from the remote server.
     */
    public void listConversations(TerminalSession session, ConversationListCallback callback) {
        if (session == null) {
            callback.onError("No active terminal session");
            return;
        }

        // Send the list command
        String command = getListConversationsCommand();
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        // Wait for output, then parse
        mainHandler.postDelayed(() -> {
            String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
            List<ConversationFile> conversations = parseConversationList(transcript);

            if (conversations == null) {
                callback.onError("Could not find conversation list markers in output");
            } else if (conversations.isEmpty()) {
                callback.onError("No conversation files found");
            } else {
                callback.onConversationsFound(conversations);
            }
        }, READ_DELAY_MS);
    }

    /**
     * Parses the terminal transcript to extract conversation file list.
     */
    public List<ConversationFile> parseConversationList(String transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return null;
        }

        // Find the marker positions
        int startIndex = transcript.lastIndexOf(LIST_MARKER_START);
        int endIndex = transcript.lastIndexOf(LIST_MARKER_END);

        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
            return null;
        }

        // Extract text between markers
        String betweenMarkers = transcript.substring(
                startIndex + LIST_MARKER_START.length(),
                endIndex);

        // Parse lines: "path size"
        List<ConversationFile> conversations = new ArrayList<>();
        String[] lines = betweenMarkers.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
                continue;

            // Split by last space to separate path and size
            int lastSpace = trimmed.lastIndexOf(' ');
            if (lastSpace == -1)
                continue;

            String path = trimmed.substring(0, lastSpace);
            String sizeStr = trimmed.substring(lastSpace + 1);

            try {
                long size = Long.parseLong(sizeStr);
                conversations.add(new ConversationFile(path, size));
            } catch (NumberFormatException e) {
                // Skip invalid entries
            }
        }

        return conversations;
    }

    /**
     * Downloads a conversation file from the remote server.
     */
    public void downloadFile(TerminalSession session, ConversationFile conversation, FileDownloadCallback callback) {
        if (session == null) {
            callback.onError("No active terminal session");
            return;
        }

        // Send the download command
        String command = getDownloadFileCommand(conversation.path);
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        // Wait for output, then parse and save
        mainHandler.postDelayed(() -> {
            String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
            String base64Content = extractFileContent(transcript);

            if (base64Content == null) {
                callback.onError("Could not find file content markers in output");
                return;
            }

            // Decode and save
            try {
                byte[] fileContent = Base64.decode(base64Content, Base64.DEFAULT);
                String localPath = saveToLocal(conversation.filename, fileContent);
                callback.onDownloadComplete(localPath);
            } catch (Exception e) {
                callback.onError("Failed to decode/save file: " + e.getMessage());
            }
        }, READ_DELAY_MS * 2); // Longer delay for file transfers
    }

    /**
     * Extracts base64 file content between markers.
     */
    private String extractFileContent(String transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return null;
        }

        int startIndex = transcript.lastIndexOf(FILE_MARKER_START);
        int endIndex = transcript.lastIndexOf(FILE_MARKER_END);

        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
            return null;
        }

        // Extract base64 content
        String content = transcript.substring(
                startIndex + FILE_MARKER_START.length(),
                endIndex);

        // Remove whitespace and newlines
        return content.replaceAll("\\s+", "");
    }

    /**
     * Saves file content to local storage.
     */
    private String saveToLocal(String filename, byte[] content) throws IOException {
        // Create storage directory if needed
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        // Generate timestamped filename to avoid collisions
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String localFilename = timestamp + "_" + filename;
        File localFile = new File(storageDir, localFilename);

        // Write content
        try (FileOutputStream fos = new FileOutputStream(localFile)) {
            fos.write(content);
        }

        return localFile.getAbsolutePath();
    }
}
