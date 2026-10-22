/*
После первых собеседований мы решили добавить code review в "домашнее задание".
Для тех, кто изначальное задание уже сделал, мы рекомендуем выполнить задание на code review в процессе подготовки к собеседованию
и мы обсудим результат уже на собеседовании. Для тех кто еще в процессе, просьба присылать результат вместе с основным заданием.

Task:

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application.
The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.

Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production.
 Please provide a clear explanation of each issue and its potential impact on production behaviour.
 */


/*
CANDIDATE ANSWER

- "thousands of reads per second, hundreds of writes per second" it is ordinary cache in memory of one service
We shouldn't go to upper system-design level and think about sharding

- main bug: no cache clean from stale entries, so size return wrong value
Naive solution: size() = cache.filterValues {System.currentTimeMillis() -  it.timestamp < ttlMs}

- System.currentTimeMillis() can jump because of time correction better to use System.nanoTime()

- cache has no boundaries. It can lead to OOM. Better to add max_size to constructor and clean logic in put()
Maybe it is a good idea to add background clean job, but I decided to not overcomplicate the code

- K and V are nullable, better to make them <K: Any, V: Any>

- maybe it is better to replace the whole class with usage of Caffeine library :)
 */

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class SimpleCacheOriginal<K, V> {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val ttlMs = 60000 // 1 minute

    data class CacheEntry<V>(val value: V, val timestamp: Long)

    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }

    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            }
        }
        return null
    }

    fun size(): Int {
        return cache.size
    }
}

class SimpleCacheUpdated<K: Any, V: Any>(
    private val maxSize: Int = 10_000
){
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val ttlNanos = TimeUnit.MINUTES.toNanos(1)// 1 minute

    //not data class for more semantic equals() in cache.remove(key, entry)
    private class CacheEntry<V>(val value: V, val timestamp: Long)

    private fun CacheEntry<V>.isExpired(now: Long) = timestamp + ttlNanos <= now

    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.nanoTime())
        if (cache.size > maxSize)
            evict()
    }

    fun get(key: K): V? {
        val entry = cache[key] ?: return null
        if(entry.isExpired(System.nanoTime())){
            cache.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun size(): Int {
        val now = System.nanoTime()
        return cache.values.count { !it.isExpired(now) }
    }

    private val evictLock = ReentrantLock()

    //O(n) but good enough for 10k cache
    private fun evict() {
        if(!evictLock.tryLock()) return
        try {
            val now = System.nanoTime()
            cache.entries.removeIf { it.value.isExpired(now) }
            while (cache.size > maxSize) {
                val oldest = cache.entries.minByOrNull { it.value.timestamp } ?: break
                cache.remove(oldest.key, oldest.value)
            }
        } finally {
            evictLock.unlock()
        }
    }
}