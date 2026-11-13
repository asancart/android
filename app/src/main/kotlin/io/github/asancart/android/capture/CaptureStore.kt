package io.github.asancart.android.capture

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.asancart.android.core.capture.UnparsedReason
import io.github.asancart.android.core.capture.UnparsedSms
import io.github.asancart.android.core.queue.TransferReport
import io.github.asancart.android.core.template.StoredTemplate
import io.github.asancart.android.core.template.TemplateSchema
import java.time.Instant

/**
 * Capture data on the phone, in app-private storage (backups are off): the unparsed list
 * (REQ-APP-015), the outgoing queue (REQ-APP-013; sending is T4.4), fingerprints of handled SMS,
 * the Parser Templates and the inbox checkpoint (REQ-APP-011). SMS text is kept only for unparsed
 * SMS and never leaves the Device (DEC-003).
 *
 * [name] null keeps the database in memory (tests).
 */
class CaptureStore(context: Context, name: String? = "capture.db") : SQLiteOpenHelper(context, name, null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE unparsed (id TEXT PRIMARY KEY, profile_id TEXT NOT NULL, sender TEXT NOT NULL, body TEXT NOT NULL, " +
                "received_at INTEGER NOT NULL, reason TEXT NOT NULL, template_name TEXT, templates_tried TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE outgoing (client_report_id TEXT PRIMARY KEY, profile_id TEXT NOT NULL, card_keys TEXT NOT NULL, " +
                "amount INTEGER NOT NULL, sms_time INTEGER NOT NULL, reference TEXT, created_at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE TABLE seen (fingerprint TEXT NOT NULL, received_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX seen_fingerprint ON seen (fingerprint)")
        db.execSQL("CREATE TABLE templates (id TEXT PRIMARY KEY, position INTEGER NOT NULL, bank_id TEXT, version INTEGER NOT NULL, custom INTEGER NOT NULL, body TEXT NOT NULL)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Runs [block] in one SQLite transaction: its writes commit together, or none of them do. */
    fun <T> inTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            return block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    // Unparsed list (REQ-APP-015)

    fun insertUnparsed(u: UnparsedSms) {
        writableDatabase.insertWithOnConflict("unparsed", null, u.values(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteUnparsed(id: String) {
        writableDatabase.delete("unparsed", "id = ?", arrayOf(id))
    }

    /** Newest first. */
    fun unparsed(): List<UnparsedSms> = readableDatabase.query("unparsed", null, null, null, null, null, "received_at DESC").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    UnparsedSms(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        profileId = c.getString(c.getColumnIndexOrThrow("profile_id")),
                        sender = c.getString(c.getColumnIndexOrThrow("sender")),
                        body = c.getString(c.getColumnIndexOrThrow("body")),
                        receivedAt = Instant.ofEpochMilli(c.getLong(c.getColumnIndexOrThrow("received_at"))),
                        reason = UnparsedReason.valueOf(c.getString(c.getColumnIndexOrThrow("reason"))),
                        templateName = c.getString(c.getColumnIndexOrThrow("template_name")),
                        templatesTried = c.getString(c.getColumnIndexOrThrow("templates_tried")).split('\n').filter { it.isNotEmpty() },
                    ),
                )
            }
        }
    }

    private fun UnparsedSms.values() = ContentValues().apply {
        put("id", id)
        put("profile_id", profileId)
        put("sender", sender)
        put("body", body)
        put("received_at", receivedAt.toEpochMilli())
        put("reason", reason.name)
        put("template_name", templateName)
        put("templates_tried", templatesTried.joinToString("\n"))
    }

    // Outgoing queue, one per Profile (REQ-APP-013)

    fun enqueue(profileId: String, report: TransferReport, now: Instant) {
        val v = ContentValues().apply {
            put("client_report_id", report.clientReportId)
            put("profile_id", profileId)
            put("card_keys", report.cardKeys.joinToString("\n"))
            put("amount", report.amount)
            put("sms_time", report.smsTime.toEpochMilli())
            put("reference", report.reference)
            put("created_at", now.toEpochMilli())
        }
        writableDatabase.insertWithOnConflict("outgoing", null, v, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Oldest first. */
    fun pending(profileId: String): List<TransferReport> =
        readableDatabase.query("outgoing", null, "profile_id = ?", arrayOf(profileId), null, null, "created_at ASC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        TransferReport(
                            clientReportId = c.getString(c.getColumnIndexOrThrow("client_report_id")),
                            cardKeys = c.getString(c.getColumnIndexOrThrow("card_keys")).split('\n'),
                            amount = c.getLong(c.getColumnIndexOrThrow("amount")),
                            smsTime = Instant.ofEpochMilli(c.getLong(c.getColumnIndexOrThrow("sms_time"))),
                            reference = c.getString(c.getColumnIndexOrThrow("reference")),
                        ),
                    )
                }
            }
        }

    fun pendingCounts(): Map<String, Int> =
        readableDatabase.rawQuery("SELECT profile_id, COUNT(*) FROM outgoing GROUP BY profile_id", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        }

    // Handled SMS, so the receiver and the inbox re-scan never handle one SMS twice

    fun seenNear(fingerprint: String, at: Instant, windowMillis: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM seen WHERE fingerprint = ? AND received_at BETWEEN ? AND ? LIMIT 1",
            arrayOf(fingerprint, (at.toEpochMilli() - windowMillis).toString(), (at.toEpochMilli() + windowMillis).toString()),
        ).use { it.moveToFirst() }

    /** Throws on failure, so a caller's [inTransaction] rolls back the writes made with it. */
    fun markSeen(fingerprint: String, at: Instant) {
        writableDatabase.insertOrThrow("seen", null, ContentValues().apply {
            put("fingerprint", fingerprint)
            put("received_at", at.toEpochMilli())
        })
    }

    fun pruneSeen(before: Instant) {
        writableDatabase.delete("seen", "received_at < ?", arrayOf(before.toEpochMilli().toString()))
    }

    // Parser Templates, in stored order (REQ-DEV-053 ties)

    fun templates(): List<StoredTemplate> = readableDatabase.query("templates", null, null, null, null, null, "position ASC").use { c ->
        buildList {
            while (c.moveToNext()) {
                val parsed = TemplateSchema.parse(c.getString(c.getColumnIndexOrThrow("body")))
                if (parsed is TemplateSchema.Result.Valid) {
                    add(
                        StoredTemplate(
                            id = c.getString(c.getColumnIndexOrThrow("id")),
                            bankId = c.getString(c.getColumnIndexOrThrow("bank_id")),
                            version = c.getInt(c.getColumnIndexOrThrow("version")),
                            custom = c.getInt(c.getColumnIndexOrThrow("custom")) == 1,
                            template = parsed.template,
                        ),
                    )
                }
            }
        }
    }

    fun replaceTemplates(list: List<StoredTemplate>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("templates", null, null)
            list.forEachIndexed { i, t ->
                db.insert("templates", null, ContentValues().apply {
                    put("id", t.id)
                    put("position", i)
                    put("bank_id", t.bankId)
                    put("version", t.version)
                    put("custom", if (t.custom) 1 else 0)
                    put("body", TemplateSchema.toJson(t.template))
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Small settings

    fun meta(key: String): String? =
        readableDatabase.query("meta", arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun setMeta(key: String, value: String) {
        writableDatabase.insertWithOnConflict("meta", null, ContentValues().apply {
            put("key", key)
            put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        private const val VERSION = 1
        const val KEY_INBOX_CHECKPOINT = "inbox_checkpoint"
        const val KEY_TEMPLATES_SINCE = "templates_since"
    }
}
