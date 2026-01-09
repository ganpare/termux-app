package com.termux.app.claude;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.termux.shared.shell.ShellUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

    // Polling configuration
    private static final int POLL_INTERVAL_MS = 500;
    private static final int MAX_POLL_ATTEMPTS = 20; // 10 seconds total timeout

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
     *
     * @param limit Maximum number of files to return (0 for no limit)
     */
    public String getListConversationsCommand(int limit) {
        // Get current working directory and convert to Claude project format
        // Use stat to get timestamp, filename, and size, then sort by timestamp
        // descending
        // Use @@@ for xargs placeholder to avoid conflict with {} in find -exec and %
        // in stat format
        // Use ~ instead of $HOME for proper shell expansion in xargs context
        
        // Debug: Show pwd and calculated search path before markers
        String debugPrefix = "echo '=== DEBUG ===' && " +
                "echo 'pwd:' $(pwd) && " +
                "echo 'search_dir:' ~/.claude/projects/$(pwd | sed 's|^/||;s|/|-|g' | sed 's|^|-|') && " +
                "echo 'dir_exists:' && ls -d ~/.claude/projects/$(pwd | sed 's|^/||;s|/|-|g' | sed 's|^|-|') 2>&1 && " +
                "echo '==============' && ";
        
        String command = debugPrefix + "echo '" + LIST_MARKER_START + "' && " +
                "pwd | sed 's|^/||;s|/|-|g' | sed 's|^|-|' | xargs -I @@@ find ~/.claude/projects/@@@ -name '*.jsonl' -type f -exec stat -c \"%Y %n %s\" {} + 2>/dev/null | "
                +
                "sort -rn | cut -d' ' -f2-";

        if (limit > 0) {
            command += " | head -n " + limit;
        }

        return command + " && echo '" + LIST_MARKER_END + "'\n";
    }

    /**
     * Generates a command to directly download the latest conversation file.
     * Skips the file selection step - finds and downloads in one operation.
     */
    public String getDownloadLatestCommand() {
        // Find latest .jsonl file (excluding agent files) and output as base64
        return "PROJ=$(pwd | sed 's|^/||;s|/|-|g' | sed 's|^|-|') && " +
                "LATEST=$(find ~/.claude/projects/$PROJ -name '*.jsonl' ! -name 'agent*' -type f -exec stat -c \"%Y %n\" {} + 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-) && " +
                "if [ -n \"$LATEST\" ]; then " +
                "echo '" + FILE_MARKER_START + "' && " +
                "echo \"FILE:$(basename \"$LATEST\")\" && " +
                "base64 \"$LATEST\" && " +
                "echo '" + FILE_MARKER_END + "'; " +
                "else " +
                "echo '" + FILE_MARKER_START + "' && " +
                "echo 'ERROR:No conversation files found' && " +
                "echo '" + FILE_MARKER_END + "'; " +
                "fi\n";
    }

    /**
     * Generates the command to download a conversation file via base64.
     */
    public String getDownloadFileCommand(String remotePath) {
        // Escape single quotes in path
        String escapedPath = remotePath.replace("'", "'\\''");
        // Use cat | base64 instead of redirection < for better compatibility
        // Redirect stderr to null to prevent error messages from corrupting the base64
        // stream
        return "echo '" + FILE_MARKER_START + "' && " +
                "cat '" + escapedPath + "' 2>/dev/null | base64 && " +
                "echo '" + FILE_MARKER_END + "'\n";
    }

    /**
     * Lists available conversation files from the remote server.
     *
     * @param limit Maximum number of conversations to list (0 for all)
     */
    public void listConversations(TerminalSession session, int limit, ConversationListCallback callback) {
        if (session == null) {
            callback.onError("No active terminal session");
            return;
        }

        // Send the list command
        String command = getListConversationsCommand(limit);
        android.util.Log.d("ConversationSync", "Generated list command: " + command);
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        // Wait for output using polling
        pollForOutput(session, LIST_MARKER_END, new OutputCallback() {
            @Override
            public void onOutputFound(String transcript) {
                List<ConversationFile> conversations = parseConversationList(transcript);

                if (conversations == null) {
                    // Debug: show marker positions
                    int startIdx = transcript.lastIndexOf(LIST_MARKER_START);
                    int endIdx = transcript.lastIndexOf(LIST_MARKER_END);
                    callback.onError("マーカーが見つかりません\n" +
                        "startIdx=" + startIdx + ", endIdx=" + endIdx + "\n" +
                        "transcript長=" + transcript.length());
                } else if (conversations.isEmpty()) {
                    // Debug: show what was between markers
                    int startIdx = transcript.lastIndexOf(LIST_MARKER_START);
                    int endIdx = transcript.lastIndexOf(LIST_MARKER_END);
                    String between = "";
                    if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                        between = transcript.substring(startIdx + LIST_MARKER_START.length(), endIdx);
                        if (between.length() > 200) {
                            between = between.substring(0, 200) + "...";
                        }
                    }
                    callback.onError("ファイルが見つかりません\n" +
                        "マーカー間の内容:\n" + between);
                } else {
                    callback.onConversationsFound(conversations);
                }
            }

            @Override
            public void onTimeout() {
                callback.onError("Timeout waiting for conversation list");
            }
        });
    }

    /**
     * Lists available conversation files from the local Termux filesystem.
     * Scans the download directory where remote files are saved.
     *
     * @param limit Maximum number of conversations to list (0 for all)
     */
    public void listLocalConversations(int limit, ConversationListCallback callback) {
        // Scan local download directory (where remote files are saved)
        File downloadDir = new File(STORAGE_DIR);

        if (!downloadDir.exists()) {
            callback.onError("ダウンロードディレクトリが存在しません: " + downloadDir.getAbsolutePath() +
                "\n\nヒント: リモートからファイルをダウンロードしてください");
            return;
        }

        if (!downloadDir.isDirectory()) {
            callback.onError(downloadDir.getAbsolutePath() + " is not a directory");
            return;
        }

        List<ConversationFile> conversations = new ArrayList<>();
        scanDirectoryForJsonl(downloadDir, conversations);

        if (conversations.isEmpty()) {
            // Count total jsonl files (including agent files) for debugging
            List<ConversationFile> allFiles = new ArrayList<>();
            scanAllJsonl(downloadDir, allFiles);

            String errorMsg = "ダウンロード済みファイルが見つかりません: " + downloadDir.getAbsolutePath();
            if (allFiles.size() > 0) {
                errorMsg += "\n\n見つかったファイル数: " + allFiles.size() + " (全てagentファイルでフィルタリングされました)";
            } else {
                errorMsg += "\n\nヒント: リモートから会話履歴をダウンロードしてください";
            }
            callback.onError(errorMsg);
            return;
        }

        // Sort by last modified time (most recent first)
        Collections.sort(conversations, new Comparator<ConversationFile>() {
            @Override
            public int compare(ConversationFile a, ConversationFile b) {
                File fileA = new File(a.path);
                File fileB = new File(b.path);
                return Long.compare(fileB.lastModified(), fileA.lastModified());
            }
        });

        // Apply limit if specified
        if (limit > 0 && conversations.size() > limit) {
            conversations = conversations.subList(0, limit);
        }

        callback.onConversationsFound(conversations);
    }

    /**
     * Recursively scans a directory for .jsonl files (excluding agent files).
     */
    private void scanDirectoryForJsonl(File directory, List<ConversationFile> result) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively scan subdirectories
                scanDirectoryForJsonl(file, result);
            } else if (file.isFile() && file.getName().endsWith(".jsonl")) {
                // Filter out files starting with "agent" (subagent histories)
                if (!file.getName().startsWith("agent")) {
                    result.add(new ConversationFile(file.getAbsolutePath(), file.length()));
                }
            }
        }
    }

    /**
     * Recursively scans a directory for all .jsonl files (no filtering).
     * Used for debugging to count total files.
     */
    private void scanAllJsonl(File directory, List<ConversationFile> result) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanAllJsonl(file, result);
            } else if (file.isFile() && file.getName().endsWith(".jsonl")) {
                result.add(new ConversationFile(file.getAbsolutePath(), file.length()));
            }
        }
    }

    /**
     * Parses the terminal transcript to extract conversation file list.
     */
    public List<ConversationFile> parseConversationList(String transcript) {
        if (transcript == null || transcript.isEmpty()) {
            android.util.Log.w("ConversationSync", "parseConversationList: transcript is null or empty");
            return null;
        }

        // Find the marker positions
        int startIndex = transcript.lastIndexOf(LIST_MARKER_START);
        int endIndex = transcript.lastIndexOf(LIST_MARKER_END);

        android.util.Log.d("ConversationSync", "parseConversationList: startIndex=" + startIndex + ", endIndex=" + endIndex);

        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
            android.util.Log.w("ConversationSync", "parseConversationList: markers not found properly");
            return null;
        }

        // Extract text between markers
        String betweenMarkers = transcript.substring(
                startIndex + LIST_MARKER_START.length(),
                endIndex);
        
        android.util.Log.d("ConversationSync", "parseConversationList: content between markers (first 500 chars): " + 
            (betweenMarkers.length() > 500 ? betweenMarkers.substring(0, 500) + "..." : betweenMarkers));

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

            // Filter out agent files (subagent histories)
            String filename = new File(path).getName();
            if (filename.startsWith("agent")) {
                continue;
            }

            try {
                long size = Long.parseLong(sizeStr);
                android.util.Log.d("ConversationSync", "Found file: " + path + " (size: " + size + ")");
                conversations.add(new ConversationFile(path, size));
            } catch (NumberFormatException e) {
                android.util.Log.w("ConversationSync", "Invalid size format for line: " + trimmed);
                // Skip invalid entries
            }
        }

        android.util.Log.i("ConversationSync", "parseConversationList: Found " + conversations.size() + " conversation files");
        return conversations;
    }

    /**
     * Downloads the latest conversation file directly from the remote server.
     * Skips file selection - finds and downloads in one step.
     */
    public void downloadLatestFile(TerminalSession session, FileDownloadCallback callback) {
        if (session == null) {
            callback.onError("No active terminal session");
            return;
        }

        String command = getDownloadLatestCommand();
        android.util.Log.d("ConversationSync", "Download latest command: " + command);
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        pollForOutput(session, FILE_MARKER_END, new OutputCallback() {
            @Override
            public void onOutputFound(String transcript) {
                // Extract content between markers
                int startIndex = transcript.lastIndexOf(FILE_MARKER_START);
                int endIndex = transcript.lastIndexOf(FILE_MARKER_END);

                if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
                    callback.onError("マーカーが見つかりません");
                    return;
                }

                String content = transcript.substring(startIndex + FILE_MARKER_START.length(), endIndex).trim();

                // Check for error
                if (content.startsWith("ERROR:")) {
                    callback.onError(content.substring(6));
                    return;
                }

                // Parse filename and base64 content
                String[] lines = content.split("\n", 2);
                if (lines.length < 2 || !lines[0].startsWith("FILE:")) {
                    callback.onError("不正なレスポンス形式");
                    return;
                }

                String filename = lines[0].substring(5); // Remove "FILE:" prefix
                String base64Content = lines[1].replaceAll("\\s+", "");

                try {
                    byte[] fileContent = Base64.decode(base64Content, Base64.DEFAULT);
                    String localPath = saveToLocal(filename, fileContent);
                    callback.onDownloadComplete(localPath);
                } catch (IllegalArgumentException e) {
                    callback.onError("Base64デコードエラー: " + e.getMessage());
                } catch (Exception e) {
                    callback.onError("保存エラー: " + e.getMessage());
                }
            }

            @Override
            public void onTimeout() {
                callback.onError("タイムアウト");
            }
        });
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
        android.util.Log.d("ConversationSync", "Generated download command for: " + conversation.path);
        android.util.Log.d("ConversationSync", "Download command: " + command);
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        // Wait for output using polling
        pollForOutput(session, FILE_MARKER_END, new OutputCallback() {
            @Override
            public void onOutputFound(String transcript) {
                String base64Content = extractFileContent(transcript);

                if (base64Content == null) {
                    android.util.Log.e("ConversationSync", "Could not find file content markers. Transcript tail: " +
                        (transcript.length() > 500 ? transcript.substring(transcript.length() - 500) : transcript));
                    callback.onError("Could not find file content markers in output");
                    return;
                }
                android.util.Log.d("ConversationSync", "Base64 content length: " + base64Content.length());

                // Decode and save
                try {
                    // Check for common error patterns or invalid characters before decoding
                    // Only standard Base64 chars are allowed (A-Z, a-z, 0-9, +, /, =)
                    // We remove whitespace first, as per standard
                    String cleanContent = base64Content.replaceAll("\\s+", "");

                    byte[] fileContent = Base64.decode(cleanContent, Base64.DEFAULT);
                    String localPath = saveToLocal(conversation.filename, fileContent);
                    callback.onDownloadComplete(localPath);
                } catch (IllegalArgumentException e) {
                    // This likely means we captured an error message (like "cat: ...") instead of
                    // base64
                    String snippet = base64Content.length() > 100 ? base64Content.substring(0, 100) + "..."
                            : base64Content;
                    callback.onError("Download failed (invalid data). Output: " + snippet);
                } catch (Exception e) {
                    callback.onError("Failed to save file: " + e.getMessage());
                }
            }

            @Override
            public void onTimeout() {
                callback.onError("Timeout waiting for file download");
            }
        });
    }

    private interface OutputCallback {
        void onOutputFound(String transcript);

        void onTimeout();
    }

    private void pollForOutput(TerminalSession session, String endMarker, OutputCallback callback) {
        pollForOutput(session, endMarker, callback, 0);
    }

    private void pollForOutput(TerminalSession session, String endMarker, OutputCallback callback, int attempt) {
        if (attempt >= MAX_POLL_ATTEMPTS) {
            android.util.Log.w("ConversationSync", "Polling timed out after " + MAX_POLL_ATTEMPTS + " attempts. EndMarker: " + endMarker);
            callback.onTimeout();
            return;
        }

        mainHandler.postDelayed(() -> {
            String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
            if (attempt % 5 == 0) {
                // Log every 5th attempt to reduce noise
                android.util.Log.d("ConversationSync", "Poll attempt " + attempt + ", transcript length: " + 
                    (transcript != null ? transcript.length() : 0) + 
                    ", contains marker: " + (transcript != null && transcript.contains(endMarker)));
            }
            if (transcript != null && transcript.contains(endMarker)) {
                android.util.Log.d("ConversationSync", "End marker found. Transcript tail (last 500 chars): " + 
                    (transcript.length() > 500 ? transcript.substring(transcript.length() - 500) : transcript));
                callback.onOutputFound(transcript);
            } else {
                pollForOutput(session, endMarker, callback, attempt + 1);
            }
        }, POLL_INTERVAL_MS);
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
