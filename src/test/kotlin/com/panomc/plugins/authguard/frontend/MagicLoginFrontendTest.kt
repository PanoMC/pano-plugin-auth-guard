package com.panomc.plugins.authguard.frontend

import com.panomc.platform.frontend.FallbackPageEnvironment
import com.panomc.platform.frontend.FallbackPageRegistry
import com.panomc.platform.frontend.FrontendTargetsRefusal
import com.panomc.platform.frontend.FrontendUrlInputs
import com.panomc.platform.frontend.FrontendUrlMap
import com.panomc.platform.frontend.ThemeRouteMap
import com.panomc.platform.frontend.UrlSource
import com.panomc.platform.frontend.pages.FallbackPageRenderer
import com.panomc.platform.mail.MailManager
import com.panomc.platform.route.FallbackPageRoute
import com.panomc.platform.ui.FrontendMode
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.mail.MagicLoginMail
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.concurrent.TimeUnit

/**
 * The magic login link on the open front-end (doc 05 sections 10.1 to 10.3): the plugin's
 * `frontend-targets.json` is accepted by the host's URL map, the mail link follows the target, and the built-in page
 * at `/_pano/auth-guard.magic-login` renders from the plugin's own template with the host's route.
 */
class MagicLoginFrontendTest {
    private class FakeInputs : FrontendUrlInputs {
        var mode = FrontendMode.NONE
        var frontend: Map<String, Any?> = emptyMap()

        override fun mode() = mode
        override fun siteUrl() = "https://site.example"
        override fun websiteUrl() = "https://pano.example"
        override fun overrides(): Map<String, String> = emptyMap()
        override fun frontendUrls() = frontend
        override fun themeRoutes(): ThemeRouteMap? = null
    }

    private class FakeEnvironment : FallbackPageEnvironment {
        var locale = "en-US"

        override fun ready() = true
        override suspend fun blockedByMaintenance(context: RoutingContext) = false
        override fun siteName() = "My Site"
        override fun locale() = locale
        override fun logoUrl() = "/api/v1/website-logo"
        override fun faviconUrl() = "/api/v1/favicon"
        override fun homeUrl() = "/"
    }

    private val logger = LoggerFactory.getLogger("test")
    private val inputs = FakeInputs()
    private val environment = FakeEnvironment()
    private val urls = FrontendUrlMap(inputs, logger)
    private val registry = FallbackPageRegistry(urls, logger)
    private val route = FallbackPageRoute(registry, urls, FallbackPageRenderer(environment, logger), environment, logger)

    private lateinit var plugin: AuthGuardPlugin
    private lateinit var vertx: Vertx
    private var port = 0

    private val targetsFile: String
        get() = javaClass.classLoader.getResourceAsStream("frontend-targets.json")!!.use { it.readBytes().toString(Charsets.UTF_8) }

    /** What `PluginManager` does when it loads the plugin: the pages first, then the targets file. */
    private fun loadPlugin() {
        registry.registerPlugin(
            plugin.pluginId,
            NAMESPACE,
            listOf(MagicLoginFallbackPage(plugin)),
            AuthGuardPlugin::class.java.classLoader
        )

        urls.registerPlugin(plugin.pluginId, NAMESPACE, targetsFile)
    }

    @BeforeEach
    fun setUp() {
        plugin = AuthGuardPlugin()

        set(plugin, "pluginId", "pano-plugin-auth-guard")

        val context = AnnotationConfigApplicationContext()

        context.beanFactory.registerSingleton("environment", environment)
        context.beanFactory.registerSingleton("urls", urls)
        context.refresh()

        set(plugin, "applicationContext", context)

        vertx = Vertx.vertx()

        val router = Router.router(vertx)

        route.paths.forEach { path ->
            router.route(path.routeType.vertxHttpMethod, path.url).order(route.order).handler(route.getHandler())
        }

        port = vertx.createHttpServer().requestHandler(router).listen(0, "127.0.0.1").blockingGet().actualPort()
    }

    @AfterEach
    fun tearDown() {
        vertx.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS)
    }

    private fun set(target: Any, field: String, value: Any) {
        val declared = generateSequence<Class<*>>(target.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .first { it.name == field }

        declared.isAccessible = true
        declared.set(target, value)
    }

    private fun <T> Future<T>.blockingGet(): T = toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS)

    private class Answer(val status: Int, val headers: Map<String, String>, val body: String)

    private fun get(path: String): Answer =
        vertx.createHttpClient(HttpClientOptions().setKeepAlive(false)).request(HttpMethod.GET, port, "127.0.0.1", path)
            .compose { it.send() }
            .compose { response ->
                response.body().map { Answer(response.statusCode(), response.headers().entries().associate { e -> e.key.lowercase() to e.value }, it.toString()) }
            }
            .blockingGet()

    // --- the targets file --------------------------------------------------------

    @Test
    fun `the targets file declares the magic login target with a fallback page that creates a session`() {
        loadPlugin()

        val target = urls.targets().first { it.id == "auth-guard.magic-login" }

        assertEquals("pano-plugin-auth-guard", target.owner)
        assertEquals("/auth-guard/magic-login?token={token}", target.defaultPath)
        assertTrue(target.fallback)
        assertTrue(target.createsSession)
        assertTrue(registry.has("auth-guard.magic-login"))
    }

    @Test
    fun `a plugin without its fallback page fails the load, naming the target`() {
        val refusal = assertThrows<FrontendTargetsRefusal> { urls.registerPlugin("pano-plugin-auth-guard", NAMESPACE, targetsFile) }

        assertTrue(refusal.message!!.contains("auth-guard.magic-login"))
    }

    @Test
    fun `unloading the plugin drops the page and the target`() {
        loadPlugin()

        registry.unregisterPlugin(plugin.pluginId)
        urls.unregisterPlugin(plugin.pluginId)

        assertFalse(registry.has("auth-guard.magic-login"))
        assertEquals(404, get("/_pano/auth-guard.magic-login?token=t").status)
    }

    // --- the mail link ----------------------------------------------------------------

    private fun link(token: String = "tok en"): String {
        val parameters = MailManager.Companion.SystemParameters("My Site", "https://pano.example") { target, params -> urls.url(target, params) }

        return MagicLoginMail(token, "steve", "steve@example.com").magicLoginLink(parameters)
    }

    @Test
    fun `the mail link goes to the built-in page while no front-end claims the target`() {
        loadPlugin()

        assertEquals("https://pano.example/_pano/auth-guard.magic-login?token=tok%20en", link())
    }

    @Test
    fun `the mail link follows a theme to the old path and a front-end that takes the target over`() {
        loadPlugin()

        inputs.mode = FrontendMode.THEME

        assertEquals("https://site.example/auth-guard/magic-login?token=tok%20en", link())

        inputs.mode = FrontendMode.CUSTOM_APP
        inputs.frontend = mapOf("auth-guard.magic-login" to "/sign-in/magic?token={token}")

        assertEquals("https://site.example/sign-in/magic?token=tok%20en", link())
        assertEquals(UrlSource.FRONTEND, urls.targets().first { it.id == "auth-guard.magic-login" }.source)
    }

    @Test
    fun `a front-end that says it has no such page leaves the visitor on the built-in page`() {
        loadPlugin()

        inputs.mode = FrontendMode.CUSTOM_APP
        inputs.frontend = mapOf("auth-guard.magic-login" to false)

        assertEquals("https://pano.example/_pano/auth-guard.magic-login?token=abc", link("abc"))
    }

    // --- the page -------------------------------------------------------------------

    @Test
    fun `the page posts the token of the link to the plugin and opens the session`() {
        loadPlugin()

        val answer = get("/_pano/auth-guard.magic-login?token=abc123")

        assertEquals(200, answer.status)
        assertTrue(answer.body.contains("""data-pano-endpoint="plugin:/magic-login/verify""""))
        assertTrue(answer.body.contains("data-pano-auto"))
        assertTrue(answer.body.contains("""name="token" value="abc123""""))
        assertTrue(answer.body.contains("Signing you in"))
        assertTrue(answer.body.contains("""href="/">Continue"""))
    }

    @Test
    fun `the page escapes the token and speaks the site language`() {
        loadPlugin()

        environment.locale = "tr"

        val answer = get("/_pano/auth-guard.magic-login?token=%22%3E%3Cscript%3E")

        assertEquals(200, answer.status)
        assertFalse(answer.body.contains("\"><script>"))
        assertTrue(answer.body.contains("Giriş yapılıyor"))
    }

    @Test
    fun `a front-end that claims the target gets the visitor with the query`() {
        loadPlugin()

        inputs.frontend = mapOf("auth-guard.magic-login" to "/sign-in/magic?token={token}")

        val answer = get("/_pano/auth-guard.magic-login?token=abc")

        assertEquals(302, answer.status)
        assertEquals("https://site.example/sign-in/magic?token=abc", answer.headers["location"])
        assertNotNull(answer.headers["cache-control"])
    }

    private companion object {
        const val NAMESPACE = "auth-guard"
    }
}
