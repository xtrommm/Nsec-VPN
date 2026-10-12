package com.nsec.vpn
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.ServerItemDto
import com.nsec.vpn.data.local.ServerCatalogCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServerCatalogCacheTest {
    private val node = ServerItemDto("fixture-node", "Fixture", "US", "United States", "Fixture city", "", 0, false)
    @Test fun restoresOnlyValidatedCatalogWithinTtl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = ServerCatalogCache(context)
        cache.write(listOf(node), 1000)
        assertEquals(listOf(node), ServerCatalogCache(context).read(1001))
        assertTrue(cache.read(1000 + ServerCatalogCache.MAX_AGE_MS + 1).isEmpty())
        assertTrue(cache.read(999).isEmpty())
    }
    @Test fun rejectsDuplicateIdsAndDoesNotInventAReplacementForCorruptCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThrows(IllegalArgumentException::class.java) { ServerCatalogCache.validate(listOf(node, node)) }
        context.getSharedPreferences("nsec_verified_server_catalog", Context.MODE_PRIVATE).edit()
            .putLong("verified_at", 1000).putString("catalog", "[{broken]").commit()
        assertTrue(ServerCatalogCache(context).read(1001).isEmpty())
    }
}
