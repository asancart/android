package io.github.asancart.android.capture

import android.content.Context
import android.provider.Telephony

/** One row of the system SMS inbox. [dateMillis] is the receive time the SMS app stored. */
data class InboxSms(val id: Long, val address: String, val body: String, val dateMillis: Long) {
    override fun toString(): String = "InboxSms(id=$id, address=$address, body=<${body.length} chars>, dateMillis=$dateMillis)"
}

/** The inbox, for the re-scan on every start (REQ-APP-011). */
fun interface InboxSource {
    /** Rows received after [afterMillis], oldest first; empty without the read SMS permission. */
    fun readSince(afterMillis: Long): List<InboxSms>
}

class AndroidInbox(private val context: Context, private val permissions: SmsPermissions) : InboxSource {
    override fun readSince(afterMillis: Long): List<InboxSms> {
        if (!permissions.canReadInbox()) return emptyList()
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(afterMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(InboxSms(id = c.getLong(0), address = c.getString(1).orEmpty(), body = c.getString(2).orEmpty(), dateMillis = c.getLong(3)))
                }
            }
        }
    }
}
