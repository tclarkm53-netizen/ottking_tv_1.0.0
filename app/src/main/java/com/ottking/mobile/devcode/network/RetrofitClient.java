package com.ottking.mobile.devcode.network;

import android.util.Log;
import com.ottking.mobile.devcode.config.Config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

public class RetrofitClient {

    private static final String TAG = "RetrofitClient";
    private static Retrofit retrofit = null;
    private static String currentBaseUrl = "";

    public static synchronized Retrofit getClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = Config.BASE_URL;
        }

        // Ensure baseUrl ends with /
        if (!baseUrl.endsWith("/")) {
            int lastSlash = baseUrl.lastIndexOf("/");
            if (lastSlash > 8) { // After http:// or https://
                baseUrl = baseUrl.substring(0, lastSlash + 1);
            } else {
                baseUrl = baseUrl + "/";
            }
        }

        if (retrofit == null || !baseUrl.equals(currentBaseUrl)) {
            currentBaseUrl = baseUrl;

            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> {
                Log.d("OkHttpNetwork", message);
            });
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            Interceptor securityInspectionInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request request = chain.request();
                    Response response = chain.proceed(request);

                    String hmacHeader = response.header("X-HMAC-Signature");
                    Log.d(TAG, "Request URL: " + request.url());
                    Log.d(TAG, "Response Code: " + response.code());
                    if (hmacHeader != null) {
                        Log.d(TAG, "Response Header [X-HMAC-Signature]: " + hmacHeader);
                    }
                    return response;
                }
            };

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(Config.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(Config.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(Config.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor(securityInspectionInterceptor)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .build();
        }
        return retrofit;
    }

    public static IptvApiService getApiService(String baseUrl) {
        return getClient(baseUrl).create(IptvApiService.class);
    }
}
