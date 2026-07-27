package com.panomc.plugins.authguard.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.config.ConfigManager
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.db.dao.TwoFactorSecretDao
import com.panomc.plugins.authguard.db.model.TwoFactorSecret
import com.panomc.plugins.authguard.service.TwoFactorService
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository

@Endpoint
class TwoFactorSetupInitAPI(
    private val plugin: AuthGuardPlugin
) : LoggedInApi() {
    override val paths = listOf(Path("/api/auth-guard/2fa/setup", RouteType.POST))

    private val logger = org.slf4j.LoggerFactory.getLogger(TwoFactorSetupInitAPI::class.java)

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
        ValidationHandlerBuilder.create(schemaRepository).build()

    override suspend fun handle(context: RoutingContext): Result {
        val config = configManager.config
        if (!config.twoFactorEnabled) {
            throw com.panomc.platform.error.BadRequest()
        }

        val sqlClient = getSqlClient()
        val userId = authProvider.getUserIdFromRoutingContext(context)
        val username = databaseManager.userDao.getUsernameFromUserId(userId, sqlClient)!!

        // Check if already enabled
        val existing = twoFactorSecretDao.getByUserId(userId, sqlClient)
        if (existing != null && existing.enabled) {
            return Successful(mapOf("alreadyEnabled" to true))
        }

        // Generate new secret
        val secret = twoFactorService.generateSecret()
        val issuer = config.twoFactorIssuer.ifBlank {
            panoConfigManager.config.websiteName.ifBlank { "Pano" }
        }
        val totpUri = twoFactorService.generateTotpUri(secret, username, issuer)

        // Save (upsert) the secret but don't enable yet
        twoFactorSecretDao.upsert(
            TwoFactorSecret(
                userId = userId,
                secret = secret,
                enabled = false
            ),
            sqlClient
        )

        if (config.debugLogging) {
            // Read it back to verify what actually landed in DB
            val stored = twoFactorSecretDao.getByUserId(userId, sqlClient)
            logger.warn(
                "[auth-guard] 2FA setup. userId={} generatedSecret='{}' storedSecret='{}' match={} totpUri='{}'",
                userId, secret, stored?.secret, stored?.secret == secret, totpUri
            )
        }

        return Successful(
            mapOf(
                "secret" to secret,
                "totpUri" to totpUri,
                "issuer" to issuer,
                "username" to username
            )
        )
    }
}
