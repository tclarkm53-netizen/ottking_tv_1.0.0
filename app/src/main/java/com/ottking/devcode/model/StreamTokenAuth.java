package com.ottking.devcode.model;

public class StreamTokenAuth {
    private final String streamToken;
    private final String appClient;
    private final long expiresAt;
    private final String authorizedStreamUrl;
    private final String authorizationHeader;
    private final boolean isSuccess;
    private final String message;
    private final String edgeCookie;

    public StreamTokenAuth(String streamToken, String appClient, long expiresAt, String authorizedStreamUrl, String authorizationHeader, boolean isSuccess, String message) {
        this(streamToken, appClient, expiresAt, authorizedStreamUrl, authorizationHeader, isSuccess, message, "");
    }

    public StreamTokenAuth(String streamToken, String appClient, long expiresAt, String authorizedStreamUrl, String authorizationHeader, boolean isSuccess, String message, String edgeCookie) {
        this.streamToken = streamToken;
        this.appClient = appClient;
        this.expiresAt = expiresAt;
        this.authorizedStreamUrl = authorizedStreamUrl;
        this.authorizationHeader = authorizationHeader;
        this.isSuccess = isSuccess;
        this.message = message;
        this.edgeCookie = edgeCookie != null ? edgeCookie : "";
    }

    public String getStreamToken() {
        return streamToken;
    }

    public String getAppClient() {
        return appClient;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getAuthorizedStreamUrl() {
        return authorizedStreamUrl;
    }

    public String getAuthorizationHeader() {
        return authorizationHeader;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String getMessage() {
        return message;
    }

    public String getEdgeCookie() {
        return edgeCookie;
    }
}
