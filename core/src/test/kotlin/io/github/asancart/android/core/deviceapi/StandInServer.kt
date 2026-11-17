package io.github.asancart.android.core.deviceapi

import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A local HTTP server for adapter tests (JDK `com.sun.net.httpserver`, no extra library). Answers
 * queued with [answer] go out in order, the last one repeats; every request is recorded.
 */
class StandInServer : Closeable {
    data class Recorded(val line: String, val headers: Map<String, String>, val body: String)

    private class Answer(val status: Int, val body: String, val headers: List<Pair<String, String>>)

    private val answers = ConcurrentLinkedQueue<Answer>()

    @Volatile
    private var last: Answer = Answer(404, "", emptyList())

    val requests: MutableList<Recorded> = CopyOnWriteArrayList()

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val headers = ex.requestHeaders.entries.associate { (k, v) -> k.lowercase() to v.last() }
            requests += Recorded("${ex.requestMethod} ${ex.requestURI.rawPath}${ex.requestURI.rawQuery?.let { "?$it" }.orEmpty()}", headers, body)
            val a = answers.poll()?.also { last = it } ?: last
            for ((k, v) in a.headers) ex.responseHeaders.add(k, v)
            val bytes = a.body.toByteArray(Charsets.UTF_8)
            if (bytes.isNotEmpty()) ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(a.status, if (a.status == 204 || bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty() && a.status != 204) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun answer(status: Int, body: String, vararg headers: Pair<String, String>) {
        answers += Answer(status, body, headers.toList())
    }

    override fun close() = server.stop(0)
}
