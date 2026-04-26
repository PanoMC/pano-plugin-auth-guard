package com.panomc.plugins.authguard.log

import com.panomc.platform.db.model.PluginActivityLog
import io.vertx.core.json.JsonObject

class AdminResetPlayerTwoFactorLog(
    adminUserId: Long,
    adminUsername: String,
    targetUserId: Long,
    targetUsername: String,
    pluginId: String
) : PluginActivityLog(
    userId = adminUserId,
    pluginId = pluginId,
    details = JsonObject()
        .put("adminUsername", adminUsername)
        .put("targetUserId", targetUserId)
        .put("targetUsername", targetUsername)
)
