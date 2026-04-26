package com.panomc.plugins.authguard.service

import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.config.CaptchaProvider
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.coAwait
import org.slf4j.LoggerFactory
import java.net.URLEncoder

class CaptchaVerificationService(
    private val configManager: PluginConfigManager<AuthGuardConfig>,
    vertx: Vertx
) {
    companion object {
        private const val RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify"
        private const val HCAPTCHA_VERIFY_URL = "https://api.hcaptcha.com/siteverify"
        private const val TURNSTILE_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"
    }

    private val logger = LoggerFactory.getLogger("AuthGuard-Captcha")

    private val webClient by lazy {
        WebClient.create(vertx, WebClientOptions().setSsl(true).setTrustAll(false))
    }

    suspend fun verify(token: String?, remoteIp: String?): Boolean {
        val config = configManager.config

        if (config.captchaProvider == CaptchaProvider.NONE) {
            return true
        }

        if (config.captchaSecretKey.isBlank() || config.captchaSiteKey.isBlank()) {
            logger.warn("Captcha provider is set to ${config.captchaProvider} but keys are not configured, bypassing captcha")
            return true
        }

        if (config.captchaProvider == CaptchaProvider.RECAPTCHA && !config.recaptchaLegacy && config.captchaProjectId.isBlank()) {
            logger.warn("reCAPTCHA Enterprise requires a project ID, bypassing captcha")
            return true
        }

        if (token.isNullOrBlank()) {
            if (config.debugLogging) {
                logger.warn("Captcha token is missing")
            }
            return false
        }

        if (config.debugLogging) {
            logger.info("Verifying captcha token (length=${token.length}, prefix=${token.take(10)}...)")
        }

        return try {
            when (config.captchaProvider) {
                CaptchaProvider.RECAPTCHA -> {
                    if (config.recaptchaLegacy) {
                        verifyRecaptcha(config.captchaSecretKey, token, remoteIp)
                    } else {
                        verifyRecaptchaEnterprise(
                            config.captchaSecretKey, config.captchaProjectId, config.captchaSiteKey, token
                        )
                    }
                }
                CaptchaProvider.HCAPTCHA -> verifyHcaptcha(config.captchaSecretKey, token, remoteIp)
                CaptchaProvider.TURNSTILE -> verifyTurnstile(config.captchaSecretKey, token, remoteIp)
                CaptchaProvider.NONE -> true
            }
        } catch (e: Exception) {
            logger.error("Captcha verification failed with exception, bypassing captcha check to prevent lockout", e)
            true
        }
    }

    private suspend fun verifyRecaptcha(secret: String, token: String, remoteIp: String?): Boolean {
        return verifyWithUrl(RECAPTCHA_VERIFY_URL, secret, token, remoteIp)
    }

    private suspend fun verifyHcaptcha(secret: String, token: String, remoteIp: String?): Boolean {
        return verifyWithUrl(HCAPTCHA_VERIFY_URL, secret, token, remoteIp)
    }

    private suspend fun verifyTurnstile(secret: String, token: String, remoteIp: String?): Boolean {
        return verifyWithUrl(TURNSTILE_VERIFY_URL, secret, token, remoteIp)
    }

    private suspend fun verifyWithUrl(url: String, secret: String, token: String, remoteIp: String?): Boolean {
        val config = configManager.config
        val formBody = buildString {
            append("secret=${URLEncoder.encode(secret, "UTF-8")}")
            append("&response=${URLEncoder.encode(token, "UTF-8")}")
            if (!remoteIp.isNullOrBlank()) {
                append("&remoteip=${URLEncoder.encode(remoteIp, "UTF-8")}")
            }
        }

        val response = webClient
            .postAbs(url)
            .putHeader("Content-Type", "application/x-www-form-urlencoded")
            .sendBuffer(Buffer.buffer(formBody))
            .coAwait()

        val body = response.bodyAsJsonObject()
        val success = body.getBoolean("success", false)

        if (!success) {
            val errorCodes = body.getJsonArray("error-codes")
            val errorList = errorCodes?.map { it.toString() } ?: emptyList()

            // Configuration/server-side errors that shouldn't lock users out
            val configErrors = setOf(
                "invalid-input-secret",  // reCAPTCHA / hCaptcha / Turnstile
                "bad-request",           // Turnstile
                "internal-error",        // reCAPTCHA / Turnstile
                "sitekey-secret-mismatch" // hCaptcha
            )

            val hasConfigError = errorList.any { it in configErrors }

            if (hasConfigError) {
                logger.error("Captcha misconfiguration detected (errors: $errorCodes), bypassing captcha to prevent lockout")
                return true
            }

            if (config.debugLogging) {
                logger.warn("Captcha verification failed. Errors: $errorCodes")
            }
        }

        return success
    }

    private suspend fun verifyRecaptchaEnterprise(
        apiKey: String,
        projectId: String,
        siteKey: String,
        token: String
    ): Boolean {
        val config = configManager.config
        val url = "https://recaptchaenterprise.googleapis.com/v1/projects/$projectId/assessments?key=$apiKey"

        val requestBody = JsonObject()
            .put("event", JsonObject()
                .put("token", token)
                .put("siteKey", siteKey)
            )

        val response = webClient
            .postAbs(url)
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(requestBody)
            .coAwait()

        val body = response.bodyAsJsonObject()

        if (config.debugLogging) {
            logger.info("reCAPTCHA Enterprise response: $body")
        }

        // Check for API-level errors
        val error = body.getJsonObject("error")
        if (error != null) {
            val errorMessage = error.getString("message", "Unknown error")
            val errorCode = error.getInteger("code", 0)
            logger.error("reCAPTCHA Enterprise API error ($errorCode): $errorMessage")
            // Fail-open on API errors to prevent lockout
            return true
        }

        // Check if the token is valid
        val tokenProperties = body.getJsonObject("tokenProperties")
        if (tokenProperties == null) {
            if (config.debugLogging) {
                logger.warn("reCAPTCHA Enterprise: No tokenProperties in response")
            }
            return false
        }

        val valid = tokenProperties.getBoolean("valid", false)
        if (!valid) {
            val invalidReason = tokenProperties.getString("invalidReason", "UNKNOWN")
            if (config.debugLogging) {
                logger.warn("reCAPTCHA Enterprise: Token invalid, reason: $invalidReason")
            }
            return false
        }

        // Token is valid — for checkbox keys this is sufficient
        val riskAnalysis = body.getJsonObject("riskAnalysis")
        val score = riskAnalysis?.getFloat("score")
        val action = tokenProperties.getString("action")
        if (config.debugLogging) {
            logger.info("reCAPTCHA Enterprise: valid=true, score=$score, action=$action")
        }

        return true
    }
}
