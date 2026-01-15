package com.termux.app.ai;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.app.TermuxActivity;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.ResultReason;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class AiInteractionDialog {
    private final TermuxActivity mActivity;
    private final AiSettingsManager mSettingsManager;
    private final LlmClient mLlmClient;

    // Azure Speech
    private SpeechRecognizer mSpeechRecognizer;
    private boolean mIsListening = false;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    public AiInteractionDialog(TermuxActivity activity, AiSettingsManager settingsManager, LlmClient llmClient) {
        mActivity = activity;
        mSettingsManager = settingsManager;
        mLlmClient = llmClient;
    }

    public void show() {
        Context context = mActivity;
        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        scrollView.addView(layout);

        // Header with Settings Button
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(context);
        title.setText("AI Assistant");
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        Button settingsBtn = new Button(context);
        settingsBtn.setText("⚙"); // Gear icon
        settingsBtn.setOnClickListener(v -> new AiSettingsDialog(mActivity, mSettingsManager, mLlmClient).show());
        header.addView(settingsBtn);
        layout.addView(header);

        // Template Spinner
        layout.addView(createLabel(context, "Template:"));
        Spinner templateSpinner = new Spinner(context);
        List<PromptTemplate> templates = mSettingsManager.getTemplates();
        ArrayAdapter<PromptTemplate> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                templates);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        templateSpinner.setAdapter(adapter);
        layout.addView(templateSpinner);

        // System Prompt (Editable)
        layout.addView(createLabel(context, "System Prompt:"));
        EditText systemPromptInput = new EditText(context);
        systemPromptInput.setMinLines(2);
        systemPromptInput.setMaxLines(5);
        layout.addView(systemPromptInput);

        // Update System Prompt when template changes
        templateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PromptTemplate template = templates.get(position);
                systemPromptInput.setText(template.systemPrompt);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // User Input
        LinearLayout inputLabelLayout = new LinearLayout(context);
        inputLabelLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLabelLayout.addView(createLabel(context, "User Input:"));

        Button micButton = new Button(context);
        micButton.setText("🎤");
        micButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        inputLabelLayout.addView(micButton);
        layout.addView(inputLabelLayout);

        EditText userInput = new EditText(context);
        userInput.setMinLines(3);
        userInput.setMaxLines(10);
        layout.addView(userInput);

        micButton.setOnClickListener(v -> toggleListening(context, micButton, userInput));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(scrollView)
                .setPositiveButton("Send", null) // Overridden later
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        Button sendButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        sendButton.setOnClickListener(v -> {
            String systemPrompt = systemPromptInput.getText().toString();
            String userPrompt = userInput.getText().toString();
            String provider = mSettingsManager.getProvider();
            String apiKey = AiSettingsManager.PROVIDER_OPENAI.equals(provider)
                    ? mSettingsManager.getOpenAiApiKey()
                    : mSettingsManager.getOpenRouterApiKey();
            String model = mSettingsManager.getModelForProvider(provider);

            if (apiKey.isEmpty()) {
                Toast.makeText(context, "Please set API Key in Settings", Toast.LENGTH_LONG).show();
                return;
            }
            if (userPrompt.isEmpty()) {
                Toast.makeText(context, "Please enter user input", Toast.LENGTH_SHORT).show();
                return;
            }

            // Prepare Request
            List<ChatRequest.Message> messages = new ArrayList<>();
            if (!systemPrompt.isEmpty()) {
                messages.add(new ChatRequest.Message("system", systemPrompt));
            }
            messages.add(new ChatRequest.Message("user", userPrompt));
            ChatRequest request = new ChatRequest(model, messages, false);

            // Show Progress
            ProgressDialog progress = new ProgressDialog(context);
            progress.setMessage("Thinking...");
            progress.setCancelable(false);
            progress.show();

            // Send Request
            mLlmClient.updateConfig(mSettingsManager.getBaseUrlForProvider(provider));
            mLlmClient.chat(apiKey, request, new Callback<ChatResponse>() {
                @Override
                public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                    progress.dismiss();
                    if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                        String content = response.body().choices.get(0).message.content;

                        // Send to AR
                        mActivity.sendAiResponseToAr(content);
                        dialog.dismiss();
                    } else {
                        String error = "Error: " + response.code();
                        try {
                            if (response.errorBody() != null)
                                error += " " + response.errorBody().string();
                        } catch (Exception e) {
                        }
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<ChatResponse> call, Throwable t) {
                    progress.dismiss();
                    Toast.makeText(context, "Failure: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void toggleListening(Context context, Button micBtn, EditText userInput) {
        if (mIsListening) {
            stopListening(micBtn);
        } else {
            startListening(context, micBtn, userInput);
        }
    }

    private void startListening(Context context, Button micBtn, EditText userInput) {
        // Check Permissions
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mActivity, new String[] { Manifest.permission.RECORD_AUDIO },
                    PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            String speechKey = mSettingsManager.getAzureSpeechKey();
            String serviceRegion = mSettingsManager.getAzureServiceRegion();

            if (speechKey.isEmpty() || serviceRegion.isEmpty()) {
                Toast.makeText(context, "Please set Azure Key/Region in Settings", Toast.LENGTH_LONG).show();
                return;
            }

            SpeechConfig config = SpeechConfig.fromSubscription(speechKey, serviceRegion);
            config.setSpeechRecognitionLanguage("ja-JP"); // Default to Japanese for this user

            mSpeechRecognizer = new SpeechRecognizer(config);

            mSpeechRecognizer.recognizing.addEventListener((s, e) -> {
                // Intermediate results (optional to show?)
            });

            mSpeechRecognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    String text = e.getResult().getText();
                    mActivity.runOnUiThread(() -> {
                        userInput.append(text);
                    });
                }
            });

            mSpeechRecognizer.sessionStopped.addEventListener((s, e) -> {
                mActivity.runOnUiThread(() -> stopListening(micBtn));
            });

            mSpeechRecognizer.canceled.addEventListener((s, e) -> {
                mActivity.runOnUiThread(() -> {
                    Toast.makeText(context, "Canceled: " + e.getErrorDetails(), Toast.LENGTH_SHORT).show();
                    stopListening(micBtn);
                });
            });

            mSpeechRecognizer.startContinuousRecognitionAsync();
            mIsListening = true;
            micBtn.setText("🛑"); // Stop icon

        } catch (Exception ex) {
            Toast.makeText(context, "Error init speech: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopListening(Button micBtn) {
        if (mSpeechRecognizer != null) {
            try {
                mSpeechRecognizer.stopContinuousRecognitionAsync();
            } catch (Exception e) {
            }
        }
        mIsListening = false;
        micBtn.setText("🎤");
    }

    private TextView createLabel(Context context, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(0, 20, 0, 0);
        return label;
    }

    // Helper for AlertDialog to override OnClick
    private interface DialogShowListener {
        void onShow(AlertDialog dialog);
    }
}

// Add simple helper for postShow since AlertDialog doesn't imply it directly in
// chain nicely
abstract class DialogHelper {
    // Just a dummy to explain the postShow usage above
}
