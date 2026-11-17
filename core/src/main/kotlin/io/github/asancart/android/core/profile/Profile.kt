package io.github.asancart.android.core.profile

import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.heartbeat.HeartbeatSetting
import java.net.URI
import java.time.Instant

/**
 * A Profile: one API target with its Device credential, Shop, Card Bindings, heartbeat setting and
 * on/off switch (REQ-APP-020). Stored on the phone by [ProfileRepository]; paired, refreshed and
 * removed by [ProfileConnections] (T4.3).
 *
 * The Device credential is deliberately not a field here: it is kept in encrypted storage
 * ([CredentialStore]) and looked up by [id], so a Profile can be logged or shown without leaking it
 * (REQ-OPS-003).
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
    /**
     * When the switch was last turned on. An SMS received before it is never captured for this
     * Profile, also not by the inbox re-scan (REQ-APP-011): off means off.
     */
    val enabledAt: Instant? = null,
) {
    /** True when an SMS received at [time] belongs to this Profile's capture. */
    fun captures(time: Instant): Boolean = enabled && (enabledAt == null || !time.isBefore(enabledAt))

    /** Paired once (REQ-APP-023); it may since have been revoked. */
    val paired: Boolean get() = connection != null

    /** REQ-APP-030: the target answered `401`; nothing is sent until it is paired again or a retry succeeds. */
    val revoked: Boolean get() = connection?.revokedAt != null
}

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

        /** Another service's target, from an address [TargetUrl.parse] accepted (REQ-APP-022). */
        fun custom(url: TargetUrl.Valid) = ApiTarget(Kind.CUSTOM, url.baseUrl)
    }
}

/** What pairing returned (REQ-DEV-001), [cards] and [banks] refreshed by REQ-DEV-008. */
data class Connection(
    val deviceId: String,
    /** Defaults to the phone model, editable (REQ-APP-023). */
    val deviceName: String,
    val shop: ShopRef,
    val cards: List<Card>,
    val banks: List<Bank>,
    /** When a `401` marked the Profile revoked (REQ-APP-030); null while the credential works. */
    val revokedAt: Instant? = null,
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
