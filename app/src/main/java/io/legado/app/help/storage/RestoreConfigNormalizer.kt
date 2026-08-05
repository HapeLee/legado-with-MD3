package io.legado.app.help.storage

import io.legado.app.constant.PreferKey

/**
 * 把 config.xml 解析出的原始键值规整为可直接批量写入设置存储的 map（纯函数，便于 JVM 单测）：
 * - [PreferKey.webDavPassword]：AES 解密；解密失败时本地无密码则保留原文，否则丢弃；
 * - Double 转 Float（JSON 数字会被解析为 Double）；
 * - 旧版 [PreferKey.bookInfoBackgroundBlur] 拆分补写两个新背景键（备份中缺新键且未被忽略时）；
 * - 过滤忽略键与 null 值。
 */
internal fun normalizeConfigMap(
    map: Map<String, Any?>,
    keyIsNotIgnore: (String) -> Boolean,
    decryptWebDavPassword: (String) -> String?,
    hasLocalWebDavPassword: Boolean,
): Map<String, Any> {
    val finalMap = mutableMapOf<String, Any>()
    map.forEach { (key, value) ->
        if (!keyIsNotIgnore(key)) return@forEach
        when (key) {
            PreferKey.webDavPassword -> {
                val password = decryptWebDavPassword(value.toString())
                    ?: value.toString().takeIf { !hasLocalWebDavPassword }
                password?.let { finalMap[key] = it }
            }

            else -> when (value) {
                is Double -> finalMap[key] = value.toFloat()
                is Int, is Boolean, is Long, is Float, is String -> finalMap[key] = value
            }
        }
    }
    val legacyBookInfoBackground = map[PreferKey.bookInfoBackgroundBlur] as? String
    if (legacyBookInfoBackground != null && keyIsNotIgnore(PreferKey.bookInfoBackgroundBlur)) {
        listOf(
            PreferKey.bookInfoNetworkCoverBackground,
            PreferKey.bookInfoDefaultCoverBackground,
        ).forEach { newKey ->
            if (newKey !in map && keyIsNotIgnore(newKey)) {
                finalMap[newKey] = legacyBookInfoBackground
            }
        }
    }
    return finalMap
}
