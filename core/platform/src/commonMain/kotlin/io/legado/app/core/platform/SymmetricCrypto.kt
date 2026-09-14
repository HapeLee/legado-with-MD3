package io.legado.app.core.platform

/**
 * 对称加解密原语（M2-3：`SymmetricCryptoProvider` 退役后由它顶上）。
 *
 * 消费方是 Room entity 的实例方法——`BaseSource.getLoginInfo` / `putLoginInfo` 用它做
 * 「用户信息 AES 加密后落 `caches` 表」。而 entity 由 Room 构造、不经过 DI；这些方法本身又是
 * 书源 JS 的兼容面（`AnalyzeRule` 里 `bindings["source"] = this`，脚本直接调
 * `source.getLoginInfo()`），签名与存在性都不能改。这类「由框架构造、拿不到注入」的消费方
 * 要访问平台能力只有两条路：全局 service locator，或平台原语。本片选后者——模式同
 * [RuleDataStorage]（场景完全相同）、[JsonCodec] 与 [JvmFileSystem]/[JcaDigest]。
 *
 * **为什么满足「原语」判据**：JCA 在 android 与 desktop 两个 JVM target 上语义恒等，
 * 且除 `:app` 的 `SymmetricCryptoAndroid` 外没有第三个实现 ⇒ 按 AGENTS.md
 * 「无第三实现且语义恒等 ⇒ `expect/actual` 原语」。(原先判成「接口 + 注入」的理由是
 * 「实现依赖 `:app` 工具」；移植时发现那些工具只用到 `kotlin.io.encoding.Base64` 与
 * `MessageDigest`，前者是 stdlib、后者是 JVM 自带，本模块自己就能提供，
 * 见 [CryptoCodecs]。)
 *
 * **兼容面（这是本原语唯一真正难的部分）**：实现必须与迁移前的
 * `SymmetricCryptoAndroid(algorithm, key)` **逐字节一致**，否则既有用户已经落库的
 * `userInfo_<sourceKey>` 会解不开（等于所有人被登出）。锁定下来的约定：
 * - `algorithm` 不含 `/` 时补 `/ECB/PKCS5Padding`；密钥算法名取 `/` 前的那一段；
 * - 密钥原样使用（不截断，理由见下）；明文 UTF-8；密文标准 Base64（带 padding）；
 * - 解密时**先判**「整串都是 `0-9a-fA-F` 且长度为偶数 ⇒ 按 hex 解」，否则 Base64（先剥空白，
 *   标准表失败回落 URL-safe 表）——顺序不能反。
 *
 * **刻意不移植的三条分支**（都按 AGENTS.md「无调用方抽象」收敛，唯一消费方在 AES 路径上）：
 * 1. `setIv(...)` 与 `iv` 字段——`BaseSource` 从不设 IV，故**只支持不带 IV 的 transformation**
 *    （ECB 系）；传 CBC 等需要 IV 的模式不保证可解（JCA 侧会自己随机取 IV）。
 * 2. `key` 传 null 时 `KeyGenerator` 随机生成密钥——消费方永远传
 *    `DeviceId.value.encodeToByteArray(0, 16)`，这里要求非空密钥。
 * 3. DES/DESede 超过 8/24 字节时截断密钥——无任何调用方使用非 AES 算法，留着就是没测试覆盖的
 *    死分支；若将来真要用 DESede，应当连同测试一起补回，而不是靠「照抄」蒙对。
 *
 * 因此本原语是「**AES + 其它不带 IV 的 transformation**」的忠实端口，而不是
 * `SymmetricCryptoAndroid` 的全量复刻。
 */
expect object SymmetricCrypto {

    /**
     * 用 `algorithm` + `key` 加密 `data`（UTF-8）并做标准 Base64 编码。
     *
     * 对齐 `SymmetricCryptoAndroid(algorithm, key).encryptBase64(data: String)`。
     */
    fun encryptBase64(algorithm: String, key: ByteArray, data: String): String

    /**
     * 用 `algorithm` + `key` 解密 `data`（Base64，或整串为 hex 时按 hex）为 UTF-8 明文。
     *
     * 对齐 `SymmetricCryptoAndroid(algorithm, key).decryptStr(data: String)`。
     *
     * 密钥不对或密文被破坏时**抛异常**（JCA 的 padding 校验失败），不做静默返回空串——
     * 调用方 `BaseSource.getLoginInfo` 已经把这一层包在 try/catch 里。
     */
    fun decryptStr(algorithm: String, key: ByteArray, data: String): String
}
