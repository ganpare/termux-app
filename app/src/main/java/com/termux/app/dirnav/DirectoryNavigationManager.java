package com.termux.app.dirnav;

import android.os.Handler;
import android.os.Looper;

import com.termux.shared.shell.ShellUtils;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages directory navigation operations via terminal screen scraping.
 *
 * This class provides functionality to:
 * - List subdirectories by sending a marker-wrapped command
 * - Parse directory names from terminal buffer output
 * - Navigate to selected directories
 * - Browse directories like a file explorer
 */
public class DirectoryNavigationManager {

    // Unique markers that won't appear in normal terminal output
    public static final String MARKER_START = "__DIR_NAV_START_8qW3rT5__";
    public static final String MARKER_END = "__DIR_NAV_END_8qW3rT5__";
    public static final String PWD_MARKER_START = "__DIR_NAV_PWD_START__";
    public static final String PWD_MARKER_END = "__DIR_NAV_PWD_END__";

    // Delay before reading terminal buffer (ms)
    private static final int READ_DELAY_MS = 500;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Callback interface for directory list results.
     */
    public interface DirectoryListCallback {
        void onDirectoriesFound(List<String> directories);
        void onError(String message);
    }

    /**
     * Callback interface for directory browse results (includes current path).
     */
    public interface DirectoryBrowseCallback {
        void onResult(String currentPath, List<String> directories);
        void onError(String message);
    }

    /**
     * Generates the command to list subdirectories with markers.
     * The output format will be:
     *   __DIR_NAV_START_8qW3rT5__
     *   dir1
     *   dir2
     *   ..
     *   __DIR_NAV_END_8qW3rT5__
     */
    public String getListDirectoriesCommand() {
        return "echo '" + MARKER_START + "' && " +
               "echo '..' && " +  // Always include parent directory
               "ls -1d */ 2>/dev/null | sed 's|/$||' && " +  // Remove trailing slashes
               "echo '" + MARKER_END + "'\n";
    }

    /**
     * Generates the command to list directories AND get current path.
     */
    public String getListDirectoriesWithPwdCommand() {
        return "echo '" + PWD_MARKER_START + "' && pwd && echo '" + PWD_MARKER_END + "' && " +
               "echo '" + MARKER_START + "' && " +
               "echo '..' && " +
               "ls -1d */ 2>/dev/null | sed 's|/$||' && " +
               "echo '" + MARKER_END + "'\n";
    }

    /**
     * Generates the command to change to a specific directory.
     */
    public String getChangeDirectoryCommand(String dirName) {
        // Escape single quotes in directory name for safety
        String escapedName = dirName.replace("'", "'\\''");
        return "cd '" + escapedName + "'\n";
    }

    /**
     * Sends the list directories command and reads the result from terminal buffer.
     *
     * @param session The terminal session to use
     * @param callback Callback to receive the parsed directory list
     */
    public void listDirectories(TerminalSession session, DirectoryListCallback callback) {
        if (session == null) {
            callback.onError("No active terminal session");
            return;
        }

        // Send the marker-wrapped command
        String command = getListDirectoriesCommand();
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        // Wait for output, then parse
        mainHandler.postDelayed(() -> {
            String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
            List<String> directories = parseDirectoryList(transcript);

            if (directories == null) {
                callback.onError("Could not find directory markers in output");
            } else if (directories.isEmpty()) {
                callback.onError("No directories found");
            } else {
                callback.onDirectoriesFound(directories);
            }
        }, READ_DELAY_MS);
    }

    /**
     * Browse directories - gets current path and directory list.
     * Used for file explorer-like navigation.
     *
     * @param session The terminal session to use
     * @param callback Callback to receive current path and directory list
     */
    public void browseDirectories(TerminalSession session, DirectoryBrowseCallback callback) {
        if (session == null) {
            callback.onError("No active terminal session");
            return;
        }

        // Send command to get pwd and directory list
        String command = getListDirectoriesWithPwdCommand();
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        // Wait for output, then parse
        mainHandler.postDelayed(() -> {
            String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
            String currentPath = parseCurrentPath(transcript);
            List<String> directories = parseDirectoryList(transcript);

            if (currentPath == null) {
                currentPath = "???";
            }
            if (directories == null) {
                directories = new ArrayList<>();
                directories.add("..");
            }

            callback.onResult(currentPath, directories);
        }, READ_DELAY_MS);
    }

    /**
     * Navigate to a directory and then get the new directory listing.
     *
     * @param session The terminal session
     * @param dirName The directory to navigate to
     * @param callback Callback to receive new path and directory list
     */
    public void navigateAndBrowse(TerminalSession session, String dirName, DirectoryBrowseCallback callback) {
        if (session == null || dirName == null) {
            callback.onError("Invalid session or directory");
            return;
        }

        // First cd, then get pwd and list
        String escapedName = dirName.replace("'", "'\\''");
        String command = "cd '" + escapedName + "' && " + getListDirectoriesWithPwdCommand();
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        // Wait for output, then parse
        mainHandler.postDelayed(() -> {
            String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
            String currentPath = parseCurrentPath(transcript);
            List<String> directories = parseDirectoryList(transcript);

            if (currentPath == null) {
                currentPath = "???";
            }
            if (directories == null) {
                directories = new ArrayList<>();
                directories.add("..");
            }

            callback.onResult(currentPath, directories);
        }, READ_DELAY_MS);
    }

    /**
     * Parses current path from transcript.
     */
    public String parseCurrentPath(String transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return null;
        }

        int startIndex = transcript.lastIndexOf(PWD_MARKER_START);
        int endIndex = transcript.lastIndexOf(PWD_MARKER_END);

        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
            return null;
        }

        String between = transcript.substring(startIndex + PWD_MARKER_START.length(), endIndex);
        String[] lines = between.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * Parses the terminal transcript to extract directory names between markers.
     *
     * @param transcript The full terminal transcript text
     * @return List of directory names, or null if markers not found
     */
    public List<String> parseDirectoryList(String transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return null;
        }

        // Find the marker positions
        int startIndex = transcript.lastIndexOf(MARKER_START);
        int endIndex = transcript.lastIndexOf(MARKER_END);

        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
            return null;
        }

        // Extract text between markers
        String betweenMarkers = transcript.substring(
            startIndex + MARKER_START.length(),
            endIndex
        );

        // Parse lines
        List<String> directories = new ArrayList<>();
        String[] lines = betweenMarkers.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            // Skip empty lines and lines that are just whitespace
            if (!trimmed.isEmpty() && !trimmed.matches("^\\s*$")) {
                directories.add(trimmed);
            }
        }

        return directories;
    }

    /**
     * Sends the change directory command for the specified directory.
     *
     * @param session The terminal session
     * @param dirName The directory name to change to
     */
    public void changeDirectory(TerminalSession session, String dirName) {
        if (session == null || dirName == null) {
            return;
        }

        String command = getChangeDirectoryCommand(dirName);
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);
    }
}
