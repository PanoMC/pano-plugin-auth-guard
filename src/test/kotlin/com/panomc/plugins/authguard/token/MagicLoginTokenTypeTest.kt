package com.panomc.plugins.authguard.token

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MagicLoginTokenTypeTest {
    @AfterEach
    fun reset() {
        MagicLoginTokenType.configuredMinutes = { MagicLoginTokenType.DEFAULT_MINUTES }
    }

    @Test
    fun `the link lifetime follows the setting`() {
        MagicLoginTokenType.configuredMinutes = { 5 }

        val remaining = MagicLoginTokenType.getExpireDate() - System.currentTimeMillis()
        assertEquals(5, MagicLoginTokenType.expireMinutes())
        assertTrue(remaining in (4 * 60_000L + 59_000L)..(5 * 60_000L))
    }

    @Test
    fun `out of range settings are clamped`() {
        MagicLoginTokenType.configuredMinutes = { 0 }
        assertEquals(MagicLoginTokenType.MIN_MINUTES, MagicLoginTokenType.expireMinutes())

        MagicLoginTokenType.configuredMinutes = { 100_000 }
        assertEquals(MagicLoginTokenType.MAX_MINUTES, MagicLoginTokenType.expireMinutes())
    }
}
