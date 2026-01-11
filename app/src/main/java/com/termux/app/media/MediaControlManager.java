package com.termux.app.media;

import android.content.Context;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
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
    private MediaControlCallback mCallback;

    public interface MediaControlCallback {
        void onNext();

        void onPrevious();
    }

    public MediaControlManager(Context context) {
        mContext = context;
        mSilentPlayer = new SilentAudioPlayer(context);
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
                    Logger.logDebug(LOG_TAG, "onSkipToNext triggered");
                    if (mCallback != null)
                        mCallback.onNext();
                }

                @Override
                public void onSkipToPrevious() {
                    Logger.logDebug(LOG_TAG, "onSkipToPrevious triggered");
                    if (mCallback != null)
                        mCallback.onPrevious();
                }

                @Override
                public void onPlay() {
                    super.onPlay();
                    // Keep active
                }

                @Override
                public void onPause() {
                    super.onPause();
                    // Allow pause but we typically want to stay active for this feature
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
}
