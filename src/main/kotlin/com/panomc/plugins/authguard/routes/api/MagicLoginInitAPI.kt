package com.panomc.plugins.authguard.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.mail.MailManager
import com.panomc.platform.model.*
import com.panomc.platform.token.TokenProvider
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.mail.MagicLoginMail
import com.panomc.plugins.authguard.token.MagicLoginTokenType
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.RequestPredicate
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.Bodies
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.*

@Endpoint
class MagicLoginInitAPI(
    private val plugin: AuthGuardPlugin
) : Api() {
    override val paths = listOf(Path("/api/auth-guard/magic-login/init", RouteType.POST))

    private val databaseManager by lazy {
        plugin.applicationContext.getBean(DatabaseManager::class.java)
    }

    private val tokenProvider by lazy {
        plugin.applicationContext.getBean(TokenProvider::class.java)
    }

    private val mailManager by lazy {
        plugin.applicationContext.getBean(MailManager::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AuthGuardConfig>
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .body(
                Bodies.json(
                    objectSchema()
                        .requiredProperty("email", stringSchema())
                )
            )
            .predicate(RequestPredicate.BODY_REQUIRED)
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        val config = configManager.config
        if (!config.magicLoginEnabled) {
            throw com.panomc.platform.error.BadRequest()
        }

        val parameters = getParameters(context)
        val data = parameters.body().jsonObject
        val email = data.getString("email")

        val sqlClient = getSqlClient()

        // Check if user exists with this email (getUserIdFromUsernameOrEmail accepts both)
        val userId = databaseManager.userDao.getUserIdFromUsernameOrEmail(email, sqlClient)

        // Always return success to prevent email enumeration
        if (userId == null) {
            return Successful()
        }

        val username = databaseManager.userDao.getUsernameFromUserId(userId, sqlClient)!!

        // Invalidate old magic login tokens for this user
        tokenProvider.invalidateTokensBySubjectAndType(userId.toString(), MagicLoginTokenType, sqlClient)

        // Generate new token (expiration is defined by MagicLoginTokenType = 15 min)
        val (token, expireDate) = tokenProvider.generateToken(
            userId.toString(),
            MagicLoginTokenType
        )

        tokenProvider.saveToken(token, userId.toString(), MagicLoginTokenType, expireDate, sqlClient)

        // Send magic login email
        mailManager.sendMail(
            sqlClient,
            userId,
            MagicLoginMail(token, username, email)
        )

        return Successful()
    }
}
