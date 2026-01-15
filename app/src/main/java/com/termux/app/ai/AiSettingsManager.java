package com.termux.app.ai;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AiSettingsManager {
    private static final String PREFS_NAME = "termux_ai_settings";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_OPENROUTER_API_KEY = "openrouter_api_key";
    private static final String KEY_OPENAI_API_KEY = "openai_api_key";
    private static final String KEY_OPENROUTER_MODEL = "openrouter_model";
    private static final String KEY_OPENAI_MODEL = "openai_model";
    private static final String KEY_OPENROUTER_MODELS = "openrouter_models";
    private static final String KEY_OPENAI_MODELS = "openai_models";
    private static final String LEGACY_KEY_API_KEY = "api_key";
    private static final String LEGACY_KEY_MODEL = "model";
    private static final String KEY_TEMPLATES = "param_templates";
    private static final String KEY_AZURE_SPEECH_KEY = "azure_speech_key";
    private static final String KEY_AZURE_SERVICE_REGION = "azure_service_region";
    private static final String KEY_TURSO_DB_URL = "turso_db_url";
    private static final String KEY_TURSO_AUTH_TOKEN = "turso_auth_token";

    // Defaults
    public static final String PROVIDER_OPENROUTER = "openrouter";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/";
    public static final String OPENAI_BASE_URL = "https://api.openai.com/v1/";
    public static final String DEFAULT_OPENROUTER_MODEL = "google/gemini-2.0-flash-exp:free";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-5.2-mini";
    public static final int TEMPLATE_EXPORT_VERSION = 1;

    private final SharedPreferences mPrefs;
    private final Gson mGson;

    public AiSettingsManager(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mGson = new Gson();
    }

    public String getProvider() {
        return mPrefs.getString(KEY_PROVIDER, PROVIDER_OPENROUTER);
    }

    public void setProvider(String provider) {
        mPrefs.edit().putString(KEY_PROVIDER, provider).apply();
    }

    public String getOpenRouterApiKey() {
        String apiKey = mPrefs.getString(KEY_OPENROUTER_API_KEY, "");
        if (apiKey.isEmpty()) {
            return mPrefs.getString(LEGACY_KEY_API_KEY, "");
        }
        return apiKey;
    }

    public void setOpenRouterApiKey(String apiKey) {
        mPrefs.edit().putString(KEY_OPENROUTER_API_KEY, apiKey).apply();
    }

    public String getOpenAiApiKey() {
        return mPrefs.getString(KEY_OPENAI_API_KEY, "");
    }

    public void setOpenAiApiKey(String apiKey) {
        mPrefs.edit().putString(KEY_OPENAI_API_KEY, apiKey).apply();
    }

    public String getModelForProvider(String provider) {
        if (PROVIDER_OPENAI.equals(provider)) {
            return mPrefs.getString(KEY_OPENAI_MODEL, DEFAULT_OPENAI_MODEL);
        }
        String model = mPrefs.getString(KEY_OPENROUTER_MODEL, "");
        if (model.isEmpty()) {
            model = mPrefs.getString(LEGACY_KEY_MODEL, DEFAULT_OPENROUTER_MODEL);
        }
        return model;
    }

    public void setModelForProvider(String provider, String model) {
        if (PROVIDER_OPENAI.equals(provider)) {
            mPrefs.edit().putString(KEY_OPENAI_MODEL, model).apply();
            return;
        }
        mPrefs.edit().putString(KEY_OPENROUTER_MODEL, model).apply();
    }

    public String getBaseUrlForProvider(String provider) {
        if (PROVIDER_OPENAI.equals(provider)) {
            return OPENAI_BASE_URL;
        }
        return OPENROUTER_BASE_URL;
    }

    public List<String> getCachedModels(String provider) {
        String key = PROVIDER_OPENAI.equals(provider) ? KEY_OPENAI_MODELS : KEY_OPENROUTER_MODELS;
        String json = mPrefs.getString(key, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<String>>() {
        }.getType();
        List<String> models = mGson.fromJson(json, type);
        return models != null ? models : new ArrayList<>();
    }

    public void setCachedModels(String provider, List<String> models) {
        String key = PROVIDER_OPENAI.equals(provider) ? KEY_OPENAI_MODELS : KEY_OPENROUTER_MODELS;
        String json = mGson.toJson(models);
        mPrefs.edit().putString(key, json).apply();
    }

    public List<PromptTemplate> getTemplates() {
        String json = mPrefs.getString(KEY_TEMPLATES, null);
        if (json == null) {
            return getDefaultTemplates();
        }
        Type type = new TypeToken<List<PromptTemplate>>() {
        }.getType();
        return mGson.fromJson(json, type);
    }

    public void setTemplates(List<PromptTemplate> templates) {
        String json = mGson.toJson(templates);
        mPrefs.edit().putString(KEY_TEMPLATES, json).apply();
    }

    // ==================== Templates Export/Import ====================

    public String exportTemplatesToJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", TEMPLATE_EXPORT_VERSION);
        root.put("exportDate", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));

        JSONArray templatesArray = new JSONArray();
        for (PromptTemplate template : getTemplates()) {
            JSONObject item = new JSONObject();
            item.put("name", template.name != null ? template.name : "");
            item.put("systemPrompt", template.systemPrompt != null ? template.systemPrompt : "");
            templatesArray.put(item);
        }
        root.put("templates", templatesArray);

        return root.toString(2);
    }

    public void exportTemplatesToFile(OutputStream outputStream) throws IOException, JSONException {
        String json = exportTemplatesToJson();
        outputStream.write(json.getBytes("UTF-8"));
        outputStream.flush();
    }

    public TemplateImportData parseTemplateImportJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version", 1);
        JSONArray templatesArray = root.optJSONArray("templates");
        List<PromptTemplate> templates = new ArrayList<>();
        if (templatesArray != null) {
            for (int i = 0; i < templatesArray.length(); i++) {
                JSONObject item = templatesArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                String systemPrompt = item.optString("systemPrompt", "");
                templates.add(new PromptTemplate(name, systemPrompt));
            }
        }
        return new TemplateImportData(version, templates);
    }

    public TemplateImportData parseTemplateImportFile(InputStream inputStream) throws IOException, JSONException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return parseTemplateImportJson(sb.toString());
    }

    public TemplateImportResult importTemplates(TemplateImportData data, TemplateImportMode mode) {
        int templatesAdded = 0;
        int templatesUpdated = 0;

        List<PromptTemplate> existing = new ArrayList<>(getTemplates());
        if (mode == TemplateImportMode.CLEAR_AND_IMPORT) {
            existing.clear();
        }

        for (PromptTemplate importTemplate : data.templates) {
            PromptTemplate current = findTemplateByName(existing, importTemplate.name);
            if (current != null) {
                if (mode == TemplateImportMode.REPLACE || mode == TemplateImportMode.CLEAR_AND_IMPORT) {
                    current.systemPrompt = importTemplate.systemPrompt;
                    templatesUpdated++;
                }
            } else {
                existing.add(new PromptTemplate(importTemplate.name, importTemplate.systemPrompt));
                templatesAdded++;
            }
        }

        setTemplates(existing);
        return new TemplateImportResult(templatesAdded, templatesUpdated);
    }

    private PromptTemplate findTemplateByName(List<PromptTemplate> templates, String name) {
        for (PromptTemplate template : templates) {
            if (template.name != null && template.name.equals(name)) {
                return template;
            }
        }
        return null;
    }

    public enum TemplateImportMode {
        MERGE,
        REPLACE,
        CLEAR_AND_IMPORT
    }

    public static class TemplateImportData {
        public final int version;
        public final List<PromptTemplate> templates;

        public TemplateImportData(int version, List<PromptTemplate> templates) {
            this.version = version;
            this.templates = templates;
        }
    }

    public static class TemplateImportResult {
        public final int templatesAdded;
        public final int templatesUpdated;

        public TemplateImportResult(int templatesAdded, int templatesUpdated) {
            this.templatesAdded = templatesAdded;
            this.templatesUpdated = templatesUpdated;
        }

        @Override
        public String toString() {
            return "追加: " + templatesAdded + ", 更新: " + templatesUpdated;
        }
    }

    public String getAzureSpeechKey() {
        return mPrefs.getString(KEY_AZURE_SPEECH_KEY, "");
    }

    public void setAzureSpeechKey(String key) {
        mPrefs.edit().putString(KEY_AZURE_SPEECH_KEY, key).apply();
    }

    public String getAzureServiceRegion() {
        return mPrefs.getString(KEY_AZURE_SERVICE_REGION, "");
    }

    public void setAzureServiceRegion(String region) {
        mPrefs.edit().putString(KEY_AZURE_SERVICE_REGION, region).apply();
    }

    public String getTursoDbUrl() {
        return mPrefs.getString(KEY_TURSO_DB_URL, "");
    }

    public void setTursoDbUrl(String url) {
        mPrefs.edit().putString(KEY_TURSO_DB_URL, url).apply();
    }

    public String getTursoAuthToken() {
        return mPrefs.getString(KEY_TURSO_AUTH_TOKEN, "");
    }

    public void setTursoAuthToken(String token) {
        mPrefs.edit().putString(KEY_TURSO_AUTH_TOKEN, token).apply();
    }

    private List<PromptTemplate> getDefaultTemplates() {
        List<PromptTemplate> list = new ArrayList<>();
        list.add(new PromptTemplate("フリーチャット (Chat)", "あなたは親切なAIアシスタントです。ユーザーの質問に日本語で答えてください。"));
        list.add(new PromptTemplate("コード解説 (Explain Code)",
                "あなたは優秀なプログラマーです。提供されたコードを解析し、その機能や仕組みを日本語で分かりやすく解説してください。"));
        list.add(new PromptTemplate("翻訳 (To Japanese)", "以下のテキストを自然な日本語に翻訳してください。解説は不要です。"));
        list.add(new PromptTemplate("翻訳 (To English)",
                "Translate the following text into natural English. No explanation needed."));
        list.add(new PromptTemplate("要約 (Summarize)", "以下のテキストを要点を押さえて簡潔に要約してください。"));
        return list;
    }
}
