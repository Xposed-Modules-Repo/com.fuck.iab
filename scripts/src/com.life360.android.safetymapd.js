// Life360 Premium Unlock - FKIAB Compatible (lite)
console.log("[*] Life360 Premium Unlock loading...");

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
