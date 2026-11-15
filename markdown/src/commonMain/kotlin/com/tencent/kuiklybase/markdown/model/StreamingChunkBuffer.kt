package com.tencent.kuiklybase.markdown.model

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** Coalesces append-only deltas without conflating or dropping any source text. */
fun Flow<String>.bufferMarkdownChunks(
    windowMillis: Long = 50,
    maxBatchChars: Int = 4096,
): Flow<String> {
    require(windowMillis > 0)
    require(maxBatchChars > 0)
    val upstream = this
    return flow {
        coroutineScope {
            val chunks = Channel<String>(Channel.RENDEZVOUS)
            val producer = launch {
                try {
                    upstream.collect { if (it.isNotEmpty()) chunks.send(it) }
                    chunks.close()
                } catch (error: Throwable) {
                    chunks.close(error)
                }
            }
            try {
                var completed = false
                while (!completed) {
                    val first = chunks.receiveCatching()
                    first.exceptionOrNull()?.let { throw it }
                    val initial = first.getOrNull() ?: break
                    val pending = StringBuilder(initial)
                    var upstreamFailure: Throwable? = null
                    val started = TimeSource.Monotonic.markNow()
                    while (pending.length < maxBatchChars) {
                        val remaining = windowMillis - started.elapsedNow().inWholeMilliseconds
                        if (remaining <= 0) break
                        val next = withTimeoutOrNull(remaining) { chunks.receiveCatching() } ?: break
                        upstreamFailure = next.exceptionOrNull()
                        if (upstreamFailure != null) break
                        val chunk = next.getOrNull()
                        if (chunk == null) {
                            completed = true
                            break
                        }
                        pending.append(chunk)
                    }
                    emit(pending.toString())
                    upstreamFailure?.let { throw it }
                }
            } finally {
                producer.cancel()
                chunks.cancel()
            }
        }
    }
}
