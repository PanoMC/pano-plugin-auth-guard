package com.panomc.plugins.authguard

import com.panomc.platform.api.PanoPlugin
import com.panomc.platform.api.PluginDatabaseManager
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.setup.SetupManager
import com.panomc.platform.token.TokenTypeRegistry
import com.panomc.plugins.authguard.config.AuthGuardConfig
import com.panomc.plugins.authguard.service.CaptchaStepTokenService
import com.panomc.plugins.authguard.service.CaptchaVerificationService
import com.panomc.plugins.authguard.service.TwoFactorService
import com.panomc.plugins.authguard.token.MagicLoginTokenType
import io.vertx.core.Vertx

class AuthGuardPlugin : PanoPlugin() {
    private val pluginDatabaseManager by lazy {
        applicationContext.getBean(PluginDatabaseManager::class.java)
    }

    private val setupManager by lazy {
        applicationContext.getBean(SetupManager::class.java)
    }

    private val tokenTypeRegistry by lazy {
        applicationContext.getBean(TokenTypeRegistry::class.java)
    }

    private var isInitialized = false

    override suspend fun onStart() {
        logger.info("Starting...")

        startPlugin()
    }

    internal suspend fun startPlugin() {
        if (isInitialized) return

        if (!setupManager.isSetupDone()) {
            logger.info("Setup is not finished, waiting for setup completion...")
            return
        }

        isInitialized = true

        // Register config
        val configManager = PluginConfigManager(this, AuthGuardConfig::class.java)
        pluginBeanContext.beanFactory.registerSingleton(PluginConfigManager::class.java.name, configManager)

        // Register services
        val vertx = applicationContext.getBean(Vertx::class.java)
        val captchaService = CaptchaVerificationService(configManager, vertx)
        pluginBeanContext.beanFactory.registerSingleton(CaptchaVerificationService::class.java.name, captchaService)

        val captchaStepTokenService = CaptchaStepTokenService()
        pluginBeanContext.beanFactory.registerSingleton(CaptchaStepTokenService::class.java.name, captchaStepTokenService)

        val twoFactorService = TwoFactorService()
        twoFactorService.debugLogging = configManager.config.debugLogging
        pluginBeanContext.beanFactory.registerSingleton(TwoFactorService::class.java.name, twoFactorService)

        // Initialize database
        pluginDatabaseManager.initialize(this)

        // Register custom token types (host unregisters on plugin unload)
        tokenTypeRegistry.registerPluginToken(pluginId, MagicLoginTokenType)

        logger.info("Started!")
    }

    override suspend fun onEnable() {
        logger.info("Enabled!")
    }

    override suspend fun onDisable() {
        isInitialized = false
    }

    override suspend fun onUninstall() {
        logger.info("Uninstalling...")
        pluginDatabaseManager.uninstall(this)
    }
}
