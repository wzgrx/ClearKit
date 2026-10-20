package io.github.clearkit.starter.hook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleLogicTest {
    @Test
    fun touchAreaNeverBelowMinimum() {
        listOf("small", "medium", "large").forEach { s ->
            val (w, h) = CapsuleLogic.touchDp(s, collapsed = false)
            val (vw, vh) = CapsuleLogic.visualDp(s)
            assertTrue(w >= 48 && h >= 56 && w > vw && h > vh)
        }
        assertEquals(48 to 56, CapsuleLogic.touchDp("large", collapsed = true))
    }

    @Test
    fun hideZoneHitTest() {
        val zone = CapsuleLogic.hideZone(parentWidth = 1080, safeBottom = 2300, zoneWidth = 400, zoneHeight = 120, bottomGap = 60)
        assertEquals(TouchBox(340f, 2120f, 740f, 2240f), zone)
        assertTrue(CapsuleLogic.inHideZone(540f, 2180f, zone, 0f))
        assertTrue(CapsuleLogic.inHideZone(330f, 2180f, zone, 20f))
        assertFalse(CapsuleLogic.inHideZone(540f, 1800f, zone, 20f))
        assertEquals("landscape", CapsuleLogic.orientationKey(2400, 1080))
        assertEquals("portrait", CapsuleLogic.orientationKey(1080, 2400))
    }
}
