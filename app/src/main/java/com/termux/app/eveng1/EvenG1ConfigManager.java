package com.termux.app.eveng1;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.preferences.SharedPreferenceUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages EVEN G1 connection configurations using SharedPreferences.
 * Similar to SshConfigManager structure.
 */
public class EvenG1ConfigManager {

    private static final String LOG_TAG = "EvenG1ConfigManager";
    private static final String PREFS_NAME = "eveng1_connections";
    private static final String KEY_CONNECTIONS = "connections_json";

    private final Context context;
    private final SharedPreferences sharedPreferences;

    /**
     * Creates a new config manager.
     *
     * @param context Application or Activity context
     */
    public EvenG1ConfigManager(@NonNull Context context) {
        this.context = context;
        this.sharedPreferences = SharedPreferenceUtils.getPrivateSharedPreferences(context, PREFS_NAME);
    }

    // ========================
    // Load / Save All Configs
    // ========================

    /**
     * Saves all configurations to SharedPreferences.
     *
     * @param configs List of configurations
     */
    public void saveConfigs(@NonNull List<EvenG1ConnectionConfig> configs) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (EvenG1ConnectionConfig config : configs) {
                jsonArray.put(configToJson(config));
            }
            sharedPreferences.edit().putString(KEY_CONNECTIONS, jsonArray.toString()).apply();
            Logger.logDebug(LOG_TAG, "Saved " + configs.size() + " EVEN G1 configurations");
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save EVEN G1 configurations", e);
        }
    }

    /**
     * Loads all configurations from SharedPreferences.
     *
     * @return List of configurations (empty if none saved)
     */
    @NonNull
    public List<EvenG1ConnectionConfig> loadConfigs() {
        List<EvenG1ConnectionConfig> configs = new ArrayList<>();
        String jsonString = sharedPreferences.getString(KEY_CONNECTIONS, null);

        if (DataUtils.isNullOrEmpty(jsonString)) {
            return configs;
        }

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                EvenG1ConnectionConfig config = jsonToConfig(jsonArray.getJSONObject(i));
                if (config != null) {
                    configs.add(config);
                }
            }
            Logger.logDebug(LOG_TAG, "Loaded " + configs.size() + " EVEN G1 configurations");
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load EVEN G1 configurations", e);
        }

        return configs;
    }

    // ========================
    // Single Config Operations
    // ========================

    /**
     * Saves a single configuration (adds or updates).
     * If ID is empty, generates a new UUID.
     *
     * @param config Configuration to save
     */
    public void saveConfig(@NonNull EvenG1ConnectionConfig config) {
        if (DataUtils.isNullOrEmpty(config.getId())) {
            config.setId(UUID.randomUUID().toString());
        }

        List<EvenG1ConnectionConfig> configs = loadConfigs();

        // Remove existing config with same ID
        configs.removeIf(c -> config.getId().equals(c.getId()));

        // Add updated config
        configs.add(config);

        saveConfigs(configs);
    }

    /**
     * Deletes a configuration by ID.
     *
     * @param id Configuration ID
     * @return true if deleted, false if not found
     */
    public boolean deleteConfig(@NonNull String id) {
        List<EvenG1ConnectionConfig> configs = loadConfigs();
        boolean removed = configs.removeIf(c -> id.equals(c.getId()));

        if (removed) {
            saveConfigs(configs);
        }

        return removed;
    }

    /**
     * Gets a configuration by ID.
     *
     * @param id Configuration ID
     * @return Configuration, or null if not found
     */
    @Nullable
    public EvenG1ConnectionConfig getConfig(@NonNull String id) {
        List<EvenG1ConnectionConfig> configs = loadConfigs();
        for (EvenG1ConnectionConfig config : configs) {
            if (id.equals(config.getId())) {
                return config;
            }
        }
        return null;
    }

    // ========================
    // JSON Conversion
    // ========================

    /**
     * Converts configuration to JSON object.
     *
     * @param config Configuration
     * @return JSON object
     * @throws JSONException if conversion fails
     */
    @NonNull
    private JSONObject configToJson(@NonNull EvenG1ConnectionConfig config) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", config.getId());
        json.put("name", config.getName());
        json.put("channelNumber", config.getChannelNumber());
        json.put("leftDeviceName", config.getLeftDeviceName());
        json.put("rightDeviceName", config.getRightDeviceName());
        json.put("leftDeviceAddress", config.getLeftDeviceAddress());
        json.put("rightDeviceAddress", config.getRightDeviceAddress());
        return json;
    }

    /**
     * Converts JSON object to configuration.
     *
     * @param json JSON object
     * @return Configuration, or null if conversion fails
     */
    @Nullable
    private EvenG1ConnectionConfig jsonToConfig(@NonNull JSONObject json) {
        try {
            EvenG1ConnectionConfig config = new EvenG1ConnectionConfig();
            config.setId(json.optString("id", UUID.randomUUID().toString()));
            config.setName(json.optString("name", ""));
            config.setChannelNumber(json.optString("channelNumber", ""));
            config.setLeftDeviceName(json.optString("leftDeviceName", null));
            config.setRightDeviceName(json.optString("rightDeviceName", null));
            config.setLeftDeviceAddress(json.optString("leftDeviceAddress", null));
            config.setRightDeviceAddress(json.optString("rightDeviceAddress", null));
            return config;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to parse EVEN G1 config from JSON", e);
            return null;
        }
    }
}
