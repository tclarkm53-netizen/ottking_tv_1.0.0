package com.example.utils;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import java.util.HashMap;
import java.util.Map;

public class PlayerUtils {

    public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    public static DefaultHttpDataSource.Factory createHttpDataSourceFactory() {
        Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("Accept", "*/*");
        requestProperties.put("User-Agent", DEFAULT_USER_AGENT);

        return new DefaultHttpDataSource.Factory()
                .setUserAgent(DEFAULT_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(requestProperties);
    }

    public static ExoPlayer createExoPlayer(Context context) {
        Context appContext = context.getApplicationContext();
        DefaultHttpDataSource.Factory httpDataSourceFactory = createHttpDataSourceFactory();
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpDataSourceFactory);

        // Configure renderers with software decoder fallback to prevent MediaCodec/HAL failures
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(appContext)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                .setEnableDecoderFallback(true)
                .setAllowedVideoJoiningTimeMs(5000);

        // Smooth buffer control
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        15000,
                        50000,
                        1500,
                        3000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(10000, false)
                .build();

        // Audio focus and attributes
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(appContext);

        return new ExoPlayer.Builder(appContext)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
    }

    public static MediaSource createMediaSource(Context context, String streamUrl, String streamType) {
        DefaultHttpDataSource.Factory httpDataSourceFactory = createHttpDataSourceFactory();
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
        }
        
        Uri uri = Uri.parse(streamUrl.trim());
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(uri);

        String typeStr = (streamType != null) ? streamType.trim().toUpperCase() : "";
        String urlLower = streamUrl.toLowerCase();

        if (urlLower.contains(".m3u8") || typeStr.equals("M3U8") || (typeStr.equals("HLS") && !urlLower.endsWith(".mp4") && !urlLower.endsWith(".mkv") && !urlLower.endsWith(".ts"))) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8);
            HlsMediaSource.Factory hlsFactory = new HlsMediaSource.Factory(httpDataSourceFactory)
                    .setAllowChunklessPreparation(true);
            return hlsFactory.createMediaSource(mediaItemBuilder.build());
        } else if (urlLower.contains(".mpd") || typeStr.equals("DASH") || typeStr.equals("MPD")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD);
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpDataSourceFactory);
            return mediaSourceFactory.createMediaSource(mediaItemBuilder.build());
        } else if (urlLower.contains(".mp4")) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4);
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpDataSourceFactory);
            return mediaSourceFactory.createMediaSource(mediaItemBuilder.build());
        } else if (urlLower.contains(".mkv")) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MATROSKA);
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpDataSourceFactory);
            return mediaSourceFactory.createMediaSource(mediaItemBuilder.build());
        } else if (urlLower.contains(".webm")) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_WEBM);
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpDataSourceFactory);
            return mediaSourceFactory.createMediaSource(mediaItemBuilder.build());
        } else if (urlLower.contains(".ts") || typeStr.equals("TS")) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T);
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpDataSourceFactory);
            return mediaSourceFactory.createMediaSource(mediaItemBuilder.build());
        } else {
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpDataSourceFactory);
            return mediaSourceFactory.createMediaSource(mediaItemBuilder.build());
        }
    }
}

