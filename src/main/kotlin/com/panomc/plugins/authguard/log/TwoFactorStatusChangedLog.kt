package com.panomc.plugins.authguard.log

import com.panomc.platform.db.model.PluginActivityLog
import io.vertx.core.json.JsonObject

class TwoFactorStatusChangedLog(
    userId: Long,
    username: String,
    enabled: Boolean,
    pluginId: String
) : PluginActivityLog(
    userId = userId,
    pluginId = pluginId,
    details = JsonObject()
        .put("username", username)
        .put("enabled", enabled)
)
