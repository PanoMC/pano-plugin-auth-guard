package com.panomc.plugins.authguard.routes.panel

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.permission.ManageAuthGuardSettingsPermission
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository

@Endpoint
class PanelGetAuthGuardConfigAPI(
    private val plugin: AuthGuardPlugin
) : PanelApi() {
    override val paths = listOf(Path("/api/panel/auth-guard/config", RouteType.GET))

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AuthGuardConfig>
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository).build()

    override suspend fun handle(context: RoutingContext): Result {
        authProvider.requirePermission(ManageAuthGuardSettingsPermission(), context)

        val config = configManager.config
        val result = JsonObject()
            .put("captchaProvider", config.captchaProvider.name)
            .put("captchaSiteKey", config.captchaSiteKey)
            .put("hasSecretKey", config.captchaSecretKey.isNotBlank())
            .put("hasProjectId", config.captchaProjectId.isNotBlank())
            .put("recaptchaLegacy", config.recaptchaLegacy)
            .put("captchaOnLogin", config.captchaOnLogin)
            .put("captchaOnRegister", config.captchaOnRegister)
            .put("captchaOnResetPassword", config.captchaOnResetPassword)
            .put("captchaOnVerifyEmail", config.captchaOnVerifyEmail)
            .put("captchaOnVerifyNewEmail", config.captchaOnVerifyNewEmail)
            .put("captchaOnRenewPassword", config.captchaOnRenewPassword)
            .put("twoFactorEnabled", config.twoFactorEnabled)
            .put("twoFactorIssuer", config.twoFactorIssuer)
            .put("magicLoginEnabled", config.magicLoginEnabled)
            .put("magicLoginLinkExpireMinutes", config.magicLoginLinkExpireMinutes)

        return Successful(result.map)
    }
}
