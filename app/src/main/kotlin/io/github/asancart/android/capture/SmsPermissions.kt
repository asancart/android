package io.github.asancart.android.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** The two SMS permissions capture needs (REQ-APP-026). Both are required to turn a Profile on (REQ-APP-027). */
class SmsPermissions(private val context: Context) {
    /** Both receive SMS and read SMS (REQ-APP-026). */
    fun granted(): Boolean = REQUIRED.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** Read SMS alone is enough for the inbox re-scan (REQ-APP-011). */
    fun canReadInbox(): Boolean = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    companion object {
        val REQUIRED = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    }
}
