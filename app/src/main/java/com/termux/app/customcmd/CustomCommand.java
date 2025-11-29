package com.termux.app.customcmd;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a custom command that can be saved and executed.
 */
public class CustomCommand {
    private String id;
    private String name;
    private String command;

    public CustomCommand(String id, String name, String command) {
        this.id = id;
        this.name = name;
        this.command = command;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Convert to JSON for storage.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("command", command);
        return json;
    }

    /**
     * Create from JSON.
     */
    public static CustomCommand fromJson(JSONObject json) throws JSONException {
        return new CustomCommand(
            json.getString("id"),
            json.getString("name"),
            json.getString("command")
        );
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}

