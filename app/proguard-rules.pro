-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}
-keepclassmembers class com.fuck.iab.NativeBridge {
    public static org.luckypray.dexkit.DexKitBridge dexKitBridge;
    public static void onPayloadReceived(java.lang.String);
}
-keep class org.luckypray.dexkit.** {
    public <methods>;
}
-repackageclasses 'fh'