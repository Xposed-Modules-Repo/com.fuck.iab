package com.fuck.iab

/**
 * Requests the module handles inside a hooked app.
 *
 * The framework API has no way to stop a target process, so the manager sends this
 * broadcast to the package and the module, which runs in that process, ends it.
 */
object ModuleActions {

    const val STOP_APP = "com.fuck.iab.action.STOP_APP"
}
