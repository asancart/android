package io.github.asancart.android.core.deviceapi

import io.github.asancart.android.core.queue.TransferReport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The HTTP adapter against a stand-in server that answers as `api/device-api.md` documents it: paths,
 * methods, headers and snake_case bodies sent; answers, error bodies and `Retry-After` read.
 */
class HttpDeviceApiTest {
    private val server = StandInServer()
    private val now = Instant.parse("2026-11-17T08:00:00Z")

    private fun api(credential: String? = "dvc_test_credential", base: String = server.baseUrl) =
        HttpDeviceApi(base, credential, userAgent = "Asancart-Android/0.1.0", clock = { now })

    @AfterTest
    fun stop() = server.close()

    private val pairBody = """
        {"device_id":"9b1d2c3e-0000-4000-8000-000000000001","device_credential":"dvc_secret","shop":{"id":"s1","name":"کافه نارنج"},
         "cards":[{"card_id":"c1","card_key":"ck_1","masked_number":"6037 99•• •••• 4821","bank_id":"melli","extra":1}],
         "banks":[{"id":"melli","name":"بانک ملی","required_fields":["reference"]}]}
    """.trimIndent()

    @Test
    fun `Pair posts the code, name and version without a credential and reads the 201 (REQ-DEV-001)`() = runTest {
        server.answer(201, pairBody, "Cache-Control" to "no-store")
        val r = api(credential = null).pair(PairRequest(" k7qm-2xpa ", "Galaxy A54", "0.1.0"))

        val ok = assertIs<ApiResult.Ok<PairResponse>>(r).value
        assertEquals("dvc_secret", ok.deviceCredential)
        assertEquals(ShopRef("s1", "کافه نارنج"), ok.shop)
        assertEquals(listOf(Card("c1", "ck_1", "6037 99•• •••• 4821", "melli")), ok.cards)
        assertEquals(listOf(Bank("melli", "بانک ملی", listOf("reference"))), ok.banks)

        val req = server.requests.single()
        assertEquals("POST /v1/device/pair", req.line)
        assertNull(req.headers["authorization"])
        assertEquals("application/json; charset=utf-8", req.headers["content-type"])
        assertEquals("Asancart-Android/0.1.0", req.headers["user-agent"])
        val body = Json.parseToJsonElement(req.body).jsonObject
        assertEquals(setOf("pairing_code", "device_name", "app_version"), body.keys)
        assertEquals("k7qm-2xpa", body["pairing_code"]!!.jsonPrimitive.content)
        assertEquals("Galaxy A54", body["device_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a contract error keeps its code, Persian message and request id (REQ-DEV-031)`() = runTest {
        server.answer(422, """{"error":{"code":"PAIRING_INVALID","message":"کد اتصال نامعتبر است یا منقضی شده است.","request_id":"r-1"}}""")
        val e = assertIs<ApiResult.Error>(api(credential = null).pair(PairRequest("AAAA-BBBB", "n", "1")))
        assertEquals(422, e.httpStatus)
        assertEquals(ErrorCode.PAIRING_INVALID, e.code)
        assertEquals("کد اتصال نامعتبر است یا منقضی شده است.", e.message)
        assertEquals("r-1", e.requestId)
        assertNull(e.retryAfterSec)
        assertFalse(e.unauthorized)
    }

    @Test
    fun `429 carries Retry-After in seconds (REQ-DEV-034, REQ-DEV-035)`() = runTest {
        server.answer(429, """{"error":{"code":"RATE_LIMITED","message":"m","request_id":"r-2"}}""", "Retry-After" to "6")
        val e = assertIs<ApiResult.Error>(api(credential = null).pair(PairRequest("AAAA-BBBB", "n", "1")))
        assertEquals(ErrorCode.RATE_LIMITED, e.code)
        assertEquals(6L, e.retryAfterSec)
    }

    @Test
    fun `an error status without a contract body still maps to a code`() = runTest {
        server.answer(429, "<html>busy</html>", "Retry-After" to "1")
        assertEquals(ErrorCode.RATE_LIMITED, assertIs<ApiResult.Error>(api().cards()).code)
        server.answer(502, "<html>bad gateway</html>")
        val e = assertIs<ApiResult.Error>(api().cards())
        assertEquals(502, e.httpStatus)
        assertEquals(ErrorCode.HTTP_ERROR, e.code)
        assertEquals("", e.message)
        server.answer(401, "")
        assertTrue(assertIs<ApiResult.Error>(api().cards()).unauthorized)
    }

    @Test
    fun `Cards refresh sends the credential as Bearer and reads cards and banks (REQ-DEV-008, REQ-DEV-030)`() = runTest {
        server.answer(200, """{"cards":[{"card_id":"c2","card_key":"ck_2","masked_number":"6104 33•• •••• 1907","bank_id":"mellat"}],"banks":[{"id":"mellat","name":"بانک ملت","required_fields":[]}]}""")
        val ok = assertIs<ApiResult.Ok<CardsResponse>>(api().cards()).value
        assertEquals("ck_2", ok.cards.single().cardKey)
        assertEquals(emptyList(), ok.banks.single().requiredFields)
        val req = server.requests.single()
        assertEquals("GET /v1/device/cards", req.line)
        assertEquals("Bearer dvc_test_credential", req.headers["authorization"])
        assertEquals("", req.body)
    }

    @Test
    fun `401 on an authenticated call is UNAUTHORIZED (REQ-DEV-030)`() = runTest {
        server.answer(401, """{"error":{"code":"UNAUTHORIZED","message":"این دستگاه دیگر به فروشگاه متصل نیست؛ آن را دوباره متصل کنید.","request_id":"r-3"}}""")
        val e = assertIs<ApiResult.Error>(api().unpair())
        assertTrue(e.unauthorized)
        assertEquals(ErrorCode.UNAUTHORIZED, e.code)
    }

    @Test
    fun `no credential means no request for an authenticated call`() = runTest {
        assertTrue(assertIs<ApiResult.Error>(api(credential = null).cards()).unauthorized)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `Unpair is a credentialed POST answered 204 (REQ-DEV-009)`() = runTest {
        server.answer(204, "")
        assertIs<ApiResult.Ok<Unit>>(api().unpair())
        val req = server.requests.single()
        assertEquals("POST /v1/device/unpair", req.line)
        assertEquals("Bearer dvc_test_credential", req.headers["authorization"])
    }

    @Test
    fun `Heartbeat body has every field, last_sms_at null included (REQ-DEV-003)`() = runTest {
        server.answer(204, "")
        assertIs<ApiResult.Ok<Unit>>(api().heartbeat(HeartbeatRequest(0, "0.1.0", 3, null)))
        val body = Json.parseToJsonElement(server.requests.single().body).jsonObject
        assertEquals(setOf("interval_sec", "app_version", "queue_length", "last_sms_at"), body.keys)
        assertEquals(JsonNull, body["last_sms_at"])
        assertEquals("0", body["interval_sec"].toString())
    }

    @Test
    fun `Reports carry device_time and the item fields only, results in order (REQ-DEV-002)`() = runTest {
        server.answer(200, """{"results":[{"client_report_id":"a","result":"ACCEPTED"},{"client_report_id":"b","result":"REJECTED","reason":"MIXED_BANKS"}]}""")
        val reports = listOf(
            TransferReport("a", listOf("ck_1"), 2_750_000, Instant.parse("2026-11-17T07:59:00Z"), null),
            TransferReport("b", listOf("ck_1", "ck_2"), 10_000, Instant.parse("2026-11-17T07:58:00Z"), "123456"),
        )
        val results = assertIs<ApiResult.Ok<List<ReportResult>>>(api().submitReports(reports)).value
        assertEquals(listOf(ReportResult.Outcome.ACCEPTED, ReportResult.Outcome.REJECTED), results.map { it.result })
        assertEquals("MIXED_BANKS", results[1].reason)
        val body = Json.parseToJsonElement(server.requests.single().body).jsonObject
        assertEquals("2026-11-17T08:00:00Z", body["device_time"]!!.jsonPrimitive.content)
        assertEquals(WireFormat.REPORT_FIELDS.toSet(), body["reports"]!!.jsonArray[0].jsonObject.keys)
    }

    @Test
    fun `a 2xx body of the wrong shape is INVALID_RESPONSE, so the queue keeps its items`() = runTest {
        server.answer(200, """{"results":[{"client_report_id":"a","result":"MAYBE"}]}""")
        val one = listOf(TransferReport("a", listOf("ck_1"), 1, now))
        assertEquals(ErrorCode.INVALID_RESPONSE, assertIs<ApiResult.Error>(api().submitReports(one)).code)
        server.answer(200, """{"results":[]}""")
        assertEquals(ErrorCode.INVALID_RESPONSE, assertIs<ApiResult.Error>(api().submitReports(one)).code)
        server.answer(200, "not json")
        assertEquals(ErrorCode.INVALID_RESPONSE, assertIs<ApiResult.Error>(api().cards()).code)
    }

    @Test
    fun `Parser Templates take since and read published and retired items (REQ-DEV-004, REQ-DEV-006)`() = runTest {
        server.answer(
            200,
            """{"server_time":"2026-11-17T07:59:00Z","items":[
              {"id":"t1","bank_id":"melli","name":"melli-a","version":2,"status":"published","published_at":"2026-11-17T07:00:00Z","retired_at":null,"body":{"name":"melli-a"}},
              {"id":"t0","bank_id":"melli","name":"melli-a","version":1,"status":"retired","published_at":"2026-10-01T00:00:00+03:30","retired_at":"2026-11-17T07:00:00Z","body":null}]}""",
        )
        val r = assertIs<ApiResult.Ok<ParserTemplatesResponse>>(api(credential = null).parserTemplates(Instant.parse("2026-11-16T00:00:00Z"))).value
        assertEquals(Instant.parse("2026-11-17T07:59:00Z"), r.serverTime)
        assertEquals(listOf(ParserTemplateItem.Status.PUBLISHED, ParserTemplateItem.Status.RETIRED), r.items.map { it.status })
        assertEquals("""{"name":"melli-a"}""", r.items[0].body)
        assertNull(r.items[1].body)
        assertEquals(Instant.parse("2026-09-30T20:30:00Z"), r.items[1].publishedAt)
        val req = server.requests.single()
        assertEquals("GET /v1/device/parser-templates?since=2026-11-16T00%3A00%3A00Z", req.line)
        assertNull(req.headers["authorization"])
    }

    @Test
    fun `Crash reports post the REQ-DEV-057 fields with the credential`() = runTest {
        server.answer(204, "")
        assertIs<ApiResult.Ok<Unit>>(api().crashReport(CrashReport("0.1.0", "14", null, "java.lang.X\n\tat a.b(C.kt:1)", now)))
        val req = server.requests.single()
        assertEquals("POST /v1/device/crash-reports", req.line)
        val body = Json.parseToJsonElement(req.body).jsonObject
        assertEquals(setOf("app_version", "android_version", "device_model", "stack_trace", "happened_at"), body.keys)
        assertEquals("2026-11-17T08:00:00Z", body["happened_at"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a base URL with a path or trailing slash keeps the path`() = runTest {
        server.answer(204, "")
        assertIs<ApiResult.Ok<Unit>>(api(base = server.baseUrl + "/asancart/").unpair())
        assertEquals("POST /asancart/v1/device/unpair", server.requests.single().line)
    }

    @Test
    fun `a redirect is not followed, so the credential goes nowhere else`() = runTest {
        server.answer(302, "", "Location" to "http://example.invalid/v1/device/cards")
        val e = assertIs<ApiResult.Error>(api().cards())
        assertEquals(302, e.httpStatus)
        assertEquals(ErrorCode.HTTP_ERROR, e.code)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun `no server is Unreachable (REQ-DEV-011)`() = runTest {
        val port = ServerSocket(0).use { it.localPort }
        assertIs<ApiResult.Unreachable>(api(base = "http://127.0.0.1:$port").cards())
    }

    @Test
    fun `request objects never print the credential (REQ-OPS-003)`() {
        val text = HttpRequest("GET", "http://x/v1/device/cards", mapOf("Authorization" to "Bearer dvc_secret"), null).toString()
        assertFalse(text.contains("dvc_secret"), text)
    }
}
