package com.termux.app.turso;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.termux.app.ai.LlmClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okio.Buffer;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TursoClient {
    private TursoService service;
    private final String dbUrl;
    private final String authToken;

    public TursoClient(String dbUrl, String authToken) {
        // Handle libsql:// scheme or missing scheme
        if (dbUrl.startsWith("libsql://")) {
            dbUrl = dbUrl.replace("libsql://", "https://");
        } else if (!dbUrl.startsWith("http://") && !dbUrl.startsWith("https://")) {
            dbUrl = "https://" + dbUrl;
        }

        // Ensure URL ends with /v2/pipeline
        if (dbUrl.endsWith("/")) {
            dbUrl = dbUrl.substring(0, dbUrl.length() - 1);
        }
        if (!dbUrl.contains("/v2/pipeline")) {
            dbUrl = dbUrl + "/v2/pipeline";
        }

        this.dbUrl = dbUrl;
        this.authToken = "Bearer " + authToken;

        Gson gson = new GsonBuilder().serializeNulls().create();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request request = chain.request();
                    android.util.Log.d("TursoClient", "Sending request: " + request.url());

                    if (request.body() != null) {
                        okio.Buffer buffer = new okio.Buffer();
                        request.body().writeTo(buffer);
                        android.util.Log.d("TursoClient", "Request Body: " + buffer.readUtf8());
                    }

                    okhttp3.Response response = chain.proceed(request);
                    android.util.Log.d("TursoClient", "Received response: " + response.code());

                    okhttp3.ResponseBody responseBody = response.peekBody(100000);
                    android.util.Log.d("TursoClient", "Response Body: " + responseBody.string());

                    return response;
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://example.com/") // Base URL is ignored as we use @Url
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        this.service = retrofit.create(TursoService.class);
    }

    public void execute(String sql, List<Object> args, Callback callback) {
        List<TursoRequest.RequestItem> items = new ArrayList<>();
        items.add(new TursoRequest.RequestItem(sql, args));
        items.add(new TursoRequest.RequestItem("close"));

        TursoRequest request = new TursoRequest(items);

        service.execute(dbUrl, authToken, request).enqueue(new retrofit2.Callback<TursoResponse>() {
            @Override
            public void onResponse(retrofit2.Call<TursoResponse> call, Response<TursoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TursoResponse body = response.body();
                    if (body.results != null && !body.results.isEmpty()) {
                        TursoResponse.ResultItem item = body.results.get(0);
                        if ("ok".equals(item.type) && item.response != null) {
                            callback.onSuccess(item.response.result);
                        } else if ("error".equals(item.type) && item.error != null) {
                            callback.onError("Turso Error: " + item.error.message);
                        } else {
                            callback.onError("Unknown Turso response");
                        }
                    } else {
                        callback.onError("Empty results from Turso");
                    }
                } else {
                    try {
                        callback.onError("HTTP Error: " + response.code() + " " + response.errorBody().string());
                    } catch (IOException e) {
                        callback.onError("HTTP Error: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<TursoResponse> call, Throwable t) {
                callback.onError("Network Error: " + t.getMessage());
            }
        });
    }

    // Helper for simpler calls
    public void execute(String sql, Callback callback) {
        execute(sql, Collections.emptyList(), callback);
    }

    // Synchronous execution (blocking)
    public TursoResponse.ResultData executeSync(String sql, List<Object> args) throws IOException {
        List<TursoRequest.RequestItem> items = new ArrayList<>();
        items.add(new TursoRequest.RequestItem(sql, args));
        items.add(new TursoRequest.RequestItem("close")); // Protocol best practice

        TursoRequest request = new TursoRequest(items);

        Response<TursoResponse> response = service.execute(dbUrl, authToken, request).execute();
        if (response.isSuccessful() && response.body() != null) {
            TursoResponse body = response.body();
            if (body.results != null && !body.results.isEmpty()) {
                TursoResponse.ResultItem item = body.results.get(0);
                if ("ok".equals(item.type) && item.response != null) {
                    return item.response.result;
                } else if ("error".equals(item.type) && item.error != null) {
                    throw new IOException("Turso Error: " + item.error.message);
                }
            }
            throw new IOException("Empty or unknown results from Turso");
        } else {
            String errorMsg = "HTTP Error: " + response.code();
            if (response.errorBody() != null) {
                errorMsg += " " + response.errorBody().string();
            }
            throw new IOException(errorMsg);
        }
    }

    public interface Callback {
        void onSuccess(TursoResponse.ResultData result);

        void onError(String errorMessage);
    }
}
