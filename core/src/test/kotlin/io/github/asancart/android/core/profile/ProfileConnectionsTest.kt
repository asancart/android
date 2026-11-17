package io.github.asancart.android.core.profile

import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.CardsResponse
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.ErrorCode
import io.github.asancart.android.core.deviceapi.PairRequest
import io.github.asancart.android.core.deviceapi.PairResponse
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.deviceapi.StubDeviceApi
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T4.3: Pair with the Platform and a custom target, Cards refresh, revoked state, removal with Unpair. */
class ProfileConnectionsTest {
    private val platform = ApiTarget.platform("https://api.example.test")
    private val custom = ApiTarget.custom(TargetUrl.Valid("https://pay.example.com"))
    private val now = Instant.parse("2026-11-17T08:00:00Z")

    /** One fake target per base URL; records which credential each call carried. */
    private inner class Target(val baseUrl: String) {
        val calls = mutableListOf<String>()
        var pairAnswer: ApiResult<PairResponse> = ApiResult.Ok(paired("dvc_$baseUrl"))
        var cardsAnswer: ApiResult<CardsResponse> = ApiResult.Ok(CardsResponse(listOf(card2), listOf(mellat)))
        var unpairAnswer: ApiResult<Unit> = ApiResult.Ok(Unit)
        var lastPair: PairRequest? = null

        fun api(credential: String?): DeviceApi = object : DeviceApi by StubDeviceApi() {
            override suspend fun pair(request: PairRequest): ApiResult<PairResponse> {
                calls += "pair cred=$credential"
                lastPair = request
                return pairAnswer
            }

            override suspend fun cards(): ApiResult<CardsResponse> {
                calls += "cards cred=$credential"
                return cardsAnswer
            }

            override suspend fun unpair(): ApiResult<Unit> {
                calls += "unpair cred=$credential"
                return unpairAnswer
            }
        }
    }

    private val card1 = Card("c1", "ck_1", "6037 99•• •••• 4821", "melli")
    private val card2 = Card("c2", "ck_2", "6104 33•• •••• 1907", "mellat")
    private val melli = Bank("melli", "بانک ملی", emptyList())
    private val mellat = Bank("mellat", "بانک ملت", listOf("reference"))

    private fun paired(credential: String) = PairResponse("dev-1", credential, ShopRef("s1", "کافه نارنج"), listOf(card1), listOf(melli))

    private val targets = mutableMapOf<String, Target>()
    private fun target(url: String) = targets.getOrPut(url) { Target(url) }

    private var ids = 0
    private val repo = InMemoryProfileRepository(platform, newId = { "p${++ids}" })
    private val credentials = InMemoryCredentialStore()
    private val removed = mutableListOf<String>()
    private val events = mutableListOf<String>()
    private val connections = ProfileConnections(
        profiles = repo,
        credentials = credentials,
        apiFor = { t, cred -> target(t.baseUrl).api(cred) },
        appVersion = "0.1.0",
        defaultProfileName = { "پروفایل اصلی" },
        onRemoved = { removed += it },
        events = { name, fields -> events += name + fields.joinToString(prefix = " ") { "${it.first}=${it.second}" } },
        clock = { now },
    )

    @Test
    fun `pairing the Platform Profile stores the credential and the Shop's Cards (REQ-DEV-001, REQ-APP-023)`() = runTest {
        val p = repo.ensureDefaultProfile("پروفایل اصلی")
        val r = assertIs<ApiResult.Ok<Profile>>(connections.pair(p.id, "K7QM-2XPA", "Galaxy A54")).value

        assertEquals(Connection("dev-1", "Galaxy A54", ShopRef("s1", "کافه نارنج"), listOf(card1), listOf(melli)), r.connection)
        assertEquals(r, repo.profiles.value.single())
        assertEquals("dvc_${platform.baseUrl}", credentials.get(p.id))
        assertEquals(listOf("pair cred=null"), target(platform.baseUrl).calls)
        assertEquals(PairRequest("K7QM-2XPA", "Galaxy A54", "0.1.0"), target(platform.baseUrl).lastPair)
        assertTrue(r.paired)
        assertFalse(r.revoked)
    }

    @Test
    fun `a custom target pairs with its own address and keeps its own credential (REQ-APP-022)`() = runTest {
        val main = repo.ensureDefaultProfile("main")
        val other = repo.add("فروشگاه دوم", custom)
        connections.pair(main.id, "AAAA-AAAA", "Galaxy")
        connections.pair(other.id, "BBBB-BBBB", "Galaxy")

        assertEquals(listOf("pair cred=null"), target("https://pay.example.com").calls)
        assertEquals("dvc_https://pay.example.com", credentials.get(other.id))
        assertEquals("dvc_${platform.baseUrl}", credentials.get(main.id))
        assertEquals(custom, repo.profiles.value.first { it.id == other.id }.target)
    }

    @Test
    fun `a failed pairing leaves the Profile and credentials as they were`() = runTest {
        val p = repo.ensureDefaultProfile("main")
        target(platform.baseUrl).pairAnswer = ApiResult.Error(422, ErrorCode.PAIRING_INVALID, "کد اتصال نامعتبر است یا منقضی شده است.")
        assertEquals(ErrorCode.PAIRING_INVALID, assertIs<ApiResult.Error>(connections.pair(p.id, "XXXX-XXXX", "n")).code)
        target(platform.baseUrl).pairAnswer = ApiResult.Unreachable("ConnectException")
        assertIs<ApiResult.Unreachable>(connections.pair(p.id, "XXXX-XXXX", "n"))

        assertNull(repo.profiles.value.single().connection)
        assertNull(credentials.get(p.id))
    }

    @Test
    fun `Cards refresh replaces Cards and Banks with the credential (REQ-DEV-008)`() = runTest {
        val p = repo.ensureDefaultProfile("main")
        connections.pair(p.id, "K7QM-2XPA", "n")
        val r = assertIs<ApiResult.Ok<Profile>>(connections.refreshCards(p.id)).value

        assertEquals(listOf(card2), r.connection!!.cards)
        assertEquals(listOf(mellat), r.connection!!.banks)
        assertEquals("cards cred=dvc_${platform.baseUrl}", target(platform.baseUrl).calls.last())
    }

    @Test
    fun `refreshing all Cards on start skips unpaired and revoked Profiles (REQ-APP-040)`() = runTest {
        val a = repo.ensureDefaultProfile("a")
        repo.add("b", custom)
        connections.pair(a.id, "K7QM-2XPA", "n")
        connections.refreshAllCards()
        assertEquals(1, target(platform.baseUrl).calls.count { it.startsWith("cards") })
        assertTrue(target(custom.baseUrl).calls.isEmpty())

        connections.markRevoked(a.id)
        connections.refreshAllCards()
        assertEquals(1, target(platform.baseUrl).calls.count { it.startsWith("cards") })
    }

    @Test
    fun `a 401 marks the Profile revoked once, and pairing again clears it and keeps Card Bindings (REQ-APP-030)`() = runTest {
        val p = repo.ensureDefaultProfile("main")
        connections.pair(p.id, "K7QM-2XPA", "n")
        repo.update(p.id) { it.copy(cardBindings = listOf(CardBinding("700717", "melli-a", listOf("ck_1")))) }
        target(platform.baseUrl).cardsAnswer = ApiResult.Error(401, ErrorCode.UNAUTHORIZED, "")

        assertTrue(assertIs<ApiResult.Error>(connections.refreshCards(p.id)).unauthorized)
        val revoked = repo.profiles.value.single()
        assertTrue(revoked.revoked)
        assertEquals(now, revoked.connection!!.revokedAt)
        assertFalse(connections.markRevoked(p.id))
        assertEquals(1, events.count { it.startsWith("profile_revoked") })

        target(platform.baseUrl).pairAnswer = ApiResult.Ok(paired("dvc_new"))
        val again = assertIs<ApiResult.Ok<Profile>>(connections.pair(p.id, "NEWC-ODE2", "n")).value
        assertFalse(again.revoked)
        assertEquals("dvc_new", credentials.get(p.id))
        assertEquals(listOf(CardBinding("700717", "melli-a", listOf("ck_1"))), again.cardBindings)
    }

    @Test
    fun `removing a paired Profile calls Unpair with its credential first (REQ-DEV-009, REQ-APP-040)`() = runTest {
        val a = repo.ensureDefaultProfile("a")
        val b = repo.add("b", custom)
        connections.pair(b.id, "BBBB-BBBB", "n")

        assertEquals(Removal.Removed(deviceStillListed = false), connections.remove(b.id, evenIfUnpairFails = false))
        assertEquals("unpair cred=dvc_https://pay.example.com", target(custom.baseUrl).calls.last())
        assertEquals(listOf(a.id), repo.profiles.value.map { it.id })
        assertNull(credentials.get(b.id))
        assertEquals(listOf(b.id), removed)
    }

    @Test
    fun `a revoked or unpaired Profile is removed without Unpair (REQ-APP-040)`() = runTest {
        val a = repo.ensureDefaultProfile("a")
        val b = repo.add("b", custom)
        connections.pair(a.id, "K7QM-2XPA", "n")
        connections.markRevoked(a.id)

        assertEquals(Removal.Removed(false), connections.remove(a.id, evenIfUnpairFails = false))
        assertEquals(Removal.Removed(false), connections.remove(b.id, evenIfUnpairFails = false))
        assertTrue(target(platform.baseUrl).calls.none { it.startsWith("unpair") })
        assertTrue(target(custom.baseUrl).calls.isEmpty())
    }

    @Test
    fun `when Unpair fails the Profile stays, or goes with the Device still listed when the user insists`() = runTest {
        val a = repo.ensureDefaultProfile("a")
        val b = repo.add("b", custom)
        connections.pair(b.id, "BBBB-BBBB", "n")
        target(custom.baseUrl).unpairAnswer = ApiResult.Unreachable("ConnectException")

        val kept = assertIs<Removal.UnpairFailed>(connections.remove(b.id, evenIfUnpairFails = false))
        assertIs<ApiResult.Unreachable>(kept.failure)
        assertEquals(listOf(a.id, b.id), repo.profiles.value.map { it.id })
        assertEquals("dvc_https://pay.example.com", credentials.get(b.id))
        assertTrue(removed.isEmpty())

        assertEquals(Removal.Removed(deviceStillListed = true), connections.remove(b.id, evenIfUnpairFails = true))
        assertEquals(listOf(a.id), repo.profiles.value.map { it.id })
        assertNull(credentials.get(b.id))
    }

    @Test
    fun `a 401 to Unpair means the target already refuses the credential, so it counts as done`() = runTest {
        val a = repo.ensureDefaultProfile("a")
        connections.pair(a.id, "K7QM-2XPA", "n")
        target(platform.baseUrl).unpairAnswer = ApiResult.Error(401, ErrorCode.UNAUTHORIZED, "")
        assertEquals(Removal.Removed(false), connections.remove(a.id, evenIfUnpairFails = false))
    }

    @Test
    fun `a paired Profile whose credential is lost can't Unpair, so its Device stays listed`() = runTest {
        val a = repo.ensureDefaultProfile("a")
        connections.pair(a.id, "K7QM-2XPA", "n")
        credentials.delete(a.id)
        assertIs<Removal.UnpairFailed>(connections.remove(a.id, evenIfUnpairFails = false))
        assertEquals(Removal.Removed(deviceStillListed = true), connections.remove(a.id, evenIfUnpairFails = true))
        assertTrue(target(platform.baseUrl).calls.none { it.startsWith("unpair") })
    }

    @Test
    fun `removing the last Profile makes a new default one (REQ-APP-021)`() = runTest {
        val a = repo.ensureDefaultProfile("a")
        connections.remove(a.id, evenIfUnpairFails = false)
        val fresh = repo.profiles.value.single()
        assertEquals("پروفایل اصلی", fresh.name)
        assertEquals(platform, fresh.target)
        assertFalse(fresh.id == a.id)
    }

    @Test
    fun `orphan credentials are dropped`() {
        val a = repo.ensureDefaultProfile("a")
        credentials.put(a.id, "dvc_a")
        credentials.put("gone", "dvc_gone")
        connections.dropOrphanCredentials()
        assertEquals("dvc_a", credentials.get(a.id))
        assertNull(credentials.get("gone"))
    }

    @Test
    fun `events carry ids and codes, never the credential or the pairing code (REQ-OPS-011)`() = runTest {
        val a = repo.ensureDefaultProfile("a")
        connections.pair(a.id, "K7QM-2XPA", "n")
        connections.refreshCards(a.id)
        connections.remove(a.id, evenIfUnpairFails = false)
        assertEquals(listOf("pair_ok", "cards_refreshed", "unpair_ok", "profile_removed"), events.map { it.substringBefore(' ') })
        for (e in events) {
            assertFalse(e.contains("dvc_"), e)
            assertFalse(e.contains("K7QM", ignoreCase = true), e)
        }
    }
}
