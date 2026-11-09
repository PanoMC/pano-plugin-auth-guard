package com.panomc.plugins.authguard.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptchaStepTokenServiceTest {
    private val service = CaptchaStepTokenService()

    @Test
    fun `a token stands in for the captcha a limited number of times`() {
        val token = service.mint(1, "Alice")

        repeat(CaptchaStepTokenService.MAX_USES) { assertTrue(service.isValid(token, "alice")) }
        assertFalse(service.isValid(token, "alice"))
    }

    @Test
    fun `asking again returns the live token instead of a fresh one`() {
        val token = service.mint(1, "alice")
        service.isValid(token, "alice")

        assertEquals(token, service.mint(1, "alice"))
    }

    @Test
    fun `a token is bound to its login`() {
        val token = service.mint(1, "alice")

        assertFalse(service.isValid(token, "bob"))
    }
}
