package com.termux.app.byobu;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for Byobu commands and their descriptions.
 */
public class ByobuCommandHelper {

    /**
     * Command type enumeration.
     */
    public enum CommandType {
        /** Normal shell command (text) */
        SHELL_COMMAND,
        /** Function key (escape sequence) */
        FUNCTION_KEY,
        /** Info only (not executable) */
        INFO_ONLY
    }

    /**
     * Represents a byobu command with description.
     */
    public static class ByobuCommand {
        private final String command;
        private final String description;
        private final String category;
        private final CommandType type;
        private final String escapeSequence;

        public ByobuCommand(String category, String description, String command) {
            this(category, description, command, CommandType.SHELL_COMMAND, null);
        }

        public ByobuCommand(String category, String description, String command, CommandType type, String escapeSequence) {
            this.category = category;
            this.description = description;
            this.command = command;
            this.type = type;
            this.escapeSequence = escapeSequence;
        }

        public String getCommand() {
            return command;
        }

        public String getDescription() {
            return description;
        }

        public String getCategory() {
            return category;
        }

        public CommandType getType() {
            return type;
        }

        public String getEscapeSequence() {
            return escapeSequence;
        }

        public boolean isExecutable() {
            return type != CommandType.INFO_ONLY;
        }

        @NonNull
        @Override
        public String toString() {
            return description + ":\n" + command;
        }
    }

    // Function key escape sequences (VT100/xterm style)
    public static final String ESC_F1 = "\u001bOP";
    public static final String ESC_F2 = "\u001bOQ";
    public static final String ESC_F3 = "\u001bOR";
    public static final String ESC_F4 = "\u001bOS";
    public static final String ESC_F5 = "\u001b[15~";
    public static final String ESC_F6 = "\u001b[17~";
    public static final String ESC_F7 = "\u001b[18~";
    public static final String ESC_F8 = "\u001b[19~";
    public static final String ESC_F9 = "\u001b[20~";
    public static final String ESC_F10 = "\u001b[21~";
    public static final String ESC_F11 = "\u001b[23~";
    public static final String ESC_F12 = "\u001b[24~";

    // Shift+Function key escape sequences
    public static final String ESC_SHIFT_F2 = "\u001b[1;2Q";
    public static final String ESC_SHIFT_F3 = "\u001b[1;2R";
    public static final String ESC_SHIFT_F4 = "\u001b[1;2S";

    // Arrow keys with Shift
    public static final String ESC_SHIFT_UP = "\u001b[1;2A";
    public static final String ESC_SHIFT_DOWN = "\u001b[1;2B";
    public static final String ESC_SHIFT_RIGHT = "\u001b[1;2C";
    public static final String ESC_SHIFT_LEFT = "\u001b[1;2D";

    /**
     * Get a list of commonly used byobu commands organized by category.
     */
    @NonNull
    public static List<ByobuCommand> getCommandList() {
        List<ByobuCommand> commands = new ArrayList<>();

        // Session Management
        commands.add(new ByobuCommand("セッション管理", "セッション一覧を表示", "byobu list-sessions"));
        commands.add(new ByobuCommand("セッション管理", "セッションに接続", "byobu attach -t <session_name>"));
        commands.add(new ByobuCommand("セッション管理", "新しいセッションを作成", "byobu new -s <session_name>"));
        commands.add(new ByobuCommand("セッション管理", "セッションから切り離し", "byobu detach"));
        commands.add(new ByobuCommand("セッション管理", "セッションを終了", "byobu kill-session -t <session_name>"));

        // Window Management
        commands.add(new ByobuCommand("ウィンドウ管理", "ウィンドウ一覧を表示", "byobu list-windows -t <session_name>"));
        commands.add(new ByobuCommand("ウィンドウ管理", "新しいウィンドウを作成", "byobu new-window -n <window_name> -t <session_name>"));
        commands.add(new ByobuCommand("ウィンドウ管理", "ウィンドウを選択", "byobu select-window -t <window_index>"));

        // Pane Management
        commands.add(new ByobuCommand("ペイン管理", "ペインを縦に分割", "byobu split-window -v"));
        commands.add(new ByobuCommand("ペイン管理", "ペインを横に分割", "byobu split-window -h"));
        commands.add(new ByobuCommand("ペイン管理", "ペインを選択", "byobu select-pane -t <pane_index>"));
        commands.add(new ByobuCommand("ペイン管理", "ペインを閉じる", "byobu kill-pane -t <pane_index>"));

        // Function Keys (executable via escape sequences)
        commands.add(new ByobuCommand("ファンクションキー", "F1: ヘルプを表示", "F1", CommandType.FUNCTION_KEY, ESC_F1));
        commands.add(new ByobuCommand("ファンクションキー", "F2: 新しいウィンドウ", "F2", CommandType.FUNCTION_KEY, ESC_F2));
        commands.add(new ByobuCommand("ファンクションキー", "F3: 前のウィンドウ", "F3", CommandType.FUNCTION_KEY, ESC_F3));
        commands.add(new ByobuCommand("ファンクションキー", "F4: 次のウィンドウ", "F4", CommandType.FUNCTION_KEY, ESC_F4));
        commands.add(new ByobuCommand("ファンクションキー", "F5: 再読み込み", "F5", CommandType.FUNCTION_KEY, ESC_F5));
        commands.add(new ByobuCommand("ファンクションキー", "F6: デタッチ", "F6", CommandType.FUNCTION_KEY, ESC_F6));
        commands.add(new ByobuCommand("ファンクションキー", "F7: スクロールモード", "F7", CommandType.FUNCTION_KEY, ESC_F7));
        commands.add(new ByobuCommand("ファンクションキー", "F8: ウィンドウ名変更", "F8", CommandType.FUNCTION_KEY, ESC_F8));
        commands.add(new ByobuCommand("ファンクションキー", "F9: 設定メニュー", "F9", CommandType.FUNCTION_KEY, ESC_F9));

        // Shift+Function Keys
        commands.add(new ByobuCommand("Shift+ファンクションキー", "Shift+F2: 水平分割", "Shift+F2", CommandType.FUNCTION_KEY, ESC_SHIFT_F2));
        commands.add(new ByobuCommand("Shift+ファンクションキー", "Shift+F3: 垂直分割", "Shift+F3", CommandType.FUNCTION_KEY, ESC_SHIFT_F3));
        commands.add(new ByobuCommand("Shift+ファンクションキー", "Shift+F4: ペイン間移動", "Shift+F4", CommandType.FUNCTION_KEY, ESC_SHIFT_F4));

        // Shift+Arrow Keys
        commands.add(new ByobuCommand("Shift+矢印キー", "Shift+↑: 上のペインへ", "Shift+Up", CommandType.FUNCTION_KEY, ESC_SHIFT_UP));
        commands.add(new ByobuCommand("Shift+矢印キー", "Shift+↓: 下のペインへ", "Shift+Down", CommandType.FUNCTION_KEY, ESC_SHIFT_DOWN));
        commands.add(new ByobuCommand("Shift+矢印キー", "Shift+←: 左のペインへ", "Shift+Left", CommandType.FUNCTION_KEY, ESC_SHIFT_LEFT));
        commands.add(new ByobuCommand("Shift+矢印キー", "Shift+→: 右のペインへ", "Shift+Right", CommandType.FUNCTION_KEY, ESC_SHIFT_RIGHT));

        // Advanced
        commands.add(new ByobuCommand("高度な操作", "コマンドを送信", "byobu send-keys -t <target> '<command>' C-m"));
        commands.add(new ByobuCommand("高度な操作", "セッション情報を表示", "byobu display-message -p '#S:#I.#P'"));

        return commands;
    }

    /**
     * Get commands by category.
     */
    @NonNull
    public static List<ByobuCommand> getCommandsByCategory(@NonNull String category) {
        List<ByobuCommand> allCommands = getCommandList();
        List<ByobuCommand> filtered = new ArrayList<>();
        for (ByobuCommand cmd : allCommands) {
            if (category.equals(cmd.getCategory())) {
                filtered.add(cmd);
            }
        }
        return filtered;
    }

    /**
     * Get all unique categories.
     */
    @NonNull
    public static List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        for (ByobuCommand cmd : getCommandList()) {
            if (!categories.contains(cmd.getCategory())) {
                categories.add(cmd.getCategory());
            }
        }
        return categories;
    }

    /**
     * Check if a command contains variables that need user input.
     */
    public static boolean hasVariables(@NonNull String command) {
        return command.contains("<") && command.contains(">");
    }

    /**
     * Extract variable names from a command.
     * Returns a list of variable names like ["session_name", "window_index"]
     */
    @NonNull
    public static List<String> extractVariables(@NonNull String command) {
        List<String> variables = new ArrayList<>();
        int start = 0;
        while (true) {
            int varStart = command.indexOf('<', start);
            if (varStart == -1) break;
            int varEnd = command.indexOf('>', varStart);
            if (varEnd == -1) break;
            String varName = command.substring(varStart + 1, varEnd);
            if (!variables.contains(varName)) {
                variables.add(varName);
            }
            start = varEnd + 1;
        }
        return variables;
    }

    /**
     * Replace variables in command with provided values.
     * @param command The command template
     * @param variableValues Map of variable name to value
     * @return Command with variables replaced
     */
    @NonNull
    public static String replaceVariables(@NonNull String command, @NonNull java.util.Map<String, String> variableValues) {
        String result = command;
        for (java.util.Map.Entry<String, String> entry : variableValues.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }

    /**
     * Get a human-readable prompt text for a variable.
     */
    @NonNull
    public static String getVariablePrompt(@NonNull String variableName) {
        switch (variableName) {
            case "session_name":
                return "セッション名を入力:";
            case "window_name":
                return "ウィンドウ名を入力:";
            case "window_index":
                return "ウィンドウ番号を入力 (0から始まる):";
            case "pane_index":
                return "ペイン番号を入力 (0から始まる):";
            case "target":
                return "ターゲットを入力 (session:window.pane):";
            case "command":
                return "実行するコマンドを入力:";
            default:
                return variableName + "を入力:";
        }
    }
}

