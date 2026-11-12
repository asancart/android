package org.asancart.android.core.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Profiles on this Device (REQ-APP-020). */
interface ProfileRepository {
    val profiles: StateFlow<List<Profile>>

    /**
     * REQ-APP-021: the app never opens empty. Creates the default Profile (the Platform target,
     * preselected per REQ-APP-022) when there is none, and returns the first Profile.
     */
    fun ensureDefaultProfile(name: String): Profile

    /** The Profile's on/off switch. Callers check [activationBlockers] before turning one on. */
    fun setEnabled(id: String, enabled: Boolean)
}

/** Keeps Profiles in memory. Replaced by on-disk storage with pairing in T4.3. */
class InMemoryProfileRepository(
    /** The Platform target of this build (see [ApiTarget.platform]). */
    private val platform: ApiTarget,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ProfileRepository {
    private val state = MutableStateFlow<List<Profile>>(emptyList())

    override val profiles: StateFlow<List<Profile>> = state.asStateFlow()

    override fun ensureDefaultProfile(name: String): Profile {
        state.update { current ->
            current.ifEmpty { listOf(Profile(id = newId(), name = name, target = platform)) }
        }
        return state.value.first()
    }

    override fun setEnabled(id: String, enabled: Boolean) {
        state.update { current -> current.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }
}
