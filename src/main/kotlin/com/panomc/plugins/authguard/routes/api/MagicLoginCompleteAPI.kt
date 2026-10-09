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
import io.vertx.ext.web.validation.RequestPredicate
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.Bodies
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.*

/**
 * The same sign-in as [MagicLoginVerifyAPI] with the token in a JSON body. The built-in fallback page
 * (`/_pano/auth-guard.magic-login`) and server-side front-ends post here: a link in a mail must not change state
 * on a plain `GET` that a mail scanner may follow.
 */
@Endpoint
class MagicLoginCompleteAPI(
    private val plugin: AuthGuardPlugin
) : Api() {
    override val paths = listOf(Path("/magic-login/verify", RouteType.POST))

    override val doc = EndpointDoc(
        summary = "Signs in with the token of a magic login link, sent in the body, and uses the token up.",
        tag = "magic-login",
        response = CoreSchemas.session,
        errors = listOf(BadRequest::class, MagicLoginLinkExpired::class)
    )

    private val verifier by lazy { MagicLoginVerifier(plugin) }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .body(
                Bodies.json(
                    objectSchema()
                        .requiredProperty("token", stringSchema())
                )
            )
            .predicate(RequestPredicate.BODY_REQUIRED)
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        val parameters = getParameters(context)
        val token = parameters.body().jsonObject.getString("token")

        return Successful(verifier.verify(token, context, getSqlClient()))
    }
}
