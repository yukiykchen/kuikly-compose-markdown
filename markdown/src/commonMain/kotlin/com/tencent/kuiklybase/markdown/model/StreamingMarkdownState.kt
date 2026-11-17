package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.tencent.kuiklybase.markdown.utils.lookupLinkDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import com.tencent.kuiklybase.markdown.compat.createStreamingFile
import com.tencent.kuiklybase.markdown.compat.parseMarkdown

/** Explicit work budgets for untrusted streaming input; violations never truncate text silently. */
@Immutable
data class StreamingMarkdownLimits(
    val maxDocumentCharacters: Int = 1_000_000,
    val maxUnstableCharacters: Int = 262_144,
) {
    init {
        require(maxDocumentCharacters > 0)
        require(maxUnstableCharacters > 0)
    }
}

/**
 * Append-only streaming state. On parser 0.7.18 each chunk is parsed by the
 * official append-only AST and only the unstable tail is reparsed. Older OHOS
 * parser forks use a full-parse compatibility factory.
 */
interface StreamingMarkdownState {
    val content: CharSequence
    val snapshot: StateFlow<Snapshot>
    val stableBlocks: StateFlow<List<MarkdownRenderBlock>>
    val stableBlockSlots: StateFlow<List<StableBlockSlot>>
    val stableRenderSegments: StateFlow<List<MarkdownRenderSegment>>
    val unstableBlocks: StateFlow<List<MarkdownRenderBlock>>
    val hasRenderableBlocks: StateFlow<Boolean>
    val diagnostics: StateFlow<Diagnostics>
    val linksRevision: StateFlow<Int>
    /** Per-block generation used to invalidate only stable segments affected by new links. */
    val blockLinkGenerations: StateFlow<Map<Long, Int>>
    /** Stable per-block generation flow; only the affected block observes a value change. */
    fun blockLinkGeneration(blockId: Long): StateFlow<Int>
    /** Active tail generation; tail blocks are small, so link changes can refresh the whole tail. */
    val tailLinkGeneration: StateFlow<Int>
    val links: StateFlow<Map<String, String?>>
    val referenceLinkHandler: ReferenceLinkHandler
    val renderCache: MarkdownRenderCache
    val recompositionCounters: RecompositionCounters
    /** Whether parsed deltas are currently committed to the UI state. */
    val uiUpdatesEnabled: StateFlow<Boolean>
    /** Parser or upstream collection failure; reset clears it. */
    val error: StateFlow<Throwable?>
    /** Number of source characters in the current session, without copying the document. */
    val contentLength: Int get() = content.length
    /** Snapshot-observable UI commit revision; host uses it for content-size bookkeeping. */
    val contentRevision: StateFlow<Int>
    /** Current reparsed source size, for adaptive network buffering. */
    val activeTailLength: Int get() = unstableBlocks.value.sumOf { it.source.length }

    suspend fun append(chunk: String): Snapshot
    /** Flushes buffered line endings and publishes the final snapshot. */
    suspend fun finish(): Snapshot
    fun reset()
    /**
     * Temporarily holds UI commits while still appending and parsing source text.
     * Useful when the host is scrolling or the user has scrolled away from the live answer.
     */
    fun pauseUiUpdates()
    /** Commits the latest parsed state and resumes per-append UI commits. */
    suspend fun resumeUiUpdates(): Snapshot

    @Immutable
    data class Snapshot(
        val stableAst: List<ASTNode>,
        val unstableAstTail: List<ASTNode>,
        val finalized: Boolean = false,
        val renderBlocks: List<MarkdownRenderBlock> = emptyList(),
        val referenceDefinitions: Map<String, String?> = emptyMap(),
    ) {
        val blocks: List<ASTNode>
            get() = stableAst + unstableAstTail
    }

    @Immutable
    data class Diagnostics(
        val appendCount: Int = 0,
        val stableBlockCount: Int = 0,
        val unstableBlockCount: Int = 0,
        val reusedStableBlocks: Int = 0,
        val newStableBlocks: Int = 0,
        val finalParseMatches: Boolean = true,
    )
}

@Composable
fun rememberStreamingMarkdownState(
    lookupLinks: Boolean = true,
    flavour: MarkdownFlavourDescriptor = remember { GFMFlavourDescriptor() },
    referenceLinkHandler: ReferenceLinkHandler = remember { ReferenceLinkHandlerImpl() },
    diagnosticsEnabled: Boolean = false,
    limits: StreamingMarkdownLimits = remember { StreamingMarkdownLimits() },
): StreamingMarkdownState = remember(lookupLinks, flavour, referenceLinkHandler, diagnosticsEnabled, limits) {
    StreamingMarkdownStateImpl(
        lookupLinks = lookupLinks,
        flavour = flavour,
        referenceLinkHandler = referenceLinkHandler,
        diagnosticsEnabled = diagnosticsEnabled,
        limits = limits,
    )
}

/** Collects append-only chunk deltas into a remembered streaming state. */
@Composable
fun Flow<String>.collectAsStreamingMarkdownState(
    lookupLinks: Boolean = true,
    flavour: MarkdownFlavourDescriptor = remember { GFMFlavourDescriptor() },
    referenceLinkHandler: ReferenceLinkHandler = remember { ReferenceLinkHandlerImpl() },
    diagnosticsEnabled: Boolean = false,
    limits: StreamingMarkdownLimits = remember { StreamingMarkdownLimits() },
): StreamingMarkdownState {
    val markdownState = rememberStreamingMarkdownState(
        lookupLinks,
        flavour,
        referenceLinkHandler,
        diagnosticsEnabled,
        limits,
    )
    LaunchedEffect(this, markdownState) {
        (markdownState as StreamingMarkdownStateImpl).collectStream(this@collectAsStreamingMarkdownState)
    }
    return markdownState
}

internal class StreamingMarkdownStateImpl(
    private val lookupLinks: Boolean,
    private val flavour: MarkdownFlavourDescriptor,
    override val referenceLinkHandler: ReferenceLinkHandler,
    private val diagnosticsEnabled: Boolean = false,
    private val limits: StreamingMarkdownLimits = StreamingMarkdownLimits(),
) : StreamingMarkdownState {
    private val publicationLock = SynchronizedObject()
    private val sessionFlow = MutableStateFlow(Session())
    override val content: CharSequence get() {
        val session = sessionFlow.value
        return synchronized(session.sourceLock) { session.source.toString() }
    }

    private inner class Session {
        val sourceLock = SynchronizedObject()
        val source = StringBuilder()
        val mutex = Mutex()
        var job: Job? = null
        var failed = false
        var finalized = false
        var pendingCarriageReturn = false
        var appendCount = 0
        var processedStableChildren = 0
        var pendingPublication: PendingPublication? = null
        val stableLinks = mutableMapOf<String, String?>()
        var stableRenderBlocks: List<MarkdownRenderBlock> = emptyList()
        var stableRenderSegments: List<MarkdownRenderSegment> = emptyList()
        var segmentedBlockCount = 0
        var stableAst: List<ASTNode> = emptyList()
        val slots = linkedMapOf<Long, StableBlockSlot>()
        val token = CancellationToken {
            job?.ensureActive()
            if (sessionFlow.value !== this) throw CancellationException("Markdown session was reset")
        }
        val file = createStreamingFile(flavour, token)
    }

    override val contentLength: Int get() {
        val session = sessionFlow.value
        return synchronized(session.sourceLock) { session.source.length }
    }

    private val snapshotFlow = MutableStateFlow(StreamingMarkdownState.Snapshot(emptyList(), emptyList()))
    override val snapshot: StateFlow<StreamingMarkdownState.Snapshot> = snapshotFlow.asStateFlow()

    private val contentRevisionFlow = MutableStateFlow(0)
    override val contentRevision: StateFlow<Int> = contentRevisionFlow.asStateFlow()

    private val stableBlockFlow = MutableStateFlow(emptyList<MarkdownRenderBlock>())
    override val stableBlocks: StateFlow<List<MarkdownRenderBlock>> = stableBlockFlow.asStateFlow()

    private val stableBlockSlotFlow = MutableStateFlow(emptyList<StableBlockSlot>())
    override val stableBlockSlots: StateFlow<List<StableBlockSlot>> = stableBlockSlotFlow.asStateFlow()

    private val stableRenderSegmentFlow = MutableStateFlow(emptyList<MarkdownRenderSegment>())
    override val stableRenderSegments: StateFlow<List<MarkdownRenderSegment>> =
        stableRenderSegmentFlow.asStateFlow()

    private val unstableBlockFlow = MutableStateFlow(emptyList<MarkdownRenderBlock>())
    override val unstableBlocks: StateFlow<List<MarkdownRenderBlock>> = unstableBlockFlow.asStateFlow()

    private val hasRenderableBlocksFlow = MutableStateFlow(false)
    override val hasRenderableBlocks: StateFlow<Boolean> = hasRenderableBlocksFlow.asStateFlow()

    private val diagnosticsFlow = MutableStateFlow(StreamingMarkdownState.Diagnostics())
    override val diagnostics: StateFlow<StreamingMarkdownState.Diagnostics> = diagnosticsFlow.asStateFlow()

    private val linksRevisionFlow = MutableStateFlow(0)
    override val linksRevision: StateFlow<Int> = linksRevisionFlow.asStateFlow()

    private val blockLinkGenerationFlow = MutableStateFlow<Map<Long, Int>>(emptyMap())
    override val blockLinkGenerations: StateFlow<Map<Long, Int>> = blockLinkGenerationFlow.asStateFlow()

    private val blockGenerationFlows = linkedMapOf<Long, MutableStateFlow<Int>>()
    private val blockGenerationFlowLock = SynchronizedObject()

    private val tailLinkGenerationFlow = MutableStateFlow(0)
    override val tailLinkGeneration: StateFlow<Int> = tailLinkGenerationFlow.asStateFlow()

    private val linkFlow = MutableStateFlow<Map<String, String?>>(emptyMap())
    override val links: StateFlow<Map<String, String?>> = linkFlow.asStateFlow()
    private val errorFlow = MutableStateFlow<Throwable?>(null)
    override val error: StateFlow<Throwable?> = errorFlow.asStateFlow()
    private val uiUpdatesEnabledFlow = MutableStateFlow(true)
    override val uiUpdatesEnabled: StateFlow<Boolean> = uiUpdatesEnabledFlow.asStateFlow()

    private class PendingPublication(
        val stable: List<ASTNode>,
        val tail: List<ASTNode>,
        val links: Map<String, String?>,
        val nextStable: List<MarkdownRenderBlock>,
        val nextTail: List<MarkdownRenderBlock>,
        val diagnostics: StreamingMarkdownState.Diagnostics?,
    ) {
        fun snapshot(finalChildren: List<ASTNode>? = null) = StreamingMarkdownState.Snapshot(
            stableAst = stable,
            unstableAstTail = if (finalChildren == null) tail else emptyList(),
            finalized = finalChildren != null,
            renderBlocks = MarkdownBlockList(nextStable, if (finalChildren == null) nextTail else emptyList()),
            referenceDefinitions = links,
        )
    }

    override val renderCache = MarkdownRenderCache()
    override val recompositionCounters = RecompositionCounters()
    override suspend fun append(chunk: String): StreamingMarkdownState.Snapshot =
        appendToSession(sessionFlow.value, chunk)

    private suspend fun appendToSession(session: Session, chunk: String): StreamingMarkdownState.Snapshot = mutate(session) {
        check(!session.finalized) { "Call reset() before appending to a finished Markdown stream" }
        require(chunk.length.toLong() <= 2L * limits.maxDocumentCharacters) {
            "Markdown chunk exceeds the input normalization budget"
        }
        val normalized = normalize(session, chunk, isEnd = false)
        if (normalized.isNotEmpty()) {
            require(normalized.length <= limits.maxDocumentCharacters - session.source.length) {
                "Streaming Markdown document exceeds ${limits.maxDocumentCharacters} characters"
            }
            val tailStart = session.file.unstableTail.firstOrNull()?.startOffset ?: session.file.endOffset
            val pendingSize = session.file.endOffset - tailStart
            require(normalized.length <= limits.maxUnstableCharacters - pendingSize) {
                "Unstable Markdown tail exceeds ${limits.maxUnstableCharacters} characters"
            }
            synchronized(session.sourceLock) { session.source.append(normalized) }
            session.file.append(normalized)
        }
        session.appendCount++
        publishSnapshot(session)
    }

    override suspend fun finish(): StreamingMarkdownState.Snapshot = finishSession(sessionFlow.value)

    private suspend fun finishSession(session: Session): StreamingMarkdownState.Snapshot = mutate(session) {
        if (session.finalized) {
            return@mutate synchronized(publicationLock) {
                uiUpdatesEnabledFlow.value = true
                snapshotFlow.value
            }
        }
        val trailing = normalize(session, "", isEnd = true)
        if (trailing.isNotEmpty()) {
            require(trailing.length <= limits.maxDocumentCharacters - session.source.length) {
                "Streaming Markdown document exceeds ${limits.maxDocumentCharacters} characters"
            }
            val tailStart = session.file.unstableTail.firstOrNull()?.startOffset ?: session.file.endOffset
            require(trailing.length <= limits.maxUnstableCharacters - (session.file.endOffset - tailStart)) {
                "Unstable Markdown tail exceeds ${limits.maxUnstableCharacters} characters"
            }
            synchronized(session.sourceLock) { session.source.append(trailing) }
            session.file.append(trailing)
        }
        val document = synchronized(session.sourceLock) { session.source.toString() }
        val tree = parseMarkdown(flavour, session.token, document)
        session.finalized = true
        publishSnapshot(session, tree.children, forceCommit = true)
    }

    private suspend fun mutate(
        session: Session,
        operation: (Session) -> StreamingMarkdownState.Snapshot,
    ): StreamingMarkdownState.Snapshot {
        // Capture before dispatch: queued work belongs to its original session.
        return withContext(Dispatchers.Default) {
            session.mutex.withLock {
                currentCoroutineContext().ensureActive()
                if (sessionFlow.value !== session) throw CancellationException("Markdown session was reset")
                check(!session.failed) { "Call reset() after a cancelled or failed Markdown parse" }
                session.job = currentCoroutineContext()[Job]
                try {
                    operation(session)
                } catch (error: Throwable) {
                    session.failed = true
                    if (error !is CancellationException) recordError(session, error)
                    throw error
                } finally {
                    session.job = null
                }
            }
        }
    }

    override fun reset(): Unit = synchronized(publicationLock) {
        sessionFlow.value = Session()
        snapshotFlow.value = StreamingMarkdownState.Snapshot(emptyList(), emptyList())
        contentRevisionFlow.value++
        stableBlockFlow.value = emptyList()
        stableBlockSlotFlow.value = emptyList()
        stableRenderSegmentFlow.value = emptyList()
        unstableBlockFlow.value = emptyList()
        hasRenderableBlocksFlow.value = false
        diagnosticsFlow.value = StreamingMarkdownState.Diagnostics()
        linksRevisionFlow.value++
        blockLinkGenerationFlow.value = emptyMap()
        synchronized(blockGenerationFlowLock) {
            blockGenerationFlows.values.forEach { it.value = 0 }
        }
        tailLinkGenerationFlow.value = 0
        linkFlow.value = emptyMap()
        errorFlow.value = null
        uiUpdatesEnabledFlow.value = true
        referenceLinkHandler.clear()
        renderCache.clear()
    }

    internal suspend fun collectStream(chunks: Flow<String>) {
        reset()
        val session = sessionFlow.value
        try {
            chunks.bufferMarkdownChunks().collect { appendToSession(session, it) }
            finishSession(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (sessionFlow.value === session && !session.failed) {
                try {
                    finishSession(session)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The original upstream failure remains the primary diagnostic.
                }
            }
            recordError(session, failure)
        }
    }

    private fun recordError(session: Session, failure: Throwable) = synchronized(publicationLock) {
        if (sessionFlow.value === session) errorFlow.value = failure
    }

    override fun pauseUiUpdates() {
        synchronized(publicationLock) {
            uiUpdatesEnabledFlow.value = false
        }
    }

    override suspend fun resumeUiUpdates(): StreamingMarkdownState.Snapshot {
        val session = sessionFlow.value
        return mutate(session) {
            synchronized(publicationLock) {
                val pending = session.pendingPublication
                if (pending != null) {
                    commitPublication(session, pending, finalChildren = null)
                } else {
                    uiUpdatesEnabledFlow.value = true
                    snapshotFlow.value
                }
            }
        }
    }

    override fun blockLinkGeneration(blockId: Long): StateFlow<Int> = synchronized(blockGenerationFlowLock) {
        blockGenerationFlows.getOrPut(blockId) {
            MutableStateFlow(blockLinkGenerationFlow.value[blockId] ?: 0)
        }
    }

    private fun publishSnapshot(
        session: Session,
        finalChildren: List<ASTNode>? = null,
        forceCommit: Boolean = false,
    ): StreamingMarkdownState.Snapshot {
        val stable = finalChildren ?: if (session.file.stableChildren.size != session.stableAst.size) {
            session.file.stableChildren.toList().also { session.stableAst = it }
        } else session.stableAst
        val tail = if (finalChildren != null) emptyList() else session.file.unstableTail.toList()
        val links = mutableMapOf<String, String?>()
        if (lookupLinks) {
            if (finalChildren != null) {
                stable.forEach { lookupLinkDefinition(links, it, session.source, onlyDefinitions = true) }
            } else {
                while (session.processedStableChildren < stable.size) {
                    lookupLinkDefinition(session.stableLinks, stable[session.processedStableChildren++], session.source, onlyDefinitions = true)
                }
                links.putAll(session.stableLinks)
                tail.forEach { lookupLinkDefinition(links, it, session.source, onlyDefinitions = true) }
            }
        }
        val previousCount = session.stableRenderBlocks.size
        val previousBlocks = session.stableRenderBlocks
        val nextStable = if (finalChildren != null) {
            val old = session.stableRenderBlocks.associateBy { it.id }
            stable.map { node ->
                val candidate = node.asRenderBlock(session.source)
                val previous = old[candidate.id]
                if (previous != null && previous.source == candidate.source &&
                    previous.node.hasSameTree(candidate.node)) previous else candidate
            }
        } else if (stable.size > previousCount) {
            previousBlocks + stable.drop(previousCount).map { it.asRenderBlock(session.source) }
        } else {
            previousBlocks
        }
        val nextTail = tail.map { it.asRenderBlock(session.source) }
        session.token.checkCancelled()
        val reused = if (diagnosticsEnabled) {
            nextStable.count { candidate -> session.slots[candidate.id]?.value === candidate }
        } else 0
        val diagnostics = if (diagnosticsEnabled) {
            val preview = session.file.children
            StreamingMarkdownState.Diagnostics(
                appendCount = session.appendCount,
                stableBlockCount = nextStable.size,
                unstableBlockCount = nextTail.size,
                reusedStableBlocks = reused,
                newStableBlocks = (nextStable.size - previousCount).coerceAtLeast(0),
                finalParseMatches = finalChildren == null ||
                    (preview.size == stable.size && preview.indices.all { preview[it].hasSameTree(stable[it]) }),
            )
        } else null
        val pending = PendingPublication(
            stable = stable,
            tail = tail,
            links = links.toMap(),
            nextStable = nextStable,
            nextTail = nextTail,
            diagnostics = diagnostics,
        )
        return synchronized(publicationLock) {
            session.token.checkCancelled()
            if (uiUpdatesEnabledFlow.value || forceCommit) {
                commitPublication(session, pending, finalChildren)
            } else {
                session.pendingPublication = pending
                pending.snapshot(finalChildren)
            }
        }
    }

    /** Commits one coalesced publication atomically. Must hold [publicationLock]. */
    private fun commitPublication(
        session: Session,
        pending: PendingPublication,
        finalChildren: List<ASTNode>?,
    ): StreamingMarkdownState.Snapshot {
        val previousCount = session.stableRenderBlocks.size
        if (linkFlow.value != pending.links) {
            val oldLinks = linkFlow.value
            referenceLinkHandler.clear()
            pending.links.forEach { (key, value) -> referenceLinkHandler.store(key, value) }
            linkFlow.value = pending.links
            linksRevisionFlow.value++
            tailLinkGenerationFlow.value++
            val changedLabels = (oldLinks.keys + pending.links.keys)
                .filter { oldLinks[it] != pending.links[it] }
                .filter { it.isNotEmpty() }
                .flatMap { label ->
                    val normalized = org.intellij.markdown.parser.LinkMap.normalizeLabel(label).toString()
                    listOf(label, label.trim('[', ']'), normalized, normalized.trim('[', ']'))
                }
                .filter { it.isNotEmpty() }
                .toSet()
            if (changedLabels.isNotEmpty()) {
                val generations = blockLinkGenerationFlow.value.toMutableMap()
                val updated = linkedMapOf<Long, Int>()
                (pending.nextStable + pending.nextTail).forEach { block ->
                    if (block.usesAnyLinkLabel(changedLabels)) {
                        val generation = (generations[block.id] ?: 0) + 1
                        generations[block.id] = generation
                        updated[block.id] = generation
                    }
                }
                blockLinkGenerationFlow.value = generations
                synchronized(blockGenerationFlowLock) {
                    updated.forEach { (blockId, generation) ->
                        blockGenerationFlows.getOrPut(blockId) { MutableStateFlow(0) }.value = generation
                        while (blockGenerationFlows.size > MAX_BLOCK_GENERATION_FLOWS) {
                            blockGenerationFlows.remove(blockGenerationFlows.keys.first())
                        }
                    }
                }
            }
        }
        val stableStructureChanged = pending.nextStable.size != previousCount ||
            pending.nextStable !== session.stableRenderBlocks ||
            finalChildren != null ||
            session.pendingPublication != null
        if (stableStructureChanged) {
            pending.nextStable.forEach { block ->
                val slot = session.slots.getOrPut(block.id) { StableBlockSlot.create(block) }
                if (slot.value !== block) slot.replace(block)
            }
            session.stableRenderBlocks = pending.nextStable
            val ids = pending.nextStable.mapTo(mutableSetOf()) { it.id }
            session.slots.keys.retainAll(ids)
            stableBlockFlow.value = pending.nextStable
            stableBlockSlotFlow.value = pending.nextStable.map { session.slots.getValue(it.id) }
            val byId = pending.nextStable.associateBy { it.id }
            val retainedSegments = session.stableRenderSegments.map { segment ->
                var changed = false
                val blocks = segment.blocks.map { block ->
                    val current = byId[block.id]
                    if (current != null && current !== block) {
                        changed = true
                        current
                    } else {
                        block
                    }
                }
                if (changed) segment.copy(blocks = blocks) else segment
            }
            val appendedSegments = pending.nextStable
                .drop(session.segmentedBlockCount)
                .asRenderSegments()
            val segments = retainedSegments + appendedSegments
            session.stableRenderSegments = segments
            session.segmentedBlockCount = pending.nextStable.size
            if (segments !== stableRenderSegmentFlow.value) {
                stableRenderSegmentFlow.value = segments
            }
        }
        unstableBlockFlow.value = if (finalChildren == null) pending.nextTail else emptyList()
        hasRenderableBlocksFlow.value = pending.nextStable.isNotEmpty() ||
            (finalChildren == null && pending.nextTail.isNotEmpty())
        pending.diagnostics?.let { diagnosticsFlow.value = it }
        val next = pending.snapshot(finalChildren)
        snapshotFlow.value = next
        contentRevisionFlow.value++
        session.pendingPublication = null
        uiUpdatesEnabledFlow.value = true
        return next
    }

    // The parser requires LF; hold a trailing CR until the next chunk decides
    // whether it is a CRLF pair or a bare CR.
    private fun normalize(session: Session, chunk: String, isEnd: Boolean): String {
        val result = StringBuilder(chunk.length + 1)
        var index = 0
        if (session.pendingCarriageReturn && (chunk.isNotEmpty() || isEnd)) {
            session.pendingCarriageReturn = false
            result.append('\n')
            if (chunk.firstOrNull() == '\n') index = 1
        }
        while (index < chunk.length) {
            val char = chunk[index]
            if (char == '\r') {
                if (index + 1 < chunk.length) {
                    result.append('\n')
                    if (chunk[index + 1] == '\n') index++
                } else if (isEnd) {
                    result.append('\n')
                } else {
                    session.pendingCarriageReturn = true
                }
            } else {
                result.append(char)
            }
            index++
        }
        return result.toString()
    }
}

/** Best-effort bracket-label scan; over-invalidating one bounded segment is safe. */
private fun MarkdownRenderBlock.usesAnyLinkLabel(labels: Set<String>): Boolean {
    var cursor = 0
    while (cursor < source.length) {
        val start = source.indexOf('[', cursor)
        if (start < 0) return false
        val end = source.indexOf(']', start + 1)
        if (end < 0) return false
        val raw = source.substring(start, end + 1)
        val inner = source.substring(start + 1, end)
        val normalizedRaw = org.intellij.markdown.parser.LinkMap.normalizeLabel(raw).toString()
        val normalizedInner = org.intellij.markdown.parser.LinkMap.normalizeLabel(inner).toString()
        if (labels.any {
                it.equals(raw, ignoreCase = true) ||
                    it.equals(inner, ignoreCase = true) ||
                    it.equals(normalizedRaw, ignoreCase = true) ||
                    it.equals(normalizedInner, ignoreCase = true)
            }
        ) return true
        cursor = end + 1
    }
    return false
}

private const val MAX_BLOCK_GENERATION_FLOWS = 1024
