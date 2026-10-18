package cc.novelia.app.data.documents

import kotlinx.coroutines.CancellationException

/** 可重建的双向索引。目录清单是事实来源；批次内增量更新，调用方在提交边界 flush。 */
internal class DocumentHashIndex(
    initial: Map<String, String> = emptyMap(),
    private val readHash: (String) -> String,
    private val persist: (Map<String, String>) -> Unit,
) {
    private val hashes = initial.toMutableMap()
    private val reverse = mutableMapOf<String, MutableSet<String>>()
    private var dirty = false

    init { hashes.forEach { (id, hash) -> addReverse(id, hash) } }

    /** 每次重新取得目录清单后调用；损坏、缺失的缓存可从轻量文档目录补齐。 */
    fun reconcile(documentIds: List<String>, checkCancelled: () -> Unit = {}) {
        val available = documentIds.toHashSet()
        hashes.keys.filter { it !in available }.forEach(::remove)
        for (id in documentIds) {
            checkCancelled()
            if(id !in hashes) {
                val hash = try { readHash(id) }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { continue }
                record(id, hash)
            }
        }
    }

    /** 只检查相同哈希的候选；多个副本时才读取书架优先顺序。 */
    fun find(hash: String, exists: (String) -> Boolean, preferredIds: () -> Sequence<String> = { emptySequence() }): String? {
        if(hash.isBlank()) return null
        val candidates = reverse[hash]?.toList().orEmpty()
        val valid = candidates.filter { id -> exists(id).also { if(!it) remove(id) } }
        if(valid.size <= 1) return valid.firstOrNull()
        val keys = valid.toHashSet()
        return preferredIds().firstOrNull { it in keys } ?: valid.minOrNull()
    }

    fun record(id: String, hash: String) {
        if (hashes[id] == hash) return
        hashes[id]?.let { removeReverse(id, it) }
        hashes[id] = hash
        addReverse(id, hash)
        dirty = true
    }

    fun remove(id: String) {
        hashes.remove(id)?.let { removeReverse(id, it); dirty = true }
    }

    fun flush() {
        if(!dirty) return
        persist(hashes.toMap())
        dirty = false
    }

    private fun addReverse(id: String, hash: String) {
        if(hash.isNotBlank()) reverse.getOrPut(hash) { linkedSetOf() }.add(id)
    }

    private fun removeReverse(id: String, hash: String) {
        reverse[hash]?.let { ids -> if(ids.remove(id) && ids.isEmpty()) reverse.remove(hash) }
    }
}
