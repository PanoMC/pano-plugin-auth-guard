package com.panomc.plugins.authguard.routes.panel

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.log.UpdatedAuthGuardSettingsLog
import com.panomc.plugins.authguard.permission.ManageAuthGuardSettingsPermission
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.Bodies
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.*

@Endpoint
class PanelUpdateAuthGuardConfigAPI(
    private val plugin: AuthGuardPlugin
) : PanelApi() {
    override val paths = listOf(Path("/config", RouteType.PUT))

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val databaseManager by lazy {
        plugin.applicationContext.getBean(DatabaseManager::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AuthGuardConfig>
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .body(
                Bodies.json(
                    objectSchema()
                        .optionalProperty("captchaProvider", stringSchema())
                        .optionalProperty("captchaSiteKey", stringSchema())
                        .optionalProperty("captchaSecretKey", stringSchema())
                        .optionalProperty("captchaProjectId", stringSchema())
                        .optionalProperty("recaptchaLegacy", booleanSchema())
                        .optionalProperty("captchaOnLogin", booleanSchema())
                        .optionalProperty("captchaOnRegister", booleanSchema())
                        .optionalProperty("captchaOnResetPassword", booleanSchema())
                        .optionalProperty("captchaOnVerifyEmail", booleanSchema())
                        .optionalProperty("captchaOnVerifyNewEmail", booleanSchema())
                        .optionalProperty("captchaOnRenewPassword", booleanSchema())
                        .optionalProperty("twoFactorEnabled", booleanSchema())
                        .optionalProperty("twoFactorIssuer", stringSchema())
                        .optionalProperty("magicLoginEnabled", booleanSchema())
                        .optionalProperty("magicLoginLinkExpireMinutes", intSchema())
                )
            )
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        authProvider.requirePermission(ManageAuthGuardSettingsPermission(), context)

        val body = context.body().asJsonObject()
        configManager.saveConfig(body)

        // Log the activity
        val sqlClient = getSqlClient()
        val adminUserId = authProvider.getUserIdFromRoutingContext(context)
        val adminUsername = databaseManager.userDao.getUsernameFromUserId(adminUserId, sqlClient)!!

        databaseManager.panelActivityLogDao.add(
            UpdatedAuthGuardSettingsLog(
                adminUserId,
                adminUsername,
                plugin.pluginId
            ),
            sqlClient
        )

        return Successful()
    }
}
