package com.aura.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLayoutProfileTest {
    @Test
    fun tinyPhoneUsesCompactSpacingAndTwoColumnAppGrid() {
        val profile = phoneLayoutProfile(width = 320.dp, height = 640.dp)

        assertEquals(12.dp, profile.horizontalPadding)
        assertEquals(10.dp, profile.verticalPadding)
        assertEquals(16.dp, profile.bottomBarHorizontalPadding)
        assertEquals(46.dp, profile.bottomNavItemSize)
        assertEquals(2, profile.appGridColumns)
        assertEquals(1, profile.storeGridColumns)
        assertEquals(2, profile.actionGridColumns)
        assertTrue(profile.dense)
        assertTrue(profile.short)
    }

    @Test
    fun normalPhoneUsesThreeColumnAppGridAndStandardNavSize() {
        val profile = phoneLayoutProfile(width = 390.dp, height = 780.dp)

        assertEquals(20.dp, profile.horizontalPadding)
        assertEquals(16.dp, profile.verticalPadding)
        assertEquals(60.dp, profile.bottomBarHorizontalPadding)
        assertEquals(52.dp, profile.bottomNavItemSize)
        assertEquals(3, profile.appGridColumns)
        assertEquals(2, profile.storeGridColumns)
        assertEquals(3, profile.actionGridColumns)
        assertFalse(profile.dense)
        assertFalse(profile.short)
    }

    @Test
    fun largePhoneWindowCanUseFourAppColumns() {
        val profile = phoneLayoutProfile(width = 520.dp, height = 900.dp)

        assertEquals(4, profile.appGridColumns)
        assertEquals(2, profile.storeGridColumns)
        assertEquals(3, profile.actionGridColumns)
        assertFalse(profile.dense)
        assertFalse(profile.short)
    }
}
