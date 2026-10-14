package com.panomc.plugins.authguard.mail

import com.panomc.platform.mail.MailManager
import com.panomc.platform.mail.template.MailTemplateContent
import com.panomc.platform.mail.template.MailTemplateIndex
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The plugin's bundled mail templates: a valid index, and every declared variant in every shipped language. */
class MailTemplatesTest {
    private val root = File("src/main/resources/mail-templates")

    private val index = MailTemplateIndex.parse(JsonObject(File(root, "index.json").readText()))

    private val systemVariables = setOf("websiteName", "websiteUrl", "websiteLogo", "year", "locale")

    @Test
    fun `every declared variant has a parsable template in every language, using only declared variables`() {
        assertTrue(index.mails.isNotEmpty())

        index.mails.forEach { mail ->
            mail.variants.forEach { variant ->
                val allowed = systemVariables + variant.variables.map { it.name }

                LOCALES.forEach { locale ->
                    val file = File(root, "${mail.key}/${variant.key}/$locale.hbs")

                    assertTrue(file.isFile, "missing $file")

                    val content = MailTemplateContent.parse(file.readText())

                    assertTrue(content.subject.isNotBlank(), "empty subject in $file")
                    assertTrue(content.body.isNotBlank(), "empty body in $file")

                    val used = VARIABLE.findAll(file.readText()).map { it.groupValues[1] }.toSet()

                    assertTrue(allowed.containsAll(used), "undeclared variables ${used - allowed} in $file")
                }
            }
        }
    }

    @Test
    fun `the magic login mail sends the variables its template declares`() = runBlocking {
        val mail = MagicLoginMail("tok", "steve", "steve@example.com")
        val variant = index.mails.firstOrNull { it.key == mail.key }?.variant(mail.variant)

        assertEquals("pano-plugin-auth-guard", mail.owner)
        assertNotNull(variant)

        val parameters = MailManager.Companion.SystemParameters("My Site", "https://pano.example") { _, _ -> null }
        val variables = mail.variables("en-US", parameters)

        assertEquals(variant!!.variables.map { it.name }.toSet(), variables.keys)
        assertEquals("https://pano.example/auth-guard/magic-login?token=tok", variables["magicLoginLink"])
    }

    companion object {
        val LOCALES = listOf("en-US", "tr", "ru")

        private val VARIABLE = Regex("\\{\\{\\{?\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}?}}")
    }
}
