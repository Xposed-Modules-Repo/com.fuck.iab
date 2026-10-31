// author: Fexture69
/*
 * Life360 Platinum Unlock – Frida Script (Final, Clean, Crash-Safe)
 * Target package: com.life360.android.safetymapd
 */

Java.perform(function () {
    console.log("[*] Starting Life360 Platinum unlock script...");

    function logHook(cls, m) { console.log(`[*] Hooking ${cls}.${m}`); }

    // Reading an enum constant before its class is initialised gives null, and the app
    // maps that null straight into a crash. values() runs <clinit>, so use it instead of
    // touching the static field.
    function enumValue(className, constantName) {
        var constants = Java.use(className).values();

        for (var i = 0; i < constants.length; i++) {
            if (constants[i].name().toString() === constantName) return constants[i];
        }

        return null;
    }

    // The same applies to Kotlin object singletons, which only exist after <clinit>.
    function objectValue(className, field) {
        var cls = Java.use(className);
        var value = cls[field].value;

        if (value === null || value === undefined) {
            try {
                Java.use('java.lang.Class').forName(className, true, cls.class.getClassLoader());
            } catch (e) { }
            value = cls[field].value;
        }

        return (value === undefined) ? null : value;
    }

    function linkedHashMapOf(key, value) {
        var map = Java.use('java.util.LinkedHashMap').$new();
        map.put(key, value);
        return map;
    }

    // ============================================================
    // 1. CircleFeatures.isPremium
    // ============================================================
    try {
        var CircleFeatures = Java.use('com.life360.model_store.base.localstore.CircleFeatures');
        CircleFeatures.isPremium.implementation = function () { return true; };
        console.log("[+] Hooked CircleFeatures.isPremium");
    } catch (e) { console.log(`[-] CircleFeatures.isPremium: ${e}`); }

    // ============================================================
    // 2. FeatureSetEntitlementManager – booleans
    // ============================================================
    var FSE = null;
    try {
        FSE = Java.use('com.life360.inapppurchase.FeatureSetEntitlementManager');
        console.log("[+] Found FeatureSetEntitlementManager");
    } catch (e) { console.log(`[-] FeatureSetEntitlementManager: ${e}`); }

    if (FSE) {
        try {
            FSE.isAvailable.overload(
                'com.life360.android.core.models.FeatureKey',
                'com.life360.inapppurchase.Premium'
            ).implementation = function () { logHook('FSE', 'isAvailable'); return true; };
        } catch (e) { console.log(`[-] isAvailable: ${e}`); }

        try {
            FSE.isEnabledForAnyCircle.overload(
                'com.life360.android.core.models.FeatureKey',
                'com.life360.inapppurchase.Premium'
            ).implementation = function () { logHook('FSE', 'isEnabledForAnyCircle'); return true; };
        } catch (e) { console.log(`[-] isEnabledForAnyCircle: ${e}`); }

        try {
            FSE.isEnabledForCircle.overload(
                'com.life360.android.core.models.FeatureKey',
                'java.lang.String',
                'com.life360.inapppurchase.Premium'
            ).implementation = function () { logHook('FSE', 'isEnabledForCircle'); return true; };
        } catch (e) { console.log(`[-] isEnabledForCircle: ${e}`); }

        try {
            FSE.isOnVictimOnlyCollisionDispatchAvailable.overload(
                'com.life360.inapppurchase.Premium'
            ).implementation = function () { return true; };
        } catch (e) { console.log(`[-] isOnVictimOnlyCollisionDispatchAvailable: ${e}`); }

        try {
            FSE.isOnVictimOnlyPremiumSosAvailable.overload(
                'com.life360.inapppurchase.Premium'
            ).implementation = function () { return true; };
        } catch (e) { console.log(`[-] isOnVictimOnlyPremiumSosAvailable: ${e}`); }

        // Integer returns
        try {
            FSE.resolveGpsTrackerForCircle.overload(
                'java.lang.String', 'com.life360.inapppurchase.Premium'
            ).implementation = function () { return 0x63; };
        } catch (e) { console.log(`[-] resolveGpsTrackerForCircle: ${e}`); }

        try {
            FSE.resolveLocationHistoryForCircle.overload(
                'java.lang.String', 'com.life360.inapppurchase.Premium'
            ).implementation = function () { return 0x7fffffff; };
        } catch (e) { console.log(`[-] resolveLocationHistoryForCircle: ${e}`); }

        try {
            FSE.resolvePlaceAlertsForCircle.overload(
                'java.lang.String', 'com.life360.inapppurchase.Premium'
            ).implementation = function () { return 0x7fffffff; };
        } catch (e) { console.log(`[-] resolvePlaceAlertsForCircle: ${e}`); }
    }

    // ============================================================
    // 3. Sku.PLATINUM
    // ============================================================
    var SkuPLATINUM = null;
    try {
        SkuPLATINUM = enumValue('com.life360.android.core.models.Sku', 'PLATINUM');
        console.log("[+] Got Sku.PLATINUM");
    } catch (e) { console.log(`[-] Sku.PLATINUM: ${e}`); }

    // ============================================================
    // 4. Map returns (with fallback)
    // ============================================================
    if (FSE && SkuPLATINUM) {
        try {
            FSE.resolveLocationHistoryPerSkus.overload(
                'com.life360.inapppurchase.Premium'
            ).implementation = function (prem) {
                try {
                    return linkedHashMapOf(SkuPLATINUM, Java.use('java.lang.Integer').$new(0x7fffffff));
                } catch (e) { return this.resolveLocationHistoryPerSkus(prem); }
            };
        } catch (e) { console.log(`[-] resolveLocationHistoryPerSkus: ${e}`); }

        try {
            FSE.isAvailableForPerSkus.overload(
                'com.life360.android.core.models.FeatureKey',
                'com.life360.inapppurchase.Premium'
            ).implementation = function (fk, prem) {
                try {
                    return linkedHashMapOf(SkuPLATINUM, Java.use('java.lang.Boolean').$new(true));
                } catch (e) { return this.isAvailableForPerSkus(fk, prem); }
            };
        } catch (e) { console.log(`[-] isAvailableForPerSkus: ${e}`); }

        try {
            FSE.resolvePlaceAlertsPerSkus.overload(
                'com.life360.inapppurchase.Premium'
            ).implementation = function (prem) {
                try {
                    var LA = Java.use('com.life360.android.core.models.AvailablePlaceAlerts$LimitedAlerts');
                    return linkedHashMapOf(SkuPLATINUM, LA.$new(0x7fffffff));
                } catch (e) { return this.resolvePlaceAlertsPerSkus(prem); }
            };
        } catch (e) { console.log(`[-] resolvePlaceAlertsPerSkus: ${e}`); }

        try {
            FSE.skuTileClassicFulfillments.overload(
                'com.life360.inapppurchase.Premium'
            ).implementation = function (prem) {
                try {
                    var sp = objectValue('com.life360.android.core.models.PremiumFeature$TileDevicePackage$StarterPack', 'INSTANCE');
                    if (sp == null) return this.skuTileClassicFulfillments(prem);
                    return linkedHashMapOf(SkuPLATINUM, sp);
                } catch (e) { return this.skuTileClassicFulfillments(prem); }
            };
        } catch (e) { console.log(`[-] skuTileClassicFulfillments: ${e}`); }

        try {
            FSE.resolveIdTheftReimbursementPerSkus.overload(
                'com.life360.inapppurchase.Premium'
            ).implementation = function (prem) {
                try {
                    var RV = Java.use('com.life360.android.core.models.ReimbursementValue');
                    var usd = enumValue('com.life360.android.core.models.PremiumFeature$MembershipCurrency', 'USD');
                    if (usd == null) return this.resolveIdTheftReimbursementPerSkus(prem);
                    return linkedHashMapOf(SkuPLATINUM, RV.$new(50000, usd));
                } catch (e) { return this.resolveIdTheftReimbursementPerSkus(prem); }
            };
        } catch (e) { console.log(`[-] resolveIdTheftReimbursementPerSkus: ${e}`); }

        try {
            FSE.resolveStolenPhoneReimbursementPerSkus.overload(
                'com.life360.inapppurchase.Premium'
            ).implementation = function (prem) {
                try {
                    var RV = Java.use('com.life360.android.core.models.ReimbursementValue');
                    var usd = enumValue('com.life360.android.core.models.PremiumFeature$MembershipCurrency', 'USD');
                    if (usd == null) return this.resolveStolenPhoneReimbursementPerSkus(prem);
                    return linkedHashMapOf(SkuPLATINUM, RV.$new(50000, usd));
                } catch (e) { return this.resolveStolenPhoneReimbursementPerSkus(prem); }
            };
        } catch (e) { console.log(`[-] resolveStolenPhoneReimbursementPerSkus: ${e}`); }

        try {
            FSE.resolveRoadsideAssistancePerSkus.overload(
                'com.life360.inapppurchase.Premium'
            ).implementation = function (prem) {
                try {
                    var ud = objectValue('com.life360.android.core.models.RoadsideAssistanceValue$UnlimitedDistance', 'INSTANCE');
                    if (ud == null) return this.resolveRoadsideAssistancePerSkus(prem);
                    return linkedHashMapOf(SkuPLATINUM, ud);
                } catch (e) { return this.resolveRoadsideAssistancePerSkus(prem); }
            };
        } catch (e) { console.log(`[-] resolveRoadsideAssistancePerSkus: ${e}`); }
    }

    // ============================================================
    // 5. FeatureSetEntitlementManager – object returns
    // ============================================================
    if (FSE) {
        try {
            FSE.resolveIdTheftReimbursementForCircle.overload(
                'java.lang.String', 'com.life360.inapppurchase.Premium'
            ).implementation = function (cid, prem) {
                try {
                    var RV = Java.use('com.life360.android.core.models.ReimbursementValue');
                    var usd = enumValue('com.life360.android.core.models.PremiumFeature$MembershipCurrency', 'USD');
                    if (usd == null) return this.resolveIdTheftReimbursementForCircle(cid, prem);
                    return RV.$new(50000, usd);
                } catch (e) { return this.resolveIdTheftReimbursementForCircle(cid, prem); }
            };
        } catch (e) { console.log(`[-] resolveIdTheftReimbursementForCircle: ${e}`); }

        try {
            FSE.resolveStolenPhoneReimbursementForCircle.overload(
                'java.lang.String', 'com.life360.inapppurchase.Premium'
            ).implementation = function (cid, prem) {
                try {
                    var RV = Java.use('com.life360.android.core.models.ReimbursementValue');
                    var usd = enumValue('com.life360.android.core.models.PremiumFeature$MembershipCurrency', 'USD');
                    if (usd == null) return this.resolveStolenPhoneReimbursementForCircle(cid, prem);
                    return RV.$new(50000, usd);
                } catch (e) { return this.resolveStolenPhoneReimbursementForCircle(cid, prem); }
            };
        } catch (e) { console.log(`[-] resolveStolenPhoneReimbursementForCircle: ${e}`); }

        try {
            FSE.resolveRoadsideAssistanceForCircle.overload(
                'java.lang.String', 'com.life360.inapppurchase.Premium'
            ).implementation = function (cid, prem) {
                try {
                    var ud = objectValue('com.life360.android.core.models.RoadsideAssistanceValue$UnlimitedDistance', 'INSTANCE');
                    if (ud == null) return this.resolveRoadsideAssistanceForCircle(cid, prem);
                    return ud;
                } catch (e) { return this.resolveRoadsideAssistanceForCircle(cid, prem); }
            };
        } catch (e) { console.log(`[-] resolveRoadsideAssistanceForCircle: ${e}`); }

        try {
            FSE.resolveSosEmergencyDispatchForCircle.overload(
                'java.lang.String', 'com.life360.inapppurchase.Premium'
            ).implementation = function (cid, prem) {
                try {
                    var ED = Java.use('com.life360.android.core.models.PremiumFeature$SOS$EmergencyDispatch');
                    return ED.$new(false);
                } catch (e) { return this.resolveSosEmergencyDispatchForCircle(cid, prem); }
            };
        } catch (e) { console.log(`[-] resolveSosEmergencyDispatchForCircle: ${e}`); }

        try {
            FSE.resolveTileDevicePackageForCircle.overload(
                'java.lang.String', 'com.life360.inapppurchase.Premium'
            ).implementation = function (cid, prem) {
                try {
                    var sp = objectValue('com.life360.android.core.models.PremiumFeature$TileDevicePackage$StarterPack', 'INSTANCE');
                    if (sp == null) return this.resolveTileDevicePackageForCircle(cid, prem);
                    return sp;
                } catch (e) { return this.resolveTileDevicePackageForCircle(cid, prem); }
            };
        } catch (e) { console.log(`[-] resolveTileDevicePackageForCircle: ${e}`); }
    }

    // ============================================================
    // 6. DefaultMembershipUtil / PremiumCircleUtilImpl lambdas
    // ============================================================
    var DMU = null;
    try { DMU = Java.use('com.life360.inapppurchase.DefaultMembershipUtil'); console.log("[+] Found DefaultMembershipUtil"); }
    catch (e) { console.log(`[-] DefaultMembershipUtil: ${e}`); }

    var PCU = null;
    try { PCU = Java.use('com.life360.inapppurchase.PremiumCircleUtilImpl'); console.log("[+] Found PremiumCircleUtilImpl"); }
    catch (e) { console.log(`[-] PremiumCircleUtilImpl: ${e}`); }

    // getActiveMappedSkuOrFree$lambda$0 returns Sku (object), NOT .value
    function hookGetActiveMapped(utilClass, name) {
        if (!utilClass) return;
        try {
            utilClass['getActiveMappedSkuOrFree$lambda$0'].overload('java.util.Optional').implementation = function (opt) {
                try {
                    var v = enumValue('com.life360.android.core.models.Sku', 'PLATINUM');
                    if (v == null) return this['getActiveMappedSkuOrFree$lambda$0'](opt);
                    return v;
                } catch (e) {
                    return this['getActiveMappedSkuOrFree$lambda$0'](opt);
                }
            };
        } catch (e) { console.log(`[-] ${name}.getActiveMappedSkuOrFree$lambda$0: ${e}`); }
    }
    hookGetActiveMapped(DMU, 'DefaultMembershipUtil');
    hookGetActiveMapped(PCU, 'PremiumCircleUtilImpl');

    if (DMU) {
        try {
            DMU['isActiveCircleFree$lambda$0'].overload('java.util.Optional').implementation = function (opt) {
                try { return Java.use('java.lang.Boolean').$new(false); }
                catch (e) { return this['isActiveCircleFree$lambda$0'](opt); }
            };
        } catch (e) { console.log(`[-] isActiveCircleFree$lambda$0: ${e}`); }

        try {
            DMU['resolveLocationHistoryForCircle$lambda$2'].overload(
                'com.life360.inapppurchase.DefaultMembershipUtil',
                'java.lang.String',
                'com.life360.inapppurchase.Premium'
            ).implementation = function () {
                return Java.use('java.lang.Integer').$new(0x7fffffff);
            };
        } catch (e) { console.log(`[-] DMU.resolveLocationHistoryForCircle$lambda$2: ${e}`); }

        try {
            DMU['resolvePlaceAlertsForCircle$lambda$3'].overload(
                'com.life360.inapppurchase.DefaultMembershipUtil',
                'java.lang.String',
                'com.life360.inapppurchase.Premium'
            ).implementation = function () {
                return Java.use('java.lang.Integer').$new(0x7fffffff);
            };
        } catch (e) { console.log(`[-] DMU.resolvePlaceAlertsForCircle$lambda$3: ${e}`); }

        if (SkuPLATINUM) {
            try {
                DMU['resolveLocationHistoryPerSkus$lambda$2'].overload(
                    'com.life360.inapppurchase.DefaultMembershipUtil',
                    'com.life360.inapppurchase.Premium'
                ).implementation = function () {
                    return linkedHashMapOf(SkuPLATINUM, Java.use('java.lang.Integer').$new(0x7fffffff));
                };
            } catch (e) { console.log(`[-] DMU.resolveLocationHistoryPerSkus$lambda$2: ${e}`); }

            try {
                DMU['resolvePlaceAlertsPerSkus$lambda$2'].overload(
                    'com.life360.inapppurchase.DefaultMembershipUtil',
                    'com.life360.inapppurchase.Premium'
                ).implementation = function () {
                    var LA = Java.use('com.life360.android.core.models.AvailablePlaceAlerts$LimitedAlerts');
                    return linkedHashMapOf(SkuPLATINUM, LA.$new(0x7fffffff));
                };
            } catch (e) { console.log(`[-] DMU.resolvePlaceAlertsPerSkus$lambda$2: ${e}`); }
        }
    }

    if (PCU) {
        try {
            PCU['resolvePlaceAlertsForCircle$lambda$2'].overload(
                'com.life360.inapppurchase.PremiumCircleUtilImpl',
                'java.lang.String',
                'com.life360.inapppurchase.Premium'
            ).implementation = function () {
                return Java.use('java.lang.Integer').$new(0x7fffffff);
            };
        } catch (e) { console.log(`[-] PCU.resolvePlaceAlertsForCircle$lambda$2: ${e}`); }
    }

    // ============================================================
    // 7. PurchasedSkuInfo getters
    // ============================================================
    try {
        var PSI = Java.use('com.life360.inapppurchase.PurchasedSkuInfo');
        PSI.getSku.implementation = function () { return "9"; };
        PSI.getProductId.implementation = function () { return "premium_4999_yearly_1"; };
        PSI.getPeriod.implementation = function () {
            try {
                var v = enumValue('com.life360.model_store.base.localstore.room.premium.PurchasePeriod', 'ANNUAL');
                if (v == null) return this.getPeriod();
                return v;
            } catch (e) {
                return this.getPeriod();
            }
        };
        PSI.getPaymentState.implementation = function () {
            try {
                var v = enumValue('com.life360.inapppurchase.PaymentState', 'PAID');
                if (v == null) return this.getPaymentState();
                return v;
            } catch (e) {
                return this.getPaymentState();
            }
        };
        console.log("[+] Hooked PurchasedSkuInfo getters");
    } catch (e) { console.log(`[-] PurchasedSkuInfo getters: ${e}`); }

    // ============================================================
    // 8. Premium class (skuInfoForCircle intentionally skipped)
    // ============================================================
    var Premium = null;
    try { Premium = Java.use('com.life360.inapppurchase.Premium'); console.log("[+] Found Premium"); }
    catch (e) { console.log(`[-] Premium: ${e}`); }

    if (Premium) {
        try {
            Premium.skuForCircle.overload('java.lang.String').implementation = function () { return "9"; };
        } catch (e) { console.log(`[-] Premium.skuForCircle: ${e}`); }

        try {
            Premium.paymentStateForCircle.overload('java.lang.String').implementation = function (cid) {
                try {
                    var v = enumValue('com.life360.inapppurchase.PaymentState', 'PAID');
                    if (v == null) return this.paymentStateForCircle(cid);
                    return v;
                } catch (e) {
                    return this.paymentStateForCircle(cid);
                }
            };
        } catch (e) { console.log(`[-] Premium.paymentStateForCircle: ${e}`); }

        try {
            Premium.membershipTierExperience.implementation = function () {
                try {
                    var v = enumValue('com.life360.inapppurchase.MembershipTierExperience', 'TRIPLE_TIER');
                    if (v == null) return this.membershipTierExperience();
                    return v;
                } catch (e) {
                    return this.membershipTierExperience();
                }
            };
        } catch (e) { console.log(`[-] membershipTierExperience: ${e}`); }

        try {
            Premium.trialForSku.overload('java.lang.String').implementation = function () {
                return Java.use('java.lang.Integer').$new(7);
            };
        } catch (e) { console.log(`[-] trialForSku: ${e}`); }

        console.log("[*] Premium.skuInfoForCircle hook intentionally skipped (constructor signature varies).");
    }

    console.log("[*] Script finished loading. App should NOT crash.");
});