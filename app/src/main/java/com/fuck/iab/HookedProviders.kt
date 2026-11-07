package com.fuck.iab

/**
 * The stores the module handles with its own Java hook rather than with a Frida script.
 *
 * For these packages `MainModule` hooks the billing service binding and returns, so no script
 * is read or started for them. Everything the manager shows has to say so, otherwise an app
 * covered by the hook looks like it is waiting for a script.
 */
object HookedProviders {

    const val PLAY_STORE = "com.android.vending"

    const val BAZAAR = "com.farsitel.bazaar"

    const val MYKET = "ir.mservices.market"

    fun handles(packageName: String): Boolean {
        return packageName == PLAY_STORE || packageName == BAZAAR || packageName == MYKET
    }
}
