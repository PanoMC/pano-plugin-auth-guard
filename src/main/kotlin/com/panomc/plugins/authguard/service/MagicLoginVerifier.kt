package com.panomc.plugins.authguard.service

import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.api.event.AuthEventListener
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.error.BadRequest
import com.panomc.platform.error.IpIsBanned
import com.panomc.platform.error.LoginUserIsBanned
import com.panomc.platform.error.PluginDeniedLogin
import com.panomc.platform.error.UsernameRequired
import com.panomc.platform.token.TokenProvider
import com.panomc.platform.util.BanUtil
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
     *
     * The link only proves the mailbox, so the sign-in still runs what a password sign-in runs: the IP ban, the
     * account ban and the plugins' `onBeforeLogin` hooks (two-factor). A hook that asks for more (a 2FA code) keeps
     * the link alive, so the same request sent again with the code in its body (`totpCode`) completes the sign-in.
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

        val activeIpBan = databaseManager.bannedIpDao.getActiveByIp(authProvider.getRemoteIP(context), sqlClient)
        if (activeIpBan != null) {
            throw IpIsBanned(
                extras = mapOf(
                    "reason" to activeIpBan.reason,
                    "bannedUntil" to activeIpBan.bannedUntil
                )
            )
        }

        // Check the user exists
        val user = databaseManager.userDao.getById(userId, sqlClient)
        if (user == null) {
            tokenProvider.invalidateToken(token, sqlClient)
            throw MagicLoginLinkExpired()
        }

        if (BanUtil.isBanned(user)) {
            tokenProvider.invalidateToken(token, sqlClient)
            throw LoginUserIsBanned(extras = mapOf("reason" to user.banMessage, "until" to user.bannedUntil))
        }

        // Step-up challenges (2FA, ...). On a deny the link stays valid so the user can send the code.
        when (val decision = authProvider.runOnBeforeLogin(user, context, sqlClient)) {
            is AuthEventListener.LoginDecision.Deny -> throw PluginDeniedLogin(decision.errorKey, decision.extras)
            is AuthEventListener.LoginDecision.RequireUsername -> {
                tokenProvider.invalidateToken(token, sqlClient)
                throw UsernameRequired(extras = mapOf("userId" to decision.userId))
            }
            else -> {}
        }

        // Invalidate the token (one-time use)
        tokenProvider.invalidateToken(token, sqlClient)

        // Open the session
        val session = authProvider.issueSession(user.username, context, sqlClient)
        databaseManager.userDao.updateLastLoginDate(userId, sqlClient)

        val updatedUser = databaseManager.userDao.getById(userId, sqlClient) ?: user
        authProvider.runOnAfterLogin(updatedUser, context, sqlClient)

        return session
    }
}
