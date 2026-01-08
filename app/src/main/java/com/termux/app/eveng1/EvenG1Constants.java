package com.termux.app.eveng1;

/**
 * Constants for EVEN G1 AR Glasses Bluetooth LE communication.
 * Based on Nordic UART Service (NUS) protocol.
 */
public class EvenG1Constants {

    // ========================
    // BLE Service/Characteristic UUIDs (Nordic UART Service)
    // ========================

    /** Nordic UART Service UUID */
    public static final String SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";

    /** Write Characteristic UUID (TX - phone to glasses) */
    public static final String WRITE_CHARACTERISTIC_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";

    /** Read Characteristic UUID (RX - glasses to phone) */
    public static final String READ_CHARACTERISTIC_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";

    /** Client Characteristic Configuration Descriptor UUID (for notifications) */
    public static final String CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    // ========================
    // Device Name Pattern
    // ========================

    /** Device name prefix: G{channel}_{L/R}_{ID} */
    public static final String DEVICE_NAME_PREFIX = "G";

    // ========================
    // Protocol Commands
    // ========================

    /** Heartbeat command (0x25) - must be sent every 15 seconds */
    public static final byte CMD_HEARTBEAT = 0x25;

    /** Exit to dashboard command (0x18) */
    public static final byte CMD_EXIT_TO_DASHBOARD = 0x18;

    /** EVEN AI data / Text display command (0x4E) */
    public static final byte CMD_EVEN_AI_DATA = 0x4E;

    /** Notification command (0x4B) */
    public static final byte CMD_NOTIFICATION = 0x4B;

    /** Whitelist command (0x04) */
    public static final byte CMD_WHITELIST = 0x04;

    /** Microphone control command (0x0E) */
    public static final byte CMD_MIC_CONTROL = 0x0E;

    /** Brightness command (0x01) */
    public static final byte CMD_BRIGHTNESS = 0x01;

    /** Head up angle command (0x0B) */
    public static final byte CMD_HEAD_UP_ANGLE = 0x0B;

    /** Display settings command (0x26) */
    public static final byte CMD_DISPLAY_SETTINGS = 0x26;

    /** Dashboard config command (0x06) */
    public static final byte CMD_DASHBOARD_CONFIG = 0x06;

    /** Get display settings command (0x3B) */
    public static final byte CMD_GET_DISPLAY_SETTINGS = 0x3B;

    /** Get head up angle command (0x32) */
    public static final byte CMD_GET_HEAD_UP_ANGLE = 0x32;

    // ========================
    // Timing Constants
    // ========================

    /** Heartbeat interval in milliseconds (15 seconds) */
    public static final long HEARTBEAT_INTERVAL_MS = 15000L;

    /** Packet send delay in milliseconds (50ms between packets) */
    public static final long PACKET_SEND_DELAY_MS = 50L;

    /** BLE scan timeout in milliseconds (30 seconds) */
    public static final long SCAN_TIMEOUT_MS = 30000L;

    /** Connection timeout in milliseconds (30 seconds) */
    public static final long CONNECTION_TIMEOUT_MS = 30000L;

    // ========================
    // Packet Size Constants
    // ========================

    /** Maximum data length per text packet (191 bytes) */
    public static final int TEXT_PACKET_MAX_DATA_LENGTH = 191;

    /** Maximum data length per notification packet (176 bytes) */
    public static final int NOTIFICATION_PACKET_MAX_DATA_LENGTH = 176;

    /** Text packet header size (9 bytes) */
    public static final int TEXT_PACKET_HEADER_SIZE = 9;

    // ========================
    // Display Constants
    // ========================

    /** Display width in pixels (approximately 488px) */
    public static final int DISPLAY_WIDTH_PX = 488;

    /** Recommended font size */
    public static final int RECOMMENDED_FONT_SIZE = 21;

    /** Maximum lines per screen */
    public static final int MAX_LINES_PER_SCREEN = 5;

    // ========================
    // newScreen Flag Values
    // ========================

    /** New screen flag: New text screen (0x71) */
    public static final int NEW_TEXT_SCREEN = 0x71;

    /** New screen flag: Append text to existing screen (0x70) */
    public static final int APPEND_TEXT = 0x70;

    /** New screen flag: AI assistant status (0x31) */
    public static final int AI_STATUS = 0x31;

    // ========================
    // Dashboard Mode
    // ========================

    /** Dashboard mode: Full display (default) */
    public static final int DASHBOARD_MODE_FULL = 0x00;

    /** Dashboard mode: Dual pane (left/right split) */
    public static final int DASHBOARD_MODE_DUAL = 0x01;

    /** Dashboard mode: Minimal display */
    public static final int DASHBOARD_MODE_MINIMAL = 0x02;

    // ========================
    // Weather Icons
    // ========================

    public static final int WEATHER_SUNNY = 0x00;
    public static final int WEATHER_CLOUDY = 0x01;
    public static final int WEATHER_PARTLY_CLOUDY = 0x02;
    public static final int WEATHER_RAINY = 0x03;
    public static final int WEATHER_HEAVY_RAIN = 0x04;
    public static final int WEATHER_THUNDERSTORM = 0x05;
    public static final int WEATHER_SNOWY = 0x06;
    public static final int WEATHER_FOGGY = 0x07;
    public static final int WEATHER_WINDY = 0x08;
    public static final int WEATHER_HAZY = 0x09;
    public static final int WEATHER_SLEET = 0x0A;
    public static final int WEATHER_UNKNOWN = 0x10;

    // Private constructor to prevent instantiation
    private EvenG1Constants() {
        throw new AssertionError("Cannot instantiate utility class");
    }
}
