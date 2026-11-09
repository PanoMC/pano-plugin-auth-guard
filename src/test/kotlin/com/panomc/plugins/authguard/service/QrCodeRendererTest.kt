package com.panomc.plugins.authguard.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class QrCodeRendererTest {
    @Test
    fun `the QR code is an inline SVG, not a link to a QR service`() {
        val url = QrCodeRenderer.svgDataUrl("otpauth://totp/Pano:alice?secret=JBSWY3DPEHPK3PXP&issuer=Pano")

        assertTrue(url.startsWith("data:image/svg+xml;base64,"))
        val svg = String(Base64.getDecoder().decode(url.substringAfter(",")))
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("<path d=\"M"))
        assertTrue(!svg.contains("JBSWY3DPEHPK3PXP"))
    }
}
