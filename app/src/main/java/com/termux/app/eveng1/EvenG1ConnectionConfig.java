package com.termux.app.eveng1;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents an EVEN G1 connection configuration.
 * Similar to SshConnectionConfig, this stores saved connection settings.
 */
public class EvenG1ConnectionConfig {

    private String id;
    private String name;
    private String channelNumber;
    private String leftDeviceName;
    private String rightDeviceName;
    private String leftDeviceAddress;
    private String rightDeviceAddress;

    /**
     * Creates a new connection configuration with default values.
     */
    public EvenG1ConnectionConfig() {
        // Empty constructor
    }

    /**
     * Creates a new connection configuration.
     *
     * @param id Unique ID (UUID recommended)
     * @param name Display name for this connection
     * @param channelNumber Channel number (e.g., "1")
     */
    public EvenG1ConnectionConfig(@NonNull String id, @NonNull String name, @NonNull String channelNumber) {
        this.id = id;
        this.name = name;
        this.channelNumber = channelNumber;
    }

    // ========================
    // Getters and Setters
    // ========================

    @Nullable
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public String getChannelNumber() {
        return channelNumber;
    }

    public void setChannelNumber(@Nullable String channelNumber) {
        this.channelNumber = channelNumber;
    }

    @Nullable
    public String getLeftDeviceName() {
        return leftDeviceName;
    }

    public void setLeftDeviceName(@Nullable String leftDeviceName) {
        this.leftDeviceName = leftDeviceName;
    }

    @Nullable
    public String getRightDeviceName() {
        return rightDeviceName;
    }

    public void setRightDeviceName(@Nullable String rightDeviceName) {
        this.rightDeviceName = rightDeviceName;
    }

    @Nullable
    public String getLeftDeviceAddress() {
        return leftDeviceAddress;
    }

    public void setLeftDeviceAddress(@Nullable String leftDeviceAddress) {
        this.leftDeviceAddress = leftDeviceAddress;
    }

    @Nullable
    public String getRightDeviceAddress() {
        return rightDeviceAddress;
    }

    public void setRightDeviceAddress(@Nullable String rightDeviceAddress) {
        this.rightDeviceAddress = rightDeviceAddress;
    }

    // ========================
    // Utility Methods
    // ========================

    /**
     * Checks if this configuration is valid (has required fields).
     *
     * @return true if valid
     */
    public boolean isValid() {
        return id != null && !id.isEmpty() &&
               channelNumber != null && !channelNumber.isEmpty() &&
               leftDeviceAddress != null && !leftDeviceAddress.isEmpty() &&
               rightDeviceAddress != null && !rightDeviceAddress.isEmpty();
    }

    /**
     * Returns a display string for UI lists.
     * Format: "Name (Channel X)"
     *
     * @return Display string
     */
    @NonNull
    @Override
    public String toString() {
        if (name != null && !name.isEmpty()) {
            return name + " (Channel " + channelNumber + ")";
        }
        return "G1 Channel " + channelNumber;
    }

    /**
     * Returns a detailed description for settings screen.
     *
     * @return Detailed string
     */
    @NonNull
    public String toDetailString() {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isEmpty()) {
            sb.append(name).append("\n");
        }
        sb.append("Channel ").append(channelNumber).append("\n");
        sb.append("Left: ").append(leftDeviceName != null ? leftDeviceName : "N/A").append("\n");
        sb.append("Right: ").append(rightDeviceName != null ? rightDeviceName : "N/A");
        return sb.toString();
    }

    // ========================
    // Object Methods
    // ========================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EvenG1ConnectionConfig)) return false;
        EvenG1ConnectionConfig other = (EvenG1ConnectionConfig) obj;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
