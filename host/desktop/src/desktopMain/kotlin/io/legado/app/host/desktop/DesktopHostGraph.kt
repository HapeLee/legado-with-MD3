package io.legado.app.host.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.DeviceId
import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.core.platform.RuleDataStorage
import io.legado.app.core.platform.Toaster
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.data.AppDatabase
import io.legado.app.data.repository.UploadRepository
import io.legado.app.data.rules.DictRuleRepositoryImpl
import io.legado.app.domain.rules.DictRuleRepository
import io.legado.app.feature.dict.rule.DictRuleViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * M1-4 的 desktop composition root：建库 + 组装 Koin graph。
 *
 * 与 `:app` 的 `appModule.kt` 是同一职责的两端（Android host / desktop host），差别只在
 * **平台实现**：Android 注入 `AndroidPlatformCapabilities.*` 与 `DirectLinkUploadRepository`，
 * 这里注入 `Desktop*`。共享层（Repository / ViewModel / Screen）两边共用同一份。
 *
 * 这正是 M1-4 要证明的事：同一 Feature 的 Koin graph 能在另一个 target 上被完整组装出来。
 *
 * @param databasePath Room 库文件路径。测试传临时目录，避免用例间互相污染。
 * @param dataDir host 数据目录；书源大变量落在其 `ruleData` 子目录下。
 */
fun desktopHostModule(
    databasePath: String,
    dataDir: String = defaultDesktopDataDir(),
): Module {
    // 平台原语必须先于任何书源规则求值就绪：entity 的实例方法
    // （BaseRssArticle/BaseBook/BookChapter 的 putBigVariable/getBigVariable）
    // 会同步读它（见 :core:data 的 RuleDataFileStore），未设置时显式抛异常。
    // 放在这里而不是单独的 init 函数，是为了「起 graph 即就绪」，没有第二处需要记得调用。
    RuleDataStorage.rootDir = "$dataDir/ruleData"
    // 设备标识同理必须先于书源规则求值就绪（`BaseSource` 的登录信息 AES 密钥来源、
    // `DatabaseMigrations` 的 readRecord 迁移都读它）。desktop 没有
    // `Settings.Secure.ANDROID_ID`，用数据目录派生一个跨启动稳定的值即可——
    // 它只需要「同一台机器 + 同一数据目录 ⇒ 同一值」。
    DeviceId.value = "desktop-" + dataDir.hashCode()
    return module {
        single<AppDatabase> { createDesktopDatabase(databasePath) }
        // M3-3：注入面是 `:domain:rules` 的端口，实现住 `:data:rules`。本模块没有单独
        // 绑定 DAO（host 只有这一处需要它），故直接从聚合根取——与 `:app` 的
        // `appDatabaseModule` + `appModule` 两段式绑定等价，只是这里合成一行。
        single<DictRuleRepository> { DictRuleRepositoryImpl(get<AppDatabase>().dictRuleDao) }

        // 平台能力：三个都是 desktop 侧实现，UploadRepository 显式不可用。
        single<Toaster> { DesktopToaster() }
        single<Clipboard> { DesktopClipboard(toaster = get()) }
        single<RuleTransferPlatform> { DesktopRuleTransferPlatform() }
        // 导入对象的按字段编辑：desktop 显式不支持（KDoc 说明为什么不顺手用 kotlinx
        // .serialization 复刻 Gson 版——那要另立行为等价证据，属独立切片）。
        single<ImportJsonEditor> { DesktopImportJsonEditor }
        single<UploadRepository> { DesktopUploadRepository }

        // ViewModel 用 factory（desktop 上没有 Android 的 `ViewModelStoreOwner` 语义，
        // 生命周期由调用方持有）。四个构造参数全部由上面的 graph 解析——如果哪个平台能力漏了注册，
        // 这里是第一个报错的地方，这正是「验证 Koin graph」的意义。
        factoryOf(::DictRuleViewModel)
    }
}

/**
 * desktop 默认数据目录：用户主目录下的 `.legado`。
 *
 * Android 侧对应 `Context.externalFiles`（应用专属外部目录，卸载即清），由
 * `:app` 的 `App.onCreate` 设置；desktop 没有等价的应用沙箱，落到用户目录。
 */
fun defaultDesktopDataDir(): String = "${System.getProperty("user.home")}/.legado"

/**
 * 建 desktop 的 Room 库。
 *
 * 写法照抄 `smoke/room-kmp-probe` 已验证的形态：`databaseBuilder(name = …)` +
 * `BundledSQLiteDriver`（不依赖系统 SQLite）+ IO 协程上下文。`AppDatabase` 的
 * `expect object AppDatabaseConstructor` 由 KSP 为 desktop target 生成 actual。
 */
fun createDesktopDatabase(path: String): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
