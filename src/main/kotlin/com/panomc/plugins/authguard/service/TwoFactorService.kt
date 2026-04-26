package com.panomc.plugins.authguard.service

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.RandomSecretGenerator
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.util.concurrent.TimeUnit

class TwoFactorService {
    companion object {
        private const val CODE_DIGITS = 6
        private const val TIME_STEP_SECONDS = 30L
        private const val ALLOWED_TIME_DRIFT = 2

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

    fun verifyCode(secret: String, code: String): Boolean {
        val normalizedInput = code.trim().filter { it.isDigit() }
        if (normalizedInput.length != CODE_DIGITS) {
            if (debugLogging) {
                logger.warn("[auth-guard] Invalid code length: raw='{}' cleaned='{}'", code, normalizedInput)
            }
            return false
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
                return true
            }
        }

        if (debugLogging) {
            logger.warn(
                "[auth-guard] TOTP verify failed. input='{}' serverTimeMs={} secretBytesLen={} candidates={}",
                normalizedInput, now, secretBytes.size, candidates
            )
        }
        return false
    }

    fun generateCurrentCode(secret: String): String {
        val secretBytes = base32.decode(secret)
        val generator = TimeBasedOneTimePasswordGenerator(secretBytes, totpConfig)
        return generator.generate()
    }
}
