package com.termux.app.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.ResultReason;

public class VoiceInputManager {
    private static final String TAG = "VoiceInputManager";

    private final Context mContext;
    private final AiSettingsManager mSettingsManager;
    private final VoiceInputCallback mCallback;
    private SpeechRecognizer mSpeechRecognizer;
    private InputState mState = InputState.IDLE;
    private String mRecognizedText = "";
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public enum InputState {
        IDLE,
        LISTENING,
        CONFIRMING
    }

    public interface VoiceInputCallback {
        void onStateChanged(InputState newState);

        void onTextRecognized(String text);

        void onInputConfirmed(String text);

        void onError(String message);
    }

    public VoiceInputManager(Context context, AiSettingsManager settingsManager, VoiceInputCallback callback) {
        mContext = context;
        mSettingsManager = settingsManager;
        mCallback = callback;
    }

    public InputState getState() {
        return mState;
    }

    public void startListening() {
        if (mState == InputState.LISTENING)
            return;

        try {
            String speechKey = mSettingsManager.getAzureSpeechKey();
            String serviceRegion = mSettingsManager.getAzureServiceRegion();

            if (speechKey.isEmpty() || serviceRegion.isEmpty()) {
                mCallback.onError("Azure API Key or Region not set.");
                return;
            }

            SpeechConfig config = SpeechConfig.fromSubscription(speechKey, serviceRegion);
            config.setSpeechRecognitionLanguage("ja-JP");

            mSpeechRecognizer = new SpeechRecognizer(config);

            mSpeechRecognizer.recognizing.addEventListener((s, e) -> {
                // Optional: Live preview
            });

            mSpeechRecognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    mRecognizedText = e.getResult().getText();
                    Log.d(TAG, "Recognized: " + mRecognizedText);
                }
            });

            mSpeechRecognizer.sessionStopped.addEventListener((s, e) -> {
                mMainHandler.post(this::onRecognitionComplete);
            });

            mSpeechRecognizer.canceled.addEventListener((s, e) -> {
                mMainHandler.post(() -> {
                    mCallback.onError("Canceled: " + e.getErrorDetails());
                    cleanup();
                    setState(InputState.IDLE);
                });
            });

            mSpeechRecognizer.startContinuousRecognitionAsync();
            setState(InputState.LISTENING);
            mRecognizedText = "";

        } catch (Exception ex) {
            mCallback.onError("Init Failed: " + ex.getMessage());
            setState(InputState.IDLE);
        }
    }

    public void stopListening() {
        if (mState != InputState.LISTENING)
            return;

        // Stop recognition (will trigger sessionStopped)
        if (mSpeechRecognizer != null) {
            try {
                mSpeechRecognizer.stopContinuousRecognitionAsync();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping", e);
            }
        }
        // Force transition if sessionStopped doesn't fire immediately?
        // Better to wait for callback, but let's ensure UI responsivity.
    }

    private void onRecognitionComplete() {
        cleanup();
        if (mRecognizedText != null && !mRecognizedText.isEmpty()) {
            setState(InputState.CONFIRMING);
            mCallback.onTextRecognized(mRecognizedText);
        } else {
            setState(InputState.IDLE);
            mCallback.onError("No speech detected.");
        }
    }

    public void confirmInput() {
        if (mState == InputState.CONFIRMING) {
            mCallback.onInputConfirmed(mRecognizedText);
            setState(InputState.IDLE);
            mRecognizedText = "";
        }
    }

    public void retryInput() {
        // Discard current text and restart listening
        mRecognizedText = "";
        startListening(); // This will handle state transition
    }

    public void cancel() {
        cleanup();
        setState(InputState.IDLE);
    }

    private void cleanup() {
        if (mSpeechRecognizer != null) {
            try {
                // mSpeechRecognizer.stopContinuousRecognitionAsync(); // Already stopped
                // usually
                mSpeechRecognizer.close();
            } catch (Exception e) {
            }
            mSpeechRecognizer = null;
        }
    }

    private void setState(InputState newState) {
        mState = newState;
        mMainHandler.post(() -> mCallback.onStateChanged(newState));
    }
}
