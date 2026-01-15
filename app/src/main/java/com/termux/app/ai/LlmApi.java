package com.termux.app.ai;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface LlmApi {
    @POST("chat/completions")
    Call<ChatResponse> chatCompletions(@Header("Authorization") String authHeader, @Body ChatRequest request);

    @GET("models")
    Call<ModelListResponse> listModels(@Header("Authorization") String authHeader);
}
