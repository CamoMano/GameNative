package app.gamenative.data

import androidx.room.Entity
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [AppInfo].
 *
 * Testing library & framework: JUnit4 (org.junit.Test, org.junit.Assert)
 */
class AppInfoTest {

    @Test
    fun `constructor with explicit values populates fields`() {
        val id = 123
        val isDownloaded = true
        val depots = listOf(1, 2, 3)

        val appInfo = AppInfo(id = id, isDownloaded = isDownloaded, downloadedDepots = depots)

        assertEquals("id should match the provided value", id, appInfo.id)
        assertEquals("isDownloaded should match the provided value", isDownloaded, appInfo.isDownloaded)
        assertEquals("downloadedDepots should match the provided list", depots, appInfo.downloadedDepots)
    }

    @Test
    fun `constructor uses defaults when optional values omitted`() {
        val id = 456

        val appInfo = AppInfo(id)

        assertEquals(id, appInfo.id)
        assertFalse("isDownloaded default should be false", appInfo.isDownloaded)
        assertTrue("downloadedDepots default should be empty", appInfo.downloadedDepots.isEmpty())
    }

    @Test
    fun `constructor accepts zero id`() {
        val appInfo = AppInfo(id = 0)

        assertEquals(0, appInfo.id)
        assertFalse(appInfo.isDownloaded)
        assertTrue(appInfo.downloadedDepots.isEmpty())
    }

    @Test
    fun `constructor accepts negative id`() {
        val appInfo = AppInfo(id = -42)

        assertEquals(-42, appInfo.id)
    }

    @Test
    fun `downloadedDepots accepts empty list`() {
        val appInfo = AppInfo(id = 10, downloadedDepots = emptyList())

        assertTrue(appInfo.downloadedDepots.isEmpty())
    }

    @Test
    fun `downloadedDepots accepts duplicates`() {
        val depots = listOf(5, 5, 7)

        val appInfo = AppInfo(id = 11, downloadedDepots = depots)

        assertEquals(depots, appInfo.downloadedDepots)
        assertEquals(2, appInfo.downloadedDepots.count { it == 5 })
    }

    @Test
    fun `downloadedDepots accepts negative depot ids`() {
        val depots = listOf(-1, -2)

        val appInfo = AppInfo(id = 12, downloadedDepots = depots)

        assertEquals(depots, appInfo.downloadedDepots)
        assertTrue(appInfo.downloadedDepots.all { it < 0 })
    }

    @Test
    fun `data class equality based on all properties`() {
        val depots = listOf(1, 2)
        val base = AppInfo(99, false, depots)

        assertEquals("identical properties should be equal", base, AppInfo(99, false, depots))
        assertNotEquals("different id should not be equal", base, AppInfo(100, false, depots))
        assertNotEquals("different isDownloaded should not be equal", base, AppInfo(99, true, depots))
        assertNotEquals("different depots should not be equal", base, AppInfo(99, false, listOf(3)))
    }

    @Test
    fun `hashCode changes with properties`() {
        val base = AppInfo(1, false, listOf(1, 2))
        val variants = listOf(
            AppInfo(2, false, listOf(1, 2)),
            AppInfo(1, true, listOf(1, 2)),
            AppInfo(1, false, listOf(3))
        )

        variants.forEach { variant ->
            assertNotEquals("hashCode should differ when properties differ", base.hashCode(), variant.hashCode())
        }
    }

    @Test
    fun `toString contains property values`() {
        val depots = listOf(7, 8)
        val repr = AppInfo(5, true, depots).toString()

        assertTrue("toString should mention id", repr.contains("id=5"))
        assertTrue("toString should mention isDownloaded", repr.contains("isDownloaded=true"))
        assertTrue("toString should mention downloadedDepots", repr.contains(depots.toString()))
    }

    @Test
    fun `copy without arguments replicates instance`() {
        val original = AppInfo(42, true, listOf(3))

        val copy = original.copy()

        assertEquals(original, copy)
        assertNotSame("copy should produce a new instance", original, copy)
    }

    @Test
    fun `copy overrides individual properties`() {
        val original = AppInfo(1, false, listOf(1))

        val changedId = original.copy(id = 2)
        val changedIsDownloaded = original.copy(isDownloaded = true)
        val changedDepots = original.copy(downloadedDepots = listOf(9, 10))

        assertEquals(2, changedId.id)
        assertEquals(true, changedIsDownloaded.isDownloaded)
        assertEquals(listOf(9, 10), changedDepots.downloadedDepots)
        assertEquals("copy should leave unspecified fields unchanged", original.isDownloaded, changedId.isDownloaded)
    }

    @Test
    fun `destructuring components return properties`() {
        val appInfo = AppInfo(77, true, listOf(9, 8))

        val (id, downloaded, depots) = appInfo

        assertEquals(77, id)
        assertTrue(downloaded)
        assertEquals(listOf(9, 8), depots)
    }

    @Test
    fun `entity annotation is present with expected table name`() {
        val annotation = AppInfo::class.java.getAnnotation(Entity::class.java)

        assertNotNull("AppInfo should be annotated with @Entity", annotation)
        assertEquals("app_info", annotation?.tableName)
    }

    @Test
    fun `mutable list copied externally does not mutate instance`() {
        val external = mutableListOf(1, 2, 3)

        val appInfo = AppInfo(id = 1, downloadedDepots = external)
        external += 4

        assertEquals(listOf(1, 2, 3), appInfo.downloadedDepots)
        assertEquals("external mutation should not change stored list reference", 4, external.size)
    }
}