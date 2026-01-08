package com.termux.app.eveng1;

import androidx.annotation.Nullable;

/**
 * Represents a pair of EVEN G1 devices (left and right).
 * Both devices must be connected for the glasses to function properly.
 */
public class EvenG1DevicePair {

    private EvenG1Device leftDevice;
    private EvenG1Device rightDevice;

    /**
     * Creates a new device pair.
     *
     * @param leftDevice Left device (can be null initially)
     * @param rightDevice Right device (can be null initially)
     */
    public EvenG1DevicePair(@Nullable EvenG1Device leftDevice, @Nullable EvenG1Device rightDevice) {
        this.leftDevice = leftDevice;
        this.rightDevice = rightDevice;
    }

    /**
     * Creates an empty device pair.
     */
    public EvenG1DevicePair() {
        this(null, null);
    }

    // ========================
    // Connection Status
    // ========================

    /**
     * Checks if both devices are connected.
     *
     * @return true if both left and right devices are connected
     */
    public boolean isBothConnected() {
        return leftDevice != null && leftDevice.isConnected() &&
               rightDevice != null && rightDevice.isConnected();
    }

    /**
     * Checks if at least one device is connected.
     *
     * @return true if either left or right device is connected
     */
    public boolean hasAnyConnected() {
        return (leftDevice != null && leftDevice.isConnected()) ||
               (rightDevice != null && rightDevice.isConnected());
    }

    /**
     * Gets the channel number from either device.
     *
     * @return Channel number, or null if no device is set
     */
    @Nullable
    public String getChannelNumber() {
        if (leftDevice != null) {
            return leftDevice.getChannelNumber();
        }
        if (rightDevice != null) {
            return rightDevice.getChannelNumber();
        }
        return null;
    }

    // ========================
    // Getters and Setters
    // ========================

    @Nullable
    public EvenG1Device getLeftDevice() {
        return leftDevice;
    }

    public void setLeftDevice(@Nullable EvenG1Device leftDevice) {
        this.leftDevice = leftDevice;
    }

    @Nullable
    public EvenG1Device getRightDevice() {
        return rightDevice;
    }

    public void setRightDevice(@Nullable EvenG1Device rightDevice) {
        this.rightDevice = rightDevice;
    }

    // ========================
    // Object Methods
    // ========================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EvenG1DevicePair{");
        if (leftDevice != null) {
            sb.append("left=").append(leftDevice.getName());
        } else {
            sb.append("left=null");
        }
        sb.append(", ");
        if (rightDevice != null) {
            sb.append("right=").append(rightDevice.getName());
        } else {
            sb.append("right=null");
        }
        sb.append("}");
        return sb.toString();
    }
}
