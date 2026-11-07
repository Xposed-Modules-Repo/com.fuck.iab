package fh

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import com.fuck.iab.HookedProviders
import com.fuck.iab.R
import com.fuck.iab._c
import com.fuck.iab.su
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The per-app menu, shared by the Home and Scripts lists: open the system app info, stop
 * the app, or open its Play Store page.
 */
fun showAppMenu(
    context: Context,
    anchor: View,
    packageName: String,
    installed: Boolean,
    scope: CoroutineScope
) {
    val popup = PopupMenu(context, anchor)
    popup.menuInflater.inflate(R.menu.menu_app_item, popup.menu)

    // Nothing to inspect or stop for an app that is gone, but its store page still exists.
    popup.menu.findItem(R.id.action_app_info).isEnabled = installed
    popup.menu.findItem(R.id.action_force_stop).isEnabled = installed

    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            R.id.action_app_info -> {
                openAppInfo(context, packageName)
                true
            }

            R.id.action_force_stop -> {
                stopApp(context, packageName, scope)
                true
            }

            R.id.action_play_store -> {
                openPlayStore(context, packageName)
                true
            }

            else -> false
        }
    }

    popup.show()
}

private fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
    }
}

/** Force stopping another app needs root, so the failure is reported instead of ignored. */
private fun stopApp(context: Context, packageName: String, scope: CoroutineScope) {
    val application = context.applicationContext

    scope.launch {
        val stopped = withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf(su(), _c(), "am force-stop $packageName")
                )
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }

        if (!stopped) {
            Toast.makeText(application, R.string.menu_force_stop_no_su, Toast.LENGTH_LONG).show()
        }
    }
}

private fun openPlayStore(context: Context, packageName: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        .setPackage(HookedProviders.PLAY_STORE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(market)
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }
}
