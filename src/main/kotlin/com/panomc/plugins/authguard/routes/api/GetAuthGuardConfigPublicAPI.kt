package com.panomc.plugins.authguard.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import com.panomc.platform.schema.EndpointDoc
import io.vertx.json.schema.common.dsl.Schemas.*

@Endpoint
class GetAuthGuardConfigPublicAPI(
    private val plugin: AuthGuardPlugin
) : Api() {
    override val paths = listOf(Path("/config", RouteType.GET))

    override val doc = EndpointDoc(
        summary = "Reads the public sign-in guard settings: captcha provider and site key, and which flows use it, 2FA and magic login.",
        tag = "config",
        response = objectSchema()
            .requiredProperty("captchaProvider", stringSchema())
            .requiredProperty("captchaSiteKey", stringSchema())
            .requiredProperty("recaptchaLegacy", booleanSchema())
            .requiredProperty("captchaOnLogin", booleanSchema())
            .requiredProperty("captchaOnRegister", booleanSchema())
            .requiredProperty("captchaOnResetPassword", booleanSchema())
            .requiredProperty("captchaOnVerifyEmail", booleanSchema())
            .requiredProperty("captchaOnVerifyNewEmail", booleanSchema())
            .requiredProperty("captchaOnRenewPassword", booleanSchema())
            .requiredProperty("twoFactorEnabled", booleanSchema())
            .requiredProperty("magicLoginEnabled", booleanSchema())
    )

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
