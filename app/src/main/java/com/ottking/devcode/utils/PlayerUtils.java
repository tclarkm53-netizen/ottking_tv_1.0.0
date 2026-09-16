package com.ottking.devcode.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.network.Config;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.security.SecurityUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;

public class PlayerUtils {

    public static final String DEFAULT_USER_AGENT = SecurePlayerHeaders.getDecryptedUserAgent();

    public enum StreamFormat {
        DASH,
        HLS,
        TS,
        PROGRESSIVE
    }

    public static Map<String, String> getDefaultPlayerHeaders(Context context) {
        return getDefaultPlayerHeaders(context, null);
    }

    public static Map<String, String> getDefaultPlayerHeaders(Context context, String streamUrl) {
        return SecurePlayerHeaders.getSecurePlayerHeaderMap(context, streamUrl);
    }

    public static String getOrGenerateApiSignature(Context context, String streamUrl) {
        if (context == null) return "";
        Context appContext = context.getApplicationContext();

        try {
            String hmacKey = AppPreferences.getInstance(appContext).getHmacKey();
            if (hmacKey == null || hmacKey.isEmpty()) {
                hmacKey = Config.HMAC_KEY;
            }
            if (hmacKey == null || hmacKey.trim().isEmpty()) {
                return "";
            }
            long timestamp = System.currentTimeMillis();
            String path = "/";
            if (streamUrl != null && !streamUrl.trim().isEmpty()) {
                try {
                    Uri uri = Uri.parse(streamUrl.trim());
                    if (uri.getPath() != null && !uri.getPath().isEmpty()) {
                        path = uri.getPath();
                    }
                } catch (Exception ignored) {}
            }
            String hmacData = timestamp + path;
            String generatedSig = SecurityUtils.generateHmac(hmacData, hmacKey);
            if (generatedSig != null && !generatedSig.isEmpty()) {
                return generatedSig;
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static MediaSource createMediaSource(Context context, String streamUrl, String streamType) {
        return createMediaSource(context, streamUrl, streamType, 0);
    }

    public static MediaSource createMediaSource(Context context, String streamUrl, String streamType, int retryAttempt) {
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
        }

        String rawUrl = streamUrl.trim();
        Map<String, String> customHeaders = new HashMap<>();
        String actualUrl = rawUrl;
        String drmLicenseUri = null;
        String drmSchemeStr = null;

        // Parse embedded pipe headers and DRM configurations
        if (rawUrl.contains("|")) {
            int pipeIndex = rawUrl.indexOf('|');
            String headerPart = rawUrl.substring(pipeIndex + 1).trim();
            actualUrl = rawUrl.substring(0, pipeIndex).trim();

            String[] headerPairs = headerPart.split("[&|]");
            for (String pair : headerPairs) {
                if (pair.contains("=")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2 && !kv[0].trim().isEmpty()) {
                        String key = kv[0].trim();
                        String val = kv[1].trim();
                        if ("drmScheme".equalsIgnoreCase(key) || "drm".equalsIgnoreCase(key)) {
                            drmSchemeStr = val;
                        } else if ("drmLicense".equalsIgnoreCase(key) || "license_key".equalsIgnoreCase(key) || "license".equalsIgnoreCase(key)) {
                            drmLicenseUri = val;
                        } else {
                            customHeaders.put(key, val);
                        }
                    }
                }
            }
        }

        Map<String, String> finalHeaders = getDefaultPlayerHeaders(context, actualUrl);
        if (!customHeaders.isEmpty()) {
            finalHeaders.putAll(customHeaders);
        }

        OkHttpClient okClient = com.ottking.devcode.ui.PlayerActivity.getSharedOkHttpClient();
        if (okClient == null) {
            okClient = new OkHttpClient();
        }

        OkHttpDataSource.Factory httpFactory = new OkHttpDataSource.Factory(okClient)
                .setUserAgent(DEFAULT_USER_AGENT)
                .setDefaultRequestProperties(finalHeaders);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context.getApplicationContext(), httpFactory);
        StreamFormat format = detectStreamFormat(actualUrl, streamType);

        switch (format) {
            case DASH:
                return createDashMediaSource(actualUrl, dataSourceFactory, drmLicenseUri, drmSchemeStr);
            case HLS:
                return createHlsMediaSource(actualUrl, dataSourceFactory, drmLicenseUri, drmSchemeStr);
            case TS:
            case PROGRESSIVE:
            default:
                return createTsMediaSource(actualUrl, dataSourceFactory, drmLicenseUri, drmSchemeStr);
        }
    }

    public static StreamFormat detectStreamFormat(String url, String streamType) {
        String lowerUrl = url != null ? url.toLowerCase() : "";
        String lowerType = streamType != null ? streamType.toLowerCase() : "";

        if (lowerUrl.contains(".mpd") || "dash".equals(lowerType) || "mpd".equals(lowerType) || lowerUrl.contains("mpd")) {
            return StreamFormat.DASH;
        }

        if (lowerUrl.contains(".m3u8") || "hls".equals(lowerType) || lowerUrl.contains("m3u8") || lowerUrl.contains("/hls")) {
            return StreamFormat.HLS;
        }

        if (lowerUrl.contains(".ts") || "ts".equals(lowerType)) {
            return StreamFormat.TS;
        }

        return StreamFormat.PROGRESSIVE;
    }

    private static MediaSource createDashMediaSource(
            String actualUrl,
            DefaultDataSource.Factory dataSourceFactory,
            String drmLicenseUri,
            String drmSchemeStr) {

        Uri uri = Uri.parse(actualUrl);
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder()
                                .setMaxPlaybackSpeed(1.0f)
                                .setMinPlaybackSpeed(1.0f)
                                .setTargetOffsetMs(C.TIME_UNSET)
                                .build()
                );

        attachDrmIfPresent(mediaItemBuilder, drmLicenseUri, drmSchemeStr);

        MediaItem mediaItem = mediaItemBuilder.build();
        DashMediaSource.Factory dashFactory = new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(dataSourceFactory),
                dataSourceFactory
        )
        .setFallbackTargetLiveOffsetMs(5000)
        .setLoadErrorHandlingPolicy(new ExponentialBackoffLoadErrorHandlingPolicy(6, 300L, 6000L, 1.5));

        return dashFactory.createMediaSource(mediaItem);
    }

    private static MediaSource createHlsMediaSource(
            String actualUrl,
            DefaultDataSource.Factory dataSourceFactory,
            String drmLicenseUri,
            String drmSchemeStr) {

        Uri uri = Uri.parse(actualUrl);
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder()
                                .setMaxPlaybackSpeed(1.0f)
                                .setMinPlaybackSpeed(1.0f)
                                .setTargetOffsetMs(C.TIME_UNSET)
                                .build()
                );

        attachDrmIfPresent(mediaItemBuilder, drmLicenseUri, drmSchemeStr);

        MediaItem mediaItem = mediaItemBuilder.build();

        int tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                | DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

        DefaultHlsExtractorFactory hlsExtractorFactory = new DefaultHlsExtractorFactory(tsFlags, true);

        return new HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(hlsExtractorFactory)
                .setAllowChunklessPreparation(true)
                .setLoadErrorHandlingPolicy(new ExponentialBackoffLoadErrorHandlingPolicy(8, 250L, 6000L, 1.5))
                .createMediaSource(mediaItem);
    }

    private static MediaSource createTsMediaSource(
            String actualUrl,
            DefaultDataSource.Factory dataSourceFactory,
            String drmLicenseUri,
            String drmSchemeStr) {

        Uri uri = Uri.parse(actualUrl);
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(uri);
        attachDrmIfPresent(mediaItemBuilder, drmLicenseUri, drmSchemeStr);

        MediaItem mediaItem = mediaItemBuilder.build();

        int tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                | DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setTsExtractorFlags(tsFlags);

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                .setLoadErrorHandlingPolicy(new ExponentialBackoffLoadErrorHandlingPolicy(6, 250L, 6000L, 1.5));
        return mediaSourceFactory.createMediaSource(mediaItem);
    }

    private static void attachDrmIfPresent(MediaItem.Builder builder, String drmLicenseUri, String drmSchemeStr) {
        if (drmLicenseUri != null && !drmLicenseUri.trim().isEmpty()) {
            java.util.UUID drmUuid = C.CLEARKEY_UUID;
            if (drmSchemeStr != null && "widevine".equalsIgnoreCase(drmSchemeStr)) {
                drmUuid = C.WIDEVINE_UUID;
            }
            builder.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(drmUuid)
                            .setLicenseUri(drmLicenseUri.trim())
                            .build()
            );
        }
    }

    public static class ExponentialBackoffLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
        private final int minRetryCount;
        private final long initialDelayMs;
        private final long maxDelayMs;
        private final double multiplier;

        public ExponentialBackoffLoadErrorHandlingPolicy() {
            this(6, 400L, 8000L, 1.8);
        }

        public ExponentialBackoffLoadErrorHandlingPolicy(int minRetryCount, long initialDelayMs, long maxDelayMs, double multiplier) {
            super(minRetryCount);
            this.minRetryCount = minRetryCount;
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.multiplier = multiplier;
        }

        @Override
        public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
            if (loadErrorInfo.exception instanceof ParserException) {
                return C.TIME_UNSET;
            }
            if (loadErrorInfo.exception instanceof HttpDataSource.InvalidResponseCodeException) {
                int code = ((HttpDataSource.InvalidResponseCodeException) loadErrorInfo.exception).responseCode;
                if (code == 401 || code == 403 || code == 404 || code == 410) {
                    return C.TIME_UNSET;
                }
            }

            int errorCount = loadErrorInfo.errorCount;
            if (errorCount > minRetryCount) {
                return C.TIME_UNSET;
            }

            long calculated = (long) (initialDelayMs * Math.pow(multiplier, errorCount - 1));
            long capped = Math.min(calculated, maxDelayMs);
            long jitter = (long) ((Math.random() * 0.3 - 0.15) * capped);
            return Math.max(initialDelayMs, capped + jitter);
        }

        @Override
        public int getMinimumLoadableRetryCount(int dataType) {
            return minRetryCount;
        }
    }

    public static int getHttpErrorCode(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) current).responseCode;
            }
            current = current.getCause();
        }
        return -1;
    }
}
