package com.panomc.plugins.authguard.config

import com.panomc.platform.api.config.PluginConfig

class AuthGuardConfig(
    val captchaProvider: CaptchaProvider = CaptchaProvider.NONE,
    val captchaSiteKey: String = "",
    val captchaSecretKey: String = "",
    val captchaProjectId: String = "",
    val recaptchaLegacy: Boolean = false,
    val captchaOnLogin: Boolean = true,
    val captchaOnRegister: Boolean = true,
    val captchaOnResetPassword: Boolean = true,
    val captchaOnVerifyEmail: Boolean = true,
    val captchaOnVerifyNewEmail: Boolean = true,
    val captchaOnRenewPassword: Boolean = true,
    val twoFactorEnabled: Boolean = false,
    val twoFactorIssuer: String = "",
    val magicLoginEnabled: Boolean = false,
    val magicLoginLinkExpireMinutes: Int = 15,
    val debugLogging: Boolean = false,
    version: Int = 2
) : PluginConfig(version)
