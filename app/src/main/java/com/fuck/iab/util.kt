package com.fuck.iab

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.random.Random

fun randomOrderId(): String {
    fun digits(length: Int): String =
        buildString(length) {
            repeat(length) {
                append(Random.nextInt(0, 10))
            }
        }

    return "${GPA_()}${digits(4)}-${digits(4)}-${digits(4)}-${digits(4)}-${digits(5)}"
}

fun randomPurchaseToken(): String {
    return (1..500).map { ('a'..'z').random() }.joinToString("")
}

private fun randomToken(): String {
    val bytes = ByteArray(64)
    SecureRandom().nextBytes(bytes)

    return Base64.encodeToString(
        bytes,
        Base64.NO_WRAP
    )
}

fun randomSignature(): String {
    val bytes = ByteArray(256)
    SecureRandom().nextBytes(bytes)

    return Base64.encodeToString(
        bytes,
        Base64.NO_WRAP
    )
}

fun createDetailsList(bundle: Bundle, type: String): ArrayList<String> {
    val ids = bundle.getStringArrayList(ITEM_ID_LIST())
        ?: return arrayListOf()

    val result = ArrayList<String>()

    for (id in ids) {
        val json = JSONObject().apply {
            put(productId(), id)
            put(type(), type)
            put(price(), zero_dollars())
            put(title(), id)
            put(name(), id)

            put(
                localizedIn(),
                JSONArray().apply {
                    put(de_DE())
                    put(en_AU())
                    put(en_CA())
                    put(en_GB())
                    put(en_IN())
                    put(en_SG())
                    put(en_US())
                    put(en_ZA())
                    put(fr_CA())
                    put(fr_FR())
                    put(ja_JP())
                    put(ko_KR())
                    put(zh_CN())
                    put(zh_HK())
                    put(zh_TW())
                }
            )

            put(description(), id)
            put(price_amount_micros(), 0)
            put(price_currency_code(), USD())
            put(skuDetailsToken(), randomToken())

            if (type == subs()) {
                put(
                    subscriptionOfferDetails(),
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put(offerIdToken(), randomToken())
                                put(basePlanId(), id)
                                put(offerId(), id)

                                put(
                                    pricingPhases(),
                                    JSONArray().apply {
                                        put(
                                            JSONObject().apply {
                                                put(priceAmountMicros(), 0)
                                                put(priceCurrencyCode(), USD())
                                                put(formattedPrice(), zero_dollars())
                                                put(billingPeriod(), P1M())
                                                put(recurrenceMode(), 1)
                                            }
                                        )
                                    }
                                )

                                put(
                                    offerTags(),
                                    JSONArray()
                                )
                            }
                        )
                    }
                )
            } else {
                put(
                    oneTimePurchaseOfferDetails(),
                    JSONObject().apply {
                        put(offerIdToken(), randomToken())
                        put(priceAmountMicros(), 0)
                        put(priceCurrencyCode(), USD())
                        put(formattedPrice(), zero_dollars())
                    }
                )
            }
        }

        result.add(json.toString())
    }

    return result
}

fun removeFromPrefs(app: Context, purchaseToken: String?) {
    val prefs = app.getSharedPreferences(fuck_iab(), MODE_PRIVATE)
    if (prefs.contains(purchaseToken)) {
        prefs.edit().remove(purchaseToken).apply()
    }
}

//fun dumpBundle(bundle: Bundle?, prefix: String = "") {
//    if (bundle == null) {
//        Log.d(TAG, "${prefix}Bundle is null")
//        return
//    }
//
//    for (key in bundle.keySet()) {
//        val value = bundle.get(key)
//
//        Log.d(TAG, "$prefix$key = $value (${value?.javaClass?.name})")
//
//        if (value is Bundle) {
//            dumpBundle(value, "$prefix  ")
//        }
//    }
//}

//fun getMethodAsString(method: Method): String {
//    val returnType = method.returnType.name
//    val className = method.declaringClass.name
//    val methodName = method.name
//
//    val params = method.parameterTypes.joinToString(", ") {
//        it.name
//    }
//
//    return "$returnType $className.$methodName($params)"
//}

//    fun findInterfaces(clazz: Class<*>): List<Class<*>> {
//        val result = mutableListOf<Class<*>>()
//
//        var current: Class<*>? = clazz
//
//        while (current != null) {
//            result += current.interfaces
//            current = current.superclass
//        }
//
//        return result.distinct()
//    }

//                for (iface in findInterfaces(implClass)) {
//                    log("INTERFACE = ${iface.name}")
//
//                    for (method in iface.declaredMethods) {
//                        log(
//                            "${method.name} : " +
//                                    method.parameterTypes.joinToString { it.name }
//                        )
//                    }
//                }

//    private fun logMethod(method: Method) {
//        val clazz = method.declaringClass
//
//        Log.d(TAG, "========== METHOD ==========")
//        Log.d(TAG, "Class: ${clazz.name}")
//        Log.d(TAG, "Method: ${method.name}")
//        Log.d(TAG, "Return type: ${method.returnType.name}")
//
//        val params = method.parameterTypes
//
//        Log.d(TAG, "Parameters count: ${params.size}")
//
//        params.forEachIndexed { index, param ->
//            Log.d(TAG, "Param[$index]: ${param.name}")
//
//            if (!param.isPrimitive) {
//                logClassHierarchy(param)
//            }
//        }
//    }

//    private fun logClassHierarchy(clazz: Class<*>) {
//        var current: Class<*>? = clazz
//
//        while (current != null) {
//
//            Log.d(TAG, "==============================")
//            Log.d(TAG, "CLASS: ${current.name}")
//
//            Log.d(TAG, "FIELDS:")
//            current.declaredFields.forEach { field ->
//                Log.d(
//                    TAG,
//                    "  ${Modifier.toString(field.modifiers)} " +
//                            "${field.type.name} ${field.name}"
//                )
//            }
//
//            Log.d(TAG, "METHODS:")
//            current.declaredMethods.forEach { method ->
//                Log.d(TAG, "  ${method.toGenericString()}")
//            }
//
////            current = current.superclass
//            current = null
//        }
//    }
//
//    fun logDeclaredFields(obj: Any?) {
//        if (obj == null) {
//            Log.d(TAG, "Object = null")
//            return
//        }
//
//        val clazz = obj.javaClass
//
//        Log.d(TAG, "========== ${clazz.name} ==========")
//
//        clazz.declaredFields.forEach { field ->
//            try {
//                field.isAccessible = true
//                val value = field.get(obj)
//
//                Log.d(
//                    TAG,
//                    "${field.name}: ${field.type.name} = $value"
//                )
//            } catch (e: Throwable) {
//                Log.d(
//                    TAG,
//                    "${field.name}: <ERROR: ${e.javaClass.simpleName}>"
//                )
//            }
//        }
//
//        Log.d(TAG, "================================")
//    }