package com.termux.app.eveng1;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages Bluetooth LE connections to EVEN G1 AR Glasses.
 * Singleton pattern for centralized connection management.
 */
public class EvenG1Manager {

    private static final String LOG_TAG = "EvenG1Manager";
    private static EvenG1Manager instance;

    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private final Handler mainHandler;
    private final Handler heartbeatHandler;
    private Runnable heartbeatRunnable;

    private ConnectionCallback callback;
    private final List<EvenG1Device> discoveredDevices;
    private EvenG1DevicePair connectedPair;
    private boolean isScanning;

    // ========================
    // Callback Interface
    // ========================

    /**
     * Callback interface for connection events.
     */
    public interface ConnectionCallback {
        /**
         * Called when a device pair is found during scan.
         *
         * @param channelNumber Channel number
         * @param leftName Left device name
         * @param rightName Right device name
         */
        void onDeviceFound(@NonNull String channelNumber, @NonNull String leftName, @NonNull String rightName);

        /**
         * Called when both devices are connected.
         *
         * @param pair Connected device pair
         */
        void onConnected(@NonNull EvenG1DevicePair pair);

        /**
         * Called when disconnected.
         */
        void onDisconnected();

        /**
         * Called when connection fails.
         *
         * @param error Error message
         */
        void onConnectionFailed(@NonNull String error);

        /**
         * Called when data is received from glasses.
         *
         * @param isLeft true if from left device
         * @param data Received data
         */
        void onDataReceived(boolean isLeft, @NonNull byte[] data);
    }

    // ========================
    // Singleton
    // ========================

    private EvenG1Manager() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.heartbeatHandler = new Handler(Looper.getMainLooper());
        this.discoveredDevices = new ArrayList<>();
        this.isScanning = false;
    }

    /**
     * Gets the singleton instance.
     *
     * @return Manager instance
     */
    @NonNull
    public static synchronized EvenG1Manager getInstance() {
        if (instance == null) {
            instance = new EvenG1Manager();
        }
        return instance;
    }

    // ========================
    // Initialization
    // ========================

    /**
     * Initializes the manager.
     *
     * @param context Application context
     * @param callback Callback for events
     */
    public void initialize(@NonNull Context context, @Nullable ConnectionCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            this.bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }
        Logger.logDebug(LOG_TAG, "EvenG1Manager initialized");
    }

    /**
     * Sets the callback.
     *
     * @param callback Callback for events
     */
    public void setCallback(@Nullable ConnectionCallback callback) {
        this.callback = callback;
    }

    // ========================
    // Scanning
    // ========================

    /**
     * Starts BLE scan for G1 devices.
     */
    @SuppressLint("MissingPermission")
    public void startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            notifyConnectionFailed("Bluetooth is not enabled");
            return;
        }

        if (bluetoothLeScanner == null) {
            notifyConnectionFailed("BLE scanner not available");
            return;
        }

        if (isScanning) {
            Logger.logDebug(LOG_TAG, "Already scanning");
            return;
        }

        discoveredDevices.clear();
        isScanning = true;

        ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();

        bluetoothLeScanner.startScan(null, settings, scanCallback);
        Logger.logDebug(LOG_TAG, "Started BLE scan");

        // Auto-stop scan after timeout
        mainHandler.postDelayed(() -> {
            if (isScanning) {
                stopScan();
                Logger.logDebug(LOG_TAG, "Scan timeout");
            }
        }, EvenG1Constants.SCAN_TIMEOUT_MS);
    }

    /**
     * Stops BLE scan.
     */
    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (!isScanning) {
            return;
        }

        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
        isScanning = false;
        Logger.logDebug(LOG_TAG, "Stopped BLE scan");
    }

    /**
     * Scan callback implementation.
     */
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            @SuppressLint("MissingPermission")
            String name = device.getName();
            String address = device.getAddress();

            if (name == null) {
                return;
            }

            // Parse device from scan result
            EvenG1Device evenDevice = EvenG1Device.fromScanResult(name, address);
            if (evenDevice == null) {
                return;
            }

            // Check if already discovered
            if (discoveredDevices.stream().anyMatch(d -> d.getAddress().equals(address))) {
                return;
            }

            discoveredDevices.add(evenDevice);
            Logger.logDebug(LOG_TAG, "Found device: " + name);

            // Check for pair
            String channelNum = evenDevice.getChannelNumber();
            List<EvenG1Device> channelDevices = new ArrayList<>();
            for (EvenG1Device d : discoveredDevices) {
                if (d.getChannelNumber().equals(channelNum)) {
                    channelDevices.add(d);
                }
            }

            if (channelDevices.size() >= 2) {
                EvenG1Device left = null;
                EvenG1Device right = null;
                for (EvenG1Device d : channelDevices) {
                    if (d.isLeft()) left = d;
                    if (d.isRight()) right = d;
                }

                if (left != null && right != null) {
                    final String leftName = left.getName();
                    final String rightName = right.getName();
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onDeviceFound(channelNum, leftName, rightName);
                        }
                    });
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Logger.logError(LOG_TAG, "Scan failed: " + errorCode);
            isScanning = false;
        }
    };

    // ========================
    // Connection
    // ========================

    /**
     * Connects to a device pair by channel number.
     *
     * @param channelNumber Channel number to connect
     */
    @SuppressLint("MissingPermission")
    public void connect(@NonNull String channelNumber) {
        EvenG1Device left = null;
        EvenG1Device right = null;

        for (EvenG1Device device : discoveredDevices) {
            if (device.getChannelNumber().equals(channelNumber)) {
                if (device.isLeft()) left = device;
                if (device.isRight()) right = device;
            }
        }

        if (left == null || right == null) {
            notifyConnectionFailed("Device pair not found for channel " + channelNumber);
            return;
        }

        connectedPair = new EvenG1DevicePair(left, right);
        Logger.logDebug(LOG_TAG, "Connecting to channel " + channelNumber);

        // Connect to both devices
        BluetoothDevice leftBtDevice = bluetoothAdapter.getRemoteDevice(left.getAddress());
        BluetoothDevice rightBtDevice = bluetoothAdapter.getRemoteDevice(right.getAddress());

        leftBtDevice.connectGatt(context, false, gattCallback);
        rightBtDevice.connectGatt(context, false, gattCallback);
    }

    /**
     * Disconnects from current device pair.
     */
    @SuppressLint("MissingPermission")
    public void disconnect() {
        stopHeartbeat();

        if (connectedPair != null) {
            EvenG1Device left = connectedPair.getLeftDevice();
            EvenG1Device right = connectedPair.getRightDevice();

            if (left != null && left.getGatt() != null) {
                left.getGatt().disconnect();
                left.getGatt().close();
                left.setGatt(null);
                left.setConnected(false);
            }

            if (right != null && right.getGatt() != null) {
                right.getGatt().disconnect();
                right.getGatt().close();
                right.setGatt(null);
                right.setConnected(false);
            }

            connectedPair = null;
        }

        Logger.logDebug(LOG_TAG, "Disconnected");
    }

    /**
     * Checks if connected.
     *
     * @return true if both devices connected
     */
    public boolean isConnected() {
        return connectedPair != null && connectedPair.isBothConnected();
    }

    // ========================
    // GATT Callback
    // ========================

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Logger.logDebug(LOG_TAG, "Connected to " + gatt.getDevice().getAddress());
                    gatt.discoverServices();
                } else {
                    Logger.logError(LOG_TAG, "Connection failed: status=" + status);
                    mainHandler.post(() -> notifyConnectionFailed("Connection failed: " + status));
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Logger.logDebug(LOG_TAG, "Disconnected from " + gatt.getDevice().getAddress());
                updateDeviceState(gatt, false, null);
                if (connectedPair != null && !connectedPair.isBothConnected()) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onDisconnected();
                        }
                    });
                }
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Logger.logError(LOG_TAG, "Service discovery failed: " + status);
                return;
            }

            BluetoothGattService service = gatt.getService(UUID.fromString(EvenG1Constants.SERVICE_UUID));
            if (service == null) {
                Logger.logError(LOG_TAG, "Nordic UART Service not found");
                return;
            }

            BluetoothGattCharacteristic readChar = service.getCharacteristic(
                UUID.fromString(EvenG1Constants.READ_CHARACTERISTIC_UUID));
            BluetoothGattCharacteristic writeChar = service.getCharacteristic(
                UUID.fromString(EvenG1Constants.WRITE_CHARACTERISTIC_UUID));

            if (readChar == null || writeChar == null) {
                Logger.logError(LOG_TAG, "Required characteristics not found");
                return;
            }

            // Enable notifications
            gatt.setCharacteristicNotification(readChar, true);
            BluetoothGattDescriptor descriptor = readChar.getDescriptor(
                UUID.fromString(EvenG1Constants.CCCD_UUID));
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }

            // Request MTU
            gatt.requestMtu(251);

            // Update device state
            updateDeviceState(gatt, true, writeChar);

            // Check if both connected
            if (connectedPair != null && connectedPair.isBothConnected()) {
                Logger.logDebug(LOG_TAG, "Both devices connected");
                startHeartbeat();
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onConnected(connectedPair);
                    }
                });
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic characteristic,
                                            @NonNull byte[] value) {
            String address = gatt.getDevice().getAddress();
            boolean isLeft = connectedPair != null &&
                connectedPair.getLeftDevice() != null &&
                connectedPair.getLeftDevice().getAddress().equals(address);

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onDataReceived(isLeft, value);
                }
            });
        }
    };

    /**
     * Updates device state after GATT events.
     */
    private void updateDeviceState(@NonNull BluetoothGatt gatt, boolean connected,
                                   @Nullable BluetoothGattCharacteristic writeChar) {
        if (connectedPair == null) return;

        String address = gatt.getDevice().getAddress();
        EvenG1Device left = connectedPair.getLeftDevice();
        EvenG1Device right = connectedPair.getRightDevice();

        if (left != null && left.getAddress().equals(address)) {
            left.setGatt(connected ? gatt : null);
            left.setConnected(connected);
            if (writeChar != null) {
                left.setWriteCharacteristic(writeChar);
            }
        } else if (right != null && right.getAddress().equals(address)) {
            right.setGatt(connected ? gatt : null);
            right.setConnected(connected);
            if (writeChar != null) {
                right.setWriteCharacteristic(writeChar);
            }
        }
    }

    // ========================
    // Data Transmission
    // ========================

    /**
     * Sends data to both devices.
     *
     * @param data Data to send
     * @return true if sent successfully to both
     */
    public boolean sendData(@NonNull byte[] data) {
        if (connectedPair == null) {
            return false;
        }

        boolean leftResult = sendToLeft(data);
        boolean rightResult = sendToRight(data);
        return leftResult && rightResult;
    }

    /**
     * Sends data to left device only.
     *
     * @param data Data to send
     * @return true if sent successfully
     */
    public boolean sendToLeft(@NonNull byte[] data) {
        if (connectedPair == null || connectedPair.getLeftDevice() == null) {
            return false;
        }
        return connectedPair.getLeftDevice().sendData(data);
    }

    /**
     * Sends data to right device only.
     *
     * @param data Data to send
     * @return true if sent successfully
     */
    public boolean sendToRight(@NonNull byte[] data) {
        if (connectedPair == null || connectedPair.getRightDevice() == null) {
            return false;
        }
        return connectedPair.getRightDevice().sendData(data);
    }

    // ========================
    // Heartbeat
    // ========================

    /**
     * Starts automatic heartbeat transmission.
     */
    private void startHeartbeat() {
        stopHeartbeat();

        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    byte[] heartbeat = EvenG1Protocol.createHeartbeatPacket();
                    sendData(heartbeat);
                    Logger.logVerbose(LOG_TAG, "Sent heartbeat");
                }
                heartbeatHandler.postDelayed(this, EvenG1Constants.HEARTBEAT_INTERVAL_MS);
            }
        };

        heartbeatHandler.postDelayed(heartbeatRunnable, EvenG1Constants.HEARTBEAT_INTERVAL_MS);
        Logger.logDebug(LOG_TAG, "Started heartbeat");
    }

    /**
     * Stops heartbeat transmission.
     */
    private void stopHeartbeat() {
        if (heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
            Logger.logDebug(LOG_TAG, "Stopped heartbeat");
        }
    }

    // ========================
    // Utility
    // ========================

    private void notifyConnectionFailed(@NonNull String error) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onConnectionFailed(error);
            }
        });
    }

    /**
     * Gets the connected device pair.
     *
     * @return Connected pair, or null if not connected
     */
    @Nullable
    public EvenG1DevicePair getConnectedPair() {
        return connectedPair;
    }

    /**
     * Gets discovered devices.
     *
     * @return List of discovered devices
     */
    @NonNull
    public List<EvenG1Device> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }
}
