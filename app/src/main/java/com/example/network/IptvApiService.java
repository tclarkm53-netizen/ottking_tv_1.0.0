package com.example.network;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface IptvApiService {

    @GET
    Call<ResponseBody> fetchFromDynamicUrl(
            @Url String url,
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=live_tv")
    Call<ResponseBody> getLiveTvRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=categories")
    Call<ResponseBody> getCategoriesRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=live_tv_channels")
    Call<ResponseBody> getLiveTvChannelsRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=channels")
    Call<ResponseBody> getChannelsRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=movies")
    Call<ResponseBody> getMoviesRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=login")
    Call<ResponseBody> loginRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=session_check")
    Call<ResponseBody> sessionCheckRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=logout")
    Call<ResponseBody> logoutRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=app_data")
    Call<ResponseBody> getAppDataRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=app_config")
    Call<ResponseBody> getAppConfigRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=series")
    Call<ResponseBody> getSeriesRoute(
            @Header("X-API-Key") String apiKey,
            @Header("X-Timestamp") String timestamp,
            @Header("X-HMAC-Signature") String signature
    );

    @GET("index.php?route=health")
    Call<ResponseBody> checkHealth(
            @Header("X-API-Key") String apiKey
    );
}
