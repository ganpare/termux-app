package com.termux.app.customcmd;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages custom commands storage and retrieval using SharedPreferences.
 */
public class CustomCommandManager {
    private static final String LOG_TAG = "CustomCommandManager";
    private static final String PREFS_NAME = "custom_commands_prefs";
    private static final String KEY_COMMANDS = "commands";

    private final SharedPreferences prefs;

    public CustomCommandManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get all saved custom commands.
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
        
        return commands;
    }

    /**
     * Save a new custom command.
     */
    public void saveCommand(String name, String command) {
        List<CustomCommand> commands = getAllCommands();
        String id = UUID.randomUUID().toString();
        commands.add(new CustomCommand(id, name, command));
        saveAllCommands(commands);
    }

    /**
     * Update an existing command.
     */
    public void updateCommand(String id, String name, String command) {
        List<CustomCommand> commands = getAllCommands();
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getId().equals(id)) {
                commands.set(i, new CustomCommand(id, name, command));
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
    public CustomCommand getCommandById(String id) {
        for (CustomCommand cmd : getAllCommands()) {
            if (cmd.getId().equals(id)) {
                return cmd;
            }
        }
        return null;
    }
}

