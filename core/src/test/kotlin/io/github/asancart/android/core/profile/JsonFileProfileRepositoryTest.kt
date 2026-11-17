package io.github.asancart.android.core.profile

import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.heartbeat.HeartbeatSetting
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-APP-020: Profiles survive a restart; the file holds no credential. */
class JsonFileProfileRepositoryTest {
    private val dir = Files.createTempDirectory("profiles").toFile()
    private val file = File(dir, "profiles.json")
    private val platform = ApiTarget.platform("http://10.0.2.2:8080")

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    private val full = Profile(
        id = "p2",
        name = "فروشگاه دوم",
        target = ApiTarget.custom(TargetUrl.Valid("https://pay.example.com")),
        connection = Connection(
            deviceId = "dev-1",
            deviceName = "Galaxy A54",
            shop = ShopRef("s1", "کافه نارنج"),
            cards = listOf(Card("c1", "ck_1", "6037 99•• •••• 4821", "melli")),
            banks = listOf(Bank("melli", "بانک ملی", listOf("reference"))),
            revokedAt = Instant.parse("2026-11-17T08:00:00Z"),
        ),
        cardBindings = listOf(CardBinding("700717", "melli-a", listOf("ck_1", "ck_3"))),
        heartbeat = HeartbeatSetting(enabled = false, intervalSec = 600),
        enabled = true,
        enabledAt = Instant.parse("2026-11-17T07:00:00Z"),
    )

    @Test
    fun `every change is on disk and read back after a restart`() {
        val repo = JsonFileProfileRepository(file, platform, newId = { "p1" })
        repo.ensureDefaultProfile("پروفایل اصلی")
        repo.save(full)
        repo.setEnabled("p1", true, Instant.parse("2026-11-17T09:00:00Z"))

        val again = JsonFileProfileRepository(file, platform)
        assertEquals(repo.profiles.value, again.profiles.value)
        assertEquals(full, again.profiles.value[1])

        again.remove("p2")
        assertEquals(listOf("p1"), JsonFileProfileRepository(file, platform).profiles.value.map { it.id })
        assertFalse(File(dir, "profiles.json.tmp").exists())
    }

    @Test
    fun `the Platform address is the build's, not a stored one (REQ-APP-022, REQ-INF-007)`() {
        JsonFileProfileRepository(file, platform, newId = { "p1" }).ensureDefaultProfile("main")
        assertFalse(file.readText().contains("10.0.2.2"))
        val release = ApiTarget.platform("https://api.asancart.org")
        assertEquals(release, JsonFileProfileRepository(file, release).profiles.value.single().target)
    }

    @Test
    fun `an unreadable file is set aside and the app starts with no Profiles`() {
        file.writeText("{not json")
        var reported = false
        val repo = JsonFileProfileRepository(file, platform, onUnreadable = { reported = true })
        assertTrue(reported)
        assertTrue(repo.profiles.value.isEmpty())
        assertTrue(File(dir, "profiles.json.bad").exists())
    }
}
