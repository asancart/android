package org.asancart.android.core.profile

import org.asancart.android.core.heartbeat.HeartbeatSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileTest {
    private val platform = ApiTarget.platform("https://api.example.test")

    @Test
    fun `first launch creates one default Platform Profile, off and unpaired (REQ-APP-021, REQ-APP-022)`() {
        val repo = InMemoryProfileRepository(platform, newId = { "p1" })
        val profile = repo.ensureDefaultProfile("پروفایل اصلی")

        assertEquals(listOf(profile), repo.profiles.value)
        assertEquals("پروفایل اصلی", profile.name)
        assertEquals(platform, profile.target)
        assertNull(profile.connection)
        assertTrue(profile.cardBindings.isEmpty())
        assertFalse(profile.enabled)
        assertEquals(HeartbeatSetting.DEFAULT, profile.heartbeat)
    }

    @Test
    fun `default Profile is created only once`() {
        var ids = 0
        val repo = InMemoryProfileRepository(platform, newId = { "p${++ids}" })
        val first = repo.ensureDefaultProfile("a")
        val again = repo.ensureDefaultProfile("b")

        assertSame(first, again)
        assertEquals(1, repo.profiles.value.size)
    }

    @Test
    fun `the switch changes only its own Profile`() {
        val repo = InMemoryProfileRepository(platform, newId = { "p1" })
        repo.ensureDefaultProfile("a")
        repo.setEnabled("p1", true)
        assertTrue(repo.profiles.value.single().enabled)
        repo.setEnabled("other", false)
        assertTrue(repo.profiles.value.single().enabled)
    }

    @Test
    fun `host shown in the UI keeps a port`() {
        assertEquals(ApiTarget.Kind.PLATFORM, platform.kind)
        assertEquals("api.asancart.org", ApiTarget.platform("https://api.asancart.org").host)
        assertEquals("10.0.2.2:8080", ApiTarget.platform("http://10.0.2.2:8080").host)
        assertEquals("pay.example.com", ApiTarget(ApiTarget.Kind.CUSTOM, "https://pay.example.com/v1").host)
    }

    @Test
    fun `turning on needs a Card Binding and both SMS permissions (REQ-APP-027)`() {
        val bare = Profile(id = "p", name = "n", target = platform)
        val bound = bare.copy(cardBindings = listOf(CardBinding("Bank Mellat", "mellat-account", listOf("ck_1"))))

        assertEquals(setOf(ActivationBlocker.NO_CARD_BINDING, ActivationBlocker.SMS_PERMISSION_MISSING), bare.activationBlockers(false))
        assertEquals(setOf(ActivationBlocker.NO_CARD_BINDING), bare.activationBlockers(true))
        assertEquals(setOf(ActivationBlocker.SMS_PERMISSION_MISSING), bound.activationBlockers(false))
        assertTrue(bound.activationBlockers(true).isEmpty())
    }
}
