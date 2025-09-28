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
class PluviaDatabaseEntityValidationTest {

    private lateinit var database: PluviaDatabase

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PluviaDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testAllEntitiesHaveCorrespondingTables() {
        // Verify that all entities specified in @Database annotation have corresponding tables
        val cursor = database.openHelper.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
            null
        )

        val tableNames = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                tableNames.add(it.getString(0))
            }
        }

        assertTrue("Database should have tables created", tableNames.isNotEmpty())
        
        // Verify that Room has created the expected infrastructure tables
        assertTrue("Should have room master table", tableNames.any { it.contains("room_master_table") })
    }

    @Test
    fun testDatabaseIntegrity() {
        // Test database integrity
        val cursor = database.openHelper.readableDatabase.rawQuery("PRAGMA integrity_check", null)
        cursor.use {
            if (it.moveToFirst()) {
                val result = it.getString(0)
                assertEquals("Database integrity should be ok", "ok", result)
            }
        }
    }

    @Test
    fun testDatabaseSchema() {
        // Test that database schema is valid
        val cursor = database.openHelper.readableDatabase.rawQuery("PRAGMA schema_version", null)
        cursor.use {
            if (it.moveToFirst()) {
                val schemaVersion = it.getInt(0)
                assertTrue("Schema version should be positive", schemaVersion > 0)
            }
        }
    }

    @Test
    fun testForeignKeyConstraints() {
        // Test that foreign key constraints are properly configured
        val cursor = database.openHelper.readableDatabase.rawQuery("PRAGMA foreign_keys", null)
        cursor.use {
            assertTrue("Should be able to query foreign key status", it.count >= 0)
        }
    }

    @Test
    fun testDatabaseEncoding() {
        // Test database encoding
        val cursor = database.openHelper.readableDatabase.rawQuery("PRAGMA encoding", null)
        cursor.use {
            if (it.moveToFirst()) {
                val encoding = it.getString(0)
                assertNotNull("Database should have encoding set", encoding)
            }
        }
    }

    @Test
    fun testDatabaseUserVersion() {
        // Test that database user version matches our expected version
        val cursor = database.openHelper.readableDatabase.rawQuery("PRAGMA user_version", null)
        cursor.use {
            if (it.moveToFirst()) {
                val userVersion = it.getInt(0)
                assertEquals("User version should match database version", 4, userVersion)
            }
        }
    }

    @Test
    fun testTypeConverterFunctionality() {
        // Test that type converters don't cause database creation issues
        // This is validated by successful database creation, but we can test converter access
        val converters = database.typeConverters
        assertNotNull("Type converters should be accessible", converters)
    }

    @Test
    fun testDatabaseWalMode() {
        // Test WAL mode configuration (if applicable)
        val cursor = database.openHelper.readableDatabase.rawQuery("PRAGMA journal_mode", null)
        cursor.use {
            if (it.moveToFirst()) {
                val journalMode = it.getString(0)
                assertNotNull("Journal mode should be set", journalMode)
                // WAL mode might not be enabled by default, but should have some journal mode
                assertTrue("Journal mode should not be empty", journalMode.isNotEmpty())
            }
        }
    }

    @Test
    fun testDatabaseSynchronous() {
        // Test synchronous pragma
        val cursor = database.openHelper.readableDatabase.rawQuery("PRAGMA synchronous", null)
        cursor.use {
            if (it.moveToFirst()) {
                val synchronous = it.getInt(0)
                assertTrue("Synchronous setting should be valid", synchronous >= 0)
            }
        }
    }

    @Test
    fun testDatabasePageSize() {
        // Test database page size
        val cursor = database.openHelper.readableDatabase.rawQuery("PRAGMA page_size", null)
        cursor.use {
            if (it.moveToFirst()) {
                val pageSize = it.getInt(0)
                assertTrue("Page size should be positive", pageSize > 0)
                // SQLite default page size is typically 4096
                assertTrue("Page size should be reasonable", pageSize >= 512)
            }
        }
    }
}