package build.conductor.android.client.data

/** Keeps the [maxEntries] values that the app used last, in memory, so a screen that opens again can show them at once. */
class RecentCache<K : Any, V : Any>(private val maxEntries: Int) {
    private val entries = object : LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > maxEntries
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        entries[key] = value
    }

    @Synchronized
    fun clear() = entries.clear()

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
