package com.termux.app.turso;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface TursoService {
    @POST
    Call<TursoResponse> execute(@Url String url, @Header("Authorization") String token, @Body TursoRequest request);
}
