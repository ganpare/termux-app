package com.termux.app.media;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import com.termux.shared.logger.Logger;

/**
 * Plays silent audio to keep the app active as a media session owner.
 * This ensures that media button events are routed to our app even in the
 * background.
 */
public class SilentAudioPlayer {

    private static final String LOG_TAG = "SilentAudioPlayer";
    private static final int SAMPLE_RATE = 44100;

    private AudioTrack mAudioTrack;
    private Thread mPlayThread;
    private volatile boolean mIsPlaying = false;
    private final Context mContext;
    private final AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener;

    public SilentAudioPlayer(Context context) {
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void play() {
        if (mIsPlaying)
            return;

        // Request audio focus
        int result;
        mAudioFocusChangeListener = focusChange -> {
            // Ignore focus changes, we just want to hold it if possible,
            // or at least have requested it.
            // Ideally we should pause if we lose focus, but for this "hack"
            // we want to be persistent.
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = mAudioManager.requestAudioFocus(
                    new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setOnAudioFocusChangeListener(mAudioFocusChangeListener)
                            .build());
        } else {
            result = mAudioManager.requestAudioFocus(
                    mAudioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Logger.logWarn(LOG_TAG, "Failed to gain audio focus");
            // Determine if we should abort or try anyway.
            // Let's try to play anyway.
        }

        mIsPlaying = true;
        mPlayThread = new Thread(() -> {
            try {
                int minBufferSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);

                // Ensure buffer is large enough for smooth playback
                int bufferSize = Math.max(minBufferSize, 4096);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mAudioTrack = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build())
                            .setBufferSizeInBytes(bufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build();
                } else {
                    mAudioTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize,
                            AudioTrack.MODE_STREAM);
                }

                mAudioTrack.play();
                byte[] silence = new byte[bufferSize]; // Default initialized to 0 (silence)

                while (mIsPlaying) {
                    mAudioTrack.write(silence, 0, silence.length);
                }

                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;

            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error playing silent audio", e);
            }
        });
        mPlayThread.start();
    }

    public void stop() {
        mIsPlaying = false;
        if (mPlayThread != null) {
            try {
                mPlayThread.join(1000);
            } catch (InterruptedException e) {
                // ignore
            }
            mPlayThread = null;
        }

        if (mAudioManager != null && mAudioFocusChangeListener != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Creating a dummy request to abandon focus is complex in O+,
                // usually we need the same request object.
                // For now, simplify or skip abandonment if not critical,
                // but correctly we should storing the request builder.
                // Actually AudioManager.abandonAudioFocusRequest(request) is the way.
                // Simpler: abandonAudioFocus(listener) is deprecated but still works for
                // compat.
                mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
            } else {
                mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
            }
        }
    }
}
