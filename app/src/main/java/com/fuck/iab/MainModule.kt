package com.fuck.iab

import android.app.Application
import fh.c
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import org.luckypray.dexkit.DexKitBridge

class MainModule : c() {

    // This is called before Application.onCreate()
    override fun onInitialized(app: Application, param: PackageLoadedParam, bridge: DexKitBridge) {

    }
}