package com.fuck.iab

import org.junit.Assert.assertEquals
import org.junit.Test

/** One test per obfuscated string function in str.kt: the function must return the exact original string. */
class StrFunctionsTest {
    @Test
    fun fn_type() {
        assertEquals("type", type())
    }
    @Test
    fun fn_orderId() {
        assertEquals("orderId", orderId())
    }
    @Test
    fun fn_packageName() {
        assertEquals("packageName", packageName())
    }
    @Test
    fun fn_productId() {
        assertEquals("productId", productId())
    }
    @Test
    fun fn_purchaseTime() {
        assertEquals("purchaseTime", purchaseTime())
    }
    @Test
    fun fn_purchaseState() {
        assertEquals("purchaseState", purchaseState())
    }
    @Test
    fun fn_developerPayload() {
        assertEquals("developerPayload", developerPayload())
    }
    @Test
    fun fn_purchaseToken() {
        assertEquals("purchaseToken", purchaseToken())
    }
    @Test
    fun fn_RESPONSE_CODE() {
        assertEquals("RESPONSE_CODE", RESPONSE_CODE())
    }
    @Test
    fun fn_INAPP_PURCHASE_DATA() {
        assertEquals("INAPP_PURCHASE_DATA", INAPP_PURCHASE_DATA())
    }
    @Test
    fun fn_INAPP_DATA_SIGNATURE() {
        assertEquals("INAPP_DATA_SIGNATURE", INAPP_DATA_SIGNATURE())
    }
    @Test
    fun fn_dexkit() {
        assertEquals("dexkit", dexkit())
    }
    @Test
    fun fn_android_app_Application() {
        assertEquals("android.app.Application", android_app_Application())
    }
    @Test
    fun fn_onCreate() {
        assertEquals("onCreate", onCreate())
    }
    @Test
    fun fn_android_content_ServiceConnection() {
        assertEquals("android.content.ServiceConnection", android_content_ServiceConnection())
    }
    @Test
    fun fn_onServiceConnected() {
        assertEquals("onServiceConnected", onServiceConnected())
    }
    @Test
    fun fn_com_farsitel_bazaar() {
        assertEquals("com.farsitel.bazaar", com_farsitel_bazaar())
    }
    @Test
    fun fn_com_android_vending_billing_IInAppBillingService() {
        assertEquals("com.android.vending.billing.IInAppBillingService", com_android_vending_billing_IInAppBillingService())
    }
    @Test
    fun fn_com_fuck_iab() {
        assertEquals("com.fuck.iab", com_fuck_iab())
    }
    @Test
    fun fn_com_fuck_iab_FakeBillingActivity() {
        assertEquals("com.fuck.iab.FakeBillingActivity", com_fuck_iab_FakeBillingActivity())
    }
    @Test
    fun fn_BUY_INTENT() {
        assertEquals("BUY_INTENT", BUY_INTENT())
    }
    @Test
    fun fn_INTENT_V2_SUPPORT() {
        assertEquals("INTENT_V2_SUPPORT", INTENT_V2_SUPPORT())
    }
    @Test
    fun fn_INTENT_V3_SUPPORT() {
        assertEquals("INTENT_V3_SUPPORT", INTENT_V3_SUPPORT())
    }
    @Test
    fun fn_boolean() {
        assertEquals("boolean", boolean())
    }
    @Test
    fun fn_Purchase_verification_failed() {
        assertEquals("Purchase verification failed", Purchase_verification_failed())
    }
    @Test
    fun fn_getInstance() {
        assertEquals("getInstance", getInstance())
    }
    @Test
    fun fn_ir_cafebazaar_poolakey_security_PurchaseVerifier() {
        assertEquals("ir.cafebazaar.poolakey.security.PurchaseVerifier", ir_cafebazaar_poolakey_security_PurchaseVerifier())
    }
    @Test
    fun fn_verify() {
        assertEquals("verify", verify())
    }
    @Test
    fun fn_DETAILS_LIST() {
        assertEquals("DETAILS_LIST", DETAILS_LIST())
    }
    @Test
    fun fn_com_android_vending() {
        assertEquals("com.android.vending", com_android_vending())
    }
    @Test
    fun fn_com_google_android_finsky_billing_iab_InAppBillingService() {
        assertEquals("com.google.android.finsky.billing.iab.InAppBillingService", com_google_android_finsky_billing_iab_InAppBillingService())
    }
    @Test
    fun fn_com_farsitel_bazaar_inappbilling_service_InAppBillingService() {
        assertEquals("com.farsitel.bazaar.inappbilling.service.InAppBillingService", com_farsitel_bazaar_inappbilling_service_InAppBillingService())
    }
    @Test
    fun fn_INAPP_PURCHASE_ITEM_LIST() {
        assertEquals("INAPP_PURCHASE_ITEM_LIST", INAPP_PURCHASE_ITEM_LIST())
    }
    @Test
    fun fn_INAPP_PURCHASE_DATA_LIST() {
        assertEquals("INAPP_PURCHASE_DATA_LIST", INAPP_PURCHASE_DATA_LIST())
    }
    @Test
    fun fn_INAPP_DATA_SIGNATURE_LIST() {
        assertEquals("INAPP_DATA_SIGNATURE_LIST", INAPP_DATA_SIGNATURE_LIST())
    }
    @Test
    fun fn_DEBUG_MESSAGE() {
        assertEquals("DEBUG_MESSAGE", DEBUG_MESSAGE())
    }
    @Test
    fun fn_ITEM_ID_LIST() {
        assertEquals("ITEM_ID_LIST", ITEM_ID_LIST())
    }
    @Test
    fun fn_inapp() {
        assertEquals("inapp", inapp())
    }
    @Test
    fun fn_price() {
        assertEquals("price", price())
    }
    @Test
    fun fn_zero_dollars() {
        assertEquals("0 \$", zero_dollars())
    }
    @Test
    fun fn_title() {
        assertEquals("title", title())
    }
    @Test
    fun fn_name() {
        assertEquals("name", name())
    }
    @Test
    fun fn_localizedIn() {
        assertEquals("localizedIn", localizedIn())
    }
    @Test
    fun fn_description() {
        assertEquals("description", description())
    }
    @Test
    fun fn_price_amount_micros() {
        assertEquals("price_amount_micros", price_amount_micros())
    }
    @Test
    fun fn_price_currency_code() {
        assertEquals("price_currency_code", price_currency_code())
    }
    @Test
    fun fn_USD() {
        assertEquals("USD", USD())
    }
    @Test
    fun fn_skuDetailsToken() {
        assertEquals("skuDetailsToken", skuDetailsToken())
    }
    @Test
    fun fn_oneTimePurchaseOfferDetails() {
        assertEquals("oneTimePurchaseOfferDetails", oneTimePurchaseOfferDetails())
    }
    @Test
    fun fn_offerIdToken() {
        assertEquals("offerIdToken", offerIdToken())
    }
    @Test
    fun fn_priceAmountMicros() {
        assertEquals("priceAmountMicros", priceAmountMicros())
    }
    @Test
    fun fn_priceCurrencyCode() {
        assertEquals("priceCurrencyCode", priceCurrencyCode())
    }
    @Test
    fun fn_formattedPrice() {
        assertEquals("formattedPrice", formattedPrice())
    }
    @Test
    fun fn_de_DE() {
        assertEquals("de_DE", de_DE())
    }
    @Test
    fun fn_en_AU() {
        assertEquals("en_AU", en_AU())
    }
    @Test
    fun fn_en_CA() {
        assertEquals("en_CA", en_CA())
    }
    @Test
    fun fn_en_GB() {
        assertEquals("en_GB", en_GB())
    }
    @Test
    fun fn_en_IN() {
        assertEquals("en_IN", en_IN())
    }
    @Test
    fun fn_en_SG() {
        assertEquals("en_SG", en_SG())
    }
    @Test
    fun fn_en_US() {
        assertEquals("en_US", en_US())
    }
    @Test
    fun fn_en_ZA() {
        assertEquals("en_ZA", en_ZA())
    }
    @Test
    fun fn_fr_CA() {
        assertEquals("fr_CA", fr_CA())
    }
    @Test
    fun fn_fr_FR() {
        assertEquals("fr_FR", fr_FR())
    }
    @Test
    fun fn_ja_JP() {
        assertEquals("ja_JP", ja_JP())
    }
    @Test
    fun fn_ko_KR() {
        assertEquals("ko_KR", ko_KR())
    }
    @Test
    fun fn_zh_CN() {
        assertEquals("zh_CN", zh_CN())
    }
    @Test
    fun fn_zh_HK() {
        assertEquals("zh_HK", zh_HK())
    }
    @Test
    fun fn_zh_TW() {
        assertEquals("zh_TW", zh_TW())
    }
    @Test
    fun fn_autoRenewing() {
        assertEquals("autoRenewing", autoRenewing())
    }
    @Test
    fun fn_acknowledged() {
        assertEquals("acknowledged", acknowledged())
    }
    @Test
    fun fn_quantity() {
        assertEquals("quantity", quantity())
    }
    @Test
    fun fn_signature() {
        assertEquals("signature", signature())
    }
    @Test
    fun fn_fuck_iab() {
        assertEquals("fuck.iab", fuck_iab())
    }
    @Test
    fun fn_data() {
        assertEquals("data", data())
    }
    @Test
    fun fn_GPA_() {
        assertEquals("GPA.", GPA_())
    }
    @Test
    fun fn_Purchase_Successful() {
        assertEquals("Purchase Successful!", Purchase_Successful())
    }
    @Test
    fun fn_ir_mservices_market() {
        assertEquals("ir.mservices.market", ir_mservices_market())
    }
    @Test
    fun fn_ir_mservices_market_service_InAppBillingService() {
        assertEquals("ir.mservices.market.service.InAppBillingService", ir_mservices_market_service_InAppBillingService())
    }
    @Test
    fun fn_subscriptionOfferDetails() {
        assertEquals("subscriptionOfferDetails", subscriptionOfferDetails())
    }
    @Test
    fun fn_basePlanId() {
        assertEquals("basePlanId", basePlanId())
    }
    @Test
    fun fn_offerId() {
        assertEquals("offerId", offerId())
    }
    @Test
    fun fn_pricingPhases() {
        assertEquals("pricingPhases", pricingPhases())
    }
    @Test
    fun fn_billingPeriod() {
        assertEquals("billingPeriod", billingPeriod())
    }
    @Test
    fun fn_recurrenceMode() {
        assertEquals("recurrenceMode", recurrenceMode())
    }
    @Test
    fun fn_offerTags() {
        assertEquals("offerTags", offerTags())
    }
    @Test
    fun fn_P1M() {
        assertEquals("P1M", P1M())
    }
    @Test
    fun fn_subs() {
        assertEquals("subs", subs())
    }
    @Test
    fun fn_Base64_decoding_failed() {
        assertEquals("Base64 decoding failed", Base64_decoding_failed())
    }
    @Test
    fun fn_nativehook() {
        assertEquals("nativehook", nativehook())
    }
    @Test
    fun fn_assets_scripts_() {
        assertEquals("assets/scripts/", assets_scripts_())
    }
    @Test
    fun fn__js() {
        assertEquals(".js", _js())
    }
    @Test
    fun fn_global() {
        assertEquals("global", global())
    }
    @Test
    fun fn_Not_connected_to_framework() {
        assertEquals("Not connected to framework", Not_connected_to_framework())
    }
    @Test
    fun fn_user_script() {
        assertEquals("user_script", user_script())
    }
    @Test
    fun fn_Script_saved() {
        assertEquals("Script saved", Script_saved())
    }
    @Test
    fun fn_Error__() {
        assertEquals("Error: ", Error__())
    }
    @Test
    fun fn_su() {
        assertEquals("su", su())
    }
    @Test
    fun fn__c() {
        assertEquals("-c", _c())
    }
    @Test
    fun fn_logcat__v_tag__s_FKIAB_V_FRIDA_V() {
        assertEquals("logcat -v tag -s FKIAB:V FRIDA:V", logcat__v_tag__s_FKIAB_V_FRIDA_V())
    }
    @Test
    fun fn___A_Z__() {
        assertEquals("^[A-Z]/", __A_Z__())
    }
    @Test
    fun fn__s_() {
        assertEquals(" : ", _s_())
    }
    @Test
    fun fn_Script() {
        assertEquals("Script", Script())
    }
    @Test
    fun fn_Log() {
        assertEquals("Log", Log())
    }
    @Test
    fun fn_Error() {
        assertEquals("Error", Error())
    }
    @Test
    fun fn_APP_SCRIPT_GOES_HERE() {
        assertEquals("APP_SCRIPT_GOES_HERE", APP_SCRIPT_GOES_HERE())
    }
    @Test
    fun fn_USER_SCRIPT_GOES_HERE() {
        assertEquals("USER_SCRIPT_GOES_HERE", USER_SCRIPT_GOES_HERE())
    }
    @Test
    fun fn_script_hint() {
        assertEquals("If you don't know what a\nFrida script is, you can\nignore this section.\n\nExample:\nconsole.log(\"hello world!\");", script_hint())
    }
    @Test
    fun fn_java_lang_Boolean() {
        assertEquals("java.lang.Boolean", java_lang_Boolean())
    }
    @Test
    fun fn_java_lang_Exception() {
        assertEquals("java.lang.Exception", java_lang_Exception())
    }
    @Test
    fun fn_getError() {
        assertEquals("getError", getError())
    }
    @Test
    fun fn_com_ea_nimble_mtx_googleplay_GooglePlayTransaction() {
        assertEquals("com.ea.nimble.mtx.googleplay.GooglePlayTransaction", com_ea_nimble_mtx_googleplay_GooglePlayTransaction())
    }
    @Test
    fun fn_getConfigValueAsString() {
        assertEquals("getConfigValueAsString", getConfigValueAsString())
    }
    @Test
    fun fn_java_lang_String() {
        assertEquals("java.lang.String", java_lang_String())
    }
    @Test
    fun fn_com_ea_nimble_NimbleApplicationConfiguration() {
        assertEquals("com.ea.nimble.NimbleApplicationConfiguration", com_ea_nimble_NimbleApplicationConfiguration())
    }
    @Test
    fun fn__false() {
        assertEquals("false", _false())
    }
    @Test
    fun fn_com_ea_nimble_mtx_enableVerification() {
        assertEquals("com.ea.nimble.mtx.enableVerification", com_ea_nimble_mtx_enableVerification())
    }
    @Test
    fun fn_com_ea_nimble_mtx_reportingEnabled() {
        assertEquals("com.ea.nimble.mtx.reportingEnabled", com_ea_nimble_mtx_reportingEnabled())
    }
    @Test
    fun fn_success() {
        assertEquals("success", success())
    }
    @Test
    fun fn__true() {
        assertEquals("true", _true())
    }
    @Test
    fun fn_kind() {
        assertEquals("kind", kind())
    }
    @Test
    fun fn_purchaseTimeMillis() {
        assertEquals("purchaseTimeMillis", purchaseTimeMillis())
    }
    @Test
    fun fn_consumptionState() {
        assertEquals("consumptionState", consumptionState())
    }
    @Test
    fun fn_purchaseType() {
        assertEquals("purchaseType", purchaseType())
    }
    @Test
    fun fn_acknowledgementState() {
        assertEquals("acknowledgementState", acknowledgementState())
    }
    @Test
    fun fn_obfuscatedExternalAccountId() {
        assertEquals("obfuscatedExternalAccountId", obfuscatedExternalAccountId())
    }
    @Test
    fun fn_obfuscatedExternalProfileId() {
        assertEquals("obfuscatedExternalProfileId", obfuscatedExternalProfileId())
    }
    @Test
    fun fn_regionCode() {
        assertEquals("regionCode", regionCode())
    }
    @Test
    fun fn_productPurchase() {
        assertEquals("productPurchase", productPurchase())
    }
    @Test
    fun fn_token() {
        assertEquals("token", token())
    }
    @Test
    fun fn_Appsflyer() {
        assertEquals("Appsflyer", Appsflyer())
    }
    @Test
    fun fn_didReceivePurchaseRevenueValidationInfo() {
        assertEquals("didReceivePurchaseRevenueValidationInfo", didReceivePurchaseRevenueValidationInfo())
    }
    @Test
    fun fn_com_unity3d_player_UnityPlayer() {
        assertEquals("com.unity3d.player.UnityPlayer", com_unity3d_player_UnityPlayer())
    }
    @Test
    fun fn_void() {
        assertEquals("void", void())
    }
    @Test
    fun fn_UnitySendMessage() {
        assertEquals("UnitySendMessage", UnitySendMessage())
    }
    @Test
    fun fn_ready() {
        assertEquals("ready", ready())
    }
    @Test
    fun fn_fkiab() {
        assertEquals("fkiab", fkiab())
    }
    @Test
    fun fn_Connected_to_Xposed_service__() {
        assertEquals("Connected to Xposed service: ", Connected_to_Xposed_service__())
    }
    @Test
    fun fn_Connected_to_() {
        assertEquals("Connected to : ", Connected_to_())
    }
    @Test
    fun fn_Not_connected_to_Xposed_service_() {
        assertEquals("Not connected to Xposed service.", Not_connected_to_Xposed_service_())
    }
    @Test
    fun fn_onBind() {
        assertEquals("onBind", onBind())
    }
    @Test
    fun fn_Custom_script_loaded() {
        assertEquals("Custom script loaded", Custom_script_loaded())
    }
    @Test
    fun fn_Custom_script_by_() {
        assertEquals("Custom script by ", Custom_script_by_())
    }
    @Test
    fun fn_callActivityOnCreate() {
        assertEquals("callActivityOnCreate", callActivityOnCreate())
    }
    @Test
    fun fn____author__() {
        assertEquals("// author: ", ___author__())
    }
    @Test
    fun fn_NothingMatchesThatSearch() {
        assertEquals("Nothing matches that search", NothingMatchesThatSearch())
    }
    @Test
    fun fn_NotConnectedToYourXposedFrameworkTryForceStoppingFkiabAndReLaf289e() {
        assertEquals("Not connected to your Xposed framework. Try force stopping FKIAB and re-launching it or adding an App to FKIAB's scope in your Xposed Framework.", NotConnectedToYourXposedFrameworkTryForceStoppingFkiabAndReLaf289e())
    }
    @Test
    fun fn_NewScript() {
        assertEquals("New script", NewScript())
    }
    @Test
    fun fn_Cancel() {
        assertEquals("Cancel", Cancel())
    }
    @Test
    fun fn_userScriptJs() {
        assertEquals("user_script.js", userScriptJs())
    }
    @Test
    fun fn_globalJs() {
        assertEquals("global.js", globalJs())
    }
    @Test
    fun fn_user() {
        assertEquals("user", user())
    }
    @Test
    fun fn_bundled() {
        assertEquals("bundled", bundled())
    }
    @Test
    fun fn_NotConnectedToTheXposedService() {
        assertEquals("Not connected to the Xposed service.", NotConnectedToTheXposedService())
    }
    @Test
    fun fn_CouldNotSaveTheScript() {
        assertEquals("Could not save the script", CouldNotSaveTheScript())
    }
    @Test
    fun fn_Delete() {
        assertEquals("Delete", Delete())
    }
    @Test
    fun fn_AreYouSure() {
        assertEquals("Are you sure?", AreYouSure())
    }
    @Test
    fun fn_YesDeleteIt() {
        assertEquals("Yes, Delete it", YesDeleteIt())
    }
    @Test
    fun fn_UserScriptRemoved() {
        assertEquals("User script removed", UserScriptRemoved())
    }
    @Test
    fun fn_ReadingTheModuleLogNeedsRootTheAppRunsSuLogcatAndNoSuWasFoun944dea() {
        assertEquals("Reading the module log needs root. The app runs \"su logcat\", and no su was found on this device, so nothing can be shown here.", ReadingTheModuleLogNeedsRootTheAppRunsSuLogcatAndNoSuWasFoun944dea())
    }
    @Test
    fun fn_RootAccessWasDeniedAllowRootForFkiabInYourRootManagerThenRetd6a4d0() {
        assertEquals("Root access was denied. Allow root for FKIAB in your root manager, then retry.", RootAccessWasDeniedAllowRootForFkiabInYourRootManagerThenRetd6a4d0())
    }
    @Test
    fun fn_TheLogStreamEndedWithoutAnyOutput() {
        assertEquals("The log stream ended without any output.", TheLogStreamEndedWithoutAnyOutput())
    }
    @Test
    fun fn_CouldNotReadTheLog1S() {
        assertEquals("Could not read the log: %1\$s", CouldNotReadTheLog1S())
    }
    @Test
    fun fn_version1S2D() {
        assertEquals("version %1\$s (%2\$d)", version1S2D())
    }
    @Test
    fun fn_sharedHelpersAndEnvironmentSetup() {
        assertEquals("shared helpers and environment setup", sharedHelpersAndEnvironmentSetup())
    }
    @Test
    fun fn_www() {
        assertEquals("www.", www())
    }
    @Test
    fun fn_playGoogleCom() {
        assertEquals("play.google.com", playGoogleCom())
    }
    @Test
    fun fn_ViewOnGooglePlay() {
        assertEquals("View on Google Play", ViewOnGooglePlay())
    }
    @Test
    fun fn_cafebazaarIr() {
        assertEquals("cafebazaar.ir", cafebazaarIr())
    }
    @Test
    fun fn_ViewOnCafeBazaar() {
        assertEquals("View on Cafe Bazaar", ViewOnCafeBazaar())
    }
    @Test
    fun fn_myketIr() {
        assertEquals("myket.ir", myketIr())
    }
    @Test
    fun fn_ViewOnMyket() {
        assertEquals("View on Myket", ViewOnMyket())
    }
    @Test
    fun fn_galaxyStore() {
        assertEquals("galaxy.store", galaxyStore())
    }
    @Test
    fun fn_ViewOnGalaxyStore() {
        assertEquals("View on Galaxy Store", ViewOnGalaxyStore())
    }
    @Test
    fun fn_appgalleryHuaweiCom() {
        assertEquals("appgallery.huawei.com", appgalleryHuaweiCom())
    }
    @Test
    fun fn_ViewOnAppgallery() {
        assertEquals("View on AppGallery", ViewOnAppgallery())
    }
    @Test
    fun fn_fDroidOrg() {
        assertEquals("f-droid.org", fDroidOrg())
    }
    @Test
    fun fn_ViewOnFDroid() {
        assertEquals("View on F-Droid", ViewOnFDroid())
    }
    @Test
    fun fn_apkpureCom() {
        assertEquals("apkpure.com", apkpureCom())
    }
    @Test
    fun fn_ViewOnApkpure() {
        assertEquals("View on APKPure", ViewOnApkpure())
    }
    @Test
    fun fn_aptoideCom() {
        assertEquals("aptoide.com", aptoideCom())
    }
    @Test
    fun fn_ViewOnAptoide() {
        assertEquals("View on Aptoide", ViewOnAptoide())
    }
    @Test
    fun fn_amazonCom() {
        assertEquals("amazon.com", amazonCom())
    }
    @Test
    fun fn_ViewOnAmazonAppstore() {
        assertEquals("View on Amazon Appstore", ViewOnAmazonAppstore())
    }
    @Test
    fun fn_appsAppleCom() {
        assertEquals("apps.apple.com", appsAppleCom())
    }
    @Test
    fun fn_itunesAppleCom() {
        assertEquals("itunes.apple.com", itunesAppleCom())
    }
    @Test
    fun fn_ViewOnAppleAppStore() {
        assertEquals("View on Apple App Store", ViewOnAppleAppStore())
    }
    @Test
    fun fn_microsoftCom() {
        assertEquals("microsoft.com", microsoftCom())
    }
    @Test
    fun fn_ViewOnMicrosoftStore() {
        assertEquals("View on Microsoft Store", ViewOnMicrosoftStore())
    }
    @Test
    fun fn_githubCom() {
        assertEquals("github.com", githubCom())
    }
    @Test
    fun fn_ViewOnGithub() {
        assertEquals("View on GitHub", ViewOnGithub())
    }
    @Test
    fun fn_gitlabCom() {
        assertEquals("gitlab.com", gitlabCom())
    }
    @Test
    fun fn_ViewOnGitlab() {
        assertEquals("View on GitLab", ViewOnGitlab())
    }
    @Test
    fun fn_orgFdroidFdroid() {
        assertEquals("org.fdroid.fdroid", orgFdroidFdroid())
    }
    @Test
    fun fn_comApkpureAegon() {
        assertEquals("com.apkpure.aegon", comApkpureAegon())
    }
    @Test
    fun fn_cmAptoidePt() {
        assertEquals("cm.aptoide.pt", cmAptoidePt())
    }
    @Test
    fun fn_customScript() {
        assertEquals("custom script", customScript())
    }
    @Test
    fun fn_customScriptRunningOnEveryApp() {
        assertEquals("custom script running on every app", customScriptRunningOnEveryApp())
    }
    @Test
    fun fn_s4a9f2e() {
        assertEquals(" · ", s4a9f2e())
    }
    @Test
    fun fn_package_() {
        assertEquals("package", package_())
    }
    @Test
    fun fn_logcatBAllCLogcatVTagSFkiabVFridaV() {
        assertEquals("logcat -b all -c && logcat -v tag -s FKIAB:V FRIDA:V", logcatBAllCLogcatVTagSFkiabVFridaV())
    }
    @Test
    fun fn_newline() {
        assertEquals("\n", newline())
    }
    @Test
    fun fn_se26549() {
        assertEquals(": ", se26549())
    }
    @Test
    fun fn_httpsGithubComXposedModulesRepoComFuckIabReleases() {
        assertEquals("https://github.com/Xposed-Modules-Repo/com.fuck.iab/releases", httpsGithubComXposedModulesRepoComFuckIabReleases())
    }
    @Test
    fun fn_tgResolveDomainFkiab() {
        assertEquals("tg://resolve?domain=fkiab", tgResolveDomainFkiab())
    }
    @Test
    fun fn_httpsTMeFkiab() {
        assertEquals("https://t.me/fkiab", httpsTMeFkiab())
    }
    @Test
    fun fn_question() {
        assertEquals("?", question())
    }
    @Test
    fun fn_BundleScriptPackage() {
        assertEquals("BUNDLE_SCRIPT_PACKAGE", BundleScriptPackage())
    }
    @Test
    fun fn_UserScriptGlobal() {
        assertEquals("USER_SCRIPT_GLOBAL", UserScriptGlobal())
    }
    @Test
    fun fn_UserScriptPackage() {
        assertEquals("USER_SCRIPT_PACKAGE", UserScriptPackage())
    }
    @Test
    fun fn_url() {
        assertEquals("url", url())
    }
    @Test
    fun fn_author() {
        assertEquals("author", author())
    }
    @Test
    fun fn_disabled_() {
        assertEquals("disabled_", disabled_())
    }
    @Test
    fun fn__scripts() {
        assertEquals("_scripts", _scripts())
    }
    @Test
    fun fn_scf64b8() {
        assertEquals("// ", scf64b8())
    }
    @Test
    fun fn__0fKb() {
        assertEquals("%.0f KB", _0fKb())
    }
    @Test
    fun fn_b() {
        assertEquals(" B", b())
    }
    @Test
    fun fn_dash() {
        assertEquals("-", dash())
    }
    @Test
    fun fn_space() {
        assertEquals(" ", space())
    }
    @Test
    fun fn_try_() {
        assertEquals("try { ", try_())
    }
    @Test
    fun fn_catchEConsoleLogETry() {
        assertEquals(" } catch(e) { console.log(e) }\ntry { ", catchEConsoleLogETry())
    }
    @Test
    fun fn_catchEConsoleLogE() {
        assertEquals(" } catch(e) { console.log(e) }", catchEConsoleLogE())
    }
    @Test
    fun fn_name_() {
        assertEquals("// name:", name_())
    }
    @Test
    fun fn_url_() {
        assertEquals("// url:", url_())
    }
    @Test
    fun fn_description_() {
        assertEquals("// description:", description_())
    }
    @Test
    fun fn_ViewAppPage() {
        assertEquals("View app page", ViewAppPage())
    }
    @Test
    fun fn_ViewOnTelegram() {
        assertEquals("View on Telegram", ViewOnTelegram())
    }
    @Test
    fun fn_tMe() {
        assertEquals("t.me", tMe())
    }
}
