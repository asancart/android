package io.github.asancart.android.core.profile

import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.heartbeat.HeartbeatSetting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * Profiles stored in one JSON file in app-private storage (REQ-APP-020), so they survive restarts
 * and reboots. Backups are off (`data_extraction_rules.xml`): a new phone pairs again. The file holds
 * no Device credential ([CredentialStore]) and no SMS data.
 *
 * Each change is written to a temporary file, synced and renamed over the old one, so a crash
 * leaves the old or the new list, never half of one. The Platform target's address is not stored:
 * it is the build's (REQ-APP-022, REQ-INF-007), read again on every start. A file that can't be read
 * is kept as `<name>.bad` and the app starts with no Profiles ([onUnreadable]), then creates its
 * default one (REQ-APP-021).
 */
class JsonFileProfileRepository(
    private val file: File,
    platform: ApiTarget,
    newId: () -> String = { UUID.randomUUID().toString() },
    onUnreadable: (Exception) -> Unit = {},
) : ListProfileRepository(platform, newId, load(file, platform, onUnreadable)) {

    override fun persist(profiles: List<Profile>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(ProfileJson.encode(profiles).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) throw IOException("could not replace ${file.name}")
    }

    private companion object {
        fun load(file: File, platform: ApiTarget, onUnreadable: (Exception) -> Unit): List<Profile> {
            if (!file.exists()) return emptyList()
            return try {
                ProfileJson.decode(file.readText(Charsets.UTF_8), platform)
            } catch (e: Exception) {
                file.renameTo(File(file.path + ".bad"))
                onUnreadable(e)
                emptyList()
            }
        }
    }
}

/** The stored form of Profiles, version 1. */
object ProfileJson {
    private const val VERSION = 1

    fun encode(profiles: List<Profile>): String = buildJsonObject {
        put("version", VERSION)
        putJsonArray("profiles") { profiles.forEach { add(profile(it)) } }
    }.toString()

    /** [platform] is the address a PLATFORM target gets: the build's, never a stored one. */
    fun decode(text: String, platform: ApiTarget): List<Profile> {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["version"]?.jsonPrimitive?.int == VERSION) { "unknown profiles version" }
        return root["profiles"]!!.jsonArray.map { profile(it.jsonObject, platform) }
    }

    private fun profile(p: Profile): JsonObject = buildJsonObject {
        put("id", p.id)
        put("name", p.name)
        put(
            "target",
            buildJsonObject {
                put("kind", p.target.kind.name)
                if (p.target.kind == ApiTarget.Kind.CUSTOM) put("base_url", p.target.baseUrl)
            },
        )
        put("connection", p.connection?.let(::connection) ?: JsonNull)
        putJsonArray("card_bindings") {
            for (b in p.cardBindings) {
                add(
                    buildJsonObject {
                        put("sender", b.sender)
                        put("template_name", b.templateName)
                        putJsonArray("card_keys") { b.cardKeys.forEach { add(it) } }
                    },
                )
            }
        }
        put(
            "heartbeat",
            buildJsonObject {
                put("enabled", p.heartbeat.enabled)
                put("interval_sec", p.heartbeat.intervalSec)
            },
        )
        put("enabled", p.enabled)
        put("enabled_at", p.enabledAt.json())
    }

    private fun connection(c: Connection): JsonObject = buildJsonObject {
        put("device_id", c.deviceId)
        put("device_name", c.deviceName)
        put(
            "shop",
            buildJsonObject {
                put("id", c.shop.id)
                put("name", c.shop.name)
            },
        )
        putJsonArray("cards") {
            for (card in c.cards) {
                add(
                    buildJsonObject {
                        put("card_id", card.cardId)
                        put("card_key", card.cardKey)
                        put("masked_number", card.maskedNumber)
                        put("bank_id", card.bankId)
                    },
                )
            }
        }
        putJsonArray("banks") {
            for (bank in c.banks) {
                add(
                    buildJsonObject {
                        put("id", bank.id)
                        put("name", bank.name)
                        putJsonArray("required_fields") { bank.requiredFields.forEach { add(it) } }
                    },
                )
            }
        }
        put("revoked_at", c.revokedAt.json())
    }

    private fun profile(o: JsonObject, platform: ApiTarget): Profile {
        val target = o["target"]!!.jsonObject
        val kind = ApiTarget.Kind.valueOf(target.str("kind"))
        val heartbeat = o["heartbeat"]!!.jsonObject
        return Profile(
            id = o.str("id"),
            name = o.str("name"),
            target = if (kind == ApiTarget.Kind.PLATFORM) platform else ApiTarget(kind, target.str("base_url")),
            connection = (o["connection"] as? JsonObject)?.let(::connection),
            cardBindings = o["card_bindings"]!!.jsonArray.map { e ->
                val b = e.jsonObject
                CardBinding(b.str("sender"), b.str("template_name"), b.strings("card_keys"))
            },
            heartbeat = HeartbeatSetting(heartbeat["enabled"]!!.jsonPrimitive.boolean, heartbeat["interval_sec"]!!.jsonPrimitive.int),
            enabled = o["enabled"]!!.jsonPrimitive.boolean,
            enabledAt = o.time("enabled_at"),
        )
    }

    private fun connection(o: JsonObject): Connection {
        val shop = o["shop"]!!.jsonObject
        return Connection(
            deviceId = o.str("device_id"),
            deviceName = o.str("device_name"),
            shop = ShopRef(shop.str("id"), shop.str("name")),
            cards = o["cards"]!!.jsonArray.map { e ->
                val c = e.jsonObject
                Card(c.str("card_id"), c.str("card_key"), c.str("masked_number"), c.str("bank_id"))
            },
            banks = o["banks"]!!.jsonArray.map { e ->
                val b = e.jsonObject
                Bank(b.str("id"), b.str("name"), b.strings("required_fields"))
            },
            revokedAt = o.time("revoked_at"),
        )
    }

    private fun Instant?.json(): JsonElement = this?.let { JsonPrimitive(it.toString()) } ?: JsonNull

    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content

    private fun JsonObject.strings(key: String): List<String> = (this[key] as JsonArray).map { it.jsonPrimitive.content }

    private fun JsonObject.time(key: String): Instant? = when (val e = this[key]) {
        null, JsonNull -> null
        else -> Instant.parse(e.jsonPrimitive.content)
    }
}
