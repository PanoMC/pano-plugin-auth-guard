package com.panomc.plugins.authguard.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.model.*
import com.panomc.platform.token.TokenProvider
import java.util.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.error.MagicLoginLinkExpired
import com.panomc.plugins.authguard.token.MagicLoginTokenType
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.Parameters
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.*

@Endpoint
class MagicLoginVerifyAPI(
    private val plugin: AuthGuardPlugin
) : Api() {
    override val paths = listOf(Path("/api/auth-guard/magic-login/verify", RouteType.GET))

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val databaseManager by lazy {
        plugin.applicationContext.getBean(DatabaseManager::class.java)
    }

    private val tokenProvider by lazy {
        plugin.applicationContext.getBean(TokenProvider::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AuthGuardConfig>
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .queryParameter(Parameters.param("token", stringSchema()))
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        val config = configManager.config
        if (!config.magicLoginEnabled) {
            throw com.panomc.platform.error.BadRequest()
        }

        val parameters = getParameters(context)
        val token = parameters.queryParameter("token").string

        val sqlClient = getSqlClient()

        // Validate the magic login token
        if (!tokenProvider.isTokenValid(token, MagicLoginTokenType, sqlClient)) {
            throw MagicLoginLinkExpired()
        }

        val jwt = tokenProvider.parseToken(token)
        val userId = jwt.subject.toLong()

        // Invalidate the token (one-time use)
        tokenProvider.invalidateToken(token, sqlClient)

        // Check user exists and is not banned
        val user = databaseManager.userDao.getById(userId, sqlClient) ?: throw MagicLoginLinkExpired()

        // Login the user
        val username = databaseManager.userDao.getUsernameFromUserId(userId, sqlClient)!!
        val authToken = authProvider.login(username, context, sqlClient)
        databaseManager.userDao.updateLastLoginDate(userId, sqlClient)

        val csrfToken = UUID.randomUUID().toString()
        authProvider.setCookies(context, authToken, csrfToken)

        return Successful(
            mapOf(
                "csrfToken" to csrfToken
            )
        )
    }
}
