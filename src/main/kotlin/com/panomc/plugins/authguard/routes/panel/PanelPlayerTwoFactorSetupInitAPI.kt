package com.panomc.plugins.authguard.routes.panel

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.config.ConfigManager
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.error.NoPermission
import com.panomc.platform.error.NotExists
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.db.dao.TwoFactorSecretDao
import com.panomc.plugins.authguard.db.model.TwoFactorSecret
import com.panomc.plugins.authguard.permission.ManagePlayerTwoFactorPermission
import com.panomc.plugins.authguard.service.QrCodeRenderer
import com.panomc.plugins.authguard.service.TwoFactorService
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.Parameters
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.stringSchema

@Endpoint
class PanelPlayerTwoFactorSetupInitAPI(
    private val plugin: AuthGuardPlugin
) : PanelApi() {
    override val paths =
        listOf(Path("/player/:username/2fa/setup-init", RouteType.POST))

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val databaseManager by lazy {
        plugin.applicationContext.getBean(DatabaseManager::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AuthGuardConfig>
    }

    private val twoFactorService by lazy {
        plugin.pluginBeanContext.getBean(TwoFactorService::class.java)
    }

    private val twoFactorSecretDao by lazy {
        plugin.pluginBeanContext.getBean(TwoFactorSecretDao::class.java)
    }

    private val panoConfigManager by lazy {
        plugin.applicationContext.getBean(ConfigManager::class.java)
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .pathParameter(Parameters.param("username", stringSchema()))
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        authProvider.requirePermission(ManagePlayerTwoFactorPermission(), context)

        val config = configManager.config
        if (!config.twoFactorEnabled) {
            throw com.panomc.platform.error.BadRequest()
        }

        val parameters = getParameters(context)
        val username = parameters.pathParameter("username").string
        val sqlClient = getSqlClient()

        val userId = databaseManager.userDao.getUserIdFromUsername(username, sqlClient) ?: throw NotExists()

        // Only an admin may change the two-factor state of an admin.
        if (authProvider.isUserAdmin(userId) &&
            !authProvider.isUserAdmin(authProvider.getUserIdFromRoutingContext(context))
        ) {
            throw NoPermission()
        }

        val existing = twoFactorSecretDao.getByUserId(userId, sqlClient)
        if (existing != null && existing.enabled) {
            return Successful(mapOf("alreadyEnabled" to true))
        }

        val secret = twoFactorService.generateSecret()
        val issuer = config.twoFactorIssuer.ifBlank {
            panoConfigManager.config.websiteName.ifBlank { "Pano" }
        }
        val totpUri = twoFactorService.generateTotpUri(secret, username, issuer)

        twoFactorSecretDao.upsert(
            TwoFactorSecret(
                userId = userId,
                secret = secret,
                enabled = false
            ),
            sqlClient
        )

        return Successful(
            mapOf(
                "secret" to secret,
                "totpUri" to totpUri,
                "qrCode" to QrCodeRenderer.svgDataUrl(totpUri),
                "issuer" to issuer,
                "username" to username
            )
        )
    }
}
