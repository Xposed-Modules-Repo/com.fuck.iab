// name: Parallel Space Pro
// url: https://play.google.com/store/apps/details?id=com.parallel.space.pro
// author: you1mak
// description: Unlocks Pro features in Parallel Space without needing a paid license.
// added-in: 1.6.1

/*
 * Parallel Space Pro v4.0.9162 — Force Pro (Frida)
 * Converted from morphe patch `parallelSpaceForceProPatch`.
 *
 * ── PRO STATUS ARCHITECTURE ─────────────────────────────────────────────
 *   SharedPreferences key "bpcs" (SPConstant.BILLING_PURCHASE_CURRENT_STATE):
 *     2 = Pro | 1 = pending/expired | 0 = free
 *   No server-side license check — all client-side.
 *
 *   PATH A — BillingClient gate:  td.J()Z   (synchronized)
 *     Reads td.b:I (runtime state), compares to 2, checks two objects
 *     (zzar, dm1) for null. All three guards nop-ed → always returns true.
 *
 *   PATH B — Direct SP read:      de.l()Z
 *     Reads "bpcs" directly from SharedPreferences and caches into de.b:I.
 *     Bypassed by returnEarly(true).
 *
 *   Both paths must be patched — different entry points use each:
 *     Path A: billing internals, be.d()Z gateway
 *     Path B: SplashActivity, CloneAndIncognitoInstallActivity,
 *             HomeView (n40.smali × 2), other UI entry points
 *
 * ── FRIDA HOOK NOTE ─────────────────────────────────────────────────────
 *   The Kotlin patch nops 3 guards at [monitorIdx+4/6/8] inside td.J(),
 *   preserving monitor-enter/exit (needed because nop-ing branches that
 *   skip monitor-exit would corrupt the lock state and throw VerifyError).
 *
 *   In Frida, .implementation replaces the ENTIRE body — monitor-enter
 *   and monitor-exit are never executed, so no VerifyError, no lock leak.
 *   This is safe here because:
 *     • td.b is a 32-bit int (atomic on ARM/ART)
 *     • zzar/dm1 are read for null-check only, not for synchronization
 *     • No caller relies on J() holding `this` monitor for correctness
 *
 * Usage:
 *   frida -U -f com.lbe.parallel -l parallelspace_pro.js --no-pause
 *   frida -U -f com.lbe.parallel.intl -l parallelspace_pro.js --no-pause
 */

Java.perform(function () {
    const TAG = "[ParallelSpacePro]";
    console.log(TAG + " ========================================");
    console.log(TAG + " Force Pro — starting...");
    console.log(TAG + " ========================================");

    /* ---------------------------------------------------------- helpers */

    /**
     * Hooks all overloads of `methodName` to return `returnValue`.
     * Tries immediately, then retries every `delayMs` up to `maxAttempts`.
     */
    function hookWithRetry(className, methodName, returnValue,
                           maxAttempts, delayMs, label) {
        function attempt() {
            try {
                const cls = Java.use(className);
                const overloads = cls[methodName].overloads;
                if (!overloads || overloads.length === 0) return false;

                // Match only Z-returning, no-arg overloads if multiple exist
                let matched = 0;
                overloads.forEach(function (ov) {
                    if (ov.argumentTypes.length === 0 &&
                        ov.returnType.className === 'boolean') {
                        ov.implementation = function () { return returnValue; };
                        matched++;
                    }
                });
                // If no filtered match, hook all overloads to be safe
                if (matched === 0) {
                    overloads.forEach(function (ov) {
                        ov.implementation = function () { return returnValue; };
                        matched++;
                    });
                }
                if (matched > 0) {
                    console.log(TAG + " [✓] " + label + " → " + returnValue +
                                " (" + matched + " overload(s))");
                    return true;
                }
                return false;
            } catch (e) {
                return false;
            }
        }

        if (attempt()) return;

        let attempts = 1;
        const iv = setInterval(function () {
            attempts++;
            const ok = attempt();
            if (ok || attempts >= maxAttempts) {
                if (!ok) {
                    console.log(TAG + " [✗] " + label + " failed after " +
                                attempts + " attempts");
                }
                clearInterval(iv);
            }
        }, delayMs);
    }

    /* ------------------------------------ PATCH 1: td.J()Z — billing gate
     * The synchronized BillingClient isPro check. Returns true always.
     * Because we replace the whole method body via .implementation,
     * monitor-enter/exit are bypassed (safe — see header note).
     * ---------------------------------------------------------------- */
    hookWithRetry(
        'com.lbe.parallel.td',
        'J',
        true,
        30,   // attempts
        200,  // ms between attempts (~6s total)
        'td.J() [billing gate]'
    );

    /* ------------------------------------ PATCH 2: de.l()Z — direct SP read
     * Reads "bpcs" key directly. Called from SplashActivity / HomeView /
     * CloneAndIncognitoInstallActivity — bypasses the billing gateway.
     * Return true unconditionally (equivalent to returnEarly(true)).
     * ---------------------------------------------------------------- */
    hookWithRetry(
        'com.lbe.parallel.de',
        'l',
        true,
        30,
        200,
        'de.l() [direct SP read]'
    );

    console.log(TAG + " ========================================");
    console.log(TAG + " All hooks scheduled. Pro should be unlocked.");
    console.log(TAG + " ========================================");
});