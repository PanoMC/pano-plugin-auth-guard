package com.panomc.plugins.authguard.mail

import com.panomc.platform.db.model.Translation.Companion.TranslationType
import com.panomc.platform.i18n.I18nManager
import com.panomc.platform.mail.Mail
import com.panomc.platform.mail.MailManager
import com.panomc.platform.mail.MailParameters

class MagicLoginMail(
    private val token: String,
    private val username: String,
    private val email: String
) : Mail {
    override val templatePath = "mail/magic-login.hbs"
    override val subject = "mail.magic-login.subject"
    override val subjectTranslationType = TranslationType.PLUGIN

    override fun getTemplateContent(): String {
        val resourceStream = MagicLoginMail::class.java.classLoader.getResourceAsStream(templatePath)
            ?: throw IllegalStateException("Template not found: $templatePath")
        return resourceStream.bufferedReader().use { it.readText() }
    }

    override suspend fun generateParameters(
        systemParameters: MailManager.Companion.SystemParameters,
        i18nManager: I18nManager,
        locale: String
    ): MailParameters {
        val translations = getTranslations(i18nManager, locale, "mail.magic-login")

        val magicLoginLink = "${systemParameters.websiteUrl}/auth-guard/magic-login?token=$token"

        return MagicLoginMailParameters(
            username = username,
            magicLoginLink = magicLoginLink,
            translation = translations
        )
    }

    override suspend fun getTranslations(
        i18nManager: I18nManager,
        locale: String,
        groupPrefix: String,
        variables: Map<String, Map<String, Any?>>
    ): Map<String, String> {
        val allTranslations = i18nManager.getTranslations(TranslationType.PLUGIN, locale)

        return allTranslations.keys
            .filter { it.startsWith("$groupPrefix.") }
            .associate { fullKey ->
                val resultKey = fullKey.removePrefix("$groupPrefix.")
                val keyVariables = variables[resultKey] ?: emptyMap()
                val translation = i18nManager.translate(TranslationType.PLUGIN, locale, fullKey, keyVariables)!!
                resultKey to translation
            }
    }

    companion object {
        data class MagicLoginMailParameters(
            val username: String,
            val magicLoginLink: String,
            val translation: Map<String, Any?>
        ) : MailParameters
    }
}
