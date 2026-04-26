package com.panomc.plugins.authguard.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.db.dao.TwoFactorSecretDao
import com.panomc.plugins.authguard.log.TwoFactorStatusChangedLog
import com.panomc.plugins.authguard.service.TwoFactorService
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.RequestPredicate
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.Bodies
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.*

@Endpoint
class TwoFactorSetupVerifyAPI(
    private val plugin: AuthGuardPlugin
) : LoggedInApi() {
    override val paths = listOf(Path("/api/auth-guard/2fa/verify-setup", RouteType.POST))

    private val logger = org.slf4j.LoggerFactory.getLogger(TwoFactorSetupVerifyAPI::class.java)

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

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .body(
                Bodies.json(
                    objectSchema()
                        .requiredProperty("code", stringSchema())
                )
            )
            .predicate(RequestPredicate.BODY_REQUIRED)
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        val config = configManager.config
        if (!config.twoFactorEnabled) {
            throw com.panomc.platform.error.BadRequest()
        }

        val parameters = getParameters(context)
        val data = parameters.body().jsonObject
        val code = data.getString("code")

        val sqlClient = getSqlClient()
        val userId = authProvider.getUserIdFromRoutingContext(context)

        val twoFactorSecret = twoFactorSecretDao.getByUserId(userId, sqlClient)
            ?: throw com.panomc.platform.error.BadRequest()

        if (twoFactorSecret.enabled) {
            return Successful(mapOf("alreadyEnabled" to true))
        }

        if (config.debugLogging) {
            logger.warn(
                "[auth-guard] 2FA verify-setup. userId={} dbSecret='{}' inputCode='{}'",
                userId, twoFactorSecret.secret, code
            )
        }

        // Verify the code
        if (!twoFactorService.verifyCode(twoFactorSecret.secret, code)) {
            return Successful(mapOf("valid" to false))
        }

        // Enable 2FA
        twoFactorSecretDao.setEnabled(userId, true, sqlClient)

        // Log the activity
        val username = databaseManager.userDao.getUsernameFromUserId(userId, sqlClient)!!
        databaseManager.panelActivityLogDao.add(
            TwoFactorStatusChangedLog(userId, username, true, plugin.pluginId),
            sqlClient
        )

        return Successful(mapOf("valid" to true))
    }
}
