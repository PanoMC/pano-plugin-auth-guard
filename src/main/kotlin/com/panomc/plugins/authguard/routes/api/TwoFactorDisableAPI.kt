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
class TwoFactorDisableAPI(
    private val plugin: AuthGuardPlugin
) : LoggedInApi() {
    override val paths = listOf(Path("/api/auth-guard/2fa/disable", RouteType.POST))

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val databaseManager by lazy {
        plugin.applicationContext.getBean(DatabaseManager::class.java)
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
                        .requiredProperty("password", stringSchema())
                )
            )
            .predicate(RequestPredicate.BODY_REQUIRED)
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        val parameters = getParameters(context)
        val data = parameters.body().jsonObject
        val code = data.getString("code")
        val password = data.getString("password")

        // Require password to disable 2FA
        authProvider.requirePassword(password, context)

        val sqlClient = getSqlClient()
        val userId = authProvider.getUserIdFromRoutingContext(context)

        val twoFactorSecret = twoFactorSecretDao.getByUserId(userId, sqlClient)
            ?: throw com.panomc.platform.error.BadRequest()

        if (!twoFactorSecret.enabled) {
            return Successful()
        }

        // Verify the code before disabling
        if (!twoFactorService.verifyCode(twoFactorSecret.secret, code)) {
            return Successful(mapOf("valid" to false))
        }

        // Delete the 2FA secret
        twoFactorSecretDao.deleteByUserId(userId, sqlClient)

        // Log the activity
        val username = databaseManager.userDao.getUsernameFromUserId(userId, sqlClient)!!
        databaseManager.panelActivityLogDao.add(
            TwoFactorStatusChangedLog(userId, username, false, plugin.pluginId),
            sqlClient
        )

        return Successful(mapOf("valid" to true))
    }
}
