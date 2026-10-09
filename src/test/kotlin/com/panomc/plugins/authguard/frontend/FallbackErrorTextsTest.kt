package com.panomc.plugins.authguard.frontend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The error texts of the magic login page are the `fallback.errors` group of the plugin's own locale files. */
class FallbackErrorTextsTest {
    private val loader = javaClass.classLoader

    @Test
    fun `every locale carries the codes of the verify route`() {
        listOf("en-US", "tr", "ru").forEach { locale ->
            val errors = FallbackTexts.of(loader, "errors", locale)

            assertEquals(setOf("MAGIC_LOGIN_LINK_EXPIRED", "BAD_REQUEST"), errors.keys, locale)
            assertTrue(errors.values.all { (it as String).isNotBlank() }, locale)
        }
    }

    @Test
    fun `the static expired hint is gone`() {
        listOf("en-US", "tr", "ru").forEach { locale ->
            assertFalse(FallbackTexts.of(loader, "magic-login", locale).containsKey("expired"), locale)
        }
    }
}
