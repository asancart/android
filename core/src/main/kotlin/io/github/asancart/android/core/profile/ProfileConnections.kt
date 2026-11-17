package io.github.asancart.android.core.profile

import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.ErrorCode
import io.github.asancart.android.core.deviceapi.PairRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * A Profile's link to its API target (T4.3): Pair (REQ-DEV-001, REQ-APP-023) with the Platform or a
 * custom target, the Cards refresh (REQ-DEV-008, REQ-APP-040), the revoked state a `401` sets
 * (REQ-APP-030) and Profile removal with Unpair (REQ-DEV-009, REQ-APP-040). The Device credential
 * goes only to [credentials]; it is never logged, nor is the pairing code ([events] gets ids, codes
 * and counts, REQ-OPS-011).
 *
 * Pairing and removal take turns, so a Profile can't be removed while it is being paired.
 */
class ProfileConnections(
    private val profiles: ProfileRepository,
    private val credentials: CredentialStore,
    /** The Device API of a target, with a credential or none (Pair). */
    private val apiFor: (target: ApiTarget, credential: String?) -> DeviceApi,
    /** Sent as `app_version` (REQ-DEV-001). */
    private val appVersion: String,
    /** REQ-APP-021: the name of the default Profile made when the last one is removed. */
    private val defaultProfileName: () -> String,
    /** The rest of a removed Profile on the phone: its queue, unparsed SMS and settings (REQ-APP-030). */
    private val onRemoved: (profileId: String) -> Unit = {},
    private val events: EventSink = EventSink { _, _ -> },
    private val clock: () -> Instant = Instant::now,
) {
    private val mutex = Mutex()

    /** The Device API of [profile] with its credential; null credential before pairing. */
    fun api(profile: Profile): DeviceApi = apiFor(profile.target, credentials.get(profile.id))

    /**
     * Pairs [profileId] with its target using the one-time [pairingCode] (REQ-DEV-001). On success the
     * credential is stored, the Profile gets its Shop, Cards and Banks, and a revoked Profile is no
     * longer revoked: pairing again in the same Profile keeps its Card Bindings (REQ-APP-030). On any
     * failure the Profile is left as it was.
     */
    suspend fun pair(profileId: String, pairingCode: String, deviceName: String): ApiResult<Profile> = mutex.withLock {
        val profile = find(profileId)
        when (val r = apiFor(profile.target, null).pair(PairRequest(pairingCode, deviceName, appVersion))) {
            is ApiResult.Ok -> {
                val paired = r.value
                credentials.put(profileId, paired.deviceCredential)
                val connection = Connection(paired.deviceId, deviceName, paired.shop, paired.cards, paired.banks)
                val updated = profiles.update(profileId) { it.copy(connection = connection) } ?: error("Profile $profileId was removed")
                events.event("pair_ok", "profile_id" to profileId, "target" to profile.target.kind.name, "device_id" to paired.deviceId, "cards" to paired.cards.size)
                ApiResult.Ok(updated)
            }
            is ApiResult.Error -> {
                events.event("pair_failed", "profile_id" to profileId, "target" to profile.target.kind.name, "http" to r.httpStatus, "code" to r.code, "request_id" to r.requestId)
                r
            }
            is ApiResult.Unreachable -> {
                events.event("pair_failed", "profile_id" to profileId, "target" to profile.target.kind.name, "reason" to "unreachable")
                r
            }
        }
    }

    /**
     * REQ-DEV-008: the Profile's Cards and Banks as the target has them now, on app start and from the
     * Profile screen (REQ-APP-040). A Profile not paired, or revoked, is returned as it is, with no call.
     * A `401` marks it revoked (REQ-APP-030).
     */
    suspend fun refreshCards(profileId: String): ApiResult<Profile> {
        val profile = find(profileId)
        if (!profile.paired || profile.revoked) return ApiResult.Ok(profile)
        return when (val r = api(profile).cards()) {
            is ApiResult.Ok -> {
                val updated = profiles.update(profileId) { p ->
                    p.copy(connection = p.connection?.copy(cards = r.value.cards, banks = r.value.banks))
                } ?: profile
                events.event("cards_refreshed", "profile_id" to profileId, "cards" to r.value.cards.size)
                ApiResult.Ok(updated)
            }
            is ApiResult.Error -> {
                events.event("cards_refresh_failed", "profile_id" to profileId, "http" to r.httpStatus, "code" to r.code, "request_id" to r.requestId)
                if (r.unauthorized) markRevoked(profileId)
                r
            }
            is ApiResult.Unreachable -> {
                events.event("cards_refresh_failed", "profile_id" to profileId, "reason" to "unreachable")
                r
            }
        }
    }

    /** App start (REQ-APP-040): every paired Profile that isn't revoked. */
    suspend fun refreshAllCards() {
        for (p in profiles.profiles.value) if (p.paired && !p.revoked) refreshCards(p.id)
    }

    /**
     * REQ-APP-030: a `401` on any authenticated call marks the Profile revoked. True when this call
     * marked it (the first `401`), so the caller notifies once.
     */
    fun markRevoked(profileId: String): Boolean {
        var marked = false
        profiles.update(profileId) { p ->
            val c = p.connection
            if (c == null || c.revokedAt != null) p else p.copy(connection = c.copy(revokedAt = clock())).also { marked = true }
        }
        if (marked) events.event("profile_revoked", "profile_id" to profileId)
        return marked
    }

    /**
     * Removes a Profile (REQ-APP-040). A paired Profile that isn't revoked calls Unpair first
     * (REQ-DEV-009); a revoked one skips it. A `401` to Unpair means the target already refuses the
     * credential, so it counts as done. When Unpair fails otherwise, the Device stays listed in the
     * dashboard until the Shop revokes it: with [evenIfUnpairFails] false the Profile is kept and
     * [Removal.UnpairFailed] says why, so the user can try again or remove it anyway.
     *
     * Removal deletes the credential and, through [onRemoved], the Profile's queue and other data. The
     * last Profile is replaced by a new default one (REQ-APP-021).
     */
    suspend fun remove(profileId: String, evenIfUnpairFails: Boolean): Removal = mutex.withLock {
        val profile = find(profileId)
        var stillListed = false
        if (profile.paired && !profile.revoked) {
            val credential = credentials.get(profileId)
            val r: ApiResult<Unit> = if (credential == null) ApiResult.Error(401, ErrorCode.UNAUTHORIZED, "") else apiFor(profile.target, credential).unpair()
            val unpaired = r is ApiResult.Ok || (credential != null && r is ApiResult.Error && r.unauthorized)
            when (r) {
                is ApiResult.Ok -> events.event("unpair_ok", "profile_id" to profileId)
                is ApiResult.Error -> events.event("unpair_failed", "profile_id" to profileId, "http" to r.httpStatus, "code" to r.code, "request_id" to r.requestId)
                is ApiResult.Unreachable -> events.event("unpair_failed", "profile_id" to profileId, "reason" to "unreachable")
            }
            if (!unpaired) {
                if (!evenIfUnpairFails) return@withLock Removal.UnpairFailed(r)
                stillListed = true
            }
        }
        profiles.remove(profileId)
        credentials.delete(profileId)
        onRemoved(profileId)
        events.event("profile_removed", "profile_id" to profileId, "device_still_listed" to stillListed)
        if (profiles.profiles.value.isEmpty()) profiles.ensureDefaultProfile(defaultProfileName())
        Removal.Removed(deviceStillListed = stillListed)
    }

    /** Drops credentials no Profile owns any more (a removal cut short by a crash). */
    fun dropOrphanCredentials() {
        credentials.retainOnly(profiles.profiles.value.map { it.id }.toSet())
    }

    private fun find(profileId: String): Profile =
        profiles.profiles.value.firstOrNull { it.id == profileId } ?: throw NoSuchElementException("no Profile $profileId")
}

/** The outcome of [ProfileConnections.remove]. */
sealed interface Removal {
    /**
     * The Profile is gone. [deviceStillListed]: Unpair failed or couldn't be called, so the Device stays
     * listed in the dashboard until the Shop revokes it (REQ-APP-040); the app tells the user.
     */
    data class Removed(val deviceStillListed: Boolean) : Removal

    /** Unpair failed and the Profile was kept ([ProfileConnections.remove] with `evenIfUnpairFails = false`). */
    data class UnpairFailed(val failure: ApiResult<Unit>) : Removal
}

/** Log events with ids, codes and counts only (REQ-OPS-011): never a credential, pairing code or SMS data. */
fun interface EventSink {
    fun event(name: String, vararg fields: Pair<String, Any?>)
}
