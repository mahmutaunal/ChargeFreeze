# ==============================================================================
# ChargeFreeze - R8 / ProGuard rules
# Package: com.alpwarestudio.chargefreeze
#
# Goal:
# - Maximum safe shrinking/optimization for release builds
# - Preserve only Android entry points and required metadata
# - Avoid broad -keep rules that would prevent R8 from shrinking the app
# ==============================================================================


# ------------------------------------------------------------------------------
# ATTRIBUTES
# ------------------------------------------------------------------------------

# Keep annotation metadata required by AndroidX/Kotlin libraries when referenced.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Keep generic signatures only when libraries rely on them.
-keepattributes Signature

# Keep enclosing/inner class metadata.
-keepattributes InnerClasses,EnclosingMethod


# ------------------------------------------------------------------------------
# ANDROID COMPONENTS
# ------------------------------------------------------------------------------

# Android components referenced from AndroidManifest.xml are normally detected
# automatically by R8. These explicit rules are intentionally narrow and act as
# a safeguard against future project changes.

-keep class com.alpwarestudio.chargefreeze.MainActivity {
    public <init>();
}

-keep class com.alpwarestudio.chargefreeze.service.FreezeService {
    public <init>();
}


# ------------------------------------------------------------------------------
# KOTLIN COROUTINES
# ------------------------------------------------------------------------------

# kotlinx.coroutines ships its own consumer rules.
# Do NOT keep the whole kotlinx.coroutines package.
#
# The following warning suppression is safe for optional coroutine integrations
# that may not exist in this application.

-dontwarn kotlinx.coroutines.internal.MainDispatcherFactory
-dontwarn kotlinx.coroutines.CoroutineExceptionHandler


# ------------------------------------------------------------------------------
# ANDROIDX / JETPACK COMPOSE
# ------------------------------------------------------------------------------

# AndroidX and Compose dependencies provide their own consumer ProGuard rules.
# Keeping entire androidx.* or androidx.compose.* namespaces would dramatically
# reduce the effectiveness of R8, so no broad keep rules are defined here.


# ------------------------------------------------------------------------------
# ENUM / REFLECTION SAFETY
# ------------------------------------------------------------------------------

# ChargeFreeze currently does not use reflection-based serialization,
# Gson, Moshi reflection, Parcelable reflection, or Java serialization.
#
# Therefore there are intentionally no broad model keep rules.
#
# If reflection-based serialization is introduced later, add rules only for the
# exact classes that require them.


# ------------------------------------------------------------------------------
# SAMSUNG BACKEND
# ------------------------------------------------------------------------------

# SamsungChargeController accesses Settings.Global using string keys.
# It does NOT access ChargeFreeze classes through reflection, so the controller
# and model classes are intentionally allowed to be renamed and optimized.
#
# Settings keys such as:
#
#   protect_battery
#   battery_protection_threshold
#   battery_protection_recharge_level
#
# are string constants and are not affected by R8 obfuscation.


# ------------------------------------------------------------------------------
# LOGGING
# ------------------------------------------------------------------------------

# Remove calls to Android's standard Log methods from optimized release builds.
#
# ChargeFreeze currently contains no important behavior inside Log arguments.
# Do not move side-effecting operations into Log calls in the future.

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}


# ------------------------------------------------------------------------------
# SOURCE FILE INFORMATION
# ------------------------------------------------------------------------------

# Replace source file names in release stack traces instead of exposing source
# file names. Line numbers remain available for local mapping-based debugging.

-renamesourcefileattribute SourceFile


# ------------------------------------------------------------------------------
# OPTIMIZATION NOTES
# ------------------------------------------------------------------------------

# Do NOT add:
#
#   -keep class com.alpwarestudio.chargefreeze.** { *; }
#   -keep class androidx.** { *; }
#   -keep class kotlin.** { *; }
#   -dontoptimize
#   -dontobfuscate
#   -dontshrink
#
# Those rules would significantly increase APK size and defeat the purpose of R8.
#
# The Android Gradle Plugin's optimized default configuration is supplied through:
#
#   getDefaultProguardFile("proguard-android-optimize.txt")
#
# in app/build.gradle.kts.