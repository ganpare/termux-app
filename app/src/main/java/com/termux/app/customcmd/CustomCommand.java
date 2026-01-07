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
    private String folderId;  // null means root level
    private int order;

    public CustomCommand(String id, String name, String command) {
        this.id = id;
        this.name = name;
        this.command = command;
        this.folderId = null;
        this.order = 0;
    }

    public CustomCommand(String id, String name, String command, String folderId, int order) {
        this.id = id;
        this.name = name;
        this.command = command;
        this.folderId = folderId;
        this.order = order;
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

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * Check if this command is in the root level (no folder).
     */
    public boolean isRootLevel() {
        return folderId == null || folderId.isEmpty();
    }

    /**
     * Convert to JSON for storage.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("command", command);
        json.put("folderId", folderId);
        json.put("order", order);
        return json;
    }

    /**
     * Create from JSON.
     */
    public static CustomCommand fromJson(JSONObject json) throws JSONException {
        return new CustomCommand(
            json.getString("id"),
            json.getString("name"),
            json.getString("command"),
            json.optString("folderId", null),
            json.optInt("order", 0)
        );
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}

