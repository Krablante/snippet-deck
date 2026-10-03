package com.rrajath.expander.sync

import com.rrajath.expander.data.Snippet
import com.rrajath.expander.util.SnippetBackupCodec
import com.rrajath.expander.domain.TriggerUtils
import java.util.Locale
import java.util.UUID

// Each installation owns one Drive file. Versions on different installations are never
// overwritten by another device, and deletions remain visible to devices that were offline.
internal data class SyncSnippet(
    val trigger: String,
    val expansion: String,
    val aliases: List<String>,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun sameContent(other: SyncSnippet?): Boolean = other != null &&
        trigger == other.trigger && expansion == other.expansion &&
        aliases == other.aliases && enabled == other.enabled

    fun asSnippet(): Snippet = Snippet(
        trigger = trigger, expansion = expansion, aliases = aliases,
        isEnabled = enabled, createdAt = createdAt, updatedAt = updatedAt,
    )
}

internal fun Snippet.forSync(): SyncSnippet = SyncSnippet(
    trigger, expansion, aliases, isEnabled, createdAt, updatedAt,
)

internal fun syncKey(trigger: String): String = trigger.lowercase(Locale.ROOT)

internal data class SyncVersion(
    val clock: Map<String, Long>,
    val value: SyncSnippet?, // null is a deletion, not an absent record.
)

internal data class SyncReplica(
    val format: String = "snippetdeck-sync",
    val schemaVersion: Int = 1,
    val deviceId: String,
    val sequence: Long,
    val entries: Map<String, List<SyncVersion>>,
)

internal data class SyncLocalState(
    val accountId: String,
    val replica: SyncReplica,
    val baseline: List<SyncSnippet>,
    val fileId: String? = null,
    val lastUploadedHash: String? = null,
    val seenFiles: Map<String, String>? = null,
)

internal class PendingSyncConflict(val triggers: List<String>) :
    IllegalStateException("Resolve conflicting edits before syncing")

internal data class SyncMerge(
    val replica: SyncReplica,
    val snippets: List<Snippet>?,
    val conflicts: List<String>,
)

internal object SyncLibrary {
    fun empty(accountId: String): SyncLocalState = SyncLocalState(
        accountId = accountId,
        replica = SyncReplica(deviceId = UUID.randomUUID().toString(), sequence = 0, entries = emptyMap()),
        baseline = emptyList(),
    )

    fun collectChanges(state: SyncLocalState, library: List<Snippet>): SyncLocalState {
        val now = library.associate { syncKey(it.trigger) to it.forSync() }
        val before = state.baseline.associateBy { syncKey(it.trigger) }
        val entries = state.replica.entries.toMutableMap()
        var sequence = state.replica.sequence
        for (key in (now.keys + before.keys).sorted()) {
            val current = now[key]
            if (current?.sameContent(before[key]) == true ||
                (current == null && before[key] == null)) continue
            // An edit during an unresolved conflict must never disappear from the baseline.
            if (entries[key].orEmpty().size > 1) throw PendingSyncConflict(listOf(key))
            val known = entries[key].orEmpty().flatMap { it.clock.entries }
                .groupBy({ it.key }, { it.value }).mapValues { it.value.max() }
                .toMutableMap()
            sequence++
            known[state.replica.deviceId] = sequence
            entries[key] = listOf(SyncVersion(known, current))
        }
        return state.copy(
            replica = state.replica.copy(sequence = sequence, entries = entries),
            baseline = library.map(Snippet::forSync),
        )
    }

    fun merge(own: SyncReplica, others: List<SyncReplica>, validate: Boolean = true): SyncMerge {
        return merge(own, others.asSequence(), validate)
    }

    fun merge(own: SyncReplica, others: Sequence<SyncReplica>, validate: Boolean = true): SyncMerge {
        val pending = mutableMapOf<String, MutableList<SyncVersion>>()
        for (replica in sequenceOf(own) + others) {
            for ((key, versions) in replica.entries) {
                require(versions.isNotEmpty()) { "Empty sync record" }
                val winners = pending.getOrPut(key) { mutableListOf() }
                versions.forEach { retainVersion(winners, it) }
            }
        }
        val entries = pending.mapValues { maximal(it.value) }
        require(entries.size <= 20_000 && entries.values.all { versions ->
            versions.size <= 50 && versions.all { it.clock.size <= 50 }
        }) { "Merged sync history exceeds the device limits" }
        var conflicts = entries.filterValues { it.size != 1 }.keys.sorted()
        var snippets = if (conflicts.isEmpty()) {
            entries.values.mapNotNull { it.single().value }.map(SyncSnippet::asSnippet)
                .sortedBy { syncKey(it.trigger) }
        } else null
        if (snippets != null && validate) {
            val seen = mutableSetOf<String>()
            val collisions = snippets.flatMap { TriggerUtils.allTriggers(it.trigger, it.aliases) }
                .filterNot { seen.add(TriggerUtils.matchKey(it)) }.distinct().sorted()
            if (collisions.isNotEmpty()) {
                conflicts = collisions
                snippets = null
            }
        }
        if (snippets != null && validate) {
            // The existing backup decoder is the single validator for trigger/alias collisions.
            SnippetBackupCodec.validateLibrary(snippets)
        }
        return SyncMerge(own.copy(entries = entries), snippets, conflicts)
    }

    fun keepLocal(state: SyncLocalState, library: List<Snippet>): SyncLocalState {
        val local = library.associate { syncKey(it.trigger) to it.forSync() }
        val entries = state.replica.entries.toMutableMap()
        var sequence = state.replica.sequence
        for (key in entries.keys + local.keys) {
            val clock = entries[key].orEmpty().flatMap { it.clock.entries }
                .groupBy({ it.key }, { it.value }).mapValues { it.value.max() }
                .toMutableMap()
            sequence++
            clock[state.replica.deviceId] = sequence
            entries[key] = listOf(SyncVersion(clock, local[key]))
        }
        return state.copy(
            replica = state.replica.copy(sequence = sequence, entries = entries),
            baseline = library.map(Snippet::forSync),
        )
    }

    private fun maximal(versions: List<SyncVersion>): List<SyncVersion> {
        val winners = mutableListOf<SyncVersion>()
        for (candidate in versions) {
            retainVersion(winners, candidate)
        }
        // Identical concurrent edits are one value with both clocks observed.
        var index = 0
        while (index < winners.size) {
            val other = (index + 1 until winners.size).firstOrNull {
                sameValue(winners[index].value, winners[it].value)
            }
            if (other == null) {
                index++
                continue
            }
            val a = winners[index]
            val b = winners.removeAt(other)
            val merged = SyncVersion(
                (a.clock.keys + b.clock.keys).associateWith { key ->
                    maxOf(a.clock[key] ?: 0, b.clock[key] ?: 0)
                },
                listOfNotNull(a.value, b.value).maxByOrNull { it.updatedAt },
            )
            winners[index] = merged
            winners.removeAll { it !== merged && dominates(merged.clock, it.clock) }
            index = 0
        }
        return winners
    }

    private fun retainVersion(winners: MutableList<SyncVersion>, candidate: SyncVersion) {
        require(candidate.clock.isNotEmpty() && candidate.clock.values.all { it > 0 }) { "Missing sync version" }
        val equal = winners.find { it.clock == candidate.clock }
        if (equal != null) {
            require(sameValue(equal.value, candidate.value)) { "Inconsistent sync version" }
            return
        }
        if (winners.any { dominates(it.clock, candidate.clock) }) return
        winners.removeAll { dominates(candidate.clock, it.clock) }
        winners += candidate
    }

    private fun sameValue(a: SyncSnippet?, b: SyncSnippet?): Boolean =
        (a == null && b == null) || a?.sameContent(b) == true

    private fun dominates(a: Map<String, Long>, b: Map<String, Long>): Boolean =
        (a.keys + b.keys).all { (a[it] ?: 0) >= (b[it] ?: 0) } &&
            (a.keys + b.keys).any { (a[it] ?: 0) > (b[it] ?: 0) }
}
