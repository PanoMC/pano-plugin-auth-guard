package com.panomc.plugins.authguard.db.model

import com.panomc.platform.db.DBEntity

open class TwoFactorSecret(
    var id: Long = -1,
    val userId: Long = -1,
    val secret: String = "",
    val enabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : DBEntity()
