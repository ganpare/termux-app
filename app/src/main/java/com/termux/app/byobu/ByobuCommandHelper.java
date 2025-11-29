package com.termux.app.byobu;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for Byobu commands and their descriptions.
 */
public class ByobuCommandHelper {

    /**
     * Represents a byobu command with description.
     */
    public static class ByobuCommand {
        private final String command;
        private final String description;
        private final String category;

        public ByobuCommand(String category, String description, String command) {
            this.category = category;
            this.description = description;
            this.command = command;
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

        @NonNull
        @Override
        public String toString() {
            return description + ":\n" + command;
        }
    }

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

        // Useful Shortcuts (説明用)
        commands.add(new ByobuCommand("キーバインド", "ヘルプを表示", "F1 (byobu内で)"));
        commands.add(new ByobuCommand("キーバインド", "新しいウィンドウ", "F2"));
        commands.add(new ByobuCommand("キーバインド", "前のウィンドウ", "Shift+F2 または F3"));
        commands.add(new ByobuCommand("キーバインド", "次のウィンドウ", "F2 または F4"));
        commands.add(new ByobuCommand("キーバインド", "ペイン分割（縦）", "Shift+F2"));
        commands.add(new ByobuCommand("キーバインド", "ペイン分割（横）", "Shift+F3"));
        commands.add(new ByobuCommand("キーバインド", "ペイン間移動", "Shift+方向キー"));

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

