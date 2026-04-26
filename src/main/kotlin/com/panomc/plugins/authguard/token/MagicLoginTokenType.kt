package com.panomc.plugins.authguard.token

import com.panomc.platform.token.TokenType
import java.util.*

/**
 * Magic login token type for passwordless email authentication.
 * Expires in 15 minutes.
 */
object MagicLoginTokenType : TokenType {
    override fun getExpireDate(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 15)
        return calendar.timeInMillis
    }
}
