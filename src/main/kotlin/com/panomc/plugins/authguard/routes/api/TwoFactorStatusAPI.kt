package com.panomc.plugins.authguard.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.db.dao.TwoFactorSecretDao
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import com.panomc.platform.schema.EndpointDoc
import io.vertx.json.schema.common.dsl.Schemas.*

@Endpoint
class TwoFactorStatusAPI(
    private val plugin: AuthGuardPlugin
) : LoggedInApi() {
    override val paths = listOf(Path("/2fa/status", RouteType.GET))

    override val doc = EndpointDoc(
        summary = "Tells whether 2FA is enabled on the site and for the signed-in user.",
        tag = "2fa",
        response = objectSchema()
            .requiredProperty("twoFactorGlobalEnabled", booleanSchema())
            .requiredProperty("twoFactorEnabled", booleanSchema())
    )

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AuthGuardConfig>
    }

    private val twoFactorSecretDao by lazy {
        plugin.pluginBeanContext.getBean(TwoFactorSecretDao::class.java)
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository).build()

    override suspend fun handle(context: RoutingContext): Result {
        val config = configManager.config
        val sqlClient = getSqlClient()
        val userId = authProvider.getUserIdFromRoutingContext(context)

        val twoFactorSecret = twoFactorSecretDao.getByUserId(userId, sqlClient)

        return Successful(
            mapOf(
                "twoFactorGlobalEnabled" to config.twoFactorEnabled,
                "twoFactorEnabled" to (twoFactorSecret?.enabled ?: false)
            )
        )
    }
}
