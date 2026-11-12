package io.github.asancart.android.core.queue

import kotlinx.coroutines.flow.Flow

/**
 * Persistent outgoing queue seam, one queue per API target (REQ-APP-013). Implemented in T4.4:
 * stored on disk, retried with backoff, flushed when the network returns. An item leaves the queue
 * only on ACCEPTED, DUPLICATE or REJECTED (REQ-DEV-011).
 */
interface OutgoingQueue {
    suspend fun enqueue(targetId: String, report: TransferReport)

    /** REQ-APP-014: pending count and last error per target, for Home and Activity. */
    fun state(targetId: String): Flow<QueueState>
}

data class QueueState(val pending: Int, val lastError: String?)
