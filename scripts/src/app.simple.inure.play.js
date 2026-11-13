// author: you1mak
// description: Unlocks the full version of Inure App Manager by patching license checks to always return true, removing the need for an unlocker app or license key.

/*
 * Inure App Manager — Unlock Full Version
 * Frida Script  (converted from morphe patches:
 *   InureUnlockPremiumPatch  [Play variant]
 *   InureGithubUnlockPremiumPatch  [GitHub variant])
 *
 * TrialPreferences flow (SplashScreen.unlockStateChecker):
 *   if isTrialWithoutFull():
 *       if isFullVersion(): gone(daysLeft); return
 *       else:               setText(daysLeft, "X days remaining")
 *   if isFullVersion():
 *       if hasLicenceKey() && !isUnlockerVerificationRequired():
 *           gone(daysLeft); return            ← licence-key safe exit
 *       elif isPackageInstalled(unlocker):
 *           gone(daysLeft); return
 *       else:
 *           showWarning("full_version_deactivated") + setFullVersion(false)
 *   else: setText(daysLeft, "X days remaining")
 *
 * With:
 *   isTrialWithoutFull        → false : skip first branch entirely
 *   isFullVersion             → true  : enter second branch
 *   hasLicenceKey             → true  : licence-key condition met
 *   isUnlockerVerificationReq → false : finish the licence-key condition
 *   → hits gone(daysLeft); return cleanly, no unlocker package required.
 *
 * Play variant additionally patches isPlayFlavor → false to bypass mandatory
 * unlocker re-verification on every launch.
 *
 * Usage:
 *   frida -U -f app.simple.inure -l inure_unlock.js --no-pause
 *   frida -U -f app.simple.inure.play -l inure_unlock.js --no-pause
 */

Java.perform(function () {
    const TAG = "[InureUnlock]";

    console.log(TAG + " ========================================");
    console.log(TAG + " Unlock Full Version — starting...");
    console.log(TAG + " ========================================");

    /* ------------------------------------------------------------------
     * Helper — single attempt
     * Hooks ALL overloads of `methodName` and returns `returnValue`.
     * Methods are no-arg Z, so overloads should be exactly one — but we
     * guard with overloads[] anyway for robustness across obfuscation.
     * ------------------------------------------------------------------ */
    function hookMethodOnce(className, methodName, returnValue) {
        try {
            const cls = Java.use(className);
            const overloads = cls[methodName].overloads;
            overloads.forEach(function (overload) {
                overload.implementation = function () {
                    return returnValue;
                };
            });
            console.log(TAG + " [✓] " + className + "." + methodName +
                        "() → " + returnValue);
            return true;
        } catch (e) {
            return false;
        }
    }

    /* ------------------------------------------------------------------
     * Helper — retry loop
     * TrialPreferences is loaded early, but on some devices / cold starts
     * the class isn't yet visible to Frida when Java.perform fires.
     * Retry every 100ms up to maxAttempts.
     * ------------------------------------------------------------------ */
    function hookMethodWithRetry(className, methodName, returnValue,
                                 maxAttempts, delayMs) {
        if (hookMethodOnce(className, methodName, returnValue)) return;

        let attempts = 1;
        const interval = setInterval(function () {
            attempts++;
            const ok = hookMethodOnce(className, methodName, returnValue);
            if (ok || attempts >= maxAttempts) {
                if (!ok) {
                    console.log(TAG + " [✗] " + className + "." + methodName +
                                "() failed after " + attempts + " attempts");
                }
                clearInterval(interval);
            }
        }, delayMs);
    }

    const TP = "app.simple.inure.preferences.TrialPreferences";
    const AU = "app.simple.inure.util.AppUtils";

    /* ------------------------------------------------------------------
     * [1] TrialPreferences — the 6 core patches (Play + GitHub share)
     * ------------------------------------------------------------------ */

    // 1. Primary gate for every premium screen
    hookMethodWithRetry(TP, "isAppFullVersionEnabled", true, 20, 100);

    // 2. Trial screen listener + unlockStateChecker entry condition
    hookMethodWithRetry(TP, "isFullVersion", true, 20, 100);

    // 3. Suppress trial upsell UI elsewhere
    hookMethodWithRetry(TP, "isWithinTrialPeriod", true, 20, 100);

    // 4. Skip first unlockStateChecker branch — days-left text never shown
    hookMethodWithRetry(TP, "isTrialWithoutFull", false, 20, 100);

    // 5+6. Force licence-key safe exit inside unlockStateChecker
    hookMethodWithRetry(TP, "hasLicenceKey", true, 20, 100);
    hookMethodWithRetry(TP, "isUnlockerVerificationRequired", false, 20, 100);

    /* ------------------------------------------------------------------
     * [7] AppUtils.isPlayFlavor (Play variant ONLY)
     *
     * Hardcoded true in Play build; forces mandatory unlocker
     * re-verification. Patch to false aligns Play trust path with GitHub
     * (no forced re-verify on every launch).
     *
     * In GitHub build the method may not exist → single attempt,
     * log quietly and continue (matches Kotlin's methodOrNull?.).
     * ------------------------------------------------------------------ */
    if (!hookMethodOnce(AU, "isPlayFlavor", false)) {
        console.log(TAG + " [i] " + AU + ".isPlayFlavor() not present " +
                    "(GitHub variant) — skipping");
    }

    /* ------------------------------------------------------------------
     * Done
     * ------------------------------------------------------------------ */
    console.log(TAG + " ========================================");
    console.log(TAG + " All hooks scheduled. Full version unlocked.");
    console.log(TAG + " ========================================");
});