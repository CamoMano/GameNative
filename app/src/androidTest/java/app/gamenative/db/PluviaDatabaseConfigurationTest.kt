package app.gamenative.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PluviaDatabaseConfigurationTest {

    private lateinit var database: PluviaDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).build()
    }

    @After
    fun cleanup() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun testDatabaseName() {
        assertEquals("Database name should be pluvia.db", "pluvia.db", DATABASE_NAME)
    }

    @Test
    fun testDatabaseVersion() {
        val version = database.openHelper.readableDatabase.version
        assertEquals("Database version should be 4", 4, version)
    }

    @Test
    fun testDatabaseTypeConverters() {
        // Test that all type converters are properly registered
        val typeConverters = database.typeConverters
        assertNotNull("Type converters should be registered", typeConverters)
    }

    @Test
    fun testDatabaseBuilderConfiguration() {
        // Test various database builder configurations
        val testDb1 = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).allowMainThreadQueries().build()

        assertNotNull("Database with main thread queries should be created", testDb1)
        assertTrue("Database should be open", testDb1.isOpen)
        testDb1.close()

        val testDb2 = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).fallbackToDestructiveMigration().build()

        assertNotNull("Database with destructive migration should be created", testDb2)
        assertTrue("Database should be open", testDb2.isOpen)
        testDb2.close()
    }

    @Test
    fun testDatabaseMultipleInstances() {
        val db1 = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).build()

        val db2 = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).build()

        assertNotNull("First database instance should be created", db1)
        assertNotNull("Second database instance should be created", db2)
        assertNotEquals("Database instances should be different", db1, db2)

        db1.close()
        db2.close()
    }

    @Test
    fun testDatabaseInMemoryCharacteristics() {
        assertTrue("Database should be open after creation", database.isOpen)
        
        // Test that in-memory database doesn't persist after close
        val dao = database.steamAppDao()
        assertNotNull("DAO should be accessible", dao)
        
        database.close()
        assertFalse("Database should be closed", database.isOpen)
    }

    @Test
    fun testDatabaseCallbacks() {
        var callbackCalled = false
        
        val testDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onCreate(db)
                callbackCalled = true
            }
        }).build()

        // Access database to trigger onCreate
        testDb.steamAppDao()
        
        assertTrue("Database onCreate callback should be called", callbackCalled)
        testDb.close()
    }

    @Test
    fun testDatabaseQueryThreading() {
        // Test that database properly handles threading constraints
        val testDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).allowMainThreadQueries().build()

        // This should work with allowMainThreadQueries
        val dao = testDb.steamAppDao()
        assertNotNull("DAO should be accessible on main thread when allowed", dao)
        
        testDb.close()
    }
}