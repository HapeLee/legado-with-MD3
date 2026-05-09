package io.legado.app.utils

import com.jayway.jsonpath.*

val jsonPath: ParseContext by lazy {
    JsonPath.using(
        Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build()
    )
}

fun ReadContext.readString(path: String): String? = this.read(path, String::class.java)

fun ReadContext.readBool(path: String): Boolean? = this.read(path, Boolean::class.java)

fun ReadContext.readInt(path: String): Int? = this.read(path, Int::class.java)

fun ReadContext.readLong(path: String): Long? = this.read(path, Long::class.java)

fun com.google.gson.JsonObject.getString(name: String, default: String? = null): String? {
    val elem = get(name)
    return when {
        elem == null || elem.isJsonNull -> default
        elem.isJsonPrimitive -> {
            val prim = elem.asJsonPrimitive
            if (prim.isString || prim.isNumber) prim.asString else default
        }
        else -> default
    }
}

fun com.google.gson.JsonObject.getInt(name: String, default: Int = 0): Int {
    val elem = get(name)
    return when {
        elem == null || elem.isJsonNull -> default
        elem.isJsonPrimitive -> {
            val prim = elem.asJsonPrimitive
            if (prim.isNumber) prim.asNumber.toInt() else default
        }
        else -> default
    }
}

fun com.google.gson.JsonObject.getFloat(name: String, default: Float = 0f): Float {
    val elem = get(name)
    return when {
        elem == null || elem.isJsonNull -> default
        elem.isJsonPrimitive -> {
            val prim = elem.asJsonPrimitive
            if (prim.isNumber) prim.asNumber.toFloat() else default
        }
        else -> default
    }
}

fun com.google.gson.JsonObject.getBoolean(name: String, default: Boolean = false): Boolean {
    val elem = get(name)
    return when {
        elem == null || elem.isJsonNull -> default
        elem.isJsonPrimitive -> {
            val prim = elem.asJsonPrimitive
            if (prim.isBoolean) prim.asBoolean else default
        }
        else -> default
    }
}

