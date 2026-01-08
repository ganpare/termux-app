package com.termux.app.customcmd;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a folder for organizing custom commands.
 */
public class CommandFolder {
    private String id;
    private String name;
    private int order;

    public CommandFolder(String id, String name, int order) {
        this.id = id;
        this.name = name;
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

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * Convert to JSON for storage.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("order", order);
        return json;
    }

    /**
     * Create from JSON.
     */
    public static CommandFolder fromJson(JSONObject json) throws JSONException {
        return new CommandFolder(
            json.getString("id"),
            json.getString("name"),
            json.optInt("order", 0)
        );
    }

    @NonNull
    @Override
    public String toString() {
        return "📁 " + name;
    }
}



