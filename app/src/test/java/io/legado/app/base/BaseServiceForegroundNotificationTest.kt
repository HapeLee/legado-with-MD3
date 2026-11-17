package io.legado.app.base

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.help.config.AppConfigStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

private const val TEST_CHANNEL_ID = "test_channel"
private const val TEST_NOTIFICATION_ID = 1

/**
 * 回归保护：前台服务已在运行时刷新通知不得再次调用 startForeground。
 *
 * Android 15 起同一 UID 的 dataSync / mediaProcessing 共享「任意 24 小时累计 6 小时」运行配额，
 * 配额耗尽后 startForeground 会抛 ForegroundServiceStartNotAllowedException；WebService 的通知
 * 刷新发生在 ConnectivityManager 的回调线程上，未捕获会直接终止进程。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BaseServiceForegroundNotificationTest {

    private lateinit var application: Application
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        AppConfigStore.init(application)
        // 跳过 BaseService 首次启动时的通知/后台权限申请
        AppConfigStore.putBoolean(LocalPreferencesKeys.PERMISSION_CHECKED.name, true)
        notificationManager = application.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(TEST_CHANNEL_ID, "test", NotificationManager.IMPORTANCE_LOW)
        )
        stopKoin()
        startKoin {
            modules(module {
                single { SettingsRepository() }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun refreshWhileForegroundOnlyUpdatesNotificationContent() {
        val initial = createNotification("starting")
        RefreshableService.initialNotification = initial
        val service = Robolectric.buildService(RefreshableService::class.java).create().get()

        assertSame(initial, shadowOf(service).lastForegroundNotification)

        service.publish(createNotification("192.168.1.10:1122"))

        // 核心断言：已在前台时刷新通知不会再次调用 startForeground
        assertSame(initial, shadowOf(service).lastForegroundNotification)
        // 但通知内容确实被刷新了
        val posted = shadowOf(notificationManager).getNotification(TEST_NOTIFICATION_ID)
        assertEquals(
            "192.168.1.10:1122",
            posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun rejectedForegroundStartStopsServiceWithoutThrowing() {
        val service = Robolectric.buildService(RejectingService::class.java).create().get()

        assertTrue(service.foregroundRejected)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    private fun createNotification(text: CharSequence): Notification =
        Notification.Builder(application, TEST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("web service")
            .setContentText(text)
            .build()

    class RefreshableService : BaseService() {

        override fun startForegroundNotification() {
            postForegroundNotification(TEST_NOTIFICATION_ID, initialNotification)
        }

        fun publish(notification: Notification) {
            postForegroundNotification(TEST_NOTIFICATION_ID, notification)
        }

        companion object {
            lateinit var initialNotification: Notification
        }
    }

    class RejectingService : BaseService() {

        val foregroundRejected: Boolean get() = foregroundStartRejected

        override fun startForegroundNotification() {
            // 模拟系统拒绝进入前台：dataSync 24 小时运行配额已耗尽
            throw IllegalStateException(
                "Time limit already exhausted for foreground service type dataSync"
            )
        }
    }
}
