# Proguard rules for RadioInfoApp - Security Hardening

# Keep app classes but allow obfuscation
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Obfuscate everything except what's needed
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Prevent reflection-based attacks
-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Protect Fragment classes (needed by AndroidX)
-keep class com.radioinfo.app.*Fragment { *; }

# Keep data binding
-keep class androidx.databinding.** { *; }
-keep class * extends androidx.databinding.ViewDataBinding { *; }

# AndroidX / Material
-keep class com.google.android.material.** { *; }
-keep class androidx.viewpager2.** { *; }
