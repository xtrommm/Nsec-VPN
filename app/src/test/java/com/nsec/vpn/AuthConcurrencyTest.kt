package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.*
import com.nsec.vpn.data.repository.VpnRepository
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthConcurrencyTest {
    @Test
    fun simultaneousUnauthorizedRequestsRotateRefreshTokenOnlyOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = TestVault.create(context)
        vault.saveTokens("expired", "one-use-refresh")
        var rejected = 0
        var rotations = 0
        val bothRejected = CompletableDeferred<Unit>()
        val api = object : FakeNsecVpnApi() {
            override suspend fun getDeviceProfile(token: String): Response<DeviceProfileResponse> {
                if (token == "Bearer expired") {
                    synchronized(bothRejected) { rejected++; if (rejected == 2) bothRejected.complete(Unit) }
                    bothRejected.await()
                    return Response.error(401, "{\"error\":\"TOKEN_INVALID\"}".toResponseBody("application/json".toMediaType()))
                }
                assertEquals("Bearer fresh", token)
                return Response.success(DeviceProfileResponse(true, DeviceInfoDto("test", "VIP", "2099-01-01T00:00:00Z")))
            }
            override suspend fun refreshToken(request: RefreshTokenRequest): Response<RefreshTokenResponse> {
                rotations++
                assertEquals("one-use-refresh", request.refreshToken)
                delay(100)
                return Response.success(RefreshTokenResponse(true, TokenDataDto("fresh", "rotated-refresh")))
            }
        }
        val repository = VpnRepository(context, api, vault)
        withTimeout(5_000) { awaitAll(async { repository.refreshVerifiedEntitlement() }, async { repository.refreshVerifiedEntitlement() }) }
        assertEquals(1, rotations)
        assertEquals("rotated-refresh", vault.getRefreshToken())
    }
    @Test
    fun cancelledRefreshDoesNotFallBackToRegistration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = TestVault.create(context)
        vault.saveTokens("expired", "refresh")
        var registrations = 0
        val api = object : FakeNsecVpnApi() {
            override suspend fun getDeviceProfile(token: String): Response<DeviceProfileResponse> =
                Response.error(401, "{\"error\":\"TOKEN_INVALID\"}".toResponseBody("application/json".toMediaType()))
            override suspend fun refreshToken(request: RefreshTokenRequest): Response<RefreshTokenResponse> = throw CancellationException("cancelled")
            override suspend fun registerDevice(request: RegisterDeviceRequest): Response<RegisterDeviceResponse> { registrations++; return super.registerDevice(request) }
        }
        val repository = VpnRepository(context, api, vault)
        try { repository.refreshVerifiedEntitlement(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(0, registrations)
        assertEquals("refresh", vault.getRefreshToken())
    }
    @Test
    fun olderProfileResponseCannotUndoANewPromoGrant() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = TestVault.create(context)
        vault.saveTokens("test-access", "test-refresh")
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val api = object : FakeNsecVpnApi() {
            override suspend fun getDeviceProfile(token: String): Response<DeviceProfileResponse> {
                started.complete(Unit)
                finish.await()
                return Response.success(DeviceProfileResponse(true, DeviceInfoDto("test", "FREE", null)))
            }
            override suspend fun redeemPromo(token: String, request: RedeemPromoRequest): Response<RedeemPromoResponse> =
                Response.success(RedeemPromoResponse(true, RedeemPromoData("VIP", "2099-01-01T00:00:00Z")))
        }
        val repository = VpnRepository(context, api, vault)
        withTimeout(5_000) {
            val profile = async { repository.refreshVerifiedEntitlement() }
            started.await()
            repository.redeemPromo("TEST-CODE")
            finish.complete(Unit)
            profile.await()
        }
        assertEquals("VIP", repository.userTier.value)
    }
}
