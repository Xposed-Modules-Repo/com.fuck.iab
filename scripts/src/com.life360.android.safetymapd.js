// name: Life360
// url: https://play.google.com/store/apps/details?id=com.life360.android.safetymapd
// author: Fexture69
// description: Unlocks all premium features in Life360 including GPS tracking, location history, and circle-based features without a premium subscription.
// added-in: 1.6.1

Java.perform(function() {
    // --- CircleFeatures ---
    try {
        Java.use('com.life360.model_store.base.localstore.CircleFeatures')
            .isPremium.implementation = function() { return true; };
    } catch(e) {}

    // --- FeatureSetEntitlementManager ---
    try {
        var FSE = Java.use('com.life360.inapppurchase.FeatureSetEntitlementManager');

        ['isAvailable','isEnabledForAnyCircle','isEnabledForCircle',
         'isOnVictimOnlyCollisionDispatchAvailable','isOnVictimOnlyPremiumSosAvailable'
        ].forEach(function(name) {
            try {
                FSE[name].overloads.forEach(function(ov) {
                    ov.implementation = function() { return true; };
                });
            } catch(e) {}
        });

        ['resolveGpsTrackerForCircle','resolveLocationHistoryForCircle',
         'resolvePlaceAlertsForCircle'
        ].forEach(function(name) {
            try {
                FSE[name].overloads.forEach(function(ov) {
                    ov.implementation = function() { return 0x7fffffff; };
                });
            } catch(e) {}
        });
    } catch(e) {}

    console.log("[+] Life360 Premium Unlock complete.");
});
