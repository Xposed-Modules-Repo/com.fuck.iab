package com.fuck.iab

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import androidx.core.content.edit
import fg.ff
import org.json.JSONObject
import kotlin.collections.component1
import kotlin.collections.component2

class FakeBinder(
    val app: Context,
    val isGoogle: Boolean,
    val realBinder: IBinder,
    val log: (String) -> Unit
) : Binder(), IInterface {

    override fun asBinder(): IBinder {
        return this
    }

    override fun queryLocalInterface(descriptor: String): IInterface {
        return this
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
//        log("code = $code")

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
//                                                log("type = $type")

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
                            ff::class.java.name
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
                    data.readString() // package name
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

                9, 902 -> {
                    // Acknowledge Purchase
                    data.readString() // package name
                    val purchaseToken = data.readString()
                    data.readInt() // bundle
                    Bundle.CREATOR.createFromParcel(data)

                    val purchaseExists = app.getSharedPreferences(fuck_iab(), MODE_PRIVATE).contains(purchaseToken)

//                                                log("9, 902: purchase exists: $purchaseExists")

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
        } catch (e: Throwable) {
            return realBinder.transact(code, data, reply, flags)
        } finally {
            data.setDataPosition(oldPos)
        }
    }
}