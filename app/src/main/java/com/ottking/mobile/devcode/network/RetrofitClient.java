package com.ottking.mobile.devcode.network;

import com.ottking.mobile.devcode.config.Config;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

public class RetrofitClient {

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
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(Config.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(Config.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(Config.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
