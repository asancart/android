package io.github.asancart.android.core.profile

import kotlin.test.Test
import kotlin.test.assertEquals

/** REQ-APP-022: the address of another service's target, as typed. */
class TargetUrlTest {
    private fun release(s: String) = TargetUrl.parse(s, allowHttp = false)
    private fun debug(s: String) = TargetUrl.parse(s, allowHttp = true)

    @Test
    fun `HTTPS addresses are kept in a normal form`() {
        assertEquals(TargetUrl.Valid("https://pay.example.com"), release("  HTTPS://Pay.Example.com/  "))
        assertEquals(TargetUrl.Valid("https://pay.example.com:8443/asancart"), release("https://pay.example.com:8443/asancart/"))
        assertEquals(TargetUrl.Valid("https://pay.example.com"), release("https://pay.example.com/v1/device/"))
    }

    @Test
    fun `release builds take HTTPS only, debug builds also HTTP (REQ-DEV-033)`() {
        assertEquals(TargetUrl.Invalid(TargetUrl.Reason.HTTPS_REQUIRED), release("http://10.0.2.2:8080"))
        assertEquals(TargetUrl.Valid("http://10.0.2.2:8080"), debug("http://10.0.2.2:8080"))
    }

    @Test
    fun `anything else is refused`() {
        assertEquals(TargetUrl.Invalid(TargetUrl.Reason.BLANK), release("   "))
        for (bad in listOf("pay.example.com", "ftp://pay.example.com", "https://", "https://user:pw@pay.example.com", "https://pay.example.com/?a=1", "https://pay.example.com/#x", "https://pay example.com")) {
            assertEquals(TargetUrl.Invalid(TargetUrl.Reason.INVALID), release(bad), bad)
        }
    }
}
