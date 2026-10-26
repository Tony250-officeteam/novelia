package cc.novelia.app.data.cache

import cc.novelia.app.data.network.SharedRequest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 有界页面快照，不持有 Activity、Compose 状态或页面作用域。过期内容可先显示再刷新。 */
internal class UiResultCache<T>(
    private val capacity: Int = 24,
    val lifetimeMillis: Long = 2 * 60_000L,
    private val maxWeight: Long = capacity.toLong(),
    private val weigh: (T) -> Long = { 1L },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Snapshot<T>(val value: T, val fetchedAt: Long, val fresh: Boolean)
    data class Position(val index: Int = 0, val offset: Int = 0)
    private data class Entry<T>(val value: T, val fetchedAt: Long, val weight: Long, val valid: Boolean = true)
    private data class RequestKey(val key: Any, val revision: Long)
    private val entries = LinkedHashMap<Any, Entry<T>>(16, .75f, true)
    private val positions = LinkedHashMap<Pair<Any, String>, Position>(16, .75f, true)
    private val requests = SharedRequest<RequestKey, T>()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    init { require(capacity > 0 && lifetimeMillis > 0 && maxWeight > 0) }

    @Synchronized fun snapshot(key: Any): Snapshot<T>? = entries[key]?.let {
        Snapshot(it.value, it.fetchedAt, it.valid && clock() - it.fetchedAt in 0 until lifetimeMillis)
    }

    /** 只有原代次仍有效才可发布，清缓存或写操作前启动的请求不能重新种入旧结果。 */
    @Synchronized fun put(key: Any, value: T, expectedRevision: Long = revision.value, fetchedAt: Long = clock()) {
        if(expectedRevision != revision.value) return
        entries.remove(key)
        val weight = weigh(value).coerceAtLeast(1)
        if(weight > maxWeight) return
        entries[key] = Entry(value, fetchedAt, weight)
        var total = entries.values.sumOf { it.weight }
        while(entries.size > capacity || total > maxWeight) {
            val oldest = entries.keys.first()
            total -= entries.remove(oldest)!!.weight
            positions.keys.removeAll { it.first == oldest }
        }
    }

    suspend fun load(key: Any, forceNetwork: Boolean = false, loader: suspend () -> T): T {
        if(!forceNetwork) snapshot(key)?.takeIf { it.fresh }?.let { return it.value }
        val version = revision.value
        return requests.await(RequestKey(key, version)) {
            val fetchedAt = clock()
            val result = loader()
            currentCoroutineContext().ensureActive()
            put(key, result, version, fetchedAt)
            result
        }
    }

    /** 保留页面当前展示的内容，由订阅者在原位置刷新；后续进入也不能再把旧值当新值。 */
    @Synchronized fun invalidate(matches: (Any) -> Boolean = { true }) {
        entries.replaceAll { key, entry -> if(matches(key)) entry.copy(valid = false) else entry }
        mutableRevision.value++
    }

    @Synchronized fun discard(key: Any) { entries.remove(key) }
    @Synchronized fun clear() { entries.clear(); positions.clear(); mutableRevision.value++ }
    @Synchronized fun trimGroup(limit: Int, matches: (Any) -> Boolean) {
        val keys = entries.keys.filter(matches)
        keys.take((keys.size - limit).coerceAtLeast(0)).forEach { entries.remove(it) }
    }
    @Synchronized fun position(key: Any, slot: String): Position = positions[key to slot] ?: Position()
    @Synchronized fun savePosition(key: Any, slot: String, value: Position) {
        positions[key to slot] = value
        while(positions.size > capacity * 3) positions.remove(positions.keys.first())
    }
}
