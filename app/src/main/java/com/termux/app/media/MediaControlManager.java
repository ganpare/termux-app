package com.termux.app.media;

import android.content.Context;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.os.Handler;
import android.os.Looper;
import com.termux.shared.logger.Logger;

/**
 * Manages MediaSession to capture Bluetooth media button events.
 */
public class MediaControlManager {

    private static final String LOG_TAG = "MediaControlManager";
    private static final String SESSION_TAG = "TermuxMediaSession";

    private final Context mContext;
    private MediaSessionCompat mMediaSession;
    private SilentAudioPlayer mSilentPlayer;
    private Handler mHandler;
    private Runnable mPendingNext;
    private Runnable mPendingPrev;
    private Runnable mPendingPlayPause;
    private static final int DOUBLE_CLICK_DELAY = 600;
    private MediaControlCallback mCallback;

    // Click counters
    private int mNextClickCount = 0;
    private Runnable mPendingNextRunnable;
    private int mPrevClickCount = 0;
    private Runnable mPendingPrevRunnable;
    private int mPlayPauseClickCount = 0;
    private Runnable mPendingPlayPauseRunnable;

    public interface MediaControlCallback {
        void onNextSingle();

        void onNextDouble();

        void onPreviousSingle();

        void onPreviousDouble();

        void onPlayPauseSingle();

        void onPlayPauseDouble();

        void onPlayPauseTriple();

        void onVolumeUp();

        void onVolumeDown();
    }

    public MediaControlManager(Context context) {
        mContext = context;
        mSilentPlayer = new SilentAudioPlayer(context);
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void start(MediaControlCallback callback) {
        if (mMediaSession != null) {
            stop();
        }

        mCallback = callback;
        mSilentPlayer.play();

        try {
            mMediaSession = new MediaSessionCompat(mContext, SESSION_TAG);

            // Set flags to let the media button receiver handle restarts
            mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

            // Create a playback state that accepts next/prev
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY |
                            PlaybackStateCompat.ACTION_PAUSE |
                            PlaybackStateCompat.ACTION_PLAY_PAUSE |
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                    .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);

            mMediaSession.setPlaybackState(stateBuilder.build());

            // Handle Volume keys
            // VOLUME_CONTROL_RELATIVE allows us to capture up/down events
            // maxVolume=100, currentVolume=50 is arbitrary, just need space to go up/down
            androidx.media.VolumeProviderCompat volumeProvider = new androidx.media.VolumeProviderCompat(
                    androidx.media.VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, 100, 50) {
                @Override
                public void onAdjustVolume(int direction) {
                    if (mCallback == null)
                        return;

                    // direction dictates the key pressed:
                    // +1: Volume Up
                    // -1: Volume Down
                    // 0: Release (sometimes)
                    if (direction > 0) {
                        android.util.Log.d(LOG_TAG, "Volume Up Detected");
                        new Handler(Looper.getMainLooper()).post(() -> mCallback.onVolumeUp());
                    } else if (direction < 0) {
                        android.util.Log.d(LOG_TAG, "Volume Down Detected");
                        new Handler(Looper.getMainLooper()).post(() -> mCallback.onVolumeDown());
                    }
                }
            };
            mMediaSession.setPlaybackToRemote(volumeProvider);

            mMediaSession.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public void onSkipToNext() {
                    android.util.Log.d(LOG_TAG, "onSkipToNext triggered");
                    if (mCallback == null)
                        return;

                    mNextClickCount++;
                    if (mPendingNextRunnable != null) {
                        mHandler.removeCallbacks(mPendingNextRunnable);
                    }

                    mPendingNextRunnable = () -> {
                        if (mNextClickCount == 1)
                            mCallback.onNextSingle();
                        else if (mNextClickCount >= 2)
                            mCallback.onNextDouble();
                        mNextClickCount = 0;
                        mPendingNextRunnable = null;
                    };
                    mHandler.postDelayed(mPendingNextRunnable, DOUBLE_CLICK_DELAY);
                }

                @Override
                public void onSkipToPrevious() {
                    android.util.Log.d(LOG_TAG, "onSkipToPrevious triggered");
                    if (mCallback == null)
                        return;

                    mPrevClickCount++;
                    if (mPendingPrevRunnable != null) {
                        mHandler.removeCallbacks(mPendingPrevRunnable);
                    }

                    mPendingPrevRunnable = () -> {
                        if (mPrevClickCount == 1)
                            mCallback.onPreviousSingle();
                        else if (mPrevClickCount >= 2)
                            mCallback.onPreviousDouble();
                        mPrevClickCount = 0;
                        mPendingPrevRunnable = null;
                    };
                    mHandler.postDelayed(mPendingPrevRunnable, DOUBLE_CLICK_DELAY);
                }

                @Override
                public void onPlay() {
                    super.onPlay();
                    handlePlayPause();
                }

                @Override
                public void onPause() {
                    super.onPause();
                    handlePlayPause();
                }
            });

            mMediaSession.setActive(true);
            Logger.logDebug(LOG_TAG, "Media Session started");

        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start Media Session", e);
        }
    }

    public void stop() {
        if (mSilentPlayer != null) {
            mSilentPlayer.stop();
        }

        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }

        mCallback = null;
        Logger.logDebug(LOG_TAG, "Media Session stopped");
    }

    private void handlePlayPause() {
        android.util.Log.d(LOG_TAG, "onPlayPause triggered");
        if (mCallback == null)
            return;

        mPlayPauseClickCount++;
        if (mPendingPlayPauseRunnable != null) {
            mHandler.removeCallbacks(mPendingPlayPauseRunnable);
        }

        mPendingPlayPauseRunnable = () -> {
            if (mPlayPauseClickCount == 1) {
                android.util.Log.d(LOG_TAG, "Firing Single Click (Play/Pause)");
                mCallback.onPlayPauseSingle();
            } else if (mPlayPauseClickCount == 2) {
                android.util.Log.d(LOG_TAG, "Firing Double Click (Play/Pause)");
                mCallback.onPlayPauseDouble();
            } else if (mPlayPauseClickCount >= 3) {
                android.util.Log.d(LOG_TAG, "Firing Triple Click (Play/Pause)");
                mCallback.onPlayPauseTriple();
            }
            mPlayPauseClickCount = 0;
            mPendingPlayPauseRunnable = null;
        };
        mHandler.postDelayed(mPendingPlayPauseRunnable, DOUBLE_CLICK_DELAY);
    }
}
