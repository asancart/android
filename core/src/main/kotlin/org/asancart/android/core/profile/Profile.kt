package org.asancart.android.core.profile

import org.asancart.android.core.deviceapi.Bank
import org.asancart.android.core.deviceapi.Card
import org.asancart.android.core.deviceapi.ShopRef
import org.asancart.android.core.heartbeat.HeartbeatSetting
import java.net.URI

/**
 * A Profile: one API target with its Device credential, Shop, Card Bindings, heartbeat setting and
 * on/off switch (REQ-APP-020). Pairing, bindings and storage come in T4.3.
 *
 * The Device credential is deliberately not a field here: it is kept in encrypted storage and looked
 * up by [id] (T4.3), so a Profile can be logged or shown without leaking it (REQ-OPS-003).
 */
data class Profile(
    val id: String,
    val name: String,
    val target: ApiTarget,
    /** Null until the Profile is paired (REQ-APP-023). */
    val connection: Connection? = null,
    val cardBindings: List<CardBinding> = emptyList(),
    val heartbeat: HeartbeatSetting = HeartbeatSetting.DEFAULT,
    /** REQ-APP-027: on = capture and send; off = ignore SMS, send nothing. */
    val enabled: Boolean = false,
)

/** An API target (REQ-APP-001): the Platform with its built-in address, or another service (REQ-APP-022). */
data class ApiTarget(val kind: Kind, val baseUrl: String) {
    enum class Kind { PLATFORM, CUSTOM }

    /** Host shown in the UI, e.g. `api.asancart.org`. */
    val host: String
        get() = runCatching { URI(baseUrl).host }.getOrNull() ?: baseUrl

    companion object {
        /** DEC-029: the app's built-in Platform address. The Device API lives under `/v1/device`. */
        const val PLATFORM_BASE_URL = "https://api.asancart.org"

        val PLATFORM = ApiTarget(Kind.PLATFORM, PLATFORM_BASE_URL)
    }
}

/** What pairing returned (REQ-DEV-001), refreshed by REQ-DEV-008. */
data class Connection(
    val deviceId: String,
    /** Defaults to the phone model, editable (REQ-APP-023). */
    val deviceName: String,
    val shop: ShopRef,
    val cards: List<Card>,
    val banks: List<Bank>,
)

/**
 * A Card Binding (REQ-APP-002): an SMS sender and a Parser Template mapped to one or more Card Keys
 * of the same bank. A Card sent to several targets has a binding in each of their Profiles.
 */
data class CardBinding(val sender: String, val templateName: String, val cardKeys: List<String>) {
    init {
        require(cardKeys.isNotEmpty()) { "a Card Binding needs at least one Card Key" }
    }
}
