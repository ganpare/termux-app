package com.termux.app.customcmd;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Manages custom commands and folders storage and retrieval using
 * SharedPreferences.
 * Supports folder organization and import/export functionality.
 */
public class CustomCommandManager {
    private static final String LOG_TAG = "CustomCommandManager";
    private static final String PREFS_NAME = "custom_commands_prefs";
    private static final String KEY_COMMANDS = "commands";
    private static final String KEY_FOLDERS = "folders";
    private static final int EXPORT_VERSION = 1;

    private final SharedPreferences prefs;
    private final Context context;

    public CustomCommandManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ==================== Folder Management ====================

    /**
     * Get all folders sorted by order.
     */
    @NonNull
    public List<CommandFolder> getAllFolders() {
        List<CommandFolder> folders = new ArrayList<>();
        String json = prefs.getString(KEY_FOLDERS, "[]");

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                folders.add(CommandFolder.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Logger.logError(LOG_TAG, "Failed to parse folders: " + e.getMessage());
        }

        Collections.sort(folders, Comparator.comparingInt(CommandFolder::getOrder));
        return folders;
    }

    /**
     * Save a new folder.
     */
    public CommandFolder createFolder(String name) {
        List<CommandFolder> folders = getAllFolders();
        String id = UUID.randomUUID().toString();
        int maxOrder = folders.stream().mapToInt(CommandFolder::getOrder).max().orElse(-1);
        CommandFolder folder = new CommandFolder(id, name, maxOrder + 1);
        folders.add(folder);
        saveAllFolders(folders);
        return folder;
    }

    /**
     * Update folder name.
     */
    public void updateFolder(String id, String name) {
        List<CommandFolder> folders = getAllFolders();
        for (CommandFolder folder : folders) {
            if (folder.getId().equals(id)) {
                folder.setName(name);
                break;
            }
        }
        saveAllFolders(folders);
    }

    /**
     * Delete a folder and optionally its commands.
     * 
     * @param deleteCommands If true, delete all commands in the folder. If false,
     *                       move them to root.
     */
    public void deleteFolder(String id, boolean deleteCommands) {
        List<CommandFolder> folders = getAllFolders();
        folders.removeIf(f -> f.getId().equals(id));
        saveAllFolders(folders);

        List<CustomCommand> commands = getAllCommands();
        if (deleteCommands) {
            commands.removeIf(cmd -> id.equals(cmd.getFolderId()));
        } else {
            for (CustomCommand cmd : commands) {
                if (id.equals(cmd.getFolderId())) {
                    cmd.setFolderId(null);
                }
            }
        }
        saveAllCommands(commands);
    }

    /**
     * Get folder by ID.
     */
    @Nullable
    public CommandFolder getFolderById(String id) {
        for (CommandFolder folder : getAllFolders()) {
            if (folder.getId().equals(id)) {
                return folder;
            }
        }
        return null;
    }

    /**
     * Save all folders to SharedPreferences.
     */
    private void saveAllFolders(List<CommandFolder> folders) {
        try {
            JSONArray array = new JSONArray();
            for (CommandFolder folder : folders) {
                array.put(folder.toJson());
            }
            prefs.edit().putString(KEY_FOLDERS, array.toString()).apply();
        } catch (JSONException e) {
            Logger.logError(LOG_TAG, "Failed to save folders: " + e.getMessage());
        }
    }

    // ==================== Command Management ====================

    /**
     * Get all saved custom commands sorted by order.
     */
    @NonNull
    public List<CustomCommand> getAllCommands() {
        List<CustomCommand> commands = new ArrayList<>();
        String json = prefs.getString(KEY_COMMANDS, "[]");

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                commands.add(CustomCommand.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Logger.logError(LOG_TAG, "Failed to parse commands: " + e.getMessage());
        }

        Collections.sort(commands, Comparator.comparingInt(CustomCommand::getOrder));
        return commands;
    }

    /**
     * Get commands in a specific folder (null for root level).
     */
    @NonNull
    public List<CustomCommand> getCommandsInFolder(@Nullable String folderId) {
        List<CustomCommand> result = new ArrayList<>();
        for (CustomCommand cmd : getAllCommands()) {
            if (folderId == null) {
                if (cmd.isRootLevel()) {
                    result.add(cmd);
                }
            } else {
                if (folderId.equals(cmd.getFolderId())) {
                    result.add(cmd);
                }
            }
        }
        return result;
    }

    /**
     * Get command count in a folder.
     */
    public int getCommandCountInFolder(String folderId) {
        return getCommandsInFolder(folderId).size();
    }

    /**
     * Save a new custom command.
     */
    public CustomCommand saveCommand(String name, String command, @Nullable String folderId) {
        List<CustomCommand> commands = getAllCommands();
        String id = UUID.randomUUID().toString();
        int maxOrder = commands.stream().mapToInt(CustomCommand::getOrder).max().orElse(-1);
        CustomCommand cmd = new CustomCommand(id, name, command, folderId, maxOrder + 1);
        commands.add(cmd);
        saveAllCommands(commands);
        return cmd;
    }

    /**
     * Save a new custom command (legacy method for compatibility).
     */
    public void saveCommand(String name, String command) {
        saveCommand(name, command, null);
    }

    /**
     * Update an existing command.
     */
    public void updateCommand(String id, String name, String command, @Nullable String folderId) {
        List<CustomCommand> commands = getAllCommands();
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getId().equals(id)) {
                CustomCommand updated = new CustomCommand(id, name, command, folderId, commands.get(i).getOrder());
                commands.set(i, updated);
                break;
            }
        }
        saveAllCommands(commands);
    }

    /**
     * Update an existing command (legacy method).
     */
    public void updateCommand(String id, String name, String command) {
        CustomCommand existing = getCommandById(id);
        String folderId = existing != null ? existing.getFolderId() : null;
        updateCommand(id, name, command, folderId);
    }

    /**
     * Move command to a different folder.
     */
    public void moveCommandToFolder(String commandId, @Nullable String folderId) {
        List<CustomCommand> commands = getAllCommands();
        for (CustomCommand cmd : commands) {
            if (cmd.getId().equals(commandId)) {
                cmd.setFolderId(folderId);
                break;
            }
        }
        saveAllCommands(commands);
    }

    /**
     * Delete a command by ID.
     */
    public void deleteCommand(String id) {
        List<CustomCommand> commands = getAllCommands();
        commands.removeIf(cmd -> cmd.getId().equals(id));
        saveAllCommands(commands);
    }

    /**
     * Reorder commands (move command at fromIndex to toIndex).
     */
    public void reorderCommands(int fromIndex, int toIndex) {
        List<CustomCommand> commands = getAllCommands();
        if (fromIndex < 0 || fromIndex >= commands.size() ||
                toIndex < 0 || toIndex >= commands.size()) {
            return;
        }
        CustomCommand cmd = commands.remove(fromIndex);
        commands.add(toIndex, cmd);
        // Update order values
        for (int i = 0; i < commands.size(); i++) {
            commands.get(i).setOrder(i);
        }
        saveAllCommands(commands);
    }

    /**
     * Save all commands to SharedPreferences.
     */
    private void saveAllCommands(List<CustomCommand> commands) {
        try {
            JSONArray array = new JSONArray();
            for (CustomCommand cmd : commands) {
                array.put(cmd.toJson());
            }
            prefs.edit().putString(KEY_COMMANDS, array.toString()).apply();
        } catch (JSONException e) {
            Logger.logError(LOG_TAG, "Failed to save commands: " + e.getMessage());
        }
    }

    /**
     * Get a command by ID.
     */
    @Nullable
    public CustomCommand getCommandById(String id) {
        for (CustomCommand cmd : getAllCommands()) {
            if (cmd.getId().equals(id)) {
                return cmd;
            }
        }
        return null;
    }

    // ==================== Export/Import ====================

    /**
     * Export all folders and commands to JSON string.
     */
    @NonNull
    public String exportToJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", EXPORT_VERSION);
        root.put("exportDate", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));

        JSONArray foldersArray = new JSONArray();
        for (CommandFolder folder : getAllFolders()) {
            foldersArray.put(folder.toJson());
        }
        root.put("folders", foldersArray);

        JSONArray commandsArray = new JSONArray();
        for (CustomCommand cmd : getAllCommands()) {
            commandsArray.put(cmd.toJson());
        }
        root.put("commands", commandsArray);

        return root.toString(2);
    }

    /**
     * Export to a file via OutputStream.
     */
    public void exportToFile(OutputStream outputStream) throws IOException, JSONException {
        String json = exportToJson();
        outputStream.write(json.getBytes("UTF-8"));
        outputStream.flush();
    }

    /**
     * Parse import data from JSON string.
     * Returns an ImportData object containing folders and commands to import.
     */
    @NonNull
    public ImportData parseImportJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version", 1);

        List<CommandFolder> folders = new ArrayList<>();
        JSONArray foldersArray = root.optJSONArray("folders");
        if (foldersArray != null) {
            for (int i = 0; i < foldersArray.length(); i++) {
                folders.add(CommandFolder.fromJson(foldersArray.getJSONObject(i)));
            }
        }

        List<CustomCommand> commands = new ArrayList<>();
        JSONArray commandsArray = root.optJSONArray("commands");
        if (commandsArray != null) {
            for (int i = 0; i < commandsArray.length(); i++) {
                commands.add(CustomCommand.fromJson(commandsArray.getJSONObject(i)));
            }
        }

        return new ImportData(version, folders, commands);
    }

    /**
     * Parse import data from a file via InputStream.
     */
    @NonNull
    public ImportData parseImportFile(InputStream inputStream) throws IOException, JSONException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return parseImportJson(sb.toString());
    }

    /**
     * Import data with the specified mode.
     * 
     * @param data The parsed import data
     * @param mode Import mode: MERGE (add new only), REPLACE (overwrite
     *             duplicates), or CLEAR_AND_IMPORT (clear all first)
     */
    public ImportResult importData(ImportData data, ImportMode mode) {
        int foldersAdded = 0;
        int foldersUpdated = 0;
        int commandsAdded = 0;
        int commandsUpdated = 0;

        List<CommandFolder> existingFolders = getAllFolders();
        List<CustomCommand> existingCommands = getAllCommands();

        if (mode == ImportMode.CLEAR_AND_IMPORT) {
            existingFolders.clear();
            existingCommands.clear();
        }

        // Create a mapping from imported folder IDs to new IDs (in case of conflicts)
        Map<String, String> folderIdMapping = new HashMap<>();

        // Import folders
        for (CommandFolder importFolder : data.folders) {
            CommandFolder existing = findFolderByName(existingFolders, importFolder.getName());

            if (existing != null) {
                if (mode == ImportMode.REPLACE || mode == ImportMode.CLEAR_AND_IMPORT) {
                    // Map old ID to existing ID
                    folderIdMapping.put(importFolder.getId(), existing.getId());
                    foldersUpdated++;
                } else {
                    // MERGE: keep existing, map ID
                    folderIdMapping.put(importFolder.getId(), existing.getId());
                }
            } else {
                // New folder - generate new ID
                String newId = UUID.randomUUID().toString();
                folderIdMapping.put(importFolder.getId(), newId);
                int maxOrder = existingFolders.stream().mapToInt(CommandFolder::getOrder).max().orElse(-1);
                existingFolders.add(new CommandFolder(newId, importFolder.getName(), maxOrder + 1));
                foldersAdded++;
            }
        }

        // Import commands
        for (CustomCommand importCmd : data.commands) {
            // Map folder ID
            String mappedFolderId = null;
            if (importCmd.getFolderId() != null) {
                mappedFolderId = folderIdMapping.get(importCmd.getFolderId());
            }

            CustomCommand existing = findCommandByName(existingCommands, importCmd.getName());

            if (existing != null) {
                if (mode == ImportMode.REPLACE || mode == ImportMode.CLEAR_AND_IMPORT) {
                    existing.setCommand(importCmd.getCommand());
                    existing.setFolderId(mappedFolderId);
                    commandsUpdated++;
                }
                // MERGE: skip existing
            } else {
                // New command - generate new ID
                String newId = UUID.randomUUID().toString();
                int maxOrder = existingCommands.stream().mapToInt(CustomCommand::getOrder).max().orElse(-1);
                existingCommands.add(new CustomCommand(newId, importCmd.getName(), importCmd.getCommand(),
                        mappedFolderId, maxOrder + 1));
                commandsAdded++;
            }
        }

        saveAllFolders(existingFolders);
        saveAllCommands(existingCommands);

        return new ImportResult(foldersAdded, foldersUpdated, commandsAdded, commandsUpdated);
    }

    @Nullable
    private CommandFolder findFolderByName(List<CommandFolder> folders, String name) {
        for (CommandFolder f : folders) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    @Nullable
    private CustomCommand findCommandByName(List<CustomCommand> commands, String name) {
        for (CustomCommand c : commands) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    // ==================== Import Helper Classes ====================

    public enum ImportMode {
        MERGE, // Add new items only, skip duplicates
        REPLACE, // Add new items and overwrite duplicates
        CLEAR_AND_IMPORT // Clear all existing data and import
    }

    public static class ImportData {
        public final int version;
        public final List<CommandFolder> folders;
        public final List<CustomCommand> commands;

        public ImportData(int version, List<CommandFolder> folders, List<CustomCommand> commands) {
            this.version = version;
            this.folders = folders;
            this.commands = commands;
        }
    }

    public static class ImportResult {
        public final int foldersAdded;
        public final int foldersUpdated;
        public final int commandsAdded;
        public final int commandsUpdated;

        public ImportResult(int foldersAdded, int foldersUpdated, int commandsAdded, int commandsUpdated) {
            this.foldersAdded = foldersAdded;
            this.foldersUpdated = foldersUpdated;
            this.commandsAdded = commandsAdded;
            this.commandsUpdated = commandsUpdated;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.US,
                    "フォルダ: %d追加, %dスキップ\nコマンド: %d追加, %d更新",
                    foldersAdded, foldersUpdated, commandsAdded, commandsUpdated);
        }
    }

    /**
     * Ensures that default AI agent commands exist.
     */
    public void ensureDefaultCommands() {
        String folderName = "AI Agents";
        CommandFolder folder = findFolderByName(getAllFolders(), folderName);

        if (folder == null) {
            folder = createFolder(folderName);
        }

        String folderId = folder.getId();
        List<CustomCommand> existingCommands = getCommandsInFolder(folderId);

        // Define default commands
        // CC: claude
        // CA: cursor-agent
        // CD: codex
        // CZ: claudez
        String[][] defaults = {
                { "CC", "claude" },
                { "CC -r", "claude -r" },
                { "CA", "cursor-agent" },
                { "CA -r", "cursor-agent --resume" },
                { "CD", "codex" },
                { "CD -r", "codex resume" },
                { "CZ", "claudez" },
                { "CZ -r", "claudez -r" }
        };

        for (String[] def : defaults) {
            String name = def[0];
            String command = def[1];

            boolean exists = false;
            for (CustomCommand cmd : existingCommands) {
                if (cmd.getName().equals(name)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                saveCommand(name, command, folderId);
            }
        }
    }
}
