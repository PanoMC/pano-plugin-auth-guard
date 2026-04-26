package com.panomc.plugins.authguard.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository

@Endpoint
class GetAuthGuardConfigPublicAPI(
    private val plugin: AuthGuardPlugin
) : Api() {
    override val paths = listOf(Path("/api/auth-guard/config", RouteType.GET))

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AuthGuardConfig>
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository).build()

    override suspend fun handle(context: RoutingContext): Result {
        val config = configManager.config

        // Only expose safe public fields — never expose secret keys
        return Successful(
            mapOf(
                "captchaProvider" to config.captchaProvider.name,
                "captchaSiteKey" to config.captchaSiteKey,
                "recaptchaLegacy" to config.recaptchaLegacy,
                "captchaOnLogin" to config.captchaOnLogin,
                "captchaOnRegister" to config.captchaOnRegister,
                "captchaOnResetPassword" to config.captchaOnResetPassword,
                "captchaOnVerifyEmail" to config.captchaOnVerifyEmail,
                "captchaOnVerifyNewEmail" to config.captchaOnVerifyNewEmail,
                "captchaOnRenewPassword" to config.captchaOnRenewPassword,
                "twoFactorEnabled" to config.twoFactorEnabled,
                "magicLoginEnabled" to config.magicLoginEnabled
            )
        )
    }
}
