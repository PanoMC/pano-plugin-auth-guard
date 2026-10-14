package com.panomc.plugins.authguard.mail

import com.panomc.platform.mail.MailManager
import com.panomc.platform.mail.TemplateMail
import com.panomc.plugins.authguard.token.MagicLoginTokenType

/** The magic login mail: template `magic-login` of this plugin (`mail-templates/magic-login/default/<locale>.hbs`). */
class MagicLoginMail(
    private val token: String,
    private val username: String,
    @Suppress("unused") private val email: String
) : TemplateMail(OWNER, KEY) {
    override suspend fun variables(locale: String, system: MailManager.Companion.SystemParameters): Map<String, Any?> =
        mapOf(
            "username" to username,
            "magicLoginLink" to magicLoginLink(system),
            "expireMinutes" to MagicLoginTokenType.EXPIRE_MINUTES
        )

    /** Where the visitor opens the session: target `auth-guard.magic-login` of the front-end URL map (frontend-targets.json). */
    internal fun magicLoginLink(systemParameters: MailManager.Companion.SystemParameters) =
        systemParameters.linkTo("auth-guard.magic-login", mapOf("token" to token), "/auth-guard/magic-login?token=$token")

    companion object {
        const val OWNER = "pano-plugin-auth-guard"
        const val KEY = "magic-login"
    }
}
