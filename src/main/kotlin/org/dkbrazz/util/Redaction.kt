package org.dkbrazz.util

/**
 * Query parameters whose values are credentials: the Shelly OAuth authorization `code`
 * and the WebSocket access token `t`. Both appear in URLs that end up in exception messages.
 */
private val SECRET_QUERY_PARAMS = Regex("([?&](?:code|t)=)[^&\\s\"']+")

fun redactSecrets(text: String?): String? =
    text?.let { SECRET_QUERY_PARAMS.replace(it, "$1<redacted>") }

/** Short, log-safe description of a throwable that never echoes credentials embedded in URLs. */
fun describeSafely(throwable: Throwable): String {
    val message = redactSecrets(throwable.message)
    return if (message.isNullOrBlank()) throwable.javaClass.simpleName else message
}
