package com.tencent.kuiklybase.markdown.model

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.intellij.markdown.parser.LinkMap

/**
 * Document-scoped link storage. Implementations must be thread-safe and their
 * methods must not block, since publication invokes them while committing state.
 */
interface ReferenceLinkHandler {
    /** Keep the provided link */
    fun store(label: String, destination: String?)

    /** Returns the link for the provided label if it exists */
    fun find(label: String): String

    /** Drops all stored references when a new document session starts. */
    fun clear()
}

/**
 * Implementation for [ReferenceLinkHandler] to resolve referenced link within the Markdown.
 *
 * The label is stored lowercase to allow case insensitive lookups. (https://github.com/adam-p/markdown-here/wiki/markdown-cheatsheet#links)
 */
class ReferenceLinkHandlerImpl : ReferenceLinkHandler {
    private val stored = mutableMapOf<String, String?>()
    private val lock = SynchronizedObject()

    override fun store(label: String, destination: String?) = synchronized(lock) {
        stored[LinkMap.normalizeLabel(label).toString()] = destination
    }

    override fun find(label: String): String = synchronized(lock) {
        stored[LinkMap.normalizeLabel(label).toString()] ?: ""
    }

    override fun clear() = synchronized(lock) {
        stored.clear()
    }
}
