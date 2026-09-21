package com.ottking.devcode.player;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;

import com.ottking.devcode.ui.PlayerActivity;
import com.ottking.devcode.utils.PlayerUtils;
import com.ottking.devcode.utils.SecurePlayerHeaders;

import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;

/**
 * Dedicated MPD / DASH Stream Configuration and MediaSource Factory.
 * Completely isolated from HLS configuration to prevent any cross-format conflicts.
 */
public final class DashConfig {

    private static final String DASH_ACCEPT_HEADER = "application/dash+xml, video/mp4, audio/mp4, */*";

    // Optimized Live Stream Offsets for MPD / DASH (Instant Zero-Delay Startup & Native Live Edge)
    public static final long DASH_TARGET_LIVE_OFFSET_MS = C.TIME_UNSET; // Derived directly from MPD manifest for zero-delay start
    public static final long DASH_MIN_LIVE_OFFSET_MS = C.TIME_UNSET;
    public static final long DASH_MAX_LIVE_OFFSET_MS = C.TIME_UNSET;
    public static final float DASH_MIN_PLAYBACK_SPEED = 0.97f;    // Dynamic micro-slowdown prevents hard buffering freezes
    public static final float DASH_MAX_PLAYBACK_SPEED = 1.03f;    // Dynamic micro-speedup seamlessly catches up to live edge

    private DashConfig() {}

    /**
     * Builds and returns an independent DashMediaSource with dedicated DASH headers,
     * DefaultDashChunkSource, fallback live target offset, and smooth live configuration.
     */
    public static MediaSource createMediaSource(
            Context context,
            String streamUrl,
            Map<String, String> customHeaders,
            String drmLicenseUri,
            String drmSchemeStr) {

        Context appContext = context.getApplicationContext();

        // 1. Dedicated DASH Headers
        Map<String, String> dashHeaders = SecurePlayerHeaders.getSecurePlayerHeaderMap(appContext, streamUrl);
        dashHeaders.put("Accept", DASH_ACCEPT_HEADER);
        if (customHeaders != null && !customHeaders.isEmpty()) {
            dashHeaders.putAll(customHeaders);
        }

        // 2. Dedicated DASH HTTP Data Source
        OkHttpClient client = PlayerActivity.getSharedOkHttpClient();
        if (client == null) {
            client = new OkHttpClient();
        }

        OkHttpDataSource.Factory httpDataSourceFactory = new OkHttpDataSource.Factory(client)
                .setUserAgent(PlayerUtils.DEFAULT_USER_AGENT)
                .setDefaultRequestProperties(dashHeaders);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(appContext, httpDataSourceFactory);

        // 3. Dedicated DASH MediaItem with Smooth Live Configuration
        Uri uri = Uri.parse(streamUrl);
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(DASH_TARGET_LIVE_OFFSET_MS)
                                .setMinOffsetMs(DASH_MIN_LIVE_OFFSET_MS)
                                .setMaxOffsetMs(DASH_MAX_LIVE_OFFSET_MS)
                                .setMinPlaybackSpeed(DASH_MIN_PLAYBACK_SPEED)
                                .setMaxPlaybackSpeed(DASH_MAX_PLAYBACK_SPEED)
                                .build()
                );

        if (drmLicenseUri != null && !drmLicenseUri.trim().isEmpty()) {
            java.util.UUID drmUuid = C.CLEARKEY_UUID;
            if (drmSchemeStr != null && "widevine".equalsIgnoreCase(drmSchemeStr)) {
                drmUuid = C.WIDEVINE_UUID;
            }
            mediaItemBuilder.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(drmUuid)
                            .setLicenseUri(drmLicenseUri.trim())
                            .build()
            );
        }

        MediaItem mediaItem = mediaItemBuilder.build();

        // 4. Dedicated DashMediaSource.Factory with DASH Chunk Source & DASH retry policy
        return new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(dataSourceFactory),
                dataSourceFactory
        )
        .setFallbackTargetLiveOffsetMs(DASH_TARGET_LIVE_OFFSET_MS)
        .setLoadErrorHandlingPolicy(new DashLoadErrorHandlingPolicy())
        .createMediaSource(mediaItem);
    }

    /**
     * Specialized load error handling policy for DASH streams:
     * Fast retry for MPD manifest reloads and chunk delays.
     */
    public static class DashLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
        public DashLoadErrorHandlingPolicy() {
            super(12);
        }

        @Override
        public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
            if (loadErrorInfo.exception instanceof ParserException) {
                return C.TIME_UNSET;
            }
            if (loadErrorInfo.exception instanceof HttpDataSource.InvalidResponseCodeException) {
                int code = ((HttpDataSource.InvalidResponseCodeException) loadErrorInfo.exception).responseCode;
                if (code == 401 || code == 403) {
                    if (loadErrorInfo.errorCount <= 3) {
                        return 300L;
                    }
                    return C.TIME_UNSET;
                }
                if (code == 404) {
                    if (loadErrorInfo.errorCount <= 3) {
                        return 400L;
                    }
                    return C.TIME_UNSET;
                }
                if (code == 410) {
                    return C.TIME_UNSET;
                }
            }
            int errorCount = loadErrorInfo.errorCount;
            if (errorCount > 12) {
                return C.TIME_UNSET;
            }
            // Fast backoff from 150ms up to 1500ms
            long delay = Math.min(150L * errorCount, 1500L);
            return delay;
        }

        @Override
        public int getMinimumLoadableRetryCount(int dataType) {
            return 12;
        }
    }
}
