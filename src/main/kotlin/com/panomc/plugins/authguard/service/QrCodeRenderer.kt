package com.panomc.plugins.authguard.service

import io.nayuki.qrcodegen.QrCode
import java.util.Base64

/**
 * Draws the setup QR code on the server. The `otpauth://` URI holds the 2FA secret, so it never goes to a
 * third-party QR service.
 */
object QrCodeRenderer {
    private const val BORDER = 4

    /** An SVG of [text] as a `data:` URL, ready for an `<img src>`. */
    fun svgDataUrl(text: String): String {
        val svg = svg(text)
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8))
    }

    fun svg(text: String): String {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val size = qr.size + BORDER * 2
        val path = StringBuilder()
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
                    path.append("M").append(x + BORDER).append(',').append(y + BORDER).append("h1v1h-1z")
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" shape-rendering=\"crispEdges\">" +
            "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>" +
            "<path d=\"$path\" fill=\"#000000\"/></svg>"
    }
}
