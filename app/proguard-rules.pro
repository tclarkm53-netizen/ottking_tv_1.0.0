# ProGuard & R8 Optimization & Obfuscation Rules for Release Builds

# Obfuscate SourceFile and LineNumbers so original file names (like Config.java) never appear in DEX
-renamesourcefileattribute ''
-keepattributes *Annotation*,Signature

# Standard Android Components - ONLY keep Activities, Receivers & Providers required by AndroidManifest
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.core.content.FileProvider

# Android Views used in layout XML inflation
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# Room Database Engine & Generated Impl Keep Rules (required by SQLite / Room reflection)
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.EntityDeletionOrUpdateAdapter
-keep class * extends androidx.room.EntityInsertionAdapter
-keep class * extends androidx.room.SharedSQLiteStatement
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.ottking.devcode.db.** { *; }
-keep class * extends com.ottking.devcode.db.** { *; }
-keepclassmembers class com.ottking.devcode.db.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.paging.**

# Media3 ExoPlayer Rules
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.extractor.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Okio Rules
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Glide Image Loading Rules
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# Lottie Animation Rules
-keep class com.airbnb.lottie.** { *; }
-keepclassmembers class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# NOTE: Config.java, ApiClient, SecurityUtils, Models, ViewModels, Preferences, and Utils
# are DELIBERATELY NOT KEPT so R8 obfuscates them (names, fields, and methods mangled)
# to ensure zero sensitive logic/class names are readable in the release classes.dex.

# Strip Log calls in release for security and smaller footprint
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
