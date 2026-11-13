package io.github.asancart.android.core.capture

import io.github.asancart.android.core.deviceapi.WireFormat
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.CardBinding
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.core.sms.IncomingSms
import io.github.asancart.android.core.template.DefaultTemplateEngine
import io.github.asancart.android.core.template.Direction
import io.github.asancart.android.core.template.ParserTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DEC-003, REQ-APP-012 on the app's own sample SMS (design prototype): the body that would be
 * sent holds the REQ-DEV-002 fields only; balance, account digits and SMS text stay on the Device.
 */
class ReportWireTest {
    private val melli = ParserTemplate(
        name = "sample-melli",
        senderPatterns = listOf("700717"),
        pattern = "^\u0628\u0627\u0646\u06A9 \u0645\u0644\u06CC \u0627\u06CC\u0631\u0627\u0646\\n\u0627\u0646\u062A\u0642\u0627\u0644\u06CC?:(?<amount>[\\d,]+)(?<sign>[+-])\\n\u062D\u0633\u0627\u0628:(?<account>\\d+)\\n\u0645\u0627\u0646\u062F\u0647:-?[\\d,]+\\n\\d{4}-\\d{2}:\\d{2}$",
        direction = Direction.Sign,
        priority = 10,
    )
    private val sms = "\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646\n\u0627\u0646\u062A\u0642\u0627\u0644:25,000,000+\n\u062D\u0633\u0627\u0628:10000\n\u0645\u0627\u0646\u062F\u0647:198,088,329\n0820-14:21"

    @Test
    fun `the request body holds only the report fields`() {
        val profile = Profile(
            id = "p",
            name = "p",
            target = ApiTarget.platform("https://api.example.test"),
            cardBindings = listOf(CardBinding("700717", "sample-melli", listOf("ck_melli"))),
            enabled = true,
        )
        val d = SmsProcessor(DefaultTemplateEngine(), newReportId = { "r" })
            .process(IncomingSms("700717", sms, Instant.parse("1999-01-01T00:00:00Z")), listOf(profile), listOf(melli))
            .single() as CaptureDecision.Report
        val wire = WireFormat.reportsRequest(listOf(d.report))
        val item = Json.parseToJsonElement(wire).jsonObject["reports"]!!.jsonArray.single().jsonObject
        assertEquals(WireFormat.REPORT_FIELDS.toSet(), item.keys)
        assertEquals("25000000", item["amount"].toString())
        for (secret in listOf("198088329", "198,088,329", "10000", "0820")) assertTrue(secret !in wire, wire)
        assertTrue(wire.none { it.code > 127 }, wire)
    }
}
