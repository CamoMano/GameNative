package app.gamenative.di

import android.content.Context
import androidx.room.Room
import app.gamenative.db.DATABASE_NAME
import app.gamenative.db.PluviaDatabase
import app.gamenative.db.dao.AppChangeNumbersDao
import app.gamenative.db.dao.AppFileChangeListsDao
import app.gamenative.db.dao.AppInfoDao
import app.gamenative.db.dao.EmoticonDao
import app.gamenative.db.dao.FriendMessagesDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.db.dao.SteamFriendDao
import app.gamenative.db.dao.SteamLicenseDao
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.annotation.LooperMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(manifest = Config.NONE)
class DatabaseModuleTest {

    private val module = DatabaseModule()

    @RelaxedMockK
    lateinit var mockContext: Context

    @RelaxedMockK
    lateinit var mockDatabase: PluviaDatabase

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        mockkStatic(Room::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Room::class)
    }

    @Test
    fun `provideDatabase builds Room database with fallback to destructive migration`() {
        val roomBuilder = mockk<RoomDatabaseBuilderStub>(relaxed = true)
        val expectedDatabase = mockk<PluviaDatabase>()

        every { Room.databaseBuilder(mockContext, PluviaDatabase::class.java, DATABASE_NAME) } returns roomBuilder
        every { roomBuilder.fallbackToDestructiveMigration() } returns roomBuilder
        every { roomBuilder.buildActual() } returns expectedDatabase

        // When
        val result = module.provideDatabase(mockContext)

        // Then
        assertEquals(expectedDatabase, result)
        verify { Room.databaseBuilder(mockContext, PluviaDatabase::class.java, DATABASE_NAME) }
        verify { roomBuilder.fallbackToDestructiveMigration() }
        verify { roomBuilder.buildActual() }
    }

    @Test
    fun `provideDatabase uses application context from RuntimeEnvironment`() {
        val applicationContext = RuntimeEnvironment.getApplication()
        val roomBuilder = mockk<RoomDatabaseBuilderStub>(relaxed = true)
        val expectedDatabase = mockk<PluviaDatabase>()

        every { Room.databaseBuilder(applicationContext, PluviaDatabase::class.java, DATABASE_NAME) } returns roomBuilder
        every { roomBuilder.fallbackToDestructiveMigration() } returns roomBuilder
        every { roomBuilder.buildActual() } returns expectedDatabase

        val result = module.provideDatabase(applicationContext)

        assertEquals(expectedDatabase, result)
        verify { Room.databaseBuilder(applicationContext, PluviaDatabase::class.java, DATABASE_NAME) }
    }

    @Test
    fun `provideDatabase handles reused builder chain`() {
        val roomBuilder = mockk<RoomDatabaseBuilderStub>()
        val expectedDatabase = mockk<PluviaDatabase>()

        every { Room.databaseBuilder(any(), PluviaDatabase::class.java, any()) } returns roomBuilder
        every { roomBuilder.fallbackToDestructiveMigration() } returns roomBuilder
        every { roomBuilder.buildActual() } returns expectedDatabase

        val result = module.provideDatabase(mockContext)

        assertEquals(expectedDatabase, result)
    }

    @Test(expected = IllegalStateException::class)
    fun `provideDatabase propagates builder failures`() {
        val roomBuilder = mockk<RoomDatabaseBuilderStub>()

        every { Room.databaseBuilder(any(), PluviaDatabase::class.java, any()) } returns roomBuilder
        every { roomBuilder.fallbackToDestructiveMigration() } returns roomBuilder
        every { roomBuilder.buildActual() } throws IllegalStateException("build failed")

        module.provideDatabase(mockContext)
    }

    @Test
    fun `provideSteamLicenseDao delegates to database`() {
        val dao = mockk<SteamLicenseDao>()
        every { mockDatabase.steamLicenseDao() } returns dao

        assertEquals(dao, module.provideSteamLicenseDao(mockDatabase))
        verify { mockDatabase.steamLicenseDao() }
    }

    @Test
    fun `provideSteamAppDao delegates to database`() {
        val dao = mockk<SteamAppDao>()
        every { mockDatabase.steamAppDao() } returns dao

        assertEquals(dao, module.provideSteamAppDao(mockDatabase))
        verify { mockDatabase.steamAppDao() }
    }

    @Test
    fun `provideSteamFriendDao delegates to database`() {
        val dao = mockk<SteamFriendDao>()
        every { mockDatabase.steamFriendDao() } returns dao

        assertEquals(dao, module.provideSteamFriendDao(mockDatabase))
        verify { mockDatabase.steamFriendDao() }
    }

    @Test
    fun `provideAppChangeNumbersDao delegates to database`() {
        val dao = mockk<AppChangeNumbersDao>()
        every { mockDatabase.appChangeNumbersDao() } returns dao

        assertEquals(dao, module.provideAppChangeNumbersDao(mockDatabase))
        verify { mockDatabase.appChangeNumbersDao() }
    }

    @Test
    fun `provideAppFileChangeListsDao delegates to database`() {
        val dao = mockk<AppFileChangeListsDao>()
        every { mockDatabase.appFileChangeListsDao() } returns dao

        assertEquals(dao, module.provideAppFileChangeListsDao(mockDatabase))
        verify { mockDatabase.appFileChangeListsDao() }
    }

    @Test
    fun `provideFriendMessagesDao delegates to database`() {
        val dao = mockk<FriendMessagesDao>()
        every { mockDatabase.friendMessagesDao() } returns dao

        assertEquals(dao, module.provideFriendMessagesDao(mockDatabase))
        verify { mockDatabase.friendMessagesDao() }
    }

    @Test
    fun `provideEmoticonDao delegates to database`() {
        val dao = mockk<EmoticonDao>()
        every { mockDatabase.emoticonDao() } returns dao

        assertEquals(dao, module.provideEmoticonDao(mockDatabase))
        verify { mockDatabase.emoticonDao() }
    }

    @Test
    fun `provideAppInfoDao delegates to database`() {
        val dao = mockk<AppInfoDao>()
        every { mockDatabase.appInfoDao() } returns dao

        assertEquals(dao, module.provideAppInfoDao(mockDatabase))
        verify { mockDatabase.appInfoDao() }
    }

    @Test
    fun `annotation metadata is preserved on provider methods`() {
        val clazz = DatabaseModule::class.java

        assertTrue(clazz.isAnnotationPresent(Module::class.java))
        assertTrue(clazz.isAnnotationPresent(InstallIn::class.java))

        val provideDatabase = clazz.getDeclaredMethod("provideDatabase", Context::class.java)
        assertTrue(provideDatabase.isAnnotationPresent(Provides::class.java))
        assertTrue(provideDatabase.isAnnotationPresent(Singleton::class.java))

        val daoMethods = listOf(
            "provideSteamLicenseDao",
            "provideSteamAppDao",
            "provideSteamFriendDao",
            "provideAppChangeNumbersDao",
            "provideAppFileChangeListsDao",
            "provideFriendMessagesDao",
            "provideEmoticonDao",
            "provideAppInfoDao"
        )

        daoMethods.forEach { name ->
            val method = clazz.getDeclaredMethod(name, PluviaDatabase::class.java)
            assertTrue("$name missing @Provides", method.isAnnotationPresent(Provides::class.java))
            assertTrue("$name missing @Singleton", method.isAnnotationPresent(Singleton::class.java))
        }
    }

    private fun <T : Any> Room.databaseBuilder(
        context: Context,
        klass: Class<T>,
        name: String
    ): RoomDatabaseBuilderStub where T : PluviaDatabase {
        throw NotImplementedError("Stub for static replacement")
    }

    private interface RoomDatabaseBuilderStub {
        fun fallbackToDestructiveMigration(): RoomDatabaseBuilderStub
        fun buildActual(): PluviaDatabase
    }
}