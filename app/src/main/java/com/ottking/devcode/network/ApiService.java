package com.ottking.devcode.network;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit interface for all OTT King API endpoints.
 */
public interface ApiService {

    @GET("categories.php")
    Call<ResponseBody> getCategories(
            @Header("X-Api-Key") String apiKey
    );

    @GET("channels.php")
    Call<ResponseBody> getChannels(
            @Query("session_token") String sessionToken,
            @Header("X-Api-Key") String apiKey
    );

    @POST("login.php")
    Call<ResponseBody> login(
            @Body RequestBody body,
            @Header("X-Api-Key") String apiKey,
            @Header("X-Signature") String signature
    );

    @POST("check-session.php")
    Call<ResponseBody> checkSession(
            @Body RequestBody body,
            @Header("X-Api-Key") String apiKey,
            @Header("X-Signature") String signature
    );

    @POST("logout.php")
    Call<ResponseBody> logout(
            @Body RequestBody body,
            @Header("X-Api-Key") String apiKey,
            @Header("X-Signature") String signature
    );

    @GET("update-app.php")
    Call<ResponseBody> checkUpdate(
            @Query("version_code") int versionCode,
            @Query("version_name") String versionName,
            @Query("build_version") int buildVersion,
            @Query("app_version") String appVersion,
            @Header("X-Api-Key") String apiKey
    );

    @POST("submit-reports.php")
    Call<ResponseBody> submitReport(
            @Body RequestBody body,
            @Header("X-Api-Key") String apiKey,
            @Header("X-Signature") String signature
    );

    @POST("stream-token.php")
    Call<ResponseBody> fetchStreamToken(
            @Body RequestBody body,
            @Header("X-Api-Key") String apiKey,
            @Header("X-Signature") String signature
    );

    @GET("notifications.php")
    Call<ResponseBody> getNotifications(
            @Header("X-Api-Key") String apiKey
    );

    @POST("track-stream.php")
    Call<ResponseBody> trackStream(
            @Body RequestBody body,
            @Header("X-Api-Key") String apiKey,
            @Header("X-Signature") String signature
    );
}
