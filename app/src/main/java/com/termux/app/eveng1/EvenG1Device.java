package com.termux.app.eveng1;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.util.regex.Pattern;

/**
 * Represents a single EVEN G1 device (left or right).
 * Device name format: G{channel}_{L/R}_{ID}
 * Example: G1_L_001, G1_R_001
 */
public class EvenG1Device {

    private static final String LOG_TAG = "EvenG1Device";
    private static final Pattern DEVICE_NAME_PATTERN = Pattern.compile("G\\d+_[LR]_\\w+");

    private final String name;
    private final String address;
    private final String channelNumber;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private boolean isConnected;

    /**
     * Creates a new EVEN G1 device.
     *
     * @param name Device name (e.g., "G1_L_001")
     * @param address Bluetooth MAC address
     * @param channelNumber Channel number extracted from name
     */
    public EvenG1Device(@NonNull String name, @NonNull String address, @NonNull String channelNumber) {
        this.name = name;
        this.address = address;
        this.channelNumber = channelNumber;
        this.isConnected = false;
    }

    // ========================
    // Factory Method
    // ========================

    /**
     * Creates an EvenG1Device from scan result.
     *
     * @param name Device name
     * @param address Device address
     * @return EvenG1Device instance, or null if invalid device name
     */
    @Nullable
    public static EvenG1Device fromScanResult(@NonNull String name, @NonNull String address) {
        if (!DEVICE_NAME_PATTERN.matcher(name).matches()) {
            return null;
        }

        String[] parts = name.split("_");
        if (parts.length < 3) {
            return null;
        }

        // Extract channel number (e.g., "G1" -> "1")
        String channelNumber = parts[0].substring(1);

        return new EvenG1Device(name, address, channelNumber);
    }

    // ========================
    // Device Type Checks
    // ========================

    /**
     * Checks if this is a left device.
     *
     * @return true if device name contains "_L_"
     */
    public boolean isLeft() {
        return name.contains("_L_");
    }

    /**
     * Checks if this is a right device.
     *
     * @return true if device name contains "_R_"
     */
    public boolean isRight() {
        return name.contains("_R_");
    }

    // ========================
    // Data Transmission
    // ========================

    /**
     * Sends data to this device.
     *
     * @param data Byte array to send
     * @return true if send was successful
     */
    @SuppressLint("MissingPermission")
    public boolean sendData(@NonNull byte[] data) {
        if (gatt == null) {
            Logger.logError(LOG_TAG, "Cannot send data: GATT is null");
            return false;
        }

        if (writeCharacteristic == null) {
            Logger.logError(LOG_TAG, "Cannot send data: Write characteristic is null");
            return false;
        }

        if (!isConnected) {
            Logger.logError(LOG_TAG, "Cannot send data: Device not connected");
            return false;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33+)
                int result = gatt.writeCharacteristic(writeCharacteristic, data,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                return result == BluetoothGatt.GATT_SUCCESS;
            } else {
                // Android 12 and below
                writeCharacteristic.setValue(data);
                writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                return gatt.writeCharacteristic(writeCharacteristic);
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error sending data to " + name, e);
            return false;
        }
    }

    // ========================
    // Getters and Setters
    // ========================

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getAddress() {
        return address;
    }

    @NonNull
    public String getChannelNumber() {
        return channelNumber;
    }

    @Nullable
    public BluetoothGatt getGatt() {
        return gatt;
    }

    public void setGatt(@Nullable BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    @Nullable
    public BluetoothGattCharacteristic getWriteCharacteristic() {
        return writeCharacteristic;
    }

    public void setWriteCharacteristic(@Nullable BluetoothGattCharacteristic writeCharacteristic) {
        this.writeCharacteristic = writeCharacteristic;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    // ========================
    // Object Methods
    // ========================

    @NonNull
    @Override
    public String toString() {
        return name + " (" + address + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EvenG1Device)) return false;
        EvenG1Device other = (EvenG1Device) obj;
        return address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}
