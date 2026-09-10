# Proguard rules for RadioInfoApp - Security Hardening

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Do not retain diagnostic logs, which may contain device or permission details.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# FragmentStateAdapter restores fragments by class name after process death.
-keep public class com.radioinfo.app.*Fragment {
    public <init>();
}
