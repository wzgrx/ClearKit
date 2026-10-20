package io.github.clearkit.starter.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingChoicesTest {
    @Test
    fun everyCatalogKeyIsARealSetting() {
        (SettingChoices.CATALOG_SETS + SettingChoices.CATALOG_SINGLE + SettingChoices.ORDERS.keys).forEach { k ->
            assertTrue(k, Settings.byKey(k) != null)
        }
        assertTrue(SettingChoices.PALETTE.all { Regex("[0-9A-F]{6}").matches(it.second) })
    }
}
