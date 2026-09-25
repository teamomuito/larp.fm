package io.github.teamomuito.scrobbler.lastfm

import java.security.MessageDigest

/** Builds the `api_sig` parameter described at https://www.last.fm/api/authspec#8. */
object LastFmSignature {
    private val UNSIGNED_PARAMS = setOf("format", "callback")

    fun sign(params: Map<String, String>, secret: String): String {
        val payload = buildString {
            for ((name, value) in params.filterKeys { it !in UNSIGNED_PARAMS }.toSortedMap()) {
                append(name).append(value)
            }
            append(secret)
        }
        val digest = MessageDigest.getInstance("MD5").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
