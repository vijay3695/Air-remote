# Magic Remote - ProGuard Rules
# Add project-specific ProGuard rules here.

# ============================================================
# KEEP RULES FOR MAGIC REMOTE CLASSES
# ============================================================

# Keep application class
-keep class com.vijay.aimagicremote.AppInitializer {
    *;
}

# Keep main activity and UI classes
-keep class com.vijay.aimagicremote.ui.** {
    *;
}

# Keep protocol classes
-keep class com.vijay.aimagicremote.protocol.** {
    *;
}

# Keep Bluetooth classes
-keep class com.vijay.aimagicremote.bluetooth.** {
    *;
}

# Keep discovery classes
-keep class com.vijay.aimagicremote.discovery.** {
    *;
}

# Keep companion classes
-keep class com.vijay.aimagicremote.companion.** {
    *;
}

# ============================================================
# OKHTTP & NETWORK
# ============================================================

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# ============================================================
# KOTLIN COROUTINES
# ============================================================

-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ============================================================
# COIL IMAGE LOADING
# ============================================================

-keep class coil.** { *; }
-dontwarn coil.**

# ============================================================
# ANDROIDX / JETPACK COMPOSE
# ============================================================

-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.appcompat.** { *; }

# ============================================================
# MEDIA3 EXOPLAYER
# ============================================================

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================================
# PERMISSION HELPER
# ============================================================

-keep class com.guolife.permissionhelper.** { *; }
-dontwarn com.guolife.permissionhelper.**

# ============================================================
# GENERIC RULES
# ============================================================

# Keep annotations
-keepattributes *Annotation*

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
}

# Don't warn about references to classes that might not be available
-dontwarn java.nio.file.**
-dontwarn java.lang.module.**
-dontwarn sun.misc.Unsafe

# Keep native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}