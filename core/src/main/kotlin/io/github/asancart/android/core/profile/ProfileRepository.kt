package io.github.asancart.android.core.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/** Profiles on this Device (REQ-APP-020). */
interface ProfileRepository {
    val profiles: StateFlow<List<Profile>>

    /**
     * REQ-APP-021: the app never opens empty. Creates the default Profile (the Platform target,
     * preselected per REQ-APP-022) when there is none, and returns the first Profile.
     */
    fun ensureDefaultProfile(name: String): Profile

    /** A new, unpaired Profile for [target] (REQ-APP-022), added at the end. */
    fun add(name: String, target: ApiTarget): Profile

    /** Adds [profile], or replaces the one with its id. */
    fun save(profile: Profile)

    /** Applies [change] to the Profile with [id] in one step; null when there is none. */
    fun update(id: String, change: (Profile) -> Profile): Profile?

    /** Removes the Profile with [id]; its credential and queue are the caller's (REQ-APP-040). */
    fun remove(id: String)

    /** The Profile's on/off switch. Callers check [activationBlockers] before turning one on. */
    fun setEnabled(id: String, enabled: Boolean, at: Instant = Instant.now())
}

/**
 * The repository's rules over one in-memory list; [persist] stores each new list before it is
 * published. Changes are serialized, so two writers never lose each other's change.
 */
abstract class ListProfileRepository(
    /** The Platform target of this build (see [ApiTarget.platform]). */
    protected val platform: ApiTarget,
    private val newId: () -> String,
    initial: List<Profile>,
) : ProfileRepository {
    private val lock = Any()
    private val state = MutableStateFlow(initial)

    override val profiles: StateFlow<List<Profile>> = state.asStateFlow()

    /** Stores [profiles] durably; throws when it can't, and the change is then not published. */
    protected abstract fun persist(profiles: List<Profile>)

    private fun mutate(block: (List<Profile>) -> List<Profile>): List<Profile> = synchronized(lock) {
        val next = block(state.value)
        if (next != state.value) {
            persist(next)
            state.value = next
        }
        next
    }

    override fun ensureDefaultProfile(name: String): Profile =
        mutate { current -> current.ifEmpty { listOf(Profile(id = newId(), name = name, target = platform)) } }.first()

    override fun add(name: String, target: ApiTarget): Profile {
        val profile = Profile(id = newId(), name = name, target = target)
        mutate { it + profile }
        return profile
    }

    override fun save(profile: Profile) {
        mutate { current ->
            if (current.any { it.id == profile.id }) current.map { if (it.id == profile.id) profile else it } else current + profile
        }
    }

    override fun update(id: String, change: (Profile) -> Profile): Profile? {
        var updated: Profile? = null
        mutate { current ->
            current.map { p -> if (p.id == id) change(p).also { updated = it } else p }
        }
        return updated
    }

    override fun remove(id: String) {
        mutate { current -> current.filterNot { it.id == id } }
    }

    override fun setEnabled(id: String, enabled: Boolean, at: Instant) {
        update(id) { it.copy(enabled = enabled, enabledAt = if (enabled) at else null) }
    }
}

/** Keeps Profiles in memory only (tests, previews). The app stores them with [JsonFileProfileRepository]. */
class InMemoryProfileRepository(
    platform: ApiTarget,
    newId: () -> String = { UUID.randomUUID().toString() },
) : ListProfileRepository(platform, newId, emptyList()) {
    override fun persist(profiles: List<Profile>) = Unit
}
