package io.github.asancart.android.core.capture

import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.deviceapi.WireFormat
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.CardBinding
import io.github.asancart.android.core.profile.Connection
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.core.sms.IncomingSms
import io.github.asancart.android.core.sms.Normalizer
import io.github.asancart.android.core.template.DefaultTemplateEngine
import io.github.asancart.android.core.template.fixtures.ParserTemplateFixtures
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DEC-003, REQ-APP-012: for every deposit fixture of every bank, the bytes that would be sent hold
 * only the REQ-DEV-002 fields, and no number of the SMS but the amount and the reference: no
 * balance, no account or card digits, no dates or times from the text, and no SMS words.
 * Runs on the private fixtures when they are synced; [ReportWireTest] covers the same rule on
 * public samples everywhere.
 */
class BalanceNeverLeavesTest {
    private val fixtures = ParserTemplateFixtures { name -> javaClass.classLoader.getResource("parser-templates/$name")!!.readText() }
    private val processor = SmsProcessor(DefaultTemplateEngine(), newReportId = { "r" })
    private val receivedAt = Instant.parse("1999-01-01T00:00:00Z")

    @Test
    fun `only the report fields leave the Device, never other SMS numbers or text`() {
        assumeTrue("fixtures not synced (scripts/sync-parser-templates.sh)", javaClass.classLoader.getResource("parser-templates/oracle.json") != null)
        var deposits = 0
        for (bank in fixtures.banks) {
            for (fx in bank.fixtures.filter { it.expect["status"]!!.jsonPrimitive.content == "DEPOSIT" }) {
                deposits++
                val profile = Profile(
                    id = "p",
                    name = "p",
                    target = ApiTarget.platform("https://api.example.test"),
                    connection = Connection("d", "phone", ShopRef("s", "shop"), listOf(Card("c", "ck", "masked", bank.id)), listOf(Bank(bank.id, bank.id, bank.requiredFields))),
                    cardBindings = listOf(CardBinding(fx.sender!!, bank.templates.first().name, listOf("ck"))),
                    enabled = true,
                )
                val decision = processor.process(IncomingSms(fx.sender!!, fx.smsText, receivedAt), listOf(profile), bank.templates).single()
                val report = (decision as? CaptureDecision.Report)?.report ?: error("${bank.id}/${fx.id}: $decision")
                val wire = WireFormat.reportsRequest(listOf(report))

                val item = Json.parseToJsonElement(wire).jsonObject["reports"]!!.jsonArray.single().jsonObject
                assertEquals(WireFormat.REPORT_FIELDS.toSet(), item.keys, "${bank.id}/${fx.id}")

                // A leaked number is its own token in the body; compare whole digit runs.
                val allowed = setOfNotNull(report.amount.toString(), report.reference)
                val sent = Regex("\\d+").findAll(wire).map { it.value }.toSet()
                val normalized = Normalizer.normalizeText(fx.smsText).replace(",", "")
                for (run in Regex("\\d{3,}").findAll(normalized).map { it.value }) {
                    if (run in allowed) continue
                    assertTrue(run !in sent, "${bank.id}/${fx.id}: number from the SMS leaves the Device")
                }
                assertTrue(wire.none { it.code > 127 }, "${bank.id}/${fx.id}: SMS text leaves the Device")
            }
        }
        assertTrue(deposits >= 40, "deposit fixtures: $deposits")
    }
}
