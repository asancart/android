package io.github.asancart.android.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-OPS-003: the log filter is fed every secret type the app could ever hold. */
class LogRedactorTest {
    private fun leaks(secret: String, line: String) = assertFalse("'$secret' leaks: $line", line.contains(secret))

    @Test
    fun deviceCredentialsAndTokensNeverReachALog() {
        val bearer = LogRedactor.redact("POST /v1/device/reports Authorization: Bearer dvc_9f8e7d6c5b4a3210")
        leaks("dvc_9f8e7d6c5b4a3210", bearer)
        for (line in listOf(
            "device_credential=dvc_9f8e7d6c5b4a3210",
            "{\"device_credential\":\"dvc_9f8e7d6c5b4a3210\"}",
            "ipg_token=ipg_live_ABCDEF123456",
            "callback_secret: cbs_0123456789abcdef",
            "password=hunter2xyz",
            "pairing_code=K7QM-2XPA",
            "otp=482913",
        )) {
            val r = LogRedactor.redact(line)
            for (secret in listOf("dvc_9f8e7d6c5b4a3210", "ipg_live_ABCDEF123456", "cbs_0123456789abcdef", "hunter2xyz", "K7QM-2XPA", "482913")) leaks(secret, r)
        }
    }

    @Test
    fun pairingCodesAndCredentialsInPlainTextAreRemoved() {
        // QA's cases: secrets that are not written as key=value.
        val pairing = LogRedactor.redact("pairing code K7QM-2XPA expired")
        leaks("K7QM-2XPA", pairing)
        val invalid = LogRedactor.redact("PAIRING_INVALID for K7QM-2XPA")
        leaks("K7QM-2XPA", invalid)
        assertTrue(invalid, invalid.startsWith("PAIRING_INVALID for "))
        leaks("k7qm-2xpa", LogRedactor.redact("code k7qm-2xpa typed by hand"))
        leaks("dvc_9f8e7d6c5b4a3210", LogRedactor.redact("credential dvc_9f8e7d6c5b4a3210 was revoked"))
        leaks("dvc_9f8e7d6c5b4a3210", LogRedactor.redact("got dvc_9f8e7d6c5b4a3210 from pair"))
        leaks("Q2xhdWRlUmVkYWN0aW9uVGVzdDEyMzQ1Ng", LogRedactor.redact("raw Q2xhdWRlUmVkYWN0aW9uVGVzdDEyMzQ1Ng in body"))
        // The dashboard QR payload (dashboard-api.md), as a JSON parse error would print it.
        val qr = LogRedactor.redact("bad QR {\"v\":1,\"url\":\"https://api.asancart.org\",\"code\":\"K7QM-2XPA\",\"cards\":[{\"card_id\":\"c1\"}]}")
        leaks("K7QM-2XPA", qr)
        assertTrue(qr, qr.contains("https://api.asancart.org"))
    }

    @Test
    fun secretFieldNamesLoseTheirValue() {
        for (key in listOf("device_credential", "token", "callback_secret", "password", "otp", "pairing_code", "sms_body", "text", "balance", "account")) {
            assertEquals(LogRedactor.REDACTED, LogRedactor.redactField(key, "anything 123"))
        }
        assertEquals("ACCEPTED", LogRedactor.redactField("result", "ACCEPTED"))
    }

    @Test
    fun smsTextAndBalancesNeverReachALog() {
        val sms = "\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646\n\u0627\u0646\u062A\u0642\u0627\u0644:25,000,000+\n\u062D\u0633\u0627\u0628:10000\n\u0645\u0627\u0646\u062F\u0647:198,088,329"
        val r = LogRedactor.redact("parse failed for $sms")
        for (part in listOf("198,088,329", "198088329", "25,000,000", "\u0645\u0627\u0646\u062F\u0647", "\u0627\u0646\u062A\u0642\u0627\u0644")) leaks(part, r)
        assertTrue(r, r.startsWith("parse failed for "))
        leaks("198088329", LogRedactor.redact("balance 198088329"))
        leaks("\u06F1\u06F9\u06F8\u06F0\u06F8\u06F8", LogRedactor.redact("x \u06F1\u06F9\u06F8\u06F0\u06F8\u06F8\u06F3\u06F2\u06F9"))
    }

    @Test
    fun cardNumbersKeepFirstAndLastFourPhonesLastFour() {
        assertEquals("card 6037********5678", LogRedactor.redact("card 6037991234565678"))
        assertEquals("card 6037********5678", LogRedactor.redact("card 6037-9912-3456-5678"))
        assertEquals("tel *******4567", LogRedactor.redact("tel 09121234567"))
        assertEquals("tel *******4567", LogRedactor.redact("tel +989121234567"))
        assertEquals("tel *******4567", LogRedactor.redact("tel 0912 123 4567"))
        assertEquals("tel *******4567", LogRedactor.redact("tel +98 912-123-4567"))
        val sheba = LogRedactor.redact("to IR820540102680020817909002")
        leaks("0540102680020817909002", sheba)
        assertTrue(sheba, sheba.endsWith("9002"))
        leaks("1122334455", LogRedactor.redact("account 1122334455"))
    }

    @Test
    fun shebaWrittenWithDashesOrSpacesIsMaskedLikeThePlainForm() {
        // T4.12: the dashed form kept 12 of its 24 digits.
        val digits = "820540102680020817909002"
        for (written in listOf("IR82-0540-1026-8002-0817-9090-02", "ir82-0540-1026-8002-0817-9090-02", "IR-82-0540-1026-8002-0817-9090-02", "IR82 0540 1026 8002 0817 9090 02", "IR820540102680020817909002")) {
            val r = LogRedactor.redact("refund to $written requested")
            assertEquals(written, "refund to IR********************9002 requested", r)
            for (group in listOf("0540", "1026", "8002", "0817", "9090")) leaks(group, r)
            leaks(digits.dropLast(4), r.filter(Char::isDigit))
        }
    }

    @Test
    fun aJwtIsRemovedWhereverItStands() {
        // T4.12: after "token <word>" only the word was removed.
        val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZXZpY2UtMSJ9.c2lnbmF0dXJlLXBhcnQtMDAx"
        for (line in listOf("token refresh $jwt", "Token type=$jwt", "renewed $jwt ok", "Authorization: Bearer $jwt", "{\"session\":\"$jwt\"}")) {
            val r = LogRedactor.redact(line)
            for (part in jwt.split('.')) leaks(part, r)
            assertTrue(r, r.contains(LogRedactor.REDACTED))
        }
    }

    @Test
    fun shebaWithAnySeparatorIsMaskedOrLosesItsDigits() {
        // T4.13: separators as copied from bank apps, a Sheba glued to a word, and one without IR.
        val groups = listOf("0540", "1026", "8002", "0817", "9090")
        val masked = "IR********************9002"
        for (written in listOf(
            "IR82  0540  1026  8002  0817  9090  02",
            "IR82 - 0540 - 1026 - 8002 - 0817 - 9090 - 02",
            "IR82.0540.1026.8002.0817.9090.02",
            "IR82\u00A00540\u00A01026\u00A08002\u00A00817\u00A09090\u00A002",
            "IR82\u200C0540\u200C1026\u200C8002\u200C0817\u200C9090\u200C02",
        )) {
            val r = LogRedactor.redact("refund to $written requested")
            assertEquals(written, "refund to $masked requested", r)
        }
        val glued = LogRedactor.redact("refund toIR82-0540-1026-8002-0817-9090-02 requested")
        assertEquals("refund to$masked requested", glued)
        val bare = LogRedactor.redact("refund to 82-0540-1026-8002-0817-9090-02 requested")
        for (group in groups) leaks(group, bare)
        assertEquals("refund to <number> requested", bare)
    }

    @Test
    fun accountNumbersInGroupsLoseTheirDigits() {
        // T4.13: LONG_NUMBER only caught runs of 6 or more digits.
        for (written in listOf("0105-1234-5678-01", "0105 1234 5678 01", "0105.1234.5678.01", "0105 - 1234 - 5678 - 01", "0105-1234-5678-0123-45")) {
            val r = LogRedactor.redact("account $written blocked")
            assertEquals(written, "account <number> blocked", r)
        }
        // A card number in a longer run of groups is not a card: no first and last 4 left behind.
        leaks("0540", LogRedactor.redact("x 82-0540-1026-8002-0817-90"))
    }

    @Test
    fun datesTimesUuidsAndRequestIdsStayNextToTheNumberRules() {
        val line = "sync from 2026-11-12 to 2026-11-13 at 2026-11-12 10:00:00 and 2026-11-12T10:00:00Z, " +
            "jalali 1405-08-22 10:05, request_id=3f2a9c1e-0b7d-4e21-9a5c-6d8e0f1b2c3d " +
            "profile 3f2b8c1e-4d5a-4e6f-8a9b-0c1d2e3f4a5b took 1.5 s, host 192.168.100.200 port 8080, " +
            "http://10.0.2.2:8080/v1, version 0.1.0-debug, rows 12 of 340"
        assertEquals(line, LogRedactor.redact(line))
        // The phone and card shapes keep their masks.
        assertEquals("card 6037********5678 tel *******4567", LogRedactor.redact("card 6037-9912-3456-5678 tel 0912-123-4567"))
    }

    @Test
    fun groupsSplitByTabSlashOrLongerRunsLoseTheirDigits() {
        // T4.16: QA's four strings from the T4.13 probe.
        val masked = "IR********************9002"
        assertEquals("to $masked ok", LogRedactor.redact("to IR82\t0540\t1026\t8002\t0817\t9090\t02 ok"))
        assertEquals("to $masked ok", LogRedactor.redact("to IR82/0540/1026/8002/0817/9090/02 ok"))
        assertEquals("account <number> blocked", LogRedactor.redact("account 0105/1234/5678/01 blocked"))
        assertEquals("account <number> blocked", LogRedactor.redact("account 0105 -  1234 -  5678 -  01 blocked"))
        // And the same account with a tab, and a card shape inside a run with a long separator.
        assertEquals("account <number> blocked", LogRedactor.redact("account 0105\t1234\t5678\t01 blocked"))
        leaks("0540", LogRedactor.redact("x 82 -  0540-1026-8002-0817 -  90"))
    }

    @Test
    fun slashDatesTimesUuidsIpsAndRequestIdsStay() {
        val line = "sms of 05/08/20-13:55, from 1405/08/22 to 1405/08/23, on 12/11/2026 and 2026/11/12 2026/11/13 at 10:05:00\t11:00, " +
            "request_id=3f2a9c1e-0b7d-4e21-9a5c-6d8e0f1b2c3d, hosts 192.168.100.200 / 10.0.0.1, http://10.0.2.2:8080/v1/device/reports, " +
            "version 0.1.0-debug, rows 12/340"
        assertEquals(line, LogRedactor.redact(line))
    }

    @Test
    fun idsTimesCountsAndNamesStay() {
        val line = "report_queued profile_id=3f2b8c1e-4d5a-4e6f-8a9b-0c1d2e3f4a5b template=mellat-account at=2026-11-12T10:00:00Z rows=12 http=503 code_name=VALIDATION_FAILED"
        assertEquals(line, LogRedactor.redact(line))
    }
}
