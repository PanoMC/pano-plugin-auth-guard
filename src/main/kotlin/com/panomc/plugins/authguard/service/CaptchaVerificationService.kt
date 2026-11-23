package com.panomc.plugins.authguard.service

import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.config.CaptchaProvider
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.coAwait
import org.slf4j.LoggerFactory
import java.net.URLEncoder

class CaptchaVerificationService(
    private val configManager: PluginConfigManager<AuthGuardConfig>,
    // Core's shared client: never closed here, the host owns it.
    private val webClient: WebClient
) {
    companion object {
        private const val RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify"
        private const val HCAPTCHA_VERIFY_URL = "https://api.hcaptcha.com/siteverify"
        private const val TURNSTILE_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"

        /** Provider tokens are a few hundred bytes to ~2 KB; anything far longer is refused without a provider call. */
        const val MAX_TOKEN_LENGTH = 4096

        // Errors of the site's own setup (wrong secret / key pair, provider side failure). They would lock every
        // visitor out, so the check is skipped for them. Errors caused by the request ("bad-request", a bad token) fail.
        private val configErrors = setOf(
            "invalid-input-secret",  // reCAPTCHA / hCaptcha / Turnstile
            "missing-input-secret",  // reCAPTCHA / hCaptcha / Turnstile
            "internal-error",        // reCAPTCHA / Turnstile
            "sitekey-secret-mismatch" // hCaptcha
        )

        /** The answer of a `siteverify` call: `null` = skip the check (site setup error / provider down). */
        internal fun siteVerifyResult(statusCode: Int, body: JsonObject?): Boolean? {
            if (statusCode >= 500) return null
            if (statusCode !in 200..299 || body == null) return false

            if (body.getBoolean("success", false) == true) return true

            val errorList = body.getJsonArray("error-codes")?.map { it.toString() } ?: emptyList()
            return if (errorList.any { it in configErrors }) null else false
        }

        /**
         * The answer of a reCAPTCHA Enterprise assessment: `null` = skip the check (bad API key / project, provider
         * down), `false` = refused, `true` = valid token.
         */
        internal fun enterpriseResult(statusCode: Int, body: JsonObject?): Boolean? {
            if (statusCode >= 500) return null
            // 401 / 403 / 404: the API key or project of the site is wrong.
            if (statusCode == 401 || statusCode == 403 || statusCode == 404) return null
            val error = body?.getJsonObject("error")
            if (error != null && isApiKeyError(error)) return null
            if (statusCode !in 200..299 || body == null || error != null) return false

            val tokenProperties = body.getJsonObject("tokenProperties") ?: return false
            return tokenProperties.getBoolean("valid", false) == true
        }

        // Google answers a wrong API key with 400 INVALID_ARGUMENT, reason API_KEY_INVALID.
        private fun isApiKeyError(error: JsonObject): Boolean {
            val details = error.getJsonArray("details")?.mapNotNull { (it as? JsonObject)?.getString("reason") } ?: emptyList()
            return "API_KEY_INVALID" in details || error.getString("message", "").contains("API key", ignoreCase = true)
        }

        private fun parseBody(text: String?): JsonObject? =
            try {
                if (text.isNullOrBlank()) null else JsonObject(text)
            } catch (e: Exception) {
                null
            }
    }

    private val logger = LoggerFactory.getLogger("AuthGuard-Captcha")

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

        if (token.length > MAX_TOKEN_LENGTH) {
            if (config.debugLogging) {
                logger.warn("Captcha token is too long (length=${token.length})")
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
            // Network failure reaching the provider: nothing the request controls.
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

        val body = parseBody(response.bodyAsString())
        val result = siteVerifyResult(response.statusCode(), body)

        if (result == null) {
            logger.error(
                "Captcha provider answered ${response.statusCode()} (errors: ${body?.getJsonArray("error-codes")}), " +
                    "bypassing captcha to prevent lockout"
            )
            return true
        }

        if (!result && config.debugLogging) {
            logger.warn("Captcha verification failed. Status: ${response.statusCode()}, errors: ${body?.getJsonArray("error-codes")}")
        }

        return result
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

        val body = parseBody(response.bodyAsString())

        if (config.debugLogging) {
            logger.info("reCAPTCHA Enterprise response (${response.statusCode()}): $body")
        }

        val result = enterpriseResult(response.statusCode(), body)

        if (result == null) {
            val error = body?.getJsonObject("error")
            logger.error(
                "reCAPTCHA Enterprise API error (${response.statusCode()}): ${error?.getString("message") ?: "no message"}, " +
                    "bypassing captcha to prevent lockout"
            )
            return true
        }

        if (!result && config.debugLogging) {
            val tokenProperties = body?.getJsonObject("tokenProperties")
            logger.warn(
                "reCAPTCHA Enterprise: token refused, error: ${body?.getJsonObject("error")?.getString("message")}, " +
                    "reason: ${tokenProperties?.getString("invalidReason")}"
            )
        }

        return result
    }
}
