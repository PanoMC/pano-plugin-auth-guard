package com.panomc.plugins.authguard.token

import com.panomc.platform.token.TokenType
import java.util.*

/**
 * Magic login token type for passwordless email authentication.
 * Expires in [EXPIRE_MINUTES] minutes.
 */
object MagicLoginTokenType : TokenType {
    const val EXPIRE_MINUTES = 15

    override fun getExpireDate(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, EXPIRE_MINUTES)
        return calendar.timeInMillis
    }
}
