package com.termux.app.eveng1;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol implementation for EVEN G1 AR Glasses.
 * Provides packet creation and transmission methods.
 */
public class EvenG1Protocol {

    private static final String LOG_TAG = "EvenG1Protocol";
    private static int heartbeatSeq = 0;
    private static int evenAISeq = 0;

    // ========================
    // Callback Interface
    // ========================

    /**
     * Callback for asynchronous text send operations.
     */
    public interface TextSendCallback {
        /**
         * Called when text send completes successfully.
         */
        void onSuccess();

        /**
         * Called when text send fails.
         *
         * @param error Error message
         */
        void onFailure(@NonNull String error);
    }

    // ========================
    // Heartbeat
    // ========================

    /**
     * Creates a heartbeat packet.
     * Must be sent every 15 seconds to maintain connection.
     *
     * @return Heartbeat packet bytes
     */
    @NonNull
    public static byte[] createHeartbeatPacket() {
        int seq = (heartbeatSeq++ & 0xFF);
        int length = 6;

        return new byte[]{
            EvenG1Constants.CMD_HEARTBEAT,
            (byte) (length & 0xFF),
            (byte) ((length >> 8) & 0xFF),
            (byte) seq,
            0x04,
            (byte) seq
        };
    }

    // ========================
    // Text Display
    // ========================

    /**
     * Sends text to AR glasses asynchronously.
     *
     * @param manager EvenG1Manager instance
     * @param text Text to display
     * @param handler Handler for posting delayed tasks
     * @param callback Callback for completion
     */
    public static void sendText(@NonNull EvenG1Manager manager,
                                @NonNull String text,
                                @NonNull Handler handler,
                                @NonNull TextSendCallback callback) {
        sendText(manager, text, handler, callback, EvenG1Constants.NEW_TEXT_SCREEN, 1, 1);
    }

    /**
     * Sends text to AR glasses asynchronously with custom parameters.
     *
     * @param manager EvenG1Manager instance
     * @param text Text to display
     * @param handler Handler for posting delayed tasks
     * @param callback Callback for completion
     * @param newScreen New screen flag (0x71=new, 0x70=append)
     * @param currentPage Current page number
     * @param maxPage Maximum page number
     */
    public static void sendText(@NonNull EvenG1Manager manager,
                                @NonNull String text,
                                @NonNull Handler handler,
                                @NonNull TextSendCallback callback,
                                int newScreen,
                                int currentPage,
                                int maxPage) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int syncSeq = (evenAISeq++ & 0xFF);

        // Create packets
        List<byte[]> packets = createTextPackets(
            textBytes,
            syncSeq,
            newScreen,
            0, // position
            currentPage,
            maxPage
        );

        Logger.logDebug(LOG_TAG, "Sending text: " + text.length() + " chars, " +
            packets.size() + " packets");

        // Send to left device
        sendPacketsSequentially(manager, packets, true, 0, handler, new PacketSendCallback() {
            @Override
            public void onSuccess() {
                // After left succeeds, send to right
                sendPacketsSequentially(manager, packets, false, 0, handler, new PacketSendCallback() {
                    @Override
                    public void onSuccess() {
                        Logger.logDebug(LOG_TAG, "Text sent successfully");
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(String error) {
                        callback.onFailure(error);
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                callback.onFailure(error);
            }
        });
    }

    /**
     * Internal callback for packet sending.
     */
    private interface PacketSendCallback {
        void onSuccess();
        void onFailure(String error);
    }

    /**
     * Sends packets sequentially with delay.
     */
    private static void sendPacketsSequentially(@NonNull EvenG1Manager manager,
                                               @NonNull List<byte[]> packets,
                                               boolean toLeft,
                                               int index,
                                               @NonNull Handler handler,
                                               @NonNull PacketSendCallback callback) {
        if (index >= packets.size()) {
            callback.onSuccess();
            return;
        }

        boolean success;
        if (toLeft) {
            success = manager.sendToLeft(packets.get(index));
        } else {
            success = manager.sendToRight(packets.get(index));
        }

        if (!success) {
            callback.onFailure("Failed to send packet " + index);
            return;
        }

        // Wait 50ms before next packet
        handler.postDelayed(() -> {
            sendPacketsSequentially(manager, packets, toLeft, index + 1, handler, callback);
        }, EvenG1Constants.PACKET_SEND_DELAY_MS);
    }

    /**
     * Creates text packets with headers.
     *
     * @param data Text data bytes
     * @param syncSeq Sync sequence number
     * @param newScreen New screen flag
     * @param position Position
     * @param currentPage Current page
     * @param maxPage Max page
     * @return List of packets
     */
    @NonNull
    private static List<byte[]> createTextPackets(@NonNull byte[] data,
                                                  int syncSeq,
                                                  int newScreen,
                                                  int position,
                                                  int currentPage,
                                                  int maxPage) {
        List<byte[]> packets = new ArrayList<>();
        int maxLen = EvenG1Constants.TEXT_PACKET_MAX_DATA_LENGTH;
        int maxSeqNum = data.length / maxLen;
        if (data.length % maxLen > 0) {
            maxSeqNum++;
        }

        for (int seq = 0; seq < maxSeqNum; seq++) {
            int start = seq * maxLen;
            int end = Math.min(start + maxLen, data.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(data, start, chunk, 0, chunk.length);

            // Create header: [cmd, syncSeq, maxSeq, seq, newScreen, pos(2bytes), currentPage, maxPage]
            ByteBuffer header = ByteBuffer.allocate(EvenG1Constants.TEXT_PACKET_HEADER_SIZE);
            header.order(ByteOrder.BIG_ENDIAN);
            header.put(EvenG1Constants.CMD_EVEN_AI_DATA);
            header.put((byte) syncSeq);
            header.put((byte) maxSeqNum);
            header.put((byte) seq);
            header.put((byte) newScreen);
            header.putShort((short) position);
            header.put((byte) currentPage);
            header.put((byte) maxPage);

            // Combine header + chunk
            byte[] packet = new byte[header.capacity() + chunk.length];
            System.arraycopy(header.array(), 0, packet, 0, header.capacity());
            System.arraycopy(chunk, 0, packet, header.capacity(), chunk.length);

            packets.add(packet);
        }

        return packets;
    }

    // ========================
    // Notification (Future expansion)
    // ========================

    /**
     * Creates notification packets (for future implementation).
     *
     * @param appName Application name
     * @param title Notification title
     * @param body Notification body
     * @return List of notification packets
     */
    @NonNull
    public static List<byte[]> createNotificationPackets(@NonNull String appName,
                                                         @NonNull String title,
                                                         @NonNull String body) {
        // JSON format for notification
        String json = String.format(
            "{\"ncs_notification\":{\"msg_id\":1,\"app_identifier\":\"%s\",\"title\":\"%s\"," +
            "\"subtitle\":\"\",\"message\":\"%s\",\"display_name\":\"%s\"," +
            "\"positive_action_label\":\"\",\"negative_action_label\":\"\"}}",
            appName, title, body, appName
        );

        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        List<byte[]> packets = new ArrayList<>();
        int maxLen = EvenG1Constants.NOTIFICATION_PACKET_MAX_DATA_LENGTH;
        int maxSeqNum = jsonBytes.length / maxLen;
        if (jsonBytes.length % maxLen > 0) {
            maxSeqNum++;
        }

        for (int seq = 0; seq < maxSeqNum; seq++) {
            int start = seq * maxLen;
            int end = Math.min(start + maxLen, jsonBytes.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(jsonBytes, start, chunk, 0, chunk.length);

            // Header: [cmd, msgId, maxSeq, seq]
            byte[] header = new byte[]{
                EvenG1Constants.CMD_NOTIFICATION,
                0x01, // msgId
                (byte) maxSeqNum,
                (byte) seq
            };

            byte[] packet = new byte[header.length + chunk.length];
            System.arraycopy(header, 0, packet, 0, header.length);
            System.arraycopy(chunk, 0, packet, header.length, chunk.length);

            packets.add(packet);
        }

        return packets;
    }

    // ========================
    // Dashboard Control (Future expansion)
    // ========================

    /**
     * Creates exit to dashboard packet.
     *
     * @return Exit packet bytes
     */
    @NonNull
    public static byte[] createExitToDashboardPacket() {
        return new byte[]{EvenG1Constants.CMD_EXIT_TO_DASHBOARD};
    }

    /**
     * Creates brightness control packet (for future implementation).
     *
     * @param brightness Brightness level (0-42)
     * @param autoBrightness Auto brightness enabled
     * @return Brightness packet bytes
     */
    @NonNull
    public static byte[] createBrightnessPacket(int brightness, boolean autoBrightness) {
        brightness = Math.max(0, Math.min(0x2A, brightness));
        return new byte[]{
            EvenG1Constants.CMD_BRIGHTNESS,
            (byte) brightness,
            (byte) (autoBrightness ? 0x01 : 0x00)
        };
    }

    // Private constructor to prevent instantiation
    private EvenG1Protocol() {
        throw new AssertionError("Cannot instantiate utility class");
    }
}
