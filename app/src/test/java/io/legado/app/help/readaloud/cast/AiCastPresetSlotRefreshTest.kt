package io.legado.app.help.readaloud.cast

import android.app.Application
import android.content.Context
import io.legado.app.data.entities.AiPromptPreset
import io.legado.app.domain.model.AiTaskType
import io.legado.app.support.InMemoryAppDatabaseFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 出厂默认文本升级后，播种过的安装要把「逐字未改的旧行」一次性刷成新默认。
 *
 * 固定行（判断规则 / 输出契约）以库里的文本为准：不刷的话，升级用户收到的 payload 里
 * 多了 aliases/last/contextAfter，生效的规则却还是上一代——本次升级要修的判断问题原样留着。
 * 用户改过一个字的行必须原样保留。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiCastPresetSlotRefreshTest {
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private val db get() = fixture.database

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        app.getSharedPreferences("ai_cast", Context.MODE_PRIVATE).edit().clear().commit()
        fixture = InMemoryAppDatabaseFixture(app)
    }

    @After fun tearDown() {
        fixture.close()
    }

    private fun seedSlot(id: String, instruction: String) {
        db.aiPromptPresetDao.upsertAllSync(
            listOf(
                AiPromptPreset(
                    id = id,
                    taskType = AiTaskType.CAST_ASSIGN,
                    name = "旧行",
                    instruction = instruction,
                    builtIn = true,
                ),
            ),
        )
    }

    @Test fun `逐字未改的旧默认规则行被刷成当前默认`() {
        seedSlot(AiCastPresetStore.RULE_SLOT_ID, AiCastPresetStore.LEGACY_SLOT_JUDGMENT_RULES)

        AiCastPresetStore.loadPresets()

        assertEquals(
            AiCastPresetStore.DEFAULT_JUDGMENT_RULES,
            db.aiPromptPresetDao.getSync(AiCastPresetStore.RULE_SLOT_ID)?.instruction,
        )
    }

    @Test fun `换标注引入的空白差异不影响认旧行`() {
        // ensureSlot 换标注后行首标注末尾会多一个换行：正文逐字未改就该认得
        val relabelled = AiCastPresetStore.LEGACY_SLOT_JUDGMENT_RULES
            .replace("以分配要求为准)：", "以分配要求为准)：\n")
        seedSlot(AiCastPresetStore.RULE_SLOT_ID, relabelled)

        AiCastPresetStore.loadPresets()

        assertEquals(
            AiCastPresetStore.DEFAULT_JUDGMENT_RULES,
            db.aiPromptPresetDao.getSync(AiCastPresetStore.RULE_SLOT_ID)?.instruction,
        )
    }

    @Test fun `用户改过的行一个字都不动`() {
        val edited = AiCastPresetStore.LEGACY_SLOT_JUDGMENT_RULES + "\n用户补充：本书人名多为三字。"
        seedSlot(AiCastPresetStore.RULE_SLOT_ID, edited)

        AiCastPresetStore.loadPresets()

        assertEquals(edited, db.aiPromptPresetDao.getSync(AiCastPresetStore.RULE_SLOT_ID)?.instruction)
    }

    @Test fun `契约行同样刷新且幂等`() {
        seedSlot(AiCastPresetStore.FORMAT_CONTRACT_ID, AiCastPresetStore.LEGACY_SLOT_CONTRACT)

        AiCastPresetStore.loadPresets()
        assertEquals(
            AiCastPresetStore.DEFAULT_CONTRACT,
            db.aiPromptPresetDao.getSync(AiCastPresetStore.FORMAT_CONTRACT_ID)?.instruction,
        )

        // 第二次不再改写（版本 gate），刷完的当前默认原样保留
        AiCastPresetStore.loadPresets()
        assertEquals(
            AiCastPresetStore.DEFAULT_CONTRACT,
            db.aiPromptPresetDao.getSync(AiCastPresetStore.FORMAT_CONTRACT_ID)?.instruction,
        )
    }
}
