package com.example.utils;

import android.os.Handler;
import android.os.Looper;

import com.example.database.ChannelEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3uParser {

    public interface OnM3uParseCallback {
        void onSuccess(List<ChannelEntity> channels);
        void onError(String errorMessage);
    }

    public static void parseUrlAsync(String m3uUrl, String defaultPlaylistTitle, OnM3uParseCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(m3uUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    List<ChannelEntity> channels = parseContent(reader, defaultPlaylistTitle);
                    reader.close();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (callback != null) {
                            if (channels.isEmpty()) {
                                callback.onError("No valid stream channels found in M3U playlist.");
                            } else {
                                callback.onSuccess(channels);
                            }
                        }
                    });
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (callback != null) {
                            callback.onError("Server returned HTTP " + responseCode);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onError("Failed to fetch M3U playlist: " + e.getLocalizedMessage());
                    }
                });
            }
        }).start();
    }

    public static List<ChannelEntity> parseContent(BufferedReader reader, String defaultPlaylistTitle) {
        List<ChannelEntity> list = new ArrayList<>();
        try {
            String line;
            String currentTitle = "";
            String currentLogo = "";
            String currentGroup = defaultPlaylistTitle != null ? defaultPlaylistTitle : "M3U Playlist";

            Pattern logoPattern = Pattern.compile("tvg-logo=\"([^\"]+)\"");
            Pattern groupPattern = Pattern.compile("group-title=\"([^\"]+)\"");

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#EXTINF:")) {
                    // Extract logo if available
                    Matcher logoMatcher = logoPattern.matcher(line);
                    if (logoMatcher.find()) {
                        currentLogo = logoMatcher.group(1);
                    } else {
                        currentLogo = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400";
                    }

                    // Extract group / category if available
                    String finalGroup = (defaultPlaylistTitle != null && !defaultPlaylistTitle.trim().isEmpty()) ? defaultPlaylistTitle.trim() : "M3U Playlist";
                    Matcher groupMatcher = groupPattern.matcher(line);
                    if (groupMatcher.find()) {
                        String parsedG = groupMatcher.group(1);
                        currentGroup = (parsedG != null && !parsedG.trim().isEmpty()) ? parsedG.trim() : finalGroup;
                    } else {
                        currentGroup = finalGroup;
                    }

                    // Extract channel name (after comma)
                    int commaIndex = line.lastIndexOf(',');
                    if (commaIndex != -1 && commaIndex < line.length() - 1) {
                        currentTitle = line.substring(commaIndex + 1).trim();
                    } else {
                        currentTitle = "IPTV Stream " + (list.size() + 1);
                    }
                } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtmp://"))) {
                    String streamUrl = line;
                    if (currentTitle.isEmpty()) {
                        currentTitle = "Stream " + (list.size() + 1);
                    }
                    if (currentLogo.isEmpty()) {
                        currentLogo = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400";
                    }

                    String streamType = "hls";
                    if (streamUrl.contains(".mpd")) {
                        streamType = "dash";
                    } else if (streamUrl.contains(".ts")) {
                        streamType = "ts";
                    }

                    String category = "tv";
                    String lowerGroup = currentGroup.toLowerCase();
                    String lowerTitle = currentTitle.toLowerCase();
                    String lowerUrl = streamUrl.toLowerCase();
                    if (lowerGroup.contains("movie") || lowerGroup.contains("vod") || lowerGroup.contains("cinema") || lowerGroup.contains("film")
                            || lowerTitle.contains("[movie]") || lowerTitle.contains("(movie)") || lowerTitle.contains("[vod]")
                            || lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".avi") || lowerUrl.contains("/movie/") || lowerUrl.contains("/vod/")) {
                        category = "movie";
                    }

                    ChannelEntity channel = new ChannelEntity(
                            currentTitle,
                            streamUrl,
                            currentLogo,
                            category,
                            currentGroup,
                            false,
                            streamType,
                            "1080p Full HD"
                    );
                    list.add(channel);

                    // Reset for next entry
                    currentTitle = "";
                    currentLogo = "";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
