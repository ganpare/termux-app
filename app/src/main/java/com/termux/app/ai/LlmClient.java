package com.termux.app.ai;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Callback;
import com.termux.shared.logger.Logger;

public class LlmClient {
    private static final String LOG_TAG = "LlmClient";
    private Retrofit mRetrofit;
    private LlmApi mApi;
    private String mBaseUrl;

    public LlmClient(String baseUrl) {
        updateConfig(baseUrl);
    }

    public void updateConfig(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            // Default to OpenRouter if empty, though Settings should handle this.
            baseUrl = "https://openrouter.ai/api/v1/";
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        if (mBaseUrl != null && mBaseUrl.equals(baseUrl)) {
            return; // No change
        }

        mBaseUrl = baseUrl;

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Logger.logDebug(LOG_TAG, message));
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        mRetrofit = new Retrofit.Builder()
                .baseUrl(mBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        mApi = mRetrofit.create(LlmApi.class);
        Logger.logDebug(LOG_TAG, "LlmClient initialized with URL: " + mBaseUrl);
    }

    public void chat(String apiKey, ChatRequest request, Callback<ChatResponse> callback) {
        if (mApi == null) {
            Logger.logError(LOG_TAG, "API not initialized");
            return;
        }
        String authHeader = "Bearer " + apiKey;
        mApi.chatCompletions(authHeader, request).enqueue(callback);
    }

    public void listModels(String apiKey, Callback<ModelListResponse> callback) {
        if (mApi == null) {
            Logger.logError(LOG_TAG, "API not initialized");
            return;
        }
        String authHeader = "Bearer " + apiKey;
        mApi.listModels(authHeader).enqueue(callback);
    }
}
