package com.fuck.iab

import android.app.Application
import ff.c
import io.github.libxposed.api.XposedModuleInterface
import org.luckypray.dexkit.DexKitBridge

class MainModule : c() {
    override fun onInitialized(app: Application, param: XposedModuleInterface.PackageLoadedParam, bridge: DexKitBridge) {
        // Toast.makeText(app, "FuckIAB Loaded!", Toast.LENGTH_SHORT).show()
    }
}