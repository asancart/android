package io.github.asancart.android.core.profile

import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.heartbeat.HeartbeatSetting
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

/**
 * An API target (REQ-APP-001): the Platform, or another service with a base URL typed by the user
 * (REQ-APP-022). The Device API lives under `/v1/device` of [baseUrl].
 */
data class ApiTarget(val kind: Kind, val baseUrl: String) {
    enum class Kind { PLATFORM, CUSTOM }

    /** Host shown in the UI, with the port when there is one: `api.asancart.org`, `10.0.2.2:8080`. */
    val host: String
        get() = runCatching {
            val uri = URI(baseUrl)
            uri.host?.let { if (uri.port == -1) it else "$it:${uri.port}" }
        }.getOrNull() ?: baseUrl

    companion object {
        /**
         * The Platform target. Its address comes from the app build, not from here: release builds
         * use the production `https://api.asancart.org` (DEC-029), debug builds a local backend.
         */
        fun platform(baseUrl: String) = ApiTarget(Kind.PLATFORM, baseUrl)
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
