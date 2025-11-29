package com.termux.app.ssh;

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
 * Manages SSH connection configurations stored in SharedPreferences.
 */
public class SshConfigManager {

    private static final String LOG_TAG = "SshConfigManager";
    private static final String PREFS_NAME = "ssh_connections";
    private static final String KEY_CONNECTIONS = "connections_json";

    private final Context context;
    private final SharedPreferences sharedPreferences;

    public SshConfigManager(@NonNull Context context) {
        this.context = context;
        this.sharedPreferences = SharedPreferenceUtils.getPrivateSharedPreferences(context, PREFS_NAME);
    }

    /**
     * Save a list of SSH configurations.
     */
    public void saveConfigs(@NonNull List<SshConnectionConfig> configs) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (SshConnectionConfig config : configs) {
                jsonArray.put(configToJson(config));
            }
            sharedPreferences.edit().putString(KEY_CONNECTIONS, jsonArray.toString()).apply();
            Logger.logDebug(LOG_TAG, "Saved " + configs.size() + " SSH configurations");
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save SSH configurations", e);
        }
    }

    /**
     * Load all SSH configurations.
     */
    @NonNull
    public List<SshConnectionConfig> loadConfigs() {
        List<SshConnectionConfig> configs = new ArrayList<>();
        String jsonString = sharedPreferences.getString(KEY_CONNECTIONS, null);
        
        if (DataUtils.isNullOrEmpty(jsonString)) {
            return configs;
        }

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                SshConnectionConfig config = jsonToConfig(jsonArray.getJSONObject(i));
                if (config != null) {
                    configs.add(config);
                }
            }
            Logger.logDebug(LOG_TAG, "Loaded " + configs.size() + " SSH configurations");
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load SSH configurations", e);
        }

        return configs;
    }

    /**
     * Add or update a configuration.
     */
    public void saveConfig(@NonNull SshConnectionConfig config) {
        if (DataUtils.isNullOrEmpty(config.getId())) {
            config.setId(UUID.randomUUID().toString());
        }

        List<SshConnectionConfig> configs = loadConfigs();
        
        // Remove existing config with same ID
        configs.removeIf(c -> config.getId().equals(c.getId()));
        
        // Add updated config
        configs.add(config);
        
        saveConfigs(configs);
    }

    /**
     * Delete a configuration by ID.
     */
    public boolean deleteConfig(@NonNull String id) {
        List<SshConnectionConfig> configs = loadConfigs();
        boolean removed = configs.removeIf(c -> id.equals(c.getId()));
        
        if (removed) {
            saveConfigs(configs);
        }
        
        return removed;
    }

    /**
     * Get a configuration by ID.
     */
    @Nullable
    public SshConnectionConfig getConfig(@NonNull String id) {
        List<SshConnectionConfig> configs = loadConfigs();
        for (SshConnectionConfig config : configs) {
            if (id.equals(config.getId())) {
                return config;
            }
        }
        return null;
    }

    /**
     * Convert SshConnectionConfig to JSONObject.
     */
    @NonNull
    private JSONObject configToJson(@NonNull SshConnectionConfig config) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", config.getId());
        json.put("name", config.getName());
        json.put("host", config.getHost());
        json.put("port", config.getPort());
        json.put("username", config.getUsername());
        json.put("privateKeyPath", config.getPrivateKeyPath());
        json.put("usePassword", config.isUsePassword());
        // Note: Password is stored in plain text for now (should use Android Keystore in production)
        json.put("password", config.getPassword());
        json.put("additionalOptions", config.getAdditionalOptions());
        return json;
    }

    /**
     * Convert JSONObject to SshConnectionConfig.
     */
    @Nullable
    private SshConnectionConfig jsonToConfig(@NonNull JSONObject json) {
        try {
            SshConnectionConfig config = new SshConnectionConfig();
            config.setId(json.optString("id", UUID.randomUUID().toString()));
            config.setName(json.optString("name", ""));
            config.setHost(json.optString("host", ""));
            config.setPort(json.optInt("port", 22));
            config.setUsername(json.optString("username", ""));
            config.setPrivateKeyPath(json.optString("privateKeyPath", null));
            config.setUsePassword(json.optBoolean("usePassword", false));
            config.setPassword(json.optString("password", null));
            config.setAdditionalOptions(json.optString("additionalOptions", null));
            return config;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to parse SSH config from JSON", e);
            return null;
        }
    }
}


