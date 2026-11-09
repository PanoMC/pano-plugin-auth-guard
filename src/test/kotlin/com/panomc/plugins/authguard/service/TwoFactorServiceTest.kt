package com.panomc.plugins.authguard.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwoFactorServiceTest {
    private val service = TwoFactorService()

    private fun wrongCode(secret: String): String {
        val right = service.generateCurrentCode(secret)
        return if (right == "000000") "111111" else "000000"
    }

    @Test
    fun `a code is accepted once`() {
        val secret = service.generateSecret()
        val code = service.generateCurrentCode(secret)

        assertTrue(service.verifyCode(1, secret, code))
        assertFalse(service.verifyCode(1, secret, code))
    }

    @Test
    fun `the used step belongs to the user`() {
        val secret = service.generateSecret()
        val code = service.generateCurrentCode(secret)

        assertTrue(service.verifyCode(1, secret, code))
        assertTrue(service.verifyCode(2, secret, code))
    }

    @Test
    fun `too many wrong codes lock the account even for the right code`() {
        val secret = service.generateSecret()
        val wrong = wrongCode(secret)

        repeat(TwoFactorService.MAX_FAILED_ATTEMPTS) { assertFalse(service.verifyCode(3, secret, wrong)) }

        assertTrue(service.isLockedOut(3))
        assertFalse(service.verifyCode(3, secret, service.generateCurrentCode(secret)))
        assertFalse(service.isLockedOut(4))
    }

    @Test
    fun `a right code resets the failure count`() {
        val secret = service.generateSecret()
        val wrong = wrongCode(secret)

        repeat(TwoFactorService.MAX_FAILED_ATTEMPTS - 1) { service.verifyCode(5, secret, wrong) }
        assertTrue(service.verifyCode(5, secret, service.generateCurrentCode(secret)))
        repeat(TwoFactorService.MAX_FAILED_ATTEMPTS - 1) { service.verifyCode(5, secret, wrong) }

        assertFalse(service.isLockedOut(5))
    }
}
