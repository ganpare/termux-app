package com.termux.app.ai;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

import com.termux.app.TermuxActivity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AiSettingsDialog {
    private final Context mContext;
    private final TermuxActivity mActivity;
    private final AiSettingsManager mSettingsManager;
    private final LlmClient mLlmClient;

    public AiSettingsDialog(TermuxActivity activity, AiSettingsManager settingsManager, LlmClient llmClient) {
        mActivity = activity;
        mContext = activity;
        mSettingsManager = settingsManager;
        mLlmClient = llmClient;
    }

    public void show() {
        ScrollView scrollView = new ScrollView(mContext);
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * mContext.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        scrollView.addView(layout);

        List<String> providerIds = Arrays.asList(AiSettingsManager.PROVIDER_OPENROUTER,
                AiSettingsManager.PROVIDER_OPENAI);
        List<String> providerLabels = Arrays.asList("OpenRouter", "OpenAI");

        layout.addView(createLabel("Provider:"));
        Spinner providerSpinner = new Spinner(mContext);
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                providerLabels);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(providerAdapter);
        int providerIndex = providerIds.indexOf(mSettingsManager.getProvider());
        providerSpinner.setSelection(providerIndex >= 0 ? providerIndex : 0);
        layout.addView(providerSpinner);

        EditText openRouterKeyInput = new EditText(mContext);
        openRouterKeyInput.setHint("OpenRouter API Key");
        openRouterKeyInput.setText(mSettingsManager.getOpenRouterApiKey());
        layout.addView(createLabel("OpenRouter API Key:"));
        layout.addView(openRouterKeyInput);

        EditText openAiKeyInput = new EditText(mContext);
        openAiKeyInput.setHint("OpenAI API Key");
        openAiKeyInput.setText(mSettingsManager.getOpenAiApiKey());
        layout.addView(createLabel("OpenAI API Key:"));
        layout.addView(openAiKeyInput);

        layout.addView(createLabel("Model:"));
        LinearLayout modelRow = new LinearLayout(mContext);
        modelRow.setOrientation(LinearLayout.HORIZONTAL);

        Spinner modelSpinner = new Spinner(mContext);
        List<String> modelItems = new ArrayList<>();
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                modelItems);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
        modelRow.addView(modelSpinner, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button refreshModelsButton = new Button(mContext);
        refreshModelsButton.setText("更新");
        modelRow.addView(refreshModelsButton);

        layout.addView(modelRow);

        EditText azureKeyInput = new EditText(mContext);
        azureKeyInput.setHint("Azure Speech Key");
        azureKeyInput.setText(mSettingsManager.getAzureSpeechKey());
        layout.addView(createLabel("Azure Speech Key:"));
        layout.addView(azureKeyInput);

        EditText azureRegionInput = new EditText(mContext);
        azureRegionInput.setHint("Azure Region (e.g., japaneast)");
        azureRegionInput.setText(mSettingsManager.getAzureServiceRegion());
        layout.addView(createLabel("Azure Service Region:"));
        layout.addView(azureRegionInput);

        EditText tursoUrlInput = new EditText(mContext);
        tursoUrlInput.setHint("Turso DB URL (https://...)");
        tursoUrlInput.setText(mSettingsManager.getTursoDbUrl());
        layout.addView(createLabel("Turso DB URL:"));
        layout.addView(tursoUrlInput);

        EditText tursoTokenInput = new EditText(mContext);
        tursoTokenInput.setHint("Turso Auth Token");
        tursoTokenInput.setText(mSettingsManager.getTursoAuthToken());
        layout.addView(createLabel("Turso Auth Token:"));
        layout.addView(tursoTokenInput);

        Button testTursoButton = new Button(mContext);
        testTursoButton.setText("🔄 Test Connection & Init DB");
        testTursoButton.setOnClickListener(v -> {
            String url = tursoUrlInput.getText().toString().trim();
            String token = tursoTokenInput.getText().toString().trim();
            if (url.isEmpty() || token.isEmpty()) {
                Toast.makeText(mContext, "Please enter URL and Token first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mActivity != null) {
                mActivity.testTursoConnection(url, token);
            }
        });
        layout.addView(testTursoButton);

        layout.addView(createLabel("Prompt Templates:"));
        LinearLayout templateIoRow = new LinearLayout(mContext);
        templateIoRow.setOrientation(LinearLayout.HORIZONTAL);

        Button exportTemplatesButton = new Button(mContext);
        exportTemplatesButton.setText("📤 エクスポート");
        exportTemplatesButton.setOnClickListener(v -> {
            if (mActivity != null) {
                mActivity.exportAiTemplates();
            }
        });
        templateIoRow.addView(exportTemplatesButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button importTemplatesButton = new Button(mContext);
        importTemplatesButton.setText("📥 インポート");
        importTemplatesButton.setOnClickListener(v -> {
            if (mActivity != null) {
                mActivity.importAiTemplates();
            }
        });
        templateIoRow.addView(importTemplatesButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        layout.addView(templateIoRow);

        updateModelSpinner(providerIds, providerSpinner, modelItems, modelAdapter, modelSpinner);

        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateModelSpinner(providerIds, providerSpinner, modelItems, modelAdapter, modelSpinner);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        refreshModelsButton.setOnClickListener(v -> {
            String provider = getSelectedProvider(providerIds, providerSpinner);
            String apiKey = AiSettingsManager.PROVIDER_OPENAI.equals(provider)
                    ? openAiKeyInput.getText().toString().trim()
                    : openRouterKeyInput.getText().toString().trim();
            if (apiKey.isEmpty()) {
                Toast.makeText(mContext, "APIキーを入力してください", Toast.LENGTH_SHORT).show();
                return;
            }

            mLlmClient.updateConfig(mSettingsManager.getBaseUrlForProvider(provider));
            mLlmClient.listModels(apiKey, new retrofit2.Callback<ModelListResponse>() {
                @Override
                public void onResponse(retrofit2.Call<ModelListResponse> call,
                        retrofit2.Response<ModelListResponse> response) {
                    if (!response.isSuccessful() || response.body() == null || response.body().data == null) {
                        Toast.makeText(mContext, "モデル一覧の取得に失敗しました", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> models = new ArrayList<>();
                    for (ModelListResponse.ModelInfo info : response.body().data) {
                        if (info == null || info.id == null || info.id.isEmpty()) {
                            continue;
                        }
                        if (AiSettingsManager.PROVIDER_OPENAI.equals(provider)
                                && !info.id.toLowerCase().startsWith("gpt-")) {
                            continue;
                        }
                        if (AiSettingsManager.PROVIDER_OPENROUTER.equals(provider)
                                && !isOpenRouterFreeModel(info)) {
                            continue;
                        }
                        models.add(info.id);
                    }
                    Collections.sort(models, String.CASE_INSENSITIVE_ORDER);

                    if (models.isEmpty()) {
                        Toast.makeText(mContext, "モデルが見つかりません", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    mSettingsManager.setCachedModels(provider, models);
                    updateModelSpinner(providerIds, providerSpinner, modelItems, modelAdapter, modelSpinner);
                    Toast.makeText(mContext, "モデル一覧を更新しました", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(retrofit2.Call<ModelListResponse> call, Throwable t) {
                    Toast.makeText(mContext, "モデル取得失敗: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        new AlertDialog.Builder(mContext)
                .setTitle("AI Settings")
                .setView(scrollView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String provider = getSelectedProvider(providerIds, providerSpinner);
                    String openRouterKey = openRouterKeyInput.getText().toString().trim();
                    String openAiKey = openAiKeyInput.getText().toString().trim();
                    String model = modelSpinner.getSelectedItem() != null
                            ? modelSpinner.getSelectedItem().toString().trim()
                            : "";
                    String azureKey = azureKeyInput.getText().toString().trim();
                    String azureRegion = azureRegionInput.getText().toString().trim();
                    String tursoUrl = tursoUrlInput.getText().toString().trim();
                    String tursoToken = tursoTokenInput.getText().toString().trim();

                    mSettingsManager.setProvider(provider);
                    mSettingsManager.setOpenRouterApiKey(openRouterKey);
                    mSettingsManager.setOpenAiApiKey(openAiKey);
                    if (!model.isEmpty()) {
                        mSettingsManager.setModelForProvider(provider, model);
                    }
                    mSettingsManager.setAzureSpeechKey(azureKey);
                    mSettingsManager.setAzureServiceRegion(azureRegion);
                    mSettingsManager.setTursoDbUrl(tursoUrl);
                    mSettingsManager.setTursoAuthToken(tursoToken);

                    // Update Retrofit client
                    mLlmClient.updateConfig(mSettingsManager.getBaseUrlForProvider(provider));

                    Toast.makeText(mContext, "Settings saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private android.widget.TextView createLabel(String text) {
        android.widget.TextView label = new android.widget.TextView(mContext);
        label.setText(text);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(0, 20, 0, 0);
        return label;
    }

    private void updateModelSpinner(List<String> providerIds, Spinner providerSpinner, List<String> modelItems,
            ArrayAdapter<String> modelAdapter, Spinner modelSpinner) {
        String provider = getSelectedProvider(providerIds, providerSpinner);
        String currentModel = mSettingsManager.getModelForProvider(provider);
        List<String> cachedModels = mSettingsManager.getCachedModels(provider);

        modelItems.clear();
        if (cachedModels.isEmpty()) {
            if (currentModel != null && !currentModel.isEmpty()) {
                modelItems.add(currentModel);
            }
        } else {
            modelItems.addAll(cachedModels);
            if (currentModel != null && !currentModel.isEmpty() && !cachedModels.contains(currentModel)) {
                modelItems.add(0, currentModel);
            }
        }

        modelAdapter.notifyDataSetChanged();
        int index = modelItems.indexOf(currentModel);
        if (index >= 0) {
            modelSpinner.setSelection(index);
        }
    }

    private String getSelectedProvider(List<String> providerIds, Spinner providerSpinner) {
        int position = providerSpinner.getSelectedItemPosition();
        if (position < 0 || position >= providerIds.size()) {
            return AiSettingsManager.PROVIDER_OPENROUTER;
        }
        return providerIds.get(position);
    }

    private boolean isOpenRouterFreeModel(ModelListResponse.ModelInfo info) {
        String id = info.id != null ? info.id.toLowerCase() : "";
        String name = info.name != null ? info.name.toLowerCase() : "";
        return id.contains("free") || name.contains("free");
    }
}
