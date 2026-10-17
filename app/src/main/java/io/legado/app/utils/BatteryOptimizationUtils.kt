package io.legado.app.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import io.legado.app.R
import splitties.init.appCtx

object BatteryOptimizationUtils {

    /**
     * 判断当前应用是否已列入原生电池优化白名单 (Unrestricted / Ignoring Battery Optimizations)
     */
    fun isIgnoringBatteryOptimizations(context: Context = appCtx): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }.getOrDefault(false)
    }

    /**
     * 获取针对当前手机厂商/品牌的后台配置操作引导文本
     */
    fun getBatteryOptimizationGuideText(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            manufacturer.contains("realme") || brand.contains("realme") ||
                    manufacturer.contains("oppo") || brand.contains("oppo") ||
                    manufacturer.contains("oneplus") || brand.contains("oneplus") -> {
                "realme / OPPO / 一加 设备指引：\n" +
                        "1. 进入【耗电管理】-> 开启【允许完全后台行为】\n" +
                        "2. 开启【允许应用自启动】与【允许应用关联启动】\n" +
                        "3. 如有【应用速冻】请关闭，以防锁屏或切后台被中断。"
            }

            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                    manufacturer.contains("redmi") || brand.contains("redmi") -> {
                "小米 / 红米 (MIUI / HyperOS) 设备指引：\n" +
                        "1. 进入【省电策略 / 后台配置】，选择【无限制】\n" +
                        "2. 在应用管理或安全中心开启【自启动】与【应用锁】。"
            }

            manufacturer.contains("vivo") || brand.contains("vivo") ||
                    manufacturer.contains("iqoo") || brand.contains("iqoo") -> {
                "vivo / iQOO (OriginOS) 设备指引：\n" +
                        "1. 进入【后台耗电管理】，开启【允许后台高耗电】\n" +
                        "2. 在权限管理中允许【自启动】与【后台弹出界面】。"
            }

            manufacturer.contains("huawei") || brand.contains("huawei") ||
                    manufacturer.contains("honor") || brand.contains("honor") -> {
                "华为 / 荣耀 (HarmonyOS / MagicOS) 设备指引：\n" +
                        "1. 进入【启动管理】，关闭【自动管理】\n" +
                        "2. 手动开启【允许自启动】、【允许关联启动】和【允许后台活动】。"
            }

            manufacturer.contains("samsung") || brand.contains("samsung") -> {
                "三星 (One UI) 设备指引：\n" +
                        "1. 进入【电池】选项，设置为【不受限制】\n" +
                        "2. 在电池后台限制中，将本应用加入【从不休眠应用程序】。"
            }

            manufacturer.contains("meizu") || brand.contains("meizu") -> {
                "魅族 (Flyme) 设备指引：\n" +
                        "进入【待机耗电管理】，设置为【保持后台运行】并允许自启动。"
            }

            else -> {
                "后台保活设置指引：\n" +
                        "1. 在跳转的设置页面中，进入【电池 / 耗电管理】\n" +
                        "2. 将后台行为设为【无限制】或【不优化】\n" +
                        "3. 允许应用【后台活动】与【自启动】。"
            }
        }
    }

    /**
     * 按照优先级多级降级跳转到电池优化/后台运行管理页面：
     * Level 1: AOSP 系统级申请加入白名单弹窗 (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
     * Level 2: 厂商定制的电池管理 / 耗电管理 / 自启动页面 (ColorOS / MIUI / OriginOS 等)
     * Level 3: AOSP 系统电池优化应用列表 (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
     * Level 4: 终极保底 —— 应用详情页 (ACTION_APPLICATION_DETAILS_SETTINGS，100% 可达)
     */
    fun openBatteryOptimizationSettings(
        context: Context,
        launcher: ActivityResultLauncher<Intent>? = null,
    ) {
        val packageName = context.packageName

        // Level 1: 如果尚未加入 AOSP 白名单，优先尝试拉起原生白名单申请对话框
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations(
                context
            )
        ) {
            val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            if (launchIntent(context, directIntent, launcher)) {
                return
            }
        }

        // Level 2: 尝试根据品牌拉起厂商定制的耗电管理 / 自启动页面
        val oemIntents = getOemBatteryIntents(context, packageName)
        for (intent in oemIntents) {
            if (launchIntent(context, intent, launcher)) {
                return
            }
        }

        // Level 3: 尝试拉起 AOSP 原生电池优化列表
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val aospSettingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (launchIntent(context, aospSettingsIntent, launcher)) {
                return
            }
        }

        // Level 4: 终极保底 —— 应用详情页（所有 Android 12~15 手机均有，且内含耗电管理/电池子页）
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        if (!launchIntent(context, appDetailsIntent, launcher)) {
            context.toastOnUi(R.string.tip_cannot_jump_setting_page)
        }
    }

    private fun getOemBatteryIntents(context: Context, packageName: String): List<Intent> {
        val list = mutableListOf<Intent>()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        // realme / OPPO / OnePlus (ColorOS / realme UI)
        if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains(
                "oneplus"
            ) ||
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")
        ) {
            list.add(Intent().apply {
                component = ComponentName(
                    "com.oplus.battery",
                    "com.oplus.powermanager.fuelgaurd.ui.PowerUsageModelActivity"
                )
                putExtra("package_name", packageName)
            })
            list.add(Intent("oplus.intent.action.APP_BATTERY_DETAIL").apply {
                putExtra("package_name", packageName)
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaurd.ui.PowerUsageModelActivity"
                )
                putExtra("package_name", packageName)
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.coloros.battery",
                    "com.coloros.powermanager.fuelgaurd.ui.PowerUsageModelActivity"
                )
                putExtra("package_name", packageName)
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            })
        }

        // 小米 / 红米 (MIUI / HyperOS)
        if (manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi")) {
            list.add(Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            })
        }

        // vivo / iQOO (OriginOS / FuntouchOS)
        if (manufacturer.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo")) {
            list.add(Intent().apply {
                component = ComponentName(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                )
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.iqoo.powersaving",
                    "com.iqoo.powersaving.PowerSavingManagerActivity"
                )
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"
                )
            })
        }

        // 华为 / 荣耀 (HarmonyOS / EMUI / MagicOS)
        if (manufacturer.contains("huawei") || manufacturer.contains("honor") || brand.contains("huawei") || brand.contains(
                "honor"
            )
        ) {
            list.add(Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            })
        }

        // 三星 (One UI)
        if (manufacturer.contains("samsung") || brand.contains("samsung")) {
            list.add(Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            })
            list.add(Intent().apply {
                component = ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            })
        }

        // 魅族 (Flyme)
        if (manufacturer.contains("meizu") || brand.contains("meizu")) {
            list.add(Intent().apply {
                component =
                    ComponentName("com.meizu.safe", "com.meizu.safe.powerui.PowerAppDispatchModel")
            })
            list.add(Intent().apply {
                component =
                    ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")
            })
        }

        return list
    }

    private fun launchIntent(
        context: Context,
        intent: Intent,
        launcher: ActivityResultLauncher<Intent>?,
    ): Boolean {
        return runCatching {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launcher != null) {
                launcher.launch(intent)
            } else {
                context.startActivity(intent)
            }
            true
        }.getOrDefault(false)
    }
}
