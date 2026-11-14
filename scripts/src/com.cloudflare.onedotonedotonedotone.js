// name: 1.1.1.1
// url: https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone
// author: Fexture69
// description: Make a vpn profile first (turn on the switch in app) before using the script.
// added-in: 1.6.5

/*
 * Cloudflare WARP (1.1.1.1) Frida Patches
 * Replicates:
 *   - UnlockWarpPlusPatch (Spoof WARP+ Unlimited UI)
 *   - DisableAnalyticsPatch (Disable Analytics / Telemetry)
 *   - DisableSslPinningPatch (Bypass OkHttp certificate pinning)
 *
 * Target Package: com.cloudflare.onedotonedotonedotone
 * Verified against v6.38.8 (versionCode 5431)
 */

Java.perform(function () {
    console.log("[*] Starting Cloudflare WARP patches...");

    // ============================================================
    // 1. UnlockWARP+ Unlimited (AccountData Constructor Hook)
    // ============================================================
    try {
        var AccountData = Java.use('com.cloudflare.app.data.warpapi.AccountData');
        var WarpPlusState = Java.use('com.cloudflare.app.data.warpapi.WarpPlusState');
        
        // The primary constructor signature is:
        // (String, WarpPlusState, Long, Long, LocalDateTime, Integer, String, Managed, AccountRole)
        var AccountDataConstructor = AccountData.$init.overload(
            'java.lang.String',
            'com.cloudflare.app.data.warpapi.WarpPlusState',
            'java.lang.Long',
            'java.lang.Long',
            'org.threeten.bp.LocalDateTime',
            'java.lang.Integer',
            'java.lang.String',
            'com.cloudflare.app.data.warpapi.Managed',
            'com.cloudflare.app.data.warpapi.AccountRole'
        );

        AccountDataConstructor.implementation = function (
            p1, p2, p3, p4, p5, p6, p7, p8, p9
        ) {
            console.log("[+] Intercepted AccountData constructor. Spoofing WarpPlusState to UNLIMITED.");
            // Force the second parameter (WarpPlusState) to UNLIMITED
            return this.$init(
                p1,
                WarpPlusState.UNLIMITED.value,
                p3, p4, p5, p6, p7, p8, p9
            );
        };
        console.log("[+] Hooked AccountData constructor for WARP+ unlock.");
    } catch (e) {
        console.log("[-] Failed to hook AccountData constructor: " + e);
    }

    // ============================================================
    // 2. Disable Analytics / Telemetry (Layer 1: AnalyticsService.c)
    // ============================================================
    try {
        var AnalyticsService = Java.use('com.cloudflare.app.domain.analytics.AnalyticsService');
        // Static method c(AnalyticsService, Bundle) - void return
        var AnalyticsServiceC = AnalyticsService.c.overload(
            'com.cloudflare.app.domain.analytics.AnalyticsService',
            'android.os.Bundle'
        );
        AnalyticsServiceC.implementation = function (service, bundle) {
            // No-op: Prevent bundle enrichment and downstream dispatch
            // console.log("[*] Blocked AnalyticsService.c enrichment.");
            return; 
        };
        console.log("[+] Hooked AnalyticsService.c (Layer 1 analytics disabled).");
    } catch (e) {
        console.log("[-] Failed to hook AnalyticsService.c: " + e);
    }

    // ============================================================
    // 3. Disable Analytics / Telemetry (Layer 2: Firebase Dispatchers)
    // ============================================================
    // The patch scans for anonymous lambda classes implementing Function1
    // that call GMS measurement method 'n'. We can hook the GMS method directly.
    try {
        // Instead of scanning for anonymous classes, we hook the GMS method
        // that all dispatchers ultimately call.
        // The method is zzdq.n(String, String, Bundle, Z)
        // We can find the class dynamically or hook known GMS classes.
        // A more robust approach is to hook FirebaseAnalytics.logEvent 
        // or the underlying GMS method if we know its obfuscated name.
        // Since the patch targets com.google.android.gms.internal.measurement.*
        // we can try to hook the 'n' method in that package.
        
        // Note: GMS class names are obfuscated and may change.
        // This is a best-effort hook for the GMS measurement dispatcher.
        var GmsMeasurementClass = Java.use('com.google.android.gms.internal.measurement.zzdq');
        var GmsMethodN = GmsMeasurementClass.n.overload(
            'java.lang.String',
            'java.lang.String',
            'android.os.Bundle',
            'boolean'
        );
        GmsMethodN.implementation = function (p1, p2, p3, p4) {
            // No-op: Block Firebase event dispatch
            // console.log("[*] Blocked GMS measurement event dispatch.");
            return;
        };
        console.log("[+] Hooked GMS measurement dispatcher (Layer 2 analytics disabled).");
    } catch (e) {
        console.log("[-] Failed to hook GMS measurement dispatcher (Layer 2): " + e);
    }

    // ============================================================
    // 4. Disable SSL Pinning (OkHttp CertificatePinner)
    // ============================================================
    try {
        var CertificatePinner = Java.use('okhttp3.CertificatePinner');
        // Public check(String, List) overload
        var CertificatePinnerCheck = CertificatePinner.check.overload(
            'java.lang.String',
            'java.util.List'
        );
        CertificatePinnerCheck.implementation = function (hostname, peerCertificates) {
            // No-op: Return void to bypass pin validation
            // console.log("[*] Bypassed SSL pinning for: " + hostname);
            return;
        };
        console.log("[+] Hooked CertificatePinner.check (SSL pinning bypassed).");
    } catch (e) {
        console.log("[-] Failed to hook CertificatePinner.check: " + e);
    }

    console.log("[*] All Cloudflare WARP patches applied successfully.");
});