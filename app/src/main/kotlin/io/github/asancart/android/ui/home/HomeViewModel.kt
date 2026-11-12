package io.github.asancart.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.core.profile.ProfileRepository
import io.github.asancart.android.core.profile.activationBlockers

/** REQ-APP-029: one card per Profile. Queue, last SMS and last heartbeat fill in with T4.2 and T4.4. */
class HomeViewModel(
    private val profiles: ProfileRepository,
    private val smsPermissionsGranted: () -> Boolean,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = profiles.profiles
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(profiles.profiles.value))

    /** REQ-APP-027: turning on is refused while a blocker remains; turning off always works. */
    fun setEnabled(id: String, enabled: Boolean) {
        val profile = profiles.profiles.value.firstOrNull { it.id == id } ?: return
        if (enabled && profile.activationBlockers(smsPermissionsGranted()).isNotEmpty()) return
        profiles.setEnabled(id, enabled)
    }

    private fun toUiState(list: List<Profile>): HomeUiState {
        val granted = smsPermissionsGranted()
        return HomeUiState(list.map { it.toCard(granted) })
    }
}

data class HomeUiState(val profiles: List<ProfileCard>) {
    /** "All Profiles that are on work" only makes sense once one is on. */
    val showAllOk: Boolean get() = profiles.any { it.enabled }
}

data class ProfileCard(
    val id: String,
    val name: String,
    val isPlatform: Boolean,
    val shopName: String?,
    val host: String,
    val enabled: Boolean,
    val canTurnOn: Boolean,
    val queueLength: Int,
    /** "Melli 4821" style labels of Cards with a Card Binding. */
    val boundCards: List<String>,
    val setupFinished: Boolean,
)

private fun Profile.toCard(smsPermissionsGranted: Boolean): ProfileCard {
    val connection = connection
    val boundKeys = cardBindings.flatMap { it.cardKeys }.toSet()
    val cards = connection?.cards.orEmpty()
    return ProfileCard(
        id = id,
        name = name,
        isPlatform = target.kind == ApiTarget.Kind.PLATFORM,
        shopName = connection?.shop?.name,
        host = target.host,
        enabled = enabled,
        canTurnOn = activationBlockers(smsPermissionsGranted).isEmpty(),
        queueLength = 0,
        boundCards = cards.filter { it.cardKey in boundKeys }.map { it.maskedNumber.takeLast(4) },
        // Design C1: setup is done once the Profile is paired and every paired Card has a binding.
        setupFinished = connection != null && cards.isNotEmpty() && cards.all { it.cardKey in boundKeys },
    )
}
