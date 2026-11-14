# Proguard rules for FrostTime
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn com.topjohnwu.superuser.**
-keep class com.topjohnwu.superuser.** { *; }
