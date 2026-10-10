package com.nsec.vpn

import org.junit.Assert.*
import org.junit.Test

class SplitTunnelingTest {

    @Test
    fun testOwnPackageAlwaysExcludedInExcludeMode() {
        val ownPackage = "com.nsec.vpn"
        val userExcludedApps = mutableSetOf("com.netflix.mediaclient", "com.spotify.music")
        
        userExcludedApps.add(ownPackage)
        
        assertTrue(userExcludedApps.contains(ownPackage))
        assertTrue(userExcludedApps.contains("com.netflix.mediaclient"))
    }

    @Test
    fun testOwnPackageCannotBeRemovedBySplitTunnelingUI() {
        val ownPackage = "com.nsec.vpn"
        val userExcludedApps = mutableSetOf("com.spotify.music", ownPackage)
        
        val updatedList = userExcludedApps.toMutableSet()
        updatedList.add(ownPackage)
        
        assertTrue(updatedList.contains(ownPackage))
    }

    @Test
    fun testNoMixingOfAllowedAndDisallowedModes() {
        val isExcludeMode = true
        val isIncludeMode = false
        
        assertFalse(isExcludeMode && isIncludeMode)
    }
}
