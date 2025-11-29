package com.termux.app.byobu;

import android.os.Handler;
import android.os.Looper;

import com.termux.shared.shell.ShellUtils;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages byobu session operations via terminal screen scraping.
 * 
 * This class provides functionality to:
 * - List byobu sessions by sending a marker-wrapped command
 * - Parse session names from terminal buffer output
 * - Attach to selected sessions
 */
public class ByobuSessionManager {

    // Unique markers that won't appear in normal terminal output
    public static final String MARKER_START = "__BYOBU_SESS_START_7x9K2mN__";
    public static final String MARKER_END = "__BYOBU_SESS_END_7x9K2mN__";

    // Delay before reading terminal buffer (ms)
    private static final int READ_DELAY_MS = 500;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Callback interface for session list results.
     */
    public interface SessionListCallback {
        void onSessionsFound(List<String> sessions);
        void onError(String message);
    }

    /**
     * Generates the command to list byobu sessions with markers.
     * The output format will be:
     *   __BYOBU_SESS_START_7x9K2mN__
     *   session1
     *   session2
     *   __BYOBU_SESS_END_7x9K2mN__
     */
    public String getListSessionsCommand() {
        return "echo '" + MARKER_START + "' && " +
               "byobu list-sessions 2>/dev/null | awk -F: '{print $1}' && " +
               "echo '" + MARKER_END + "'\n";
    }

    /**
     * Generates the command to attach to a specific byobu session.
     */
    public String getAttachCommand(String sessionName) {
        // Escape single quotes in session name for safety
        String escapedName = sessionName.replace("'", "'\\''");
        return "byobu attach -t '" + escapedName + "'\n";
    }

    /**
     * Sends the list sessions command and reads the result from terminal buffer.
     * 
     * @param session The terminal session to use
     * @param callback Callback to receive the parsed session list
     */
    public void listSessions(TerminalSession session, SessionListCallback callback) {
        if (session == null) {
            callback.onError("No active terminal session");
            return;
        }

        // Send the marker-wrapped command
        String command = getListSessionsCommand();
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);

        // Wait for output, then parse
        mainHandler.postDelayed(() -> {
            String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
            List<String> sessions = parseSessionList(transcript);
            
            if (sessions == null) {
                callback.onError("Could not find session markers in output");
            } else if (sessions.isEmpty()) {
                callback.onError("No byobu sessions found");
            } else {
                callback.onSessionsFound(sessions);
            }
        }, READ_DELAY_MS);
    }

    /**
     * Parses the terminal transcript to extract session names between markers.
     * 
     * @param transcript The full terminal transcript text
     * @return List of session names, or null if markers not found
     */
    public List<String> parseSessionList(String transcript) {
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
        List<String> sessions = new ArrayList<>();
        String[] lines = betweenMarkers.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip empty lines and lines that are just whitespace
            if (!trimmed.isEmpty() && !trimmed.matches("^\\s*$")) {
                sessions.add(trimmed);
            }
        }

        return sessions;
    }

    /**
     * Sends the attach command for the specified session.
     * 
     * @param session The terminal session
     * @param sessionName The byobu session name to attach to
     */
    public void attachToSession(TerminalSession session, String sessionName) {
        if (session == null || sessionName == null) {
            return;
        }

        String command = getAttachCommand(sessionName);
        byte[] data = command.getBytes();
        session.write(data, 0, data.length);
    }
}


