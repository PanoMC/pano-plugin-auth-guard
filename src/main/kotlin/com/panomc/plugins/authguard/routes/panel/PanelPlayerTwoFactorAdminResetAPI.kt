package com.panomc.plugins.authguard.routes.panel

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.error.NoPermission
import com.panomc.platform.error.NotExists
import com.panomc.platform.model.*
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.db.dao.TwoFactorSecretDao
import com.panomc.plugins.authguard.log.AdminResetPlayerTwoFactorLog
import com.panomc.plugins.authguard.permission.ManagePlayerTwoFactorPermission
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.Parameters
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.stringSchema

@Endpoint
class PanelPlayerTwoFactorAdminResetAPI(
    private val plugin: AuthGuardPlugin
) : PanelApi() {
    override val paths = listOf(Path("/player/:username/2fa/reset", RouteType.POST))

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val databaseManager by lazy {
        plugin.applicationContext.getBean(DatabaseManager::class.java)
    }

    private val twoFactorSecretDao by lazy {
        plugin.pluginBeanContext.getBean(TwoFactorSecretDao::class.java)
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .pathParameter(Parameters.param("username", stringSchema()))
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        authProvider.requirePermission(ManagePlayerTwoFactorPermission(), context)

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

        val row = twoFactorSecretDao.getByUserId(userId, sqlClient)
        if (row == null) {
            return Successful(mapOf("removed" to false))
        }

        twoFactorSecretDao.deleteByUserId(userId, sqlClient)

        val adminUserId = authProvider.getUserIdFromRoutingContext(context)
        val adminUsername = databaseManager.userDao.getUsernameFromUserId(adminUserId, sqlClient)!!

        databaseManager.panelActivityLogDao.add(
            AdminResetPlayerTwoFactorLog(
                adminUserId,
                adminUsername,
                userId,
                username,
                plugin.pluginId
            ),
            sqlClient
        )

        return Successful(mapOf("removed" to true))
    }
}
