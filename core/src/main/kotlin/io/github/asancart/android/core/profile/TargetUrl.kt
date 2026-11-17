package io.github.asancart.android.core.profile

import java.net.URI
import java.net.URISyntaxException

/**
 * The base URL of another service's target, as the user types it in Profile setup (REQ-APP-022,
 * REQ-APP-001). The Device API lives under `<baseUrl>/v1/device` (DEC-010).
 *
 * HTTPS only in release builds (REQ-DEV-033: the Device credential travels in every request); debug
 * builds also take `http`, for a local backend (REQ-INF-006). No user info, query or fragment. The
 * address is kept in a normal form: scheme and host in lower case, no trailing `/`, and a typed
 * `/v1/device` suffix dropped.
 */
object TargetUrl {
    sealed interface Result

    data class Valid(val baseUrl: String) : Result

    data class Invalid(val reason: Reason) : Result

    enum class Reason {
        /** Nothing typed. */
        BLANK,

        /** Not an `http(s)://host[:port][/path]` address. */
        INVALID,

        /** `http://` in a build that only takes HTTPS. */
        HTTPS_REQUIRED,
    }

    fun parse(input: String, allowHttp: Boolean): Result {
        val text = input.trim()
        if (text.isEmpty()) return Invalid(Reason.BLANK)
        val uri = try {
            URI(text)
        } catch (e: URISyntaxException) {
            return Invalid(Reason.INVALID)
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return Invalid(Reason.INVALID)
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty() || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            return Invalid(Reason.INVALID)
        }
        if (scheme == "http" && !allowHttp) return Invalid(Reason.HTTPS_REQUIRED)
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.rawPath.orEmpty().trimEnd('/').removeSuffix("/v1/device").trimEnd('/')
        return Valid("$scheme://$host$port$path")
    }
}
