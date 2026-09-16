package com.fuck.iab

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log
import androidx.core.content.edit
import com.fuck.iab.NativeBridge.startScript
import fh.d
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.security.PublicKey
import java.util.zip.ZipFile

object NativeBridge {

    init {
        System.loadLibrary(nativehook())
    }

    @JvmStatic
    external fun startScript(packageName: String, scriptSource: String)

    private val payloadChannel = Channel<String>(capacity = Channel.UNLIMITED)

    val payloads = payloadChannel.receiveAsFlow()

    @JvmStatic
    fun onPayloadReceived(payload: String) {
        payloadChannel.trySend(payload)
    }
}

object AppScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Default
}

class MainModule : XposedModule() {

    lateinit var app: Application

    companion object {
//        const val TAG = "FKIAB"

        init {
            System.loadLibrary(dexkit())
        }
    }

//    private fun log(text: String) = log(Log.INFO, TAG, text)

    override fun onModuleLoaded(param: ModuleLoadedParam) {
//        log(Log.INFO, TAG, "onModuleLoaded: " + param.processName)
//        log(Log.INFO, TAG, "framework: $frameworkName($frameworkVersionCode) API $apiVersion")
//
//        val hasProp: (Long) -> Boolean = { prop -> frameworkProperties.and(prop) != 0L }
//        log(Log.INFO, TAG, "system supported: " + hasProp(PROP_CAP_SYSTEM))
//        log(Log.INFO, TAG, "remote supported: " + hasProp(PROP_CAP_REMOTE))
//        log(Log.INFO, TAG, "api protection: " + hasProp(PROP_RT_API_PROTECTION))
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
//        log(Log.INFO, TAG, "onPackageLoaded: " + param.packageName)
//        log(Log.INFO, TAG, "default classloader is " + param.defaultClassLoader)

        if (!param.isFirstPackage) return

        AppScope.launch {
            NativeBridge.payloads.collect { payload ->
                if (payload == java_ready()) {
                    try {
                        val activityThread = Class.forName(android_app_ActivityThread())

                        val currentApplication = activityThread.getMethod(currentApplication()).invoke(null) as? Application

                        app = currentApplication!!

                        DexKitBridge.create(param.applicationInfo.sourceDir).use { bridge ->
                            hookOnServiceConnected(param, bridge)
                            hookSignatureVerificationMethods(param, bridge)
                        }

                    } catch (e: Exception) {

                    }
                }
            }
        }

        val packageName = param.packageName
        val global = readScriptForPackage(global())

        if (global != null) {
            val appScript = readScriptForPackage(packageName)
            val userScript = readUserScript()
            var combined = global.replace(APP_SCRIPT_GOES_HERE(), userScript ?: "")
            combined = combined.replace(USER_SCRIPT_GOES_HERE(), appScript ?: "")
            if (combined.isNotEmpty()) {
                startScript(packageName, combined)
            }
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
//        log(Log.INFO, TAG, "onPackageReady: " + param.packageName)
//        log(Log.INFO, TAG, "app classloader is " + param.classLoader)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            log(Log.INFO, TAG, "app acf is " + param.appComponentFactory)
//        }
//        log(Log.INFO, TAG, "module apk path: " + this.moduleApplicationInfo.sourceDir)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
//        log(Log.INFO, TAG, "onSystemServerStarting, system classloader: " + param.classLoader)
    }

    private fun hookOnServiceConnected(param: PackageLoadedParam, bridge: DexKitBridge) {
        try {
            val classes = bridge.findClass {
                matcher {
                    addInterface(android_content_ServiceConnection())
                }
            }

//            log("found ${classes.size} android.content.ServiceConnection subclasses")

            classes.forEach { clazz ->
//                log("----> ${clazz.name}")

                clazz.methods.forEach {
                    if (it.name != onServiceConnected()) return@forEach

                    val onServiceConnectedMethod = it.getMethodInstance(param.defaultClassLoader)
//                    log("hooking ${it.name}")
                    hook(onServiceConnectedMethod).intercept { chain ->

                        val componentName = chain.args[0] as ComponentName
                        val realBinder = chain.args[1] as IBinder

//                        log("component name = $componentName")
//                        log("binder = ${realBinder.javaClass}")


                        val isGoogle = componentName.packageName == com_android_vending() && componentName.className == com_google_android_finsky_billing_iab_InAppBillingService()
                        val isBazaar = !isGoogle && componentName.packageName == com_farsitel_bazaar() && componentName.className == com_farsitel_bazaar_inappbilling_service_InAppBillingService()
                        val isMyket = !isGoogle && !isBazaar && componentName.packageName == ir_mservices_market() && componentName.className == ir_mservices_market_service_InAppBillingService()

                        if (isGoogle || isBazaar || isMyket) {
                            val fakeBinder = object : Binder(), IInterface {

                                override fun asBinder(): IBinder {
                                    return this
                                }

                                override fun queryLocalInterface(descriptor: String): IInterface {
                                    return this
                                }

                                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
//                                    log("code = $code")

                                    val oldPos = data.dataPosition()

                                    try {
                                        data.enforceInterface(com_android_vending_billing_IInAppBillingService())

                                        val apiVersion = data.readInt()
//                                        log("api version is $apiVersion")

                                        when (code) {
                                            1 -> {
                                                if (isGoogle) {
                                                    if (apiVersion > 17) {
                                                        reply!!.writeNoException()
                                                        reply.writeInt(3)
                                                    } else {
                                                        reply!!.writeNoException()
                                                        reply.writeInt(0)
                                                    }
                                                } else {
                                                    reply!!.writeNoException()
                                                    reply.writeInt(0)
                                                }
                                                return true

//                                                val result = realBinder.transact(code, data, reply, flags)
//                                                try {
//                                                    reply!!.readException()
//                                                    val i = reply.readInt()
//                                                    log("1: real result = $i")
//                                                } finally {
//                                                    reply!!.setDataPosition(0)
//                                                }
//                                                return result
                                            }

                                            2, 901 -> {
                                                // getSkuDetails
                                                data.readString() // package name
                                                val type = data.readString() // type
                                                data.readInt() // bundle
                                                val bundle1 = Bundle.CREATOR.createFromParcel(data)
//
//                                                log("901: type is $type")
//                                                log("901: bundle:")
//                                                dumpBundle(bundle1)
//
                                                val b = Bundle().apply {
                                                    putStringArrayList(DETAILS_LIST(), createDetailsList(bundle1, type!!))
                                                    putInt(RESPONSE_CODE(), 0)
                                                }
//                                                log("response bundle:")
//                                                dumpBundle(b)
                                                reply!!.writeNoException()
                                                reply.writeInt(1)
                                                b.writeToParcel(reply, 1)
                                                return true

//                                                val result = realBinder.transact(code, data, reply, flags)
//                                                try {
//                                                    reply!!.readException()
//                                                    reply.readInt() // bundle
//                                                    val bundleResult = Bundle.CREATOR.createFromParcel(reply)
//                                                    log("901: bundle_result:")
//                                                    dumpBundle(bundleResult)
//                                                } finally {
//                                                    reply!!.setDataPosition(0)
//                                                }
//                                                return result
                                            }

                                            3, 8 -> {
                                                // getBuyIntent
                                                val packageName = data.readString()
                                                val sku = data.readString()
                                                val type = data.readString()
                                                val developerPayload = data.readString()

//                                                log("sku = $sku")

                                                val purchaseToken = randomPurchaseToken()
                                                val signature = randomSignature()

                                                val data = JSONObject().apply {
                                                    put(orderId(), randomOrderId())
                                                    put(packageName(), packageName)
                                                    put(productId(), sku)
                                                    put(purchaseTime(), System.currentTimeMillis())
                                                    put(purchaseState(), 0)
                                                    put(developerPayload(), developerPayload)
                                                    put(purchaseToken(), purchaseToken)
                                                }

                                                val dataString = data.toString()

                                                val prefsData = JSONObject(dataString).apply {
                                                    put(signature(), signature)
                                                    put(type(), type)
                                                    remove(purchaseToken())
                                                }

                                                val prefs = app.getSharedPreferences(fuck_iab(), MODE_PRIVATE)
                                                prefs.edit(commit = true) { putString(purchaseToken, prefsData.toString()) }

                                                val intent = Intent().apply {
                                                    component = ComponentName(
                                                        com_fuck_iab(),
                                                        d::class.java.name
                                                    )
                                                }

                                                intent.putExtra(data(), dataString)
                                                intent.putExtra(signature(), signature)

                                                val fakePendingIntent = PendingIntent.getActivity(
                                                    app,
                                                    1001,
                                                    intent,
                                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                                )

                                                val b = Bundle().apply {
                                                    putInt(RESPONSE_CODE(), 0)
                                                    putParcelable(BUY_INTENT(), fakePendingIntent)
                                                }

                                                reply!!.writeNoException()
                                                reply.writeInt(1)
                                                b.writeToParcel(reply, 1)
                                                return true
                                            }

                                            4, 11 -> {
                                                // getPurchases
                                                data.readString() // package name
                                                val type = data.readString()
//                                                log("11: type is $type")
                                                val b = Bundle().apply {
                                                    val inAppPurchaseItemList = arrayListOf<String>()
                                                    val inAppPurchaseDataList = arrayListOf<String>()
                                                    val inAppDataSignatureList = arrayListOf<String>()

                                                    val prefs = app.getSharedPreferences(fuck_iab(), MODE_PRIVATE)

                                                    prefs.all.forEach { (key, data) ->
                                                        val value = data as? String ?: return@forEach
                                                        val json = try {
                                                            JSONObject(value)
                                                        } catch (_: Exception) {
                                                            return@forEach
                                                        }

                                                        if (type != json.remove(type())) return@forEach

                                                        json.put(purchaseToken(), key)
                                                        json.put(autoRenewing(), true)
                                                        json.put(acknowledged(), true)
                                                        json.put(quantity(), 1)

                                                        val signature = json.remove(signature()) as String

                                                        inAppPurchaseItemList.add(json.getString(productId()))
                                                        inAppPurchaseDataList.add(json.toString())
                                                        inAppDataSignatureList.add(signature)
                                                    }

//                                                    log("11: INAPP_PURCHASE_ITEM_LIST: ${inAppPurchaseItemList.joinToString()}")
//                                                    log("11: INAPP_PURCHASE_DATA_LIST: ${inAppPurchaseDataList.joinToString()}")

                                                    putStringArrayList(INAPP_PURCHASE_ITEM_LIST(), inAppPurchaseItemList)
                                                    putInt(RESPONSE_CODE(), 0)
                                                    putStringArrayList(INAPP_PURCHASE_DATA_LIST(), inAppPurchaseDataList)
                                                    putStringArrayList(INAPP_DATA_SIGNATURE_LIST(), inAppDataSignatureList)
                                                }
                                                reply!!.writeNoException()
                                                reply.writeInt(1)
                                                b.writeToParcel(reply, 1)
                                                return true
                                            }

                                            5 -> {
                                                // consume old (bazaar)
                                                val purchaseToken = data.readString()
                                                removeFromPrefs(app, purchaseToken)
                                                reply!!.writeNoException()
                                                reply.writeInt(0)
                                                return true
                                            }

                                            7 -> {
                                                // get purchase config bazaar
                                                val b = Bundle().apply {
                                                    putBoolean(INTENT_V2_SUPPORT(), false)
                                                    putBoolean(INTENT_V3_SUPPORT(), false)
                                                }
                                                reply!!.writeNoException()
                                                reply.writeInt(1)
                                                b.writeToParcel(reply, 1)
                                                return true
                                            }

                                            9 -> {
                                                // Acknowledge Purchase
                                                data.readString() // package name
                                                val purchaseToken = data.readString()
                                                data.readInt() // bundle
                                                Bundle.CREATOR.createFromParcel(data)

                                                val purchaseExists = app.getSharedPreferences(fuck_iab(), MODE_PRIVATE).contains(purchaseToken)

                                                val b = Bundle().apply {
                                                    putInt(RESPONSE_CODE(), if (purchaseExists) 0 else 1)
                                                    putString(DEBUG_MESSAGE(), "")
                                                }
                                                reply!!.writeNoException()
                                                reply.writeInt(1)
                                                b.writeToParcel(reply, 1)
                                                return true
                                            }

                                            12 -> {
                                                // consume purchase
                                                data.readString() // package name
                                                val purchaseToken = data.readString()

                                                removeFromPrefs(app, purchaseToken)

                                                val b = Bundle().apply {
                                                    putInt(RESPONSE_CODE(), 0)
                                                    putString(DEBUG_MESSAGE(), "")
                                                }
                                                reply!!.writeNoException()
                                                reply.writeInt(1)
                                                b.writeToParcel(reply, 1)
                                                return true
                                            }

                                            else -> {
                                                return realBinder.transact(code, data, reply, flags)
                                            }
                                        }
                                    } finally {
                                        data.setDataPosition(oldPos)
                                    }
                                }
                            }
                            return@intercept chain.proceed(arrayOf(chain.args[0], fakeBinder))
                        }

                        return@intercept chain.proceed()
                    }
                }
            }
        } catch (e: Exception) {
//            e.printStackTrace()
//            log(Log.ERROR, TAG, e.message!!)
        }
    }

    private fun hookSignatureVerificationMethods(param: PackageLoadedParam, bridge: DexKitBridge) {
        var m = bridge.findMethod {
            matcher {
                returnType = boolean()
                // paramTypes(String::class.java, String::class.java, String::class.java)
                usingStrings(
                    Purchase_verification_failed(),
                    Base64_decoding_failed()
                )
            }
        }.singleOrNull()
        if (m != null) {
            val method = m.getMethodInstance(param.defaultClassLoader)
//            log("hooking ${getMethodAsString(method)}")
            hook(method).intercept {
                true
            }
        }

        m = bridge.findMethod {
            matcher {
                returnType = boolean()
                paramTypes(PublicKey::class.java, String::class.java, String::class.java)
                invokeMethods {
                    add {
                        name = getInstance()
                        paramTypes(String::class.java)
                    }
                }
            }
        }.singleOrNull()
        if (m != null) {
            val method = m.getMethodInstance(param.defaultClassLoader)
//            log("** hooking ${getMethodAsString(method)}")
            hook(method).intercept {
                true
            }
        }

        m = bridge.findMethod {
            matcher {
                returnType = java_lang_Boolean()
                paramTypes(PublicKey::class.java, String::class.java, String::class.java)
                invokeMethods {
                    add {
                        name = getInstance()
                        paramTypes(String::class.java)
                    }
                }
            }
        }.singleOrNull()
        if (m != null) {
            val method = m.getMethodInstance(param.defaultClassLoader)
//            log("** hooking ${getMethodAsString(method)}")
            hook(method).intercept {
                java.lang.Boolean.TRUE
            }
        }

        try {
            hook(
                Class.forName(ir_cafebazaar_poolakey_security_PurchaseVerifier()).getDeclaredMethod(
                    verify(),
                    PublicKey::class.java,
                    String::class.java,
                    String::class.java
                )
            ).intercept { chain ->
                true
            }
        } catch (e: Exception) {

        }
    }

    private fun readScriptForPackage(packageName: String): String? {
        val apkPath = this.moduleApplicationInfo.sourceDir
        return try {
            ZipFile(apkPath).use { zip ->
                val entry = zip.getEntry("${assets_scripts_()}$packageName${_js()}")
                    ?: return null
                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
//            log("Failed to read script for $packageName: ${e.message}")
            null
        }
    }

    private fun readUserScript(): String? {
        return try {
            val pfd = openRemoteFile(user_script_js())
            FileInputStream(pfd.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}