package com.panomc.plugins.authguard.routes

import com.panomc.platform.model.Api
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.routes.api.GetAuthGuardConfigPublicAPI
import com.panomc.plugins.authguard.routes.api.MagicLoginCompleteAPI
import com.panomc.plugins.authguard.routes.api.MagicLoginInitAPI
import com.panomc.plugins.authguard.routes.api.MagicLoginVerifyAPI
import com.panomc.plugins.authguard.routes.api.TwoFactorDisableAPI
import com.panomc.plugins.authguard.routes.api.TwoFactorSetupInitAPI
import com.panomc.plugins.authguard.routes.api.TwoFactorSetupVerifyAPI
import com.panomc.plugins.authguard.routes.api.TwoFactorStatusAPI
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The public endpoints describe their answers (doc 04 section 5), and the session answers match the host's session shape. */
class EndpointDocsTest {
    private val plugin = AuthGuardPlugin()

    private val publicEndpoints: List<Api> = listOf(
        MagicLoginInitAPI(plugin),
        MagicLoginVerifyAPI(plugin),
        MagicLoginCompleteAPI(plugin),
        GetAuthGuardConfigPublicAPI(plugin),
        TwoFactorDisableAPI(plugin),
        TwoFactorSetupInitAPI(plugin),
        TwoFactorSetupVerifyAPI(plugin),
        TwoFactorStatusAPI(plugin)
    )

    @Test
    fun `every public endpoint has a usable doc`() {
        publicEndpoints.forEach { api ->
            val doc = api.doc

            assertNotNull(doc, "${api.javaClass.simpleName} has no doc")
            assertNull(doc!!.problem(), api.javaClass.simpleName)
            assertNotNull(doc.responseSchema, api.javaClass.simpleName)
        }
    }

    @Test
    fun `the magic login endpoints answer a cookie session or a site session`() {
        listOf(MagicLoginVerifyAPI(plugin), MagicLoginCompleteAPI(plugin)).forEach { api ->
            val doc = api.doc!!

            assertEquals(emptyList<String>(), doc.check(JsonObject().put("csrfToken", "c")))
            assertEquals(emptyList<String>(), doc.check(JsonObject().put("sessionToken", "eyJ").put("expiresAt", 1791400000000L)))
            assertTrue(doc.check(JsonObject().put("csrfToken", 5)).isNotEmpty())
        }
    }

    @Test
    fun `the magic login link is accepted on a GET for the theme and on a POST for the built-in page`() {
        assertEquals("GET", MagicLoginVerifyAPI(plugin).paths.single().routeType.name)
        assertEquals("POST", MagicLoginCompleteAPI(plugin).paths.single().routeType.name)
        assertEquals(MagicLoginVerifyAPI(plugin).paths.single().url, MagicLoginCompleteAPI(plugin).paths.single().url)
    }
}
