package com.panomc.plugins.authguard.service

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived server tokens proving captcha was already passed, so the client can complete 2FA
 * without reusing a one-time provider captcha response.
 */
class CaptchaStepTokenService {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val entries = ConcurrentHashMap<String, StepEntry>()

    companion object {
        /** How many requests one token may stand in for the captcha: the 2FA step plus a few retries. */
        const val MAX_USES = 5
    }

    data class StepEntry(
        val userId: Long,
        val loginKey: String,
        val expiresAtMs: Long,
        val uses: Int = 0
    )

    private fun normalizeLoginKey(s: String) = s.trim().lowercase()

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        for (key in entries.keys.toList()) {
            val e = entries[key] ?: continue
            if (e.expiresAtMs < now) {
                entries.remove(key, e)
            }
        }
    }

    /**
     * @param ttlMs default 5 minutes
     */
    fun mint(userId: Long, usernameOrEmail: String, ttlMs: Long = 5 * 60 * 1000L): String {
        purgeExpired()
        val loginKey = normalizeLoginKey(usernameOrEmail)

        // One live token per user and login: asking again hands back the same token, so its use count is not reset.
        entries.entries.firstOrNull { it.value.userId == userId && it.value.loginKey == loginKey }?.let {
            return it.key
        }

        val tokenBytes = ByteArray(32)
        random.nextBytes(tokenBytes)
        val token = encoder.encodeToString(tokenBytes)
        entries[token] = StepEntry(userId, loginKey, System.currentTimeMillis() + ttlMs)
        return token
    }

    /**
     * Returns true if the token is valid for this login identifier. Each call uses it once; it is gone after
     * [MAX_USES] uses, on expiry or on [clearForUserId].
     */
    fun isValid(token: String?, usernameOrEmail: String): Boolean {
        if (token.isNullOrBlank()) return false
        purgeExpired()
        val entry = entries[token] ?: return false
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            entries.remove(token, entry)
            return false
        }
        if (normalizeLoginKey(usernameOrEmail) != entry.loginKey) return false

        val used = entry.copy(uses = entry.uses + 1)
        if (used.uses >= MAX_USES) {
            return entries.remove(token, entry)
        }
        return entries.replace(token, entry, used)
    }

    fun clearForUserId(userId: Long) {
        for (key in entries.keys.toList()) {
            val e = entries[key] ?: continue
            if (e.userId == userId) {
                entries.remove(key, e)
            }
        }
    }
}
