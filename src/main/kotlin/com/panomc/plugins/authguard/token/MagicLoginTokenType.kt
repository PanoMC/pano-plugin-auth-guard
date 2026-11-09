package com.panomc.plugins.authguard.token

import com.panomc.platform.token.TokenType

/**
 * Magic login token type for passwordless email authentication.
 * Expires after the panel setting `magicLoginLinkExpireMinutes` (clamped to [MIN_MINUTES]..[MAX_MINUTES]).
 */
object MagicLoginTokenType : TokenType {
    const val DEFAULT_MINUTES = 15
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 1440 // the panel field allows up to a day

    /** Reads the configured lifetime; set by the plugin on start. */
    @Volatile
    var configuredMinutes: () -> Int = { DEFAULT_MINUTES }

    fun expireMinutes(): Int = configuredMinutes().coerceIn(MIN_MINUTES, MAX_MINUTES)

    override fun getExpireDate(): Long = System.currentTimeMillis() + expireMinutes() * 60_000L
}
