package com.panomc.plugins.authguard.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.error.BadRequest
import com.panomc.platform.model.*
import com.panomc.platform.schema.CoreSchemas
import com.panomc.platform.schema.EndpointDoc
import com.panomc.plugins.authguard.AuthGuardPlugin
import com.panomc.plugins.authguard.error.MagicLoginLinkExpired
import com.panomc.plugins.authguard.service.MagicLoginVerifier
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.Parameters
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.*

/** The link of the magic login mail, followed by the theme's page: `GET` with the token in the query. */
@Endpoint
class MagicLoginVerifyAPI(
    private val plugin: AuthGuardPlugin
) : Api() {
    override val paths = listOf(Path("/magic-login/verify", RouteType.GET))

    override val doc = EndpointDoc(
        summary = "Signs in with the token of a magic login link and uses the token up.",
        tag = "magic-login",
        response = CoreSchemas.session,
        errors = listOf(BadRequest::class, MagicLoginLinkExpired::class)
    )

    private val verifier by lazy { MagicLoginVerifier(plugin) }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .queryParameter(Parameters.param("token", stringSchema()))
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        val parameters = getParameters(context)
        val token = parameters.queryParameter("token").string

        return Successful(verifier.verify(token, context, getSqlClient()))
    }
}
