package com.fuck.iab

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import com.fuck.iab.NativeBridge.startScript
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.zip.ZipFile
import java.security.PublicKey
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds

object NativeBridge {

    lateinit var dexKitBridge: DexKitBridge

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

    /**
     * Stores handled by the hook below. The package list lives in [HookedProviders], because the
     * manager app has to label these apps as hooked instead of scripted.
     */
    val iabProviders = mapOf(
        HookedProviders.PLAY_STORE to com_google_android_finsky_billing_iab_InAppBillingService(),
        HookedProviders.BAZAAR to com_farsitel_bazaar_inappbilling_service_InAppBillingService(),
        HookedProviders.MYKET to ir_mservices_market_service_InAppBillingService()
    )

    lateinit var app: Application
    val latch = CountDownLatch(1)
    var customScript = false
    var scriptAuthor: String? = null

    companion object {
//        const val TAG = "FKIAB"

        init {
            System.loadLibrary(dexkit())
        }
    }

    private fun log(text: String) {
//        log(Log.INFO, TAG, text)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
//        log(Log.INFO, TAG, "onModuleLoaded: " + param.processName)
//        log(Log.INFO, TAG, "framework: $frameworkName($frameworkVersionCode) API $apiVersion")

        val hasProp: (Long) -> Boolean = { prop -> frameworkProperties.and(prop) != 0L }
//        log(Log.INFO, TAG, "system supported: " + hasProp(PROP_CAP_SYSTEM))
//        log(Log.INFO, TAG, "remote supported: " + hasProp(PROP_CAP_REMOTE))
//        log(Log.INFO, TAG, "api protection: " + hasProp(PROP_RT_API_PROTECTION))
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
//        log(Log.INFO, TAG, "onPackageLoaded: " + param.packageName)

        if (!param.isFirstPackage) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            log(Log.INFO, TAG, "default classloader is " + param.defaultClassLoader)
            packageLoaded(param.packageName, param.applicationInfo, param.defaultClassLoader)
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
//        log(Log.INFO, TAG, "onPackageReady: " + param.packageName)
//        log(Log.INFO, TAG, "app classloader is " + param.classLoader)
//        log(Log.INFO, TAG, "app acf is " + param.appComponentFactory)
//        log(Log.INFO, TAG, "module apk path: " + this.moduleApplicationInfo.sourceDir)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            packageLoaded(param.packageName, param.applicationInfo, param.classLoader)
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
//        log(Log.INFO, TAG, "onSystemServerStarting, system classloader: " + param.classLoader)
    }


    private fun packageLoaded(
        packageName: String,
        applicationInfo: ApplicationInfo,
        classLoader: ClassLoader
    ) {
        try {
            if (iabProviders.containsKey(packageName)) {
                val bridge = DexKitBridge.create(applicationInfo.sourceDir)
                val clazz = bridge.findClass {
                    matcher {
                        className = iabProviders[packageName]!!
                    }
                }.singleOrNull() ?: return
                hook(clazz.getInstance(classLoader).getMethod(onBind(), Intent::class.java)).intercept { chain ->
                    FakeBinder((chain.thisObject as? Context)?.applicationContext!!, packageName == com_android_vending(), chain.proceed() as IBinder, ::log)
                }
            } else {
                val applicationClassName = applicationInfo.className ?: android_app_Application()
                val applicationClass = classLoader.loadClass(applicationClassName)
                val onCreate = applicationClass.getMethod(onCreate())

                hook(onCreate).intercept { chain ->
                    app = chain.thisObject as Application

                    val bridge = DexKitBridge.create(applicationInfo.sourceDir)
                    NativeBridge.dexKitBridge = bridge

                    hookOnServiceConnected(classLoader, bridge)
                    hookPurchaseVerificationMethods(classLoader, bridge)

                    val global = readScriptForPackage(global())

                    val script = if (global != null) {
                        val apps = readScriptForPackage(packageName)
                        val (author, appScript) = if (apps == null || apps == "") {
                            null to ""
                        } else {
                            customScript = true
                            extractAuthor(apps)
                        }
                        scriptAuthor = author
                        val userScript = readUserScript()
                        var combined = global.replace(APP_SCRIPT_GOES_HERE(), userScript ?: "")
                        combined = combined.replace(USER_SCRIPT_GOES_HERE(), appScript)
                        combined.ifEmpty { null }
                    } else null

                    if (script != null) {
                        AppScope.launch {
                            NativeBridge.payloads.collect { payload ->
                                if (payload == ready()) {
//                                log("frida is alive. lets call on create")
                                    latch.countDown()
                                }
                            }
                        }

//                    log("before start script package name: $packageName")
                        startScript(packageName, script)
//                    log("after start script")

                        AppScope.launch {
                            delay(3.seconds)
//                        log("timeout passed. frida is dead. lets call on create")
                            latch.countDown()
                        }

                        try {
                            latch.await()
//                        log("calling on create")
                            chain.proceed()
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    } else {
                        chain.proceed()
                    }
                }

                val method = Instrumentation::class.java.getDeclaredMethod(
                    callActivityOnCreate(),
                    Activity::class.java,
                    Bundle::class.java
                )

                hook(method).intercept { chain ->
                    val activity = chain.getArg(0) as? Activity
                    val intent = activity?.intent

                    val isLauncher = intent != null &&
                            intent.action == Intent.ACTION_MAIN &&
                            intent.hasCategory(Intent.CATEGORY_LAUNCHER)

                    if (activity != null && isLauncher && customScript) {
                        if (scriptAuthor != null && scriptAuthor != "") {
                            Toast.makeText(activity, "${Custom_script_by_()}$scriptAuthor", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, Custom_script_loaded(), Toast.LENGTH_SHORT).show()
                        }
                    }

                    chain.proceed()
                }
            }
        } catch (e: Exception) {
//            log(e.message!!)
//            e.printStackTrace()
        }
    }

    private fun hookOnServiceConnected(classLoader: ClassLoader, bridge: DexKitBridge) {
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

                    val onServiceConnectedMethod = it.getMethodInstance(classLoader)
//                    log("hooking ${it.name}")
                    hook(onServiceConnectedMethod).intercept { chain ->

                        val componentName = chain.args[0] as ComponentName
                        val realBinder = chain.args[1] as IBinder

//                        log("component name = $componentName")
//                        log("binder = ${realBinder.javaClass}")

                        if (iabProviders[componentName.packageName] == componentName.className) {
                            val isGoogle = componentName.packageName == com_android_vending()
                            return@intercept chain.proceed(arrayOf(chain.args[0], FakeBinder(app, isGoogle, realBinder, ::log)))
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

    private fun hookPurchaseVerificationMethods(classLoader: ClassLoader, bridge: DexKitBridge) {
        var methods = bridge.findMethod {
            matcher {
                returnType = boolean()
                // paramTypes(String::class.java, String::class.java, String::class.java)
                usingStrings(
                    Purchase_verification_failed(),
                    Base64_decoding_failed()
                )
            }
        }
        for (m in methods) {
            try {
                val method = m.getMethodInstance(classLoader)
//            log("hooking ${getMethodAsString(method)}")
                hook(method).intercept {
                    true
                }
            } catch (e: Exception) {

            }
        }

        methods = bridge.findMethod {
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
        }
        for (m in methods) {
            try {
                val method = m.getMethodInstance(classLoader)
//                log("** hooking ${getMethodAsString(method)}")
                hook(method).intercept {
                    true
                }
            } catch (e: Exception) {

            }
        }

        methods = bridge.findMethod {
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
        }
        for (m in methods) {
            val method = m.getMethodInstance(classLoader)
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

        try {
            val m = bridge.findMethod {
                matcher {
                    name = getError()
                    returnType = java_lang_Exception()
                    paramCount = 0
                    declaredClass = com_ea_nimble_mtx_googleplay_GooglePlayTransaction()
                }
            }.singleOrNull()
            if (m != null) {
                val method = m.getMethodInstance(classLoader)
//            log("** hooking ${getMethodAsString(method)}")
                hook(method).intercept { chain ->
                    null
                }
            }
        } catch (e: Exception) {

        }

        try {
            val m = bridge.findMethod {
                matcher {
                    name = getConfigValueAsString()
                    returnType = java_lang_String()
                    paramTypes(java_lang_String())
                    declaredClass = com_ea_nimble_NimbleApplicationConfiguration()
                }
            }.singleOrNull()
            if (m != null) {
                val method = m.getMethodInstance(classLoader)
//            log("** hooking ${getMethodAsString(method)}")
                hook(method).intercept { chain ->
                    if (chain.args[0] == com_ea_nimble_mtx_enableVerification()) {
                        return@intercept _false()
                    } else if (chain.args[0] == com_ea_nimble_mtx_reportingEnabled()) {
                        return@intercept _false()
                    } else {
                        chain.proceed()
                    }
                }
            }
        } catch (e: Exception) {

        }

        try {
            val m = bridge.findMethod {
                matcher {
                    name = UnitySendMessage()
                    returnType = void()
                    paramTypes(java_lang_String(), java_lang_String(), java_lang_String())
                    declaredClass = com_unity3d_player_UnityPlayer()
                }
            }.singleOrNull()
            if (m != null) {
                val method = m.getMethodInstance(classLoader)
//                log("** hooking ${getMethodAsString(method)}")
                hook(method).intercept { chain ->
                    val arg1 = chain.args[0] as String
                    val arg2 = chain.args[1] as String
                    val arg3 = chain.args[2] as String

                    if (arg1 == Appsflyer() && arg2 == didReceivePurchaseRevenueValidationInfo()) {
                        val json = JSONObject(arg3)

                        val success = json.getString(success())
                        val token = json.getString(token())

                        if (success == _false()) {
                            val prefs = app.getSharedPreferences(fuck_iab(), MODE_PRIVATE)

                            val value = prefs.getString(token, "")

                            if (value == "") return@intercept chain.proceed()

                            val json = try {
                                JSONObject(value!!)
                            } catch (_: Exception) {
                                return@intercept chain.proceed()
                            }

                            val map = HashMap<String, Any>()
                            val map2 = HashMap<String, Any>()
                            map[token()] = token
                            map[success()] = _true()

                            map2[productId()] = json.getString(productId())
                            map2[purchaseState()] = json.getInt(purchaseState())
                            map2[kind()] = inapp()
                            map2[purchaseTimeMillis()] = try {
                                json.getLong(purchaseTime()).toString()
                            } catch (e: Exception) {
                                System.currentTimeMillis().toString()
                            }
                            map2[consumptionState()] = 0
                            map2[developerPayload()] = try {
                                json.getString(developerPayload())
                            } catch (e: Exception) {
                                developerPayload()
                            }
                            map2[orderId()] = json.getString(orderId())
                            map2[purchaseType()] = 0
                            map2[acknowledgementState()] = 1
                            map2[purchaseToken()] = token
                            map2[quantity()] = 1
                            map2[obfuscatedExternalAccountId()] = obfuscatedExternalAccountId()
                            map2[obfuscatedExternalProfileId()] = obfuscatedExternalProfileId()
                            map2[regionCode()] = regionCode()
                            map[productPurchase()] = map2

                            return@intercept chain.proceed(arrayOf(arg1, arg2, JSONObject(map).toString()))
                        }
                    }
                    chain.proceed()
                }
            }
        } catch (e: Exception) {

        }
    }

    private fun readScriptForPackage(packageName: String): String? {
        // A script switched off in the manager app must not run for that app.
        if (isScriptDisabled(packageName)) return null

        return readUserScriptForPackage(packageName) ?: readBundledScript(packageName)
    }

    // The scripts compiled into the APK are what runs when the manager has no user copy.
    private fun readBundledScript(packageName: String): String? {
        return try {
            ZipFile(this.moduleApplicationInfo.sourceDir).use { zip ->
                val entry = zip.getEntry("${assets_scripts_()}$packageName${_js()}") ?: return null
                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isScriptDisabled(packageName: String): Boolean {
        if (frameworkProperties.and(XposedInterface.PROP_CAP_REMOTE) == 0L) return false

        return try {
            val disabled = getRemotePreferences(fuck_iab())
                .getStringSet(RemoteScripts.DISABLED_SCRIPTS_KEY, emptySet()) ?: return false

            disabled.contains(packageName) || disabled.contains(global())
        } catch (e: Exception) {
            false
        }
    }

    // A script written in the manager app wins over the script bundled in the APK.
    private fun readUserScriptForPackage(packageName: String): String? {
        if (frameworkProperties.and(XposedInterface.PROP_CAP_REMOTE) == 0L) return null

        val name = RemoteScripts.userFileName(packageName)

        return try {
            if (listRemoteFiles().none { it == name }) return null

            val pfd = openRemoteFile(name)
            FileInputStream(pfd.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
                .takeIf { it.isNotBlank() }
        } catch (e: Exception) {
//            log("Failed to read the user script for $packageName: ${e.message}")
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
//        return getRemotePreferences(fkiab()).getString(user_script_js(), "")
    }

    fun extractAuthor(content: String): Pair<String, String> {
        val prefix = ___author__()
        val newlineIndex = content.indexOf('\n')
        val firstLine = if (newlineIndex == -1) content else content.substring(0, newlineIndex)

        return if (firstLine.startsWith(prefix)) {
            val author = firstLine.removePrefix(prefix).trimEnd('\r').trim()/*.substringBefore(' ')*/
            val rest = if (newlineIndex == -1) "" else content.substring(newlineIndex + 1)
            author to rest
        } else {
            "" to content
        }
    }
}