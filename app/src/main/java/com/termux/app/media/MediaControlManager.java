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
    private static final int DOUBLE_CLICK_DELAY = 300;
    private MediaControlCallback mCallback;

    public interface MediaControlCallback {
        void onNextSingle();

        void onNextDouble();

        void onPreviousSingle();

        void onPreviousDouble();

        void onPlayPauseSingle();

        void onPlayPauseDouble();
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
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);

            mMediaSession.setPlaybackState(stateBuilder.build());

            mMediaSession.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public void onSkipToNext() {
                    android.util.Log.d(LOG_TAG, "onSkipToNext triggered");
                    if (mCallback == null)
                        return;

                    if (mPendingNext != null) {
                        android.util.Log.d(LOG_TAG, "Double Click Detected (Next)");
                        mHandler.removeCallbacks(mPendingNext);
                        mPendingNext = null;
                        mCallback.onNextDouble();
                    } else {
                        android.util.Log.d(LOG_TAG, "Scheduling Single Click (Next)");
                        mPendingNext = () -> {
                            android.util.Log.d(LOG_TAG, "Firing Single Click (Next)");
                            mCallback.onNextSingle();
                            mPendingNext = null;
                        };
                        mHandler.postDelayed(mPendingNext, DOUBLE_CLICK_DELAY);
                    }
                }

                @Override
                public void onSkipToPrevious() {
                    android.util.Log.d(LOG_TAG, "onSkipToPrevious triggered");
                    if (mCallback == null)
                        return;

                    if (mPendingPrev != null) {
                        android.util.Log.d(LOG_TAG, "Double Click Detected (Prev)");
                        mHandler.removeCallbacks(mPendingPrev);
                        mPendingPrev = null;
                        mCallback.onPreviousDouble();
                    } else {
                        android.util.Log.d(LOG_TAG, "Scheduling Single Click (Prev)");
                        mPendingPrev = () -> {
                            android.util.Log.d(LOG_TAG, "Firing Single Click (Prev)");
                            mCallback.onPreviousSingle();
                            mPendingPrev = null;
                        };
                        mHandler.postDelayed(mPendingPrev, DOUBLE_CLICK_DELAY);
                    }
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

        if (mPendingPlayPause != null) {
            android.util.Log.d(LOG_TAG, "Double Click Detected (Play/Pause)");
            mHandler.removeCallbacks(mPendingPlayPause);
            mPendingPlayPause = null;
            mCallback.onPlayPauseDouble();
        } else {
            android.util.Log.d(LOG_TAG, "Scheduling Single Click (Play/Pause)");
            mPendingPlayPause = () -> {
                android.util.Log.d(LOG_TAG, "Firing Single Click (Play/Pause)");
                mCallback.onPlayPauseSingle();
                mPendingPlayPause = null;
            };
            mHandler.postDelayed(mPendingPlayPause, DOUBLE_CLICK_DELAY);
        }
    }
}
