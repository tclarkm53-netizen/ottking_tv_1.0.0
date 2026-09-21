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
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import com.ottking.devcode.ui.PlayerActivity;
import com.ottking.devcode.utils.PlayerUtils;
import com.ottking.devcode.utils.SecurePlayerHeaders;

import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;

/**
 * Dedicated HLS Stream Configuration and MediaSource Factory.
 * Completely isolated from MPD / DASH configuration to prevent any cross-format conflicts.
 */
public final class HlsConfig {

    private static final String HLS_ACCEPT_HEADER = "application/x-mpegURL, application/vnd.apple.mpegurl, video/MP2T, */*";

    // Optimized Live Stream Offsets for HLS (Instant Zero-Delay Startup & Native Live Edge)
    public static final long HLS_TARGET_LIVE_OFFSET_MS = C.TIME_UNSET; // Derived directly from manifest for instant zero-delay playback start
    public static final long HLS_MIN_LIVE_OFFSET_MS = C.TIME_UNSET;
    public static final long HLS_MAX_LIVE_OFFSET_MS = C.TIME_UNSET;
    public static final float HLS_MIN_PLAYBACK_SPEED = 0.97f;    // Dynamic micro-slowdown prevents hard buffering freezes
    public static final float HLS_MAX_PLAYBACK_SPEED = 1.03f;    // Dynamic micro-speedup seamlessly catches up to live edge

    private HlsConfig() {}

    /**
     * Builds and returns an independent HlsMediaSource with dedicated HLS headers,
     * MPEG-TS extractor flags, deep segment preparation, and smooth live configuration.
     */
    public static MediaSource createMediaSource(
            Context context,
            String streamUrl,
            Map<String, String> customHeaders,
            String drmLicenseUri,
            String drmSchemeStr) {

        Context appContext = context.getApplicationContext();

        // 1. Dedicated HLS Headers
        Map<String, String> hlsHeaders = SecurePlayerHeaders.getSecurePlayerHeaderMap(appContext, streamUrl);
        hlsHeaders.put("Accept", HLS_ACCEPT_HEADER);
        if (customHeaders != null && !customHeaders.isEmpty()) {
            hlsHeaders.putAll(customHeaders);
        }

        // 2. Dedicated HLS HTTP Data Source
        OkHttpClient client = PlayerActivity.getSharedOkHttpClient();
        if (client == null) {
            client = new OkHttpClient();
        }

        OkHttpDataSource.Factory httpDataSourceFactory = new OkHttpDataSource.Factory(client)
                .setUserAgent(PlayerUtils.DEFAULT_USER_AGENT)
                .setDefaultRequestProperties(hlsHeaders);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(appContext, httpDataSourceFactory);

        // 3. Dedicated HLS MediaItem with Smooth Live Configuration
        Uri uri = Uri.parse(streamUrl);
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(HLS_TARGET_LIVE_OFFSET_MS)
                                .setMinOffsetMs(HLS_MIN_LIVE_OFFSET_MS)
                                .setMaxOffsetMs(HLS_MAX_LIVE_OFFSET_MS)
                                .setMinPlaybackSpeed(HLS_MIN_PLAYBACK_SPEED)
                                .setMaxPlaybackSpeed(HLS_MAX_PLAYBACK_SPEED)
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

        // 4. Dedicated HLS TS Extractor Factory
        int tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                | DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

        DefaultHlsExtractorFactory hlsExtractorFactory = new DefaultHlsExtractorFactory(tsFlags, true);

        // 5. Dedicated HlsMediaSource.Factory with ultra-fast chunkless preparation & HLS retry policy
        return new HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(hlsExtractorFactory)
                .setAllowChunklessPreparation(true)
                .setUseSessionKeys(true)
                .setLoadErrorHandlingPolicy(new HlsLoadErrorHandlingPolicy())
                .createMediaSource(mediaItem);
    }

    /**
     * Specialized load error handling policy for HLS streams:
     * Fast proactive retry for playlist refreshes and chunk delays to avoid buffer underruns.
     */
    public static class HlsLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
        public HlsLoadErrorHandlingPolicy() {
            super(12);
        }

        @Override
        public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
            if (loadErrorInfo.exception instanceof ParserException) {
                return C.TIME_UNSET;
            }
            if (loadErrorInfo.exception instanceof HttpDataSource.InvalidResponseCodeException) {
                int code = ((HttpDataSource.InvalidResponseCodeException) loadErrorInfo.exception).responseCode;
                // Fast recovery retry for transient authorization gaps or CDN chunk synchronization
                if (code == 401 || code == 403) {
                    if (loadErrorInfo.errorCount <= 3) {
                        return 300L;
                    }
                    return C.TIME_UNSET;
                }
                if (code == 404) {
                    // For live HLS chunks that are publishing, retry up to 3 times quickly
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
            // Ultra-snappy backoff starting at 150ms to prevent buffer exhaustion
            long delay = Math.min(150L * errorCount, 1500L);
            return delay;
        }

        @Override
        public int getMinimumLoadableRetryCount(int dataType) {
            return 12;
        }
    }
}
