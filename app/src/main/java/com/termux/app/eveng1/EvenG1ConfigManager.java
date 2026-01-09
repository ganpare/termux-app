package com.termux.app.eveng1;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.preferences.SharedPreferenceUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manages a single saved EVEN G1 device configuration using SharedPreferences.
 * Simplified to store only one device for quick reconnection.
 */
public class EvenG1ConfigManager {

    private static final String LOG_TAG = "EvenG1ConfigManager";
    private static final String PREFS_NAME = "eveng1_connections";
    private static final String KEY_SAVED_DEVICE = "saved_device_json";

    private final SharedPreferences sharedPreferences;

    /**
     * Creates a new config manager.
     *
     * @param context Application or Activity context
     */
    public EvenG1ConfigManager(@NonNull Context context) {
        this.sharedPreferences = SharedPreferenceUtils.getPrivateSharedPreferences(context, PREFS_NAME);
    }

    // ========================
    // Saved Device Operations
    // ========================

    /**
     * Saves current connected device pair for quick reconnection.
     *
     * @param pair The connected device pair to save
     */
    public void saveCurrentDevice(@NonNull EvenG1DevicePair pair) {
        EvenG1Device left = pair.getLeftDevice();
        EvenG1Device right = pair.getRightDevice();
        
        if (left == null || right == null) {
            Logger.logError(LOG_TAG, "Cannot save: device pair incomplete");
            return;
        }
        
        try {
            JSONObject json = new JSONObject();
            json.put("channelNumber", left.getChannelNumber());
            json.put("leftDeviceName", left.getName());
            json.put("rightDeviceName", right.getName());
            json.put("leftDeviceAddress", left.getAddress());
            json.put("rightDeviceAddress", right.getAddress());
            
            sharedPreferences.edit().putString(KEY_SAVED_DEVICE, json.toString()).apply();
            Logger.logDebug(LOG_TAG, "Saved G1 device: Channel " + left.getChannelNumber());
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save G1 device", e);
        }
    }

    /**
     * Gets the saved device configuration.
     *
     * @return Saved config, or null if none saved
     */
    @Nullable
    public SavedG1Device getSavedDevice() {
        String jsonString = sharedPreferences.getString(KEY_SAVED_DEVICE, null);
        
        if (DataUtils.isNullOrEmpty(jsonString)) {
            return null;
        }
        
        try {
            JSONObject json = new JSONObject(jsonString);
            return new SavedG1Device(
                json.getString("channelNumber"),
                json.getString("leftDeviceName"),
                json.getString("rightDeviceName"),
                json.getString("leftDeviceAddress"),
                json.getString("rightDeviceAddress")
            );
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load saved G1 device", e);
            return null;
        }
    }

    /**
     * Checks if a device is saved.
     *
     * @return true if a device is saved
     */
    public boolean hasSavedDevice() {
        return getSavedDevice() != null;
    }

    /**
     * Clears the saved device.
     */
    public void clearSavedDevice() {
        sharedPreferences.edit().remove(KEY_SAVED_DEVICE).apply();
        Logger.logDebug(LOG_TAG, "Cleared saved G1 device");
    }

    // ========================
    // Saved Device Data Class
    // ========================

    /**
     * Simple data class for saved G1 device info.
     */
    public static class SavedG1Device {
        public final String channelNumber;
        public final String leftDeviceName;
        public final String rightDeviceName;
        public final String leftDeviceAddress;
        public final String rightDeviceAddress;

        public SavedG1Device(String channelNumber, String leftDeviceName, String rightDeviceName,
                            String leftDeviceAddress, String rightDeviceAddress) {
            this.channelNumber = channelNumber;
            this.leftDeviceName = leftDeviceName;
            this.rightDeviceName = rightDeviceName;
            this.leftDeviceAddress = leftDeviceAddress;
            this.rightDeviceAddress = rightDeviceAddress;
        }

        @NonNull
        @Override
        public String toString() {
            return "G1 Channel " + channelNumber;
        }
    }
}
