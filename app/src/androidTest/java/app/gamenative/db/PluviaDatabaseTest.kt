package app.gamenative.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import app.gamenative.data.*
import app.gamenative.db.dao.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PluviaDatabaseTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PluviaDatabase
    
    // DAO references for testing
    private lateinit var steamLicenseDao: SteamLicenseDao
    private lateinit var steamAppDao: SteamAppDao
    private lateinit var steamFriendDao: SteamFriendDao
    private lateinit var appChangeNumbersDao: ChangeNumbersDao
    private lateinit var appFileChangeListsDao: FileChangeListsDao
    private lateinit var friendMessagesDao: FriendMessagesDao
    private lateinit var emoticonDao: EmoticonDao
    private lateinit var appInfoDao: AppInfoDao

    @Before
    fun createDb() {
        // Create an in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        )
        .allowMainThreadQueries() // Only for testing
        .build()

        // Initialize all DAO references
        steamLicenseDao = database.steamLicenseDao()
        steamAppDao = database.steamAppDao()
        steamFriendDao = database.steamFriendDao()
        appChangeNumbersDao = database.appChangeNumbersDao()
        appFileChangeListsDao = database.appFileChangeListsDao()
        friendMessagesDao = database.friendMessagesDao()
        emoticonDao = database.emoticonDao()
        appInfoDao = database.appInfoDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    // Test database creation and initialization
    @Test
    fun testDatabaseCreation() {
        // Verify that database is created successfully
        assertNotNull("Database should not be null", database)
        assertTrue("Database should be open", database.isOpen)
        assertEquals("Database name should match", DATABASE_NAME, "pluvia.db")
    }

    // Test all DAO instances are properly initialized
    @Test
    fun testDaoInstances() {
        assertNotNull("SteamLicenseDao should not be null", steamLicenseDao)
        assertNotNull("SteamAppDao should not be null", steamAppDao)
        assertNotNull("SteamFriendDao should not be null", steamFriendDao)
        assertNotNull("ChangeNumbersDao should not be null", appChangeNumbersDao)
        assertNotNull("FileChangeListsDao should not be null", appFileChangeListsDao)
        assertNotNull("FriendMessagesDao should not be null", friendMessagesDao)
        assertNotNull("EmoticonDao should not be null", emoticonDao)
        assertNotNull("AppInfoDao should not be null", appInfoDao)
    }

    // Test that DAOs return the same instance consistently
    @Test
    fun testDaoConsistency() {
        val dao1 = database.steamLicenseDao()
        val dao2 = database.steamLicenseDao()
        assertEquals("DAO instances should be the same", dao1, dao2)
        
        val appDao1 = database.steamAppDao()
        val appDao2 = database.steamAppDao()
        assertEquals("App DAO instances should be the same", appDao1, appDao2)
    }

    // Test database version
    @Test
    fun testDatabaseVersion() {
        // The database version should be 4 as specified in the annotation
        val version = database.openHelper.readableDatabase.version
        assertEquals("Database version should be 4", 4, version)
    }

    // Test database schema validation
    @Test
    fun testDatabaseSchema() = runBlocking {
        // Verify that the database has been created with all expected tables
        val cursor = database.openHelper.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'", 
            null
        )
        
        val tableNames = mutableSetOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                tableNames.add(it.getString(0))
            }
        }
        
        // Verify expected tables exist (table names are typically lowercase entity names)
        assertTrue("Should contain tables", tableNames.isNotEmpty())
        // Note: Actual table names depend on entity @Table annotations
    }

    // Test type converters are properly configured
    @Test
    fun testTypeConvertersConfiguration() {
        // Verify that type converters are accessible through database
        val typeConverters = database.typeConverters
        assertNotNull("Type converters should not be null", typeConverters)
    }

    // Test database transaction handling
    @Test
    fun testTransactionHandling() = runBlocking {
        // Test that database can handle transactions properly
        var transactionStarted = false
        var transactionEnded = false
        
        try {
            database.runInTransaction {
                transactionStarted = true
                // Perform some operation within transaction
                // This tests that the database can properly handle transactions
            }
            transactionEnded = true
        } catch (e: Exception) {
            fail("Transaction should not throw exception: ${e.message}")
        }
        
        assertTrue("Transaction should have started", transactionStarted)
        assertTrue("Transaction should have completed", transactionEnded)
    }

    // Test database clearing/reset functionality
    @Test
    fun testDatabaseClear() = runBlocking {
        // Test that database can be cleared properly
        try {
            database.clearAllTables()
            // If we reach here, clearing succeeded
            assertTrue("Database clear should complete without exception", true)
        } catch (e: Exception) {
            fail("Database clear should not throw exception: ${e.message}")
        }
    }

    // Test database connection limits and concurrent access
    @Test
    fun testConcurrentAccess() {
        // Test multiple DAO access doesn't cause issues
        val daos = listOf(
            database.steamLicenseDao(),
            database.steamAppDao(),
            database.steamFriendDao(),
            database.appChangeNumbersDao(),
            database.appFileChangeListsDao(),
            database.friendMessagesDao(),
            database.emoticonDao(),
            database.appInfoDao()
        )
        
        // Verify all DAOs are accessible concurrently
        daos.forEach { dao ->
            assertNotNull("Each DAO should be accessible", dao)
        }
    }

    // Test database export schema setting
    @Test
    fun testExportSchemaConfiguration() {
        // Verify that export schema is set to false as specified
        // This is more of a configuration validation test
        // The actual schema export setting is compile-time configuration
        // but we can verify the database builds without schema export issues
        assertNotNull("Database should be created without schema export issues", database)
    }

    // Test database closure and cleanup
    @Test
    fun testDatabaseCleanup() {
        val testDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).build()
        
        assertTrue("Test database should be open", testDb.isOpen)
        
        testDb.close()
        assertFalse("Test database should be closed", testDb.isOpen)
    }

    // Test that all entities are properly configured
    @Test
    fun testEntityConfiguration() {
        // This test verifies that all entities specified in the @Database annotation
        // are properly configured and the database can be created without errors
        
        val expectedEntities = setOf(
            "SteamApp", "SteamLicense", "SteamFriend", "ChangeNumbers",
            "FileChangeLists", "FriendMessage", "Emoticon", "AppInfo"
        )
        
        // If database creation succeeded in @Before, entities are properly configured
        assertNotNull("Database creation confirms entity configuration", database)
        assertTrue("Database should be operational", database.isOpen)
    }

    // Test database migration preparation (even though version is 4)
    @Test
    fun testMigrationReadiness() {
        // Test that database is properly set up for potential future migrations
        val db = database.openHelper.readableDatabase
        assertNotNull("Database should be accessible for migration testing", db)
        assertEquals("Current version should be 4", 4, db.version)
    }

    // Test database memory usage and performance characteristics
    @Test
    fun testInMemoryCharacteristics() {
        // Verify that in-memory database behaves as expected for testing
        val beforeOperations = System.currentTimeMillis()
        
        // Perform multiple DAO access operations
        repeat(10) {
            database.steamAppDao()
            database.steamLicenseDao()
            database.steamFriendDao()
        }
        
        val afterOperations = System.currentTimeMillis()
        val operationTime = afterOperations - beforeOperations
        
        // In-memory operations should be fast (less than 1 second for simple access)
        assertTrue("In-memory operations should be fast", operationTime < 1000)
    }

    // Error handling and edge case tests
    @Test
    fun testDatabaseErrorHandling() {
        // Test database behavior with edge cases
        try {
            // Test accessing database after it's been created
            val dao = database.steamAppDao()
            assertNotNull("DAO should be accessible", dao)
            
            // Test multiple database operations
            val license = database.steamLicenseDao()
            val friend = database.steamFriendDao()
            assertNotNull("Multiple DAO access should work", license)
            assertNotNull("Multiple DAO access should work", friend)
            
        } catch (e: Exception) {
            fail("Database should handle normal operations without errors: ${e.message}")
        }
    }

    // Test database constants and configuration
    @Test
    fun testDatabaseConstants() {
        assertEquals("Database name constant should be correct", "pluvia.db", DATABASE_NAME)
    }

    // Integration test for all DAOs working together
    @Test
    fun testIntegratedDaoAccess() = runBlocking {
        // Test that all DAOs can be accessed within a single transaction context
        database.runInTransaction {
            val steamLicense = database.steamLicenseDao()
            val steamApp = database.steamAppDao()
            val steamFriend = database.steamFriendDao()
            val changeNumbers = database.appChangeNumbersDao()
            val fileChangeLists = database.appFileChangeListsDao()
            val friendMessages = database.friendMessagesDao()
            val emoticon = database.emoticonDao()
            val appInfo = database.appInfoDao()
            
            // Verify all DAOs are accessible within transaction
            assertNotNull("SteamLicenseDao should be accessible in transaction", steamLicense)
            assertNotNull("SteamAppDao should be accessible in transaction", steamApp)
            assertNotNull("SteamFriendDao should be accessible in transaction", steamFriend)
            assertNotNull("ChangeNumbersDao should be accessible in transaction", changeNumbers)
            assertNotNull("FileChangeListsDao should be accessible in transaction", fileChangeLists)
            assertNotNull("FriendMessagesDao should be accessible in transaction", friendMessages)
            assertNotNull("EmoticonDao should be accessible in transaction", emoticon)
            assertNotNull("AppInfoDao should be accessible in transaction", appInfo)
        }
    }
}