package io.github.asancart.android.core.profile

/** Why a Profile can't be turned on yet (REQ-APP-027). */
enum class ActivationBlocker {
    /** The Profile has no Card Binding. */
    NO_CARD_BINDING,

    /** Receive SMS or read SMS is not granted (REQ-APP-026). */
    SMS_PERMISSION_MISSING,
}

/**
 * REQ-APP-027: turning a Profile on is blocked, with the reasons shown, until it has at least one
 * Card Binding and both SMS permissions are granted. Empty means the switch can be turned on.
 */
fun Profile.activationBlockers(smsPermissionsGranted: Boolean): Set<ActivationBlocker> = buildSet {
    if (cardBindings.isEmpty()) add(ActivationBlocker.NO_CARD_BINDING)
    if (!smsPermissionsGranted) add(ActivationBlocker.SMS_PERMISSION_MISSING)
}
