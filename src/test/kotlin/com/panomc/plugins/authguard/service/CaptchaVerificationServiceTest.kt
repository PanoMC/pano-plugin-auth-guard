package com.panomc.plugins.authguard.service

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaptchaVerificationServiceTest {
    private fun errors(vararg codes: String) =
        JsonObject().put("success", false).put("error-codes", JsonArray(codes.toList()))

    @Test
    fun `siteverify passes only a successful answer`() {
        assertEquals(true, CaptchaVerificationService.siteVerifyResult(200, JsonObject().put("success", true)))
        assertEquals(false, CaptchaVerificationService.siteVerifyResult(200, errors("invalid-input-response")))
    }

    @Test
    fun `errors the request causes fail closed`() {
        assertEquals(false, CaptchaVerificationService.siteVerifyResult(200, errors("bad-request")))
        // Non-JSON error page (oversized or malformed request) and client errors.
        assertEquals(false, CaptchaVerificationService.siteVerifyResult(200, null))
        assertEquals(false, CaptchaVerificationService.siteVerifyResult(413, null))
        assertEquals(false, CaptchaVerificationService.siteVerifyResult(400, JsonObject().put("success", true)))
    }

    @Test
    fun `site setup errors and provider outages skip the check`() {
        assertNull(CaptchaVerificationService.siteVerifyResult(200, errors("invalid-input-secret")))
        assertNull(CaptchaVerificationService.siteVerifyResult(503, null))
    }

    @Test
    fun `enterprise refuses request errors and keeps key errors open`() {
        val invalidArgument = JsonObject().put(
            "error",
            JsonObject().put("code", 400).put("message", "Request contains an invalid argument.").put("status", "INVALID_ARGUMENT")
        )
        assertEquals(false, CaptchaVerificationService.enterpriseResult(400, invalidArgument))
        assertEquals(false, CaptchaVerificationService.enterpriseResult(200, null))

        val badKey = JsonObject().put(
            "error",
            JsonObject().put("code", 400).put("message", "API key not valid. Please pass a valid API key.")
        )
        assertNull(CaptchaVerificationService.enterpriseResult(400, badKey))
        assertNull(CaptchaVerificationService.enterpriseResult(403, null))

        val valid = JsonObject().put("tokenProperties", JsonObject().put("valid", true))
        assertEquals(true, CaptchaVerificationService.enterpriseResult(200, valid))
        val invalid = JsonObject().put("tokenProperties", JsonObject().put("valid", false))
        assertEquals(false, CaptchaVerificationService.enterpriseResult(200, invalid))
    }
}
