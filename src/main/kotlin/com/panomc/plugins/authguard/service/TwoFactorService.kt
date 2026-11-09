package com.panomc.plugins.authguard.service

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.RandomSecretGenerator
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TwoFactorService {
    companion object {
        private const val CODE_DIGITS = 6
        private const val TIME_STEP_SECONDS = 30L
        private const val ALLOWED_TIME_DRIFT = 1

        /** Wrong codes in a row before the account's code check is locked for [LOCKOUT_MILLIS]. */
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 5 * 60 * 1000L

        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(TwoFactorService::class.java)
    }

    var debugLogging: Boolean = false

    private val totpConfig = TimeBasedOneTimePasswordConfig(
        codeDigits = CODE_DIGITS,
        hmacAlgorithm = HmacAlgorithm.SHA1,
        timeStep = TIME_STEP_SECONDS,
        timeStepUnit = TimeUnit.SECONDS
    )

    private val base32 = Base32()
    private val secretGenerator = RandomSecretGenerator()

    fun generateSecret(): String {
        val secretBytes = secretGenerator.createRandomSecret(HmacAlgorithm.SHA1)
        return base32.encodeAsString(secretBytes).replace("=", "")
    }

    fun generateTotpUri(secret: String, username: String, issuer: String): String {
        val encodedIssuer = java.net.URLEncoder.encode(issuer, "UTF-8")
        val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
        return "otpauth://totp/$encodedIssuer:$encodedUsername?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=$CODE_DIGITS&period=$TIME_STEP_SECONDS"
    }

    private data class Failures(val count: Int, val lockedUntil: Long)

    // Last accepted time step per user: a code is accepted once, never again (also not an older one).
    private val lastUsedStep = ConcurrentHashMap<Long, Long>()

    // Wrong codes per user, independent of the IP the guesses come from.
    private val failures = ConcurrentHashMap<Long, Failures>()

    /** True while [userId] made too many wrong guesses; every code is refused until the lock ends. */
    fun isLockedOut(userId: Long): Boolean {
        val entry = failures[userId] ?: return false
        if (entry.lockedUntil == 0L) return false
        if (entry.lockedUntil > System.currentTimeMillis()) return true
        failures.remove(userId, entry)
        return false
    }

    /**
     * Checks [code] for [userId]. A code already used (or one from an older time step than the last used one) is
     * refused, and [MAX_FAILED_ATTEMPTS] wrong codes in a row lock the check for [LOCKOUT_MILLIS].
     */
    fun verifyCode(userId: Long, secret: String, code: String): Boolean {
        if (isLockedOut(userId)) {
            return false
        }

        val step = matchingStep(secret, code)
        if (step != null) {
            var accepted = false
            lastUsedStep.compute(userId) { _, last ->
                if (last == null || step > last) {
                    accepted = true
                    step
                } else {
                    last
                }
            }
            if (accepted) {
                failures.remove(userId)
                return true
            }
        }

        failures.compute(userId) { _, entry ->
            val count = (entry?.takeIf { it.lockedUntil == 0L }?.count ?: 0) + 1
            if (count >= MAX_FAILED_ATTEMPTS) {
                Failures(0, System.currentTimeMillis() + LOCKOUT_MILLIS)
            } else {
                Failures(count, 0L)
            }
        }
        return false
    }

    /** The time step [code] belongs to within the allowed drift, or null when it matches none. */
    private fun matchingStep(secret: String, code: String): Long? {
        val normalizedInput = code.trim().filter { it.isDigit() }
        if (normalizedInput.length != CODE_DIGITS) {
            if (debugLogging) {
                logger.warn("[auth-guard] Invalid code length: raw='{}' cleaned='{}'", code, normalizedInput)
            }
            return null
        }

        val secretBytes = base32.decode(secret)
        val generator = TimeBasedOneTimePasswordGenerator(secretBytes, totpConfig)

        val now = System.currentTimeMillis()
        val stepMillis = TIME_STEP_SECONDS * 1000

        val candidates = mutableListOf<String>()
        for (drift in -ALLOWED_TIME_DRIFT..ALLOWED_TIME_DRIFT) {
            val timestamp = now + drift * stepMillis
            val expectedCode = generator.generate(java.util.Date(timestamp))
            candidates += "d=$drift→$expectedCode"
            if (expectedCode == normalizedInput) {
                return timestamp / stepMillis
            }
        }

        if (debugLogging) {
            logger.warn(
                "[auth-guard] TOTP verify failed. input='{}' serverTimeMs={} secretBytesLen={} candidates={}",
                normalizedInput, now, secretBytes.size, candidates
            )
        }
        return null
    }

    fun generateCurrentCode(secret: String): String {
        val secretBytes = base32.decode(secret)
        val generator = TimeBasedOneTimePasswordGenerator(secretBytes, totpConfig)
        return generator.generate()
    }
}
