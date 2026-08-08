# Keep all data models and entities
-keep class com.example.model.** { *; }
-keep class com.example.db.** { *; }
-keep class com.example.network.** { *; }
-keep class com.example.preferences.** { *; }
-keep class com.example.security.** { *; }

# Room Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 ExoPlayer Rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Retrofit Rules
-keep class okhttp3.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Moshi Rules
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

