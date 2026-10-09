package com.panomc.plugins.authguard.frontend

import com.panomc.platform.annotation.FallbackPageDefinition
import com.panomc.platform.frontend.FallbackPage
import com.panomc.platform.frontend.FallbackPageEnvironment
import com.panomc.platform.frontend.FrontendUrlMap
import com.panomc.plugins.authguard.AuthGuardPlugin
import io.vertx.ext.web.RoutingContext

/**
 * The link of the magic login mail, for a site whose front-end has not taken the target `auth-guard.magic-login`
 * over (doc 05 section 10.3). The page posts the token of the link to the plugin and opens a cookie session on
 * Pano's host.
 */
@FallbackPageDefinition
class MagicLoginFallbackPage(
    private val plugin: AuthGuardPlugin
) : FallbackPage("magic-login", "fallback/magic-login.hbs") {
    private val environment by lazy { plugin.applicationContext.getBean(FallbackPageEnvironment::class.java) }

    private val urls by lazy { plugin.applicationContext.getBean(FrontendUrlMap::class.java) }

    override suspend fun model(context: RoutingContext): Map<String, Any?> = mapOf(
        "p" to FallbackTexts.of(javaClass.classLoader, "magic-login", environment.locale()),
        "token" to (context.request().getParam("token") ?: ""),
        "endpoint" to "plugin:/magic-login/verify",
        "homeUrl" to environment.homeUrl(),
        "clientConfig" to mapOf("errors" to FallbackTexts.of(javaClass.classLoader, "errors", environment.locale())),
        "loginUrl" to runCatching { urls.url("auth.login") }.getOrNull()
    )
}
