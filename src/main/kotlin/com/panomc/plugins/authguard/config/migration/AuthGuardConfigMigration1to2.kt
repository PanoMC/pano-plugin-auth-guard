package com.panomc.plugins.authguard.config.migration

import com.panomc.platform.annotation.Migration
import com.panomc.platform.api.config.PluginConfigMigration
import io.vertx.core.json.JsonObject

@Migration
class AuthGuardConfigMigration1to2 : PluginConfigMigration(
    1,
    2,
    "Per-page captcha for password reset, email verify, and renew password flows"
) {
    override fun migrate(config: JsonObject) {
        val login = config.getBoolean("captchaOnLogin", true)
        if (!config.containsKey("captchaOnResetPassword")) {
            config.put("captchaOnResetPassword", login)
        }
        if (!config.containsKey("captchaOnVerifyEmail")) {
            config.put("captchaOnVerifyEmail", login)
        }
        if (!config.containsKey("captchaOnVerifyNewEmail")) {
            config.put("captchaOnVerifyNewEmail", login)
        }
        if (!config.containsKey("captchaOnRenewPassword")) {
            config.put("captchaOnRenewPassword", login)
        }
    }
}
