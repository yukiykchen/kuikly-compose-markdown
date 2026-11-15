package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.tencent.kuiklybase.markdown.utils.lookupLinkDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.EmptyStreamingMarkdownFile
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.parser.StreamingMarkdownFile

/**
 * A [MarkdownState] that executes the parsing of the markdown content with the [MarkdownParser] asynchronously.
 *
 * @param content The markdown content to parse.
 * @param lookupLinks Whether to lookup links in the parsed tree or not.
 * @param flavour The [MarkdownFlavourDescriptor] to use for parsing.
 * @param parser The [MarkdownParser] to use for parsing.
 * @param referenceLinkHandler The [ReferenceLinkHandler] to use for storing links.
 * @param immediate Whether to parse the content immediately or not. (WARNING: This is not advices, as it will block the composition!)
 */
@Composable
fun rememberMarkdownState(
    lookupLinks: Boolean = true,
    isShareMode: Boolean = false,
    flavour: MarkdownFlavourDescriptor = remember { GFMFlavourDescriptor() },
    parser: MarkdownParser = remember(flavour) { MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable) },
    referenceLinkHandler: ReferenceLinkHandler = remember { ReferenceLinkHandlerImpl() },
): MarkdownState {
    val state = remember(lookupLinks, isShareMode, flavour, parser, referenceLinkHandler) {
        MarkdownState(
            Input(
                content = "",
                shareModel = isShareMode,
                lookupLinks = lookupLinks,
                flavour = flavour,
                parser = parser,
                referenceLinkHandler = referenceLinkHandler,
            )
        )
    }
    return state
}

/**
 * A [MarkdownState] that that executes the parsing of the markdown content with the [MarkdownParser] asynchronously.
 */
@Stable
class MarkdownState internal constructor(
    private val input: Input,
) {
    val referenceLinkHandler: ReferenceLinkHandler get() = input.referenceLinkHandler
    private val stateFlow: MutableStateFlow<State?> = MutableStateFlow(null)
    val state: StateFlow<State?> = stateFlow.asStateFlow()

    private val linkStateFlow: MutableStateFlow<Map<String, String?>> = MutableStateFlow(emptyMap())
    val links: StateFlow<Map<String, String?>> = linkStateFlow.asStateFlow()

    val nodeListState: SnapshotStateList<ParseResult> = mutableStateListOf()

    /** Read-only view of the current AST blocks, primarily for parity checks. */
    val blocks: List<ASTNode>
        get() = nodeListState.map { it.node }

    private val mutex = Mutex()
    private var streamSession = 0L
    private var streamRevision = 0L
    private var streamingFile: StreamingMarkdownFile? = null
    private var normalizedContent = StringBuilder()

    internal fun getContent(): String = input.content

    fun skipLinkDefinition() = input.lookupLinks

    fun shareMode() = input.shareModel

    suspend fun parse(content: String, immediate: Boolean) {
        val operation = {
            cancelStreaming()
            input.content = content
            parseBlocking()
        }
        if (immediate) mutex.withLock { operation() }
        else withContext(Dispatchers.Default) {
            mutex.withLock { operation() }
        }
    }

    /**
     * Starts a new streaming document. The accumulated text and AST from any
     * previous stream are discarded.
     */
    fun startStream() {
        streamSession++
        resetStream()
        input.content = ""
        streamingFile = EmptyStreamingMarkdownFile(
            input.flavour,
            CancellationToken.NonCancellable,
        )
        nodeListState.clear()
        linkStateFlow.value = emptyMap()
        stateFlow.value = null
    }

    private fun ensureStream() {
        if (streamingFile == null) {
            normalizedContent = StringBuilder()
            pendingCarriageReturn = false
            input.content = ""
            nodeListState.clear()
            linkStateFlow.value = emptyMap()
            streamingFile = EmptyStreamingMarkdownFile(
                input.flavour,
                CancellationToken.NonCancellable,
            )
        }
    }

    /**
     * Appends the next delta of an append-only stream. Chunks from one stream
     * must be submitted in document order; use [startStream] to reset after
     * edits or a shorter document. [delta] must be a complete UTF-8 string;
     * a trailing CR is buffered until its following character is known.
     */
    suspend fun append(delta: String, immediate: Boolean = false) {
        val session = streamSession
        val revision = ++streamRevision
        val appendBlock = {
            if (session == streamSession) {
                ensureStream()
                val file = streamingFile!!
                val normalized = normalizeStreamingChunk(delta, isEnd = false)
                if (normalized.isNotEmpty()) {
                    normalizedContent.append(normalized)
                    file.append(normalized)
                }
                input.content = normalizedContent.toString()
                if (revision == streamRevision) {
                    publishAst(file)
                }
            }
        }
        if (immediate) {
            mutex.withLock {
                appendBlock()
            }
        } else {
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    appendBlock()
                }
            }
        }
    }

    /**
     * Flushes line-ending normalization and performs one full parse so the
     * final AST matches the non-streaming parser.
     */
    suspend fun finishStream(immediate: Boolean = false) {
        val session = streamSession
        ++streamRevision
        val finishBlock = {
            if (session == streamSession) {
                if (streamingFile != null) {
                    val trailing = normalizeStreamingChunk("", isEnd = true)
                    if (trailing.isNotEmpty()) {
                        normalizedContent.append(trailing)
                        streamingFile!!.append(trailing)
                    }
                    input.content = normalizedContent.toString()
                }
                parseBlocking()
                streamingFile = null
                normalizedContent = StringBuilder()
                pendingCarriageReturn = false
            }
        }
        if (immediate) {
            mutex.withLock {
                finishBlock()
            }
        } else {
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    finishBlock()
                }
            }
        }
    }

    private fun cancelStreaming() {
        streamSession++
        resetStream()
    }

    private fun resetStream() {
        streamRevision = 0L
        streamingFile = null
        normalizedContent = StringBuilder()
        pendingCarriageReturn = false
        linkStateFlow.value = emptyMap()
        input.referenceLinkHandler.clear()
    }

    private var pendingCarriageReturn = false

    /**
     * The parser requires LF. A CR at a chunk boundary may be the first half
     * of CRLF, so hold it until the next chunk or [isEnd].
     */
    private fun normalizeStreamingChunk(chunk: String, isEnd: Boolean): String {
        val result = StringBuilder(chunk.length + 1)
        var index = 0
        if (pendingCarriageReturn) {
            pendingCarriageReturn = false
            if (chunk.isEmpty()) {
                result.append('\n')
            } else if (chunk[0] == '\n') {
                result.append('\n')
                index = 1
            } else {
                result.append('\n')
            }
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
                    pendingCarriageReturn = true
                }
            } else {
                result.append(char)
            }
            index++
        }
        return result.toString()
    }

    private fun publishAst(file: StreamingMarkdownFile) {
        publishBlocks(
            file.stableChildren + file.unstableTail,
            stableCount = file.stableChildren.size,
            updateLinks = true,
        )
    }


    /**
     * Parses the markdown content asynchronously using the Default dispatcher.
     * When a result is available it will be emitted to the [state] flow.
     */
    suspend fun parse() = withContext(Dispatchers.Default) {
        mutex.withLock {
            parseBlocking()
        }
    }

    /**
     * Parses the markdown content synchronously.
     */
    private fun parseBlocking() {
        try {
            val parsedResult: ASTNode = input.parser.buildMarkdownTreeFromString(
                input.content as CharSequence,
            )
            publishBlocks(parsedResult.children, stableCount = 0, updateLinks = true)
        } catch (error: Throwable) {
            stateFlow.value = State.Error(error, input.referenceLinkHandler)
        }
    }

    private fun publishBlocks(
        blocks: List<ASTNode>,
        stableCount: Int,
        updateLinks: Boolean,
    ) {
        if (updateLinks && input.lookupLinks) {
            val links = mutableMapOf<String, String?>()
            val root = object : ASTNode {
                override val type = org.intellij.markdown.MarkdownElementTypes.MARKDOWN_FILE
                override val startOffset = 0
                override val endOffset = input.content.length
                override val parent: ASTNode? = null
                override val children: List<ASTNode> = blocks
            }
            lookupLinkDefinition(links, root, input.content, recursive = true)
            input.referenceLinkHandler.clear()
            links.onEach { (key, value) -> input.referenceLinkHandler.store(key, value) }
            linkStateFlow.value = links
        }

        blocks.forEachIndexed { index, node ->
            val current = nodeListState.getOrNull(index)
            if (current != null && index < stableCount && current.node === node) {
                // Stable AST nodes are append-only and never need to be replaced.
            } else {
                val result = ParseResult(node.startOffset, node.endOffset).apply { this.node = node }
                if (index < nodeListState.size) {
                    nodeListState.set(index, result)
                } else {
                    nodeListState.add(result)
                }
            }
        }
        if (blocks.size < nodeListState.size) {
            nodeListState.removeRange(blocks.size, nodeListState.size)
        }
    }
}

/**
 * The input for the [MarkdownState].
 *
 * @param content The markdown content to parse.
 * @param lookupLinks Whether to lookup links in the parsed tree or not.
 * @param flavour The [MarkdownFlavourDescriptor] to use for parsing.
 * @param parser The [MarkdownParser] to use for parsing.
 * @param referenceLinkHandler The [ReferenceLinkHandler] to use for storing links.
 */
data class Input(
    var content: String,
    val lookupLinks: Boolean,
    val shareModel: Boolean,
    val flavour: MarkdownFlavourDescriptor,
    val parser: MarkdownParser,
    val referenceLinkHandler: ReferenceLinkHandler,
)

/**
 * The current state of the [MarkdownState].
 */
@Stable
sealed interface State {

    /** The [ReferenceLinkHandler] to store links in. */
    val referenceLinkHandler: ReferenceLinkHandler

    /** The parsing is in-progress. */
//    data class Loading(
//        override val referenceLinkHandler: ReferenceLinkHandler,
//    ) : State

    /** The parsing was successful. */
    data class Success(
        val node: SnapshotStateList<ParseResult>,
        val content: String,
        val linksLookedUp: Boolean,
        val cost: Long,
        override val referenceLinkHandler: ReferenceLinkHandler,
    ) : State

    /** The parsing failed due to [Throwable]. */
    data class Error(
        val result: Throwable,
        override val referenceLinkHandler: ReferenceLinkHandler,
    ) : State
}

@Stable
data class ParseResult(
    val startIndex: Int,
    val endIndex: Int
) {
    private val nodeState = mutableStateOf<ASTNode?>(null)

    var node: ASTNode
        get() = nodeState.value ?: error("ParseResult.node has not been initialized")
        set(value) {
            nodeState.value = value
        }
}
