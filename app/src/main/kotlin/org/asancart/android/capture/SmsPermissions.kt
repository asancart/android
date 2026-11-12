package org.asancart.android.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * Capture seam. T4.2 adds the manifest-registered `SMS_RECEIVED` receiver (REQ-APP-010), the inbox
 * re-scan from the last checkpoint on every start (REQ-APP-011) and the permissions they need. Until
 * then the manifest asks for no SMS permission, so [granted] is false and no Profile can be turned on
 * (REQ-APP-027).
 */
class SmsPermissions(private val context: Context) {
    /** Both receive SMS and read SMS (REQ-APP-026). */
    fun granted(): Boolean = REQUIRED.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private companion object {
        val REQUIRED = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    }
}
