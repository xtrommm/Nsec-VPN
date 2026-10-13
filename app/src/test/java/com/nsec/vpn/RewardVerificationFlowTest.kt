package com.nsec.vpn

import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.ClaimRewardRequest
import com.nsec.vpn.data.api.RedeemPromoData
import com.nsec.vpn.data.api.RedeemPromoResponse
import com.nsec.vpn.data.repository.VpnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RewardVerificationFlowTest {
    @Test
    fun pendingVerificationCannotSetVipAndIsPolledUntilVerified() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = TestVault.create(context)
        vault.saveTokens("test-access", "test-refresh")
        var calls = 0
        lateinit var repository: VpnRepository
        val api = object : FakeNsecVpnApi() {
            override suspend fun claimReward(token: String, request: ClaimRewardRequest): Response<RedeemPromoResponse> {
                assertEquals("FREE", repository.userTier.value)
                calls++
                return if (calls == 1) Response.success(202, RedeemPromoResponse(false, null))
                else Response.success(RedeemPromoResponse(true, RedeemPromoData("VIP", "2099-01-01T00:00:00Z")))
            }
        }
        repository = VpnRepository(context, api, vault)
        repository.claimReward("test-ticket")
        assertEquals(2, calls)
        assertEquals("VIP", repository.userTier.value)
        org.junit.Assert.assertNull(vault.getPendingRewardTicket())
    }
}
