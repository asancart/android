package io.github.asancart.android.core.template

import io.github.asancart.android.core.deviceapi.ParserTemplateItem
import io.github.asancart.android.core.deviceapi.ParserTemplatesResponse
import java.time.Instant

/** A Parser Template kept on the phone: official (REQ-DEV-004) or custom (DEC-014). */
data class StoredTemplate(
    /** Server id for official templates; `custom/<name>` for custom ones. */
    val id: String,
    val bankId: String?,
    val version: Int,
    val custom: Boolean,
    val template: ParserTemplate,
) {
    val name: String get() = template.name
}

/**
 * REQ-DEV-006: apply a Parser Templates response by `id`. `published` stores or replaces (in place,
 * so stored order holds), `retired` deletes; applying twice is harmless. The next `since` is the
 * response's `server_time`, never the phone's clock. A published body that fails schema v1 is
 * skipped and the stored copy, if any, is kept.
 */
object TemplateSync {
    data class Applied(val templates: List<StoredTemplate>, val since: Instant, val rejected: List<String>)

    fun apply(current: List<StoredTemplate>, response: ParserTemplatesResponse): Applied {
        val list = current.toMutableList()
        val rejected = mutableListOf<String>()
        for (item in response.items) {
            val at = list.indexOfFirst { it.id == item.id && !it.custom }
            when (item.status) {
                ParserTemplateItem.Status.RETIRED -> if (at >= 0) list.removeAt(at)
                ParserTemplateItem.Status.PUBLISHED -> {
                    val parsed = item.body?.let(TemplateSchema::parse)
                    if (parsed !is TemplateSchema.Result.Valid) {
                        rejected += item.id
                        continue
                    }
                    val stored = StoredTemplate(item.id, item.bankId, item.version, custom = false, template = parsed.template)
                    if (at >= 0) list[at] = stored else list.add(officialInsertAt(list), stored)
                }
            }
        }
        return Applied(list, response.serverTime, rejected)
    }

    /** Official templates before custom ones, so stored order is official, then custom. */
    private fun officialInsertAt(list: List<StoredTemplate>): Int = list.indexOfFirst { it.custom }.let { if (it < 0) list.size else it }
}
