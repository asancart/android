package io.github.asancart.android.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.asancart.android.AsancartApp
import io.github.asancart.android.core.sms.IncomingSms
import java.time.Instant

/**
 * REQ-APP-010: the manifest-registered `SMS_RECEIVED` receiver, so capture works while the app is
 * closed. Only the system can deliver it (`android:permission="android.permission.BROADCAST_SMS"`).
 * The parts of a long SMS are joined; the receive time is now.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = fromIntent(intent, Instant.now())
        if (messages.isEmpty()) return
        val app = context.applicationContext as AsancartApp
        // Null when the receiver is called directly (tests); the work still runs.
        val pending: PendingResult? = goAsync()
        app.container.capture.onReceived(messages).invokeOnCompletion { pending?.finish() }
    }

    companion object {
        /** One [IncomingSms] per sender, parts in order. */
        fun fromIntent(intent: Intent, receivedAt: Instant): List<IncomingSms> {
            val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
            val bySender = LinkedHashMap<String, StringBuilder>()
            for (p in parts) {
                val sender = p.originatingAddress ?: p.displayOriginatingAddress ?: continue
                bySender.getOrPut(sender) { StringBuilder() }.append(p.messageBody.orEmpty())
            }
            return bySender.map { (sender, body) -> IncomingSms(sender, body.toString(), receivedAt) }
        }
    }
}
