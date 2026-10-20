package io.github.clearkit.starter.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigScopeAccountTest {
    @Test
    fun accountOverridesClientOverridesGlobal() {
        val stored = mapOf("player.defaultSpeed" to 100, "cn|player.defaultSpeed" to 125, "u42|player.defaultSpeed" to 150)
        assertEquals(150, ConfigScope.lookup(stored, "player.defaultSpeed", "cn", "u42"))
        assertEquals(125, ConfigScope.lookup(stored, "player.defaultSpeed", "cn", "u7"))
        assertEquals(100, ConfigScope.lookup(stored, "player.defaultSpeed", "play", null))
        assertEquals(42L, ConfigScope.accountMid("u42"))
        assertNull(ConfigScope.accountMid("cn"))
        assertEquals("账号 42", ConfigScope.label("u42"))
        assertEquals(listOf("u42"), ConfigScope.accountLayers(stored))
    }
}
