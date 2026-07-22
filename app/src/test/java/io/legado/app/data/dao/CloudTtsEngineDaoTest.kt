package io.legado.app.data.dao

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.CloudTtsEngineEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CloudTtsEngineDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: CloudTtsEngineDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.cloudTtsEngineDao
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `旧凭据迁移不会恢复已删除的引擎`() = runBlocking {
        val engine = CloudTtsEngineEntity(
            id = "deleted-engine",
            name = "Deleted engine",
            provider = "openai_speech",
            apiKey = "plain-api-key",
            secretKey = "plain-secret-key",
        )
        dao.upsert(engine)
        dao.delete(engine)

        dao.updateCredentials(
            id = engine.id,
            oldApiKey = engine.apiKey,
            oldSecretKey = engine.secretKey,
            newApiKey = "enc:v1:api-key",
            newSecretKey = "enc:v1:secret-key",
        )

        assertNull(dao.get(engine.id))
    }
}
