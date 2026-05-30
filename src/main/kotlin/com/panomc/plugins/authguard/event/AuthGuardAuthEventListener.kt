package com.panomc.plugins.authguard.event

import com.panomc.platform.api.annotation.EventListener
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.api.event.AuthEventListener
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.model.User
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.config.CaptchaProvider
import com.panomc.plugins.authguard.db.dao.TwoFactorSecretDao
import com.panomc.plugins.authguard.service.CaptchaStepTokenService
import com.panomc.plugins.authguard.service.CaptchaVerificationService
import com.panomc.plugins.authguard.service.TwoFactorService
import io.vertx.ext.web.RoutingContext
import io.vertx.sqlclient.SqlClient

@EventListener
class AuthGuardAuthEventListener(
    private val plugin: AuthGuardPlugin
) : AuthEventListener {
    private val logger by lazy {
        plugin.logger
    }

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AuthGuardConfig>
    }

    private val captchaService by lazy {
        plugin.pluginBeanContext.getBean(CaptchaVerificationService::class.java)
    }

    private val captchaStepTokenService by lazy {
        plugin.pluginBeanContext.getBean(CaptchaStepTokenService::class.java)
    }

    private val twoFactorService by lazy {
        plugin.pluginBeanContext.getBean(TwoFactorService::class.java)
    }

    private val twoFactorSecretDao by lazy {
        plugin.pluginBeanContext.getBean(TwoFactorSecretDao::class.java)
    }

    // Captcha check runs BEFORE password verification. Login: only when a password is being submitted
    // (not the "username first" step), including link-only users — captcha is enforced on
    // verifyLinkCode (onBeforeVerifyLinkCode). Register: always when enabled
    // (link-token register bypasses the platform listener entirely).
    override suspend fun onBeforeAuthenticate(
        context: RoutingContext,
        sqlClient: SqlClient
    ): AuthEventListener.LoginDecision? {
        val config = configManager.config
        val body = context.body().asJsonObject()
        val remoteIp = authProvider.getRemoteIP(context)

        if (config.captchaProvider == CaptchaProvider.NONE) {
            return null
        }

        // Phase signal: any flow running the auth hooks (the core APIs, or a plugin like social
        // login that runs its own register/login) can set `pano-auth-phase` to "register"/"login"
        // so we don't have to sniff the URI. Falls back to the URI for the core /api/auth/* routes.
        val phase = context.get<String?>("pano-auth-phase")
        val uri = context.request().uri()
        val isRegister = if (phase != null) phase == "register" else uri.contains("/api/auth/register")
        if (isRegister) {
            if (!config.captchaOnRegister) {
                return null
            }
        } else if (isUnauthenticatedCaptchaPath(uri)) {
            if (!captchaEnabledForPasswordlessPath(config, uri)) {
                return null
            }
        } else {
            if (!config.captchaOnLogin) {
                return null
            }
        }

        val isPasswordlessAuthEndpoint = isUnauthenticatedCaptchaPath(uri)

        val usernameOrEmail = body.getString("usernameOrEmail") ?: ""

        if (!isRegister) {
            val stepToken = body.getString("captchaStepToken")
            if (captchaStepTokenService.isValid(stepToken, usernameOrEmail)) {
                return null
            }
            if (!isPasswordlessAuthEndpoint) {
                val password = body.getString("password")?.trim() ?: ""
                if (password.isEmpty()) {
                    // Username-only step, or link-only before LinkCodeRequired; link flow uses onBeforeVerifyLinkCode
                    return null
                }
            }
        }

        val captchaToken = body.getString("captchaToken")

        if (!captchaService.verify(captchaToken, remoteIp)) {
            if (config.debugLogging) {
                logger.warn("Captcha verification failed from IP $remoteIp")
            }
            return AuthEventListener.LoginDecision.Deny(
                "CAPTCHA_VERIFICATION_FAILED",
                mapOf("reason" to "plugins.pano-plugin-auth-guard.errors.captcha-verification-failed")
            )
        }

        return null
    }

    private fun isUnauthenticatedCaptchaPath(uri: String) =
        uri.contains("/api/auth/resetPassword") ||
            uri.contains("/api/auth/verifyEmail") ||
            uri.contains("/api/auth/verifyNewEmail") ||
            uri.contains("/api/auth/renewPassword")

    private fun captchaEnabledForPasswordlessPath(config: AuthGuardConfig, uri: String): Boolean {
        return when {
            uri.contains("/api/auth/resetPassword") -> config.captchaOnResetPassword
            uri.contains("/api/auth/verifyEmail") -> config.captchaOnVerifyEmail
            uri.contains("/api/auth/verifyNewEmail") -> config.captchaOnVerifyNewEmail
            uri.contains("/api/auth/renewPassword") -> config.captchaOnRenewPassword
            else -> false
        }
    }

    override suspend fun onBeforeVerifyLinkCode(
        context: RoutingContext,
        sqlClient: SqlClient
    ): AuthEventListener.LoginDecision? {
        val config = configManager.config
        if (config.captchaProvider == CaptchaProvider.NONE) {
            return null
        }
        if (!config.captchaOnLogin) {
            return null
        }
        val body = context.body().asJsonObject()
        val remoteIp = authProvider.getRemoteIP(context)
        val captchaToken = body.getString("captchaToken")
        if (!captchaService.verify(captchaToken, remoteIp)) {
            if (config.debugLogging) {
                logger.warn("Captcha verification failed (verify link) from IP $remoteIp")
            }
            return AuthEventListener.LoginDecision.Deny(
                "CAPTCHA_VERIFICATION_FAILED",
                mapOf("reason" to "plugins.pano-plugin-auth-guard.errors.captcha-verification-failed")
            )
        }
        return null
    }

    // 2FA check runs AFTER password verification (requires user object)
    override suspend fun onBeforeLogin(
        user: User,
        context: RoutingContext,
        sqlClient: SqlClient
    ): AuthEventListener.LoginDecision? {
        val config = configManager.config
        val body = context.body().asJsonObject()

        if (config.twoFactorEnabled) {
            val twoFactorSecret = twoFactorSecretDao.getByUserId(user.id, sqlClient)

            if (twoFactorSecret != null && twoFactorSecret.enabled) {
                val totpCode = body.getString("totpCode")

                if (totpCode.isNullOrBlank()) {
                    val uoe = body.getString("usernameOrEmail") ?: ""
                    val extras = mutableMapOf<String, Any?>(
                        "reason" to "plugins.pano-plugin-auth-guard.errors.two-factor-required"
                    )
                    if (config.captchaProvider != CaptchaProvider.NONE && config.captchaOnLogin) {
                        extras["captchaStepToken"] = captchaStepTokenService.mint(user.id, uoe)
                    }
                    return AuthEventListener.LoginDecision.Deny(
                        "TWO_FACTOR_REQUIRED",
                        extras
                    )
                }

                if (!twoFactorService.verifyCode(twoFactorSecret.secret, totpCode)) {
                    return AuthEventListener.LoginDecision.Deny(
                        "TWO_FACTOR_INVALID_CODE",
                        mapOf("reason" to "plugins.pano-plugin-auth-guard.errors.two-factor-invalid-code")
                    )
                }
            }
        }

        return null
    }

    override suspend fun onAfterLogin(
        user: User,
        context: RoutingContext,
        sqlClient: SqlClient
    ) {
        captchaStepTokenService.clearForUserId(user.id)
    }
}
