package io.github.asancart.android.core.deviceapi

import io.github.asancart.android.core.queue.TransferReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant

/**
 * The Device API over HTTP (`api/device-api.md`, DEC-010, T4.3) for one API target: the Platform or
 * another service implementing the contract. [baseUrl] is the target's address; the API lives under
 * `<baseUrl>/v1/device`. [credential] is the Device credential from Pair (REQ-DEV-030), null before
 * pairing: an authenticated call without one answers `401` here without a request.
 *
 * Every answer maps to [ApiResult]: a 2xx with the documented body is [ApiResult.Ok]; any other
 * status is [ApiResult.Error] with the REQ-DEV-031 body's `code`, `message` and `request_id` and a
 * `429`'s `Retry-After` (REQ-DEV-034, REQ-DEV-035); no answer is [ApiResult.Unreachable]. Nothing is
 * logged here: callers log codes and statuses, never the credential, the pairing code or bodies
 * (REQ-OPS-003, REQ-OPS-011).
 */
class HttpDeviceApi(
    baseUrl: String,
    private val credential: String?,
    /** Sent as `User-Agent`, e.g. `Asancart-Android/0.1.0`. */
    private val userAgent: String,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val clock: () -> Instant = Instant::now,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceApi {
    private val root = baseUrl.trimEnd('/') + "/v1/device"

    override suspend fun pair(request: PairRequest): ApiResult<PairResponse> =
        call("POST", "/pair", { WireFormat.pairRequest(request) }, auth = false) { WireFormat.pairResponse(it.text) }

    override suspend fun submitReports(reports: List<TransferReport>): ApiResult<List<ReportResult>> =
        call("POST", "/reports", { WireFormat.reportsRequest(reports, deviceTime = clock()) }, auth = true) {
            WireFormat.reportsResponse(it.text, reports.size)
        }

    override suspend fun heartbeat(request: HeartbeatRequest): ApiResult<Unit> =
        call("POST", "/heartbeat", { WireFormat.heartbeatRequest(request) }, auth = true) { }

    override suspend fun cards(): ApiResult<CardsResponse> =
        call("GET", "/cards", body = null, auth = true) { WireFormat.cardsResponse(it.text) }

    override suspend fun unpair(): ApiResult<Unit> = call("POST", "/unpair", body = null, auth = true) { }

    override suspend fun parserTemplates(since: Instant?): ApiResult<ParserTemplatesResponse> {
        val query = since?.let { "?since=" + URLEncoder.encode(it.toString(), "UTF-8") }.orEmpty()
        return call("GET", "/parser-templates$query", body = null, auth = false) { WireFormat.parserTemplatesResponse(it.text) }
    }

    override suspend fun crashReport(report: CrashReport): ApiResult<Unit> =
        call("POST", "/crash-reports", { WireFormat.crashReportRequest(report) }, auth = true) { }

    /** [body] is built per attempt, so `device_time` is the clock at send time (REQ-DEV-002). */
    private suspend fun <T> call(method: String, path: String, body: (() -> String)?, auth: Boolean, parse: (HttpResponse) -> T): ApiResult<T> {
        if (auth && credential == null) return ApiResult.Error(401, ErrorCode.UNAUTHORIZED, "")
        return withContext(dispatcher) {
            val bytes = body?.invoke()?.toByteArray(Charsets.UTF_8)
            val headers = buildMap {
                put("Accept", "application/json")
                put("User-Agent", userAgent)
                if (bytes != null) put("Content-Type", "application/json; charset=utf-8")
                if (auth) put("Authorization", "Bearer $credential")
            }
            val response = try {
                transport.execute(HttpRequest(method, root + path, headers, bytes))
            } catch (e: IOException) {
                return@withContext ApiResult.Unreachable(e.javaClass.simpleName)
            } catch (e: SecurityException) {
                // No INTERNET permission, or cleartext refused by the network security config.
                return@withContext ApiResult.Unreachable(e.javaClass.simpleName)
            }
            if (response.status in 200..299) {
                try {
                    ApiResult.Ok(parse(response))
                } catch (e: RuntimeException) {
                    ApiResult.Error(response.status, ErrorCode.INVALID_RESPONSE, "")
                }
            } else {
                error(response)
            }
        }
    }

    private fun error(r: HttpResponse): ApiResult.Error {
        val parsed = WireFormat.errorBody(r.text)
        val code = when {
            // REQ-DEV-030: a 401 is a refused credential whatever the body says.
            r.status == 401 -> ErrorCode.UNAUTHORIZED
            parsed != null -> parsed.code
            r.status == 429 -> ErrorCode.RATE_LIMITED
            else -> ErrorCode.HTTP_ERROR
        }
        return ApiResult.Error(
            httpStatus = r.status,
            code = code,
            message = parsed?.message.orEmpty(),
            retryAfterSec = r.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 },
            requestId = parsed?.requestId,
        )
    }
}
