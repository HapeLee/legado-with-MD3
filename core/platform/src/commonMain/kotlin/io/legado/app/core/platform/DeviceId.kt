package io.legado.app.core.platform

/**
 * 设备标识配置项（M2-4b）。
 *
 * 用途有两个，都要求**跨进程重启稳定**：
 *  1. 书源登录信息 AES 的密钥来源（`BaseSource` 里
 *     `androidId().encodeToByteArray(0, 16)`，见 [SymmetricCrypto] 的兼容面说明）；
 *  2. `DatabaseMigrations` 里 `readRecord` 旧表迁移的 `androidId` 列取值。
 *
 * **为什么是「host 写入的配置项」而不是原语/契约**：值本身来自 Android 的
 * `Settings.Secure.ANDROID_ID`（要 `ContentResolver`），而共享层拿不到 `Context`；
 * 但共享层需要的只是一个**字符串**，不是会变的服务——这正是本仓对「原语要配置不要服务」
 * 的判据（同 [RuleDataStorage.rootDir]、[LogSettings]）。所以它不需要 expect/actual，
 * 一个普通 object 即可。
 *
 * 宿主契约（`:app`）：`App.onCreate` 里在 `super.onCreate()` 之后、任何书源规则求值之前
 * 设置一次（与 `RuleDataStorage.rootDir` 同一处）。
 *
 * @throws IllegalStateException 未设置时读取。
 */
object DeviceId {

    @Volatile
    private var configured: String? = null

    var value: String
        get() = configured ?: error(
            "DeviceId.value 未设置：请在 host 的 composition root 设置设备标识" +
                "（Android 取 Settings.Secure.ANDROID_ID）。"
        )
        set(newValue) {
            configured = newValue
        }
}
