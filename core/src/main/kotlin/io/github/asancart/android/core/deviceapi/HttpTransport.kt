package io.github.asancart.android.core.deviceapi

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** One HTTP exchange. Blocking; [HttpDeviceApi] calls it off the main thread. */
fun interface HttpTransport {
    /** Throws [IOException] when the target can't be reached or the connection breaks. */
    fun execute(request: HttpRequest): HttpResponse
}

/** [headers] may hold the Device credential, so [toString] leaves them and the body out (REQ-OPS-003). */
class HttpRequest(val method: String, val url: String, val headers: Map<String, String>, val body: ByteArray?) {
    override fun toString(): String = "HttpRequest($method $url)"
}

/** Header names are lower case. */
class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
    fun header(name: String): String? = headers[name.lowercase()]

    val text: String get() = body.toString(Charsets.UTF_8)
}

/**
 * [HttpTransport] over [HttpURLConnection], on the JVM and on Android, with no library and no Google
 * services (REQ-APP-041). Redirects are not followed, so a credential never goes to another address
 * than the target's (a `3xx` is answered as an error). Nothing is cached.
 */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxBodyBytes: Int = 8 * 1024 * 1024,
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val c = URI(request.url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = request.method
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            for ((k, v) in request.headers) c.setRequestProperty(k, v)
            val body = request.body
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val status = c.responseCode
            val stream = if (status >= 400) c.errorStream else runCatching { c.inputStream }.getOrNull()
            val bytes = stream?.use { readCapped(it) } ?: ByteArray(0)
            val headers = c.headerFields.orEmpty()
                .filterKeys { it != null }
                .mapNotNull { (k, v) -> v.lastOrNull()?.let { k.lowercase() to it } }
                .toMap()
            return HttpResponse(status, headers, bytes)
        } finally {
            c.disconnect()
        }
    }

    private fun readCapped(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > maxBodyBytes) throw IOException("response body over $maxBodyBytes bytes")
        }
        return out.toByteArray()
    }
}
