package com.panomc.plugins.authguard.service

import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.error.BadRequest
import com.panomc.platform.token.TokenProvider
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.error.MagicLoginLinkExpired
import com.panomc.plugins.authguard.token.MagicLoginTokenType
import io.vertx.ext.web.RoutingContext
import io.vertx.sqlclient.SqlClient

/**
 * Turns the token of a magic login link into a session. Shared by the two endpoints that accept the link
 * (`GET` with the token in the query, for the theme page, and `POST` with the token in the body, for the built-in
 * fallback page), so both open the session the same way (doc 05 section 3.3).
 */
class MagicLoginVerifier(
    private val plugin: AuthGuardPlugin
) {
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

    /**
     * Uses up [token] and opens a session for its user. The answer is [AuthProvider.issueSession]'s: `csrfToken`
     * (cookies set) for a normal request, `sessionToken` and `expiresAt` for a front-end with a stored key.
     */
    suspend fun verify(token: String, context: RoutingContext, sqlClient: SqlClient): Map<String, Any?> {
        if (!configManager.config.magicLoginEnabled) {
            throw BadRequest()
        }

        // Validate the magic login token
        if (!tokenProvider.isTokenValid(token, MagicLoginTokenType, sqlClient)) {
            throw MagicLoginLinkExpired()
        }

        val jwt = tokenProvider.parseToken(token)
        val userId = jwt.subject.toLong()

        // Invalidate the token (one-time use)
        tokenProvider.invalidateToken(token, sqlClient)

        // Check the user exists
        databaseManager.userDao.getById(userId, sqlClient) ?: throw MagicLoginLinkExpired()

        // Open the session
        val username = databaseManager.userDao.getUsernameFromUserId(userId, sqlClient)!!
        val session = authProvider.issueSession(username, context, sqlClient)
        databaseManager.userDao.updateLastLoginDate(userId, sqlClient)

        return session
    }
}
