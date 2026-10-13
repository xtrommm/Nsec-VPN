package com.nsec.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nsec.vpn.data.api.ClaimRewardRequest
import com.nsec.vpn.data.api.RedeemPromoData
import com.nsec.vpn.data.api.RedeemPromoResponse
import com.nsec.vpn.data.repository.VpnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RewardClaimFeedbackTest {
    @Test fun pendingCannotGrantVipAndVerifiedResponseActivatesImmediately() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = TestVault.create(context)
        var calls = 0
        lateinit var repo: VpnRepository
        val api = object : FakeNsecVpnApi() {
            override suspend fun claimReward(token: String, request: ClaimRewardRequest): Response<RedeemPromoResponse> {
                assertEquals("FREE", repo.userTier.value)
                return if (++calls == 1) Response.success(202, RedeemPromoResponse(false, null)) else
                    Response.success(RedeemPromoResponse(true, RedeemPromoData("VIP", Instant.now().plusSeconds(2_700).toString())))
            }
        }
        repo = VpnRepository(context, api, vault)
        repo.claimReward("fixture-ticket")
        assertEquals(2, calls)
        assertEquals("VIP", repo.userTier.value)
        assertFalse(repo.rewardPending.value)
        assertNull(vault.getPendingRewardTicket())
    }

    @Test fun freeOrExpiredGrantNeverReportsVipActivation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = TestVault.create(context)
        val api = object : FakeNsecVpnApi() {
            override suspend fun claimReward(token: String, request: ClaimRewardRequest) =
                Response.success(RedeemPromoResponse(true, RedeemPromoData("FREE", null)))
        }
        val repo = VpnRepository(context, api, vault)
        try { repo.claimReward("fixture-expired"); fail("Expired reward must not succeed") }
        catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("Expired")) }
        assertEquals("FREE", repo.userTier.value)
        assertFalse(repo.rewardPending.value)
    }

    @Test fun startupWithoutPendingTicketDoesNotThrottleALaterVerifiedClaim() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = TestVault.create(context)
        vault.savePendingRewardTicket(null)
        var calls = 0
        val api = object : FakeNsecVpnApi() {
            override suspend fun claimReward(token: String, request: ClaimRewardRequest): Response<RedeemPromoResponse> {
                calls++
                return Response.success(RedeemPromoResponse(true, RedeemPromoData("VIP", Instant.now().plusSeconds(2_700).toString())))
            }
        }
        val repo = VpnRepository(context, api, vault)
        repo.retryPendingReward()
        vault.savePendingRewardTicket("fixture-new-ticket")
        repo.retryPendingReward()
        assertEquals(1, calls)
        assertEquals("VIP", repo.userTier.value)
    }
}
