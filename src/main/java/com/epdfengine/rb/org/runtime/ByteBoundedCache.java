/*
 * ePDF Engine (epdf-org) — an open-source PDF engine and toolkit.
 * Part of the ePDF Engine project.  Copyright (c) 2026 Rupesh Borse.
 *
 * Licensed under the ePDF Engine License, a permissive open-source license:
 * you may freely use, copy, modify, and distribute this software (including in
 * commercial or closed-source products) provided this notice and the LICENSE
 * file are retained. See LICENSE at the repository root for the full terms.
 *
 * Original clean-room work: no third-party or copyleft (AGPL/GPL) code.
 * Cryptography is intentionally out of scope for epdf-org.
 *
 * Author: Rupesh Borse
 */
package com.epdfengine.rb.org.runtime;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * A thread-safe, <b>byte-bounded</b> LRU cache for expensive-to-produce, reusable artifacts
 * (parsed stylesheets, decoded images, font programs). Sizing is by <i>weight in bytes</i>
 * rather than entry count, so memory stays predictable in a shared, high-throughput service.
 *
 * <p>Storage is split into power-of-two <b>shards</b>, each an access-ordered
 * {@link LinkedHashMap} under its own lock, so concurrent requests rarely contend. A loader
 * passed to {@link #computeIfAbsent} runs <i>outside</i> the shard lock — two threads racing on
 * the same missing key may both compute it (harmless duplicate work) but never block each other
 * behind slow I/O. Counters use {@link LongAdder} to stay cheap under contention.</p>
 *
 * @param <K> key type (must have a stable {@code hashCode}/{@code equals})
 * @param <V> value type; treat returned values as immutable (they are shared)
 */
public final class ByteBoundedCache<K, V> {

    private final Shard<K, V>[] shards;
    private final int mask;
    private final LongAdder hits      = new LongAdder();
    private final LongAdder misses    = new LongAdder();
    private final LongAdder evictions = new LongAdder();

    public ByteBoundedCache(long maxBytes, ToLongFunction<V> weigher) {
        this(maxBytes, weigher, 16);
    }

    @SuppressWarnings("unchecked")
    public ByteBoundedCache(long maxBytes, ToLongFunction<V> weigher, int shardHint) {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be > 0");
        Objects.requireNonNull(weigher, "weigher");
        int n = 1;
        while (n < Math.max(1, shardHint)) n <<= 1;   // round up to a power of two
        this.mask = n - 1;
        long perShard = Math.max(1L, maxBytes / n);
        this.shards = (Shard<K, V>[]) new Shard[n];
        for (int i = 0; i < n; i++) shards[i] = new Shard<>(perShard, weigher, evictions);
    }

    private Shard<K, V> shardFor(K key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return shards[h & mask];
    }

    /** Returns the cached value or {@code null}, recording a hit/miss. */
    public V get(K key) {
        V v = shardFor(key).get(key);
        if (v != null) hits.increment(); else misses.increment();
        return v;
    }

    public void put(K key, V value) {
        if (key == null || value == null) return;
        shardFor(key).put(key, value);
    }

    /**
     * Returns the cached value, or computes it with {@code loader} (outside the lock) and caches
     * it. A {@code null} result is not cached. On a race, {@code loader} may run more than once.
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> loader) {
        Shard<K, V> s = shardFor(key);
        V v = s.get(key);
        if (v != null) { hits.increment(); return v; }
        misses.increment();
        V loaded = loader.apply(key);
        if (loaded == null) return null;
        s.put(key, loaded);
        return loaded;
    }

    public void clear() { for (Shard<K, V> s : shards) s.clear(); }

    public Stats stats() {
        long bytes = 0, entries = 0;
        for (Shard<K, V> s : shards) { bytes += s.bytes(); entries += s.size(); }
        return new Stats(hits.sum(), misses.sum(), evictions.sum(), bytes, entries);
    }

    /** Point-in-time cache counters. */
    public record Stats(long hits, long misses, long evictions, long bytes, long entries) {
        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }
    }

    private static final class Shard<K, V> {
        private final long budget;
        private final ToLongFunction<V> weigher;
        private final LongAdder evictions;
        private final LinkedHashMap<K, Weighted<V>> map = new LinkedHashMap<>(16, 0.75f, true);
        private long bytes;

        Shard(long budget, ToLongFunction<V> weigher, LongAdder evictions) {
            this.budget = budget; this.weigher = weigher; this.evictions = evictions;
        }

        synchronized V get(K key) {
            Weighted<V> w = map.get(key);
            return w == null ? null : w.value;
        }

        synchronized void put(K key, V value) {
            long w = Math.max(0L, weigher.applyAsLong(value));
            Weighted<V> prev = map.put(key, new Weighted<>(value, w));
            bytes += w - (prev == null ? 0L : prev.weight);
            evict();
        }

        private void evict() {
            Iterator<Map.Entry<K, Weighted<V>>> it = map.entrySet().iterator();
            while (bytes > budget && it.hasNext()) {
                Map.Entry<K, Weighted<V>> eldest = it.next();   // access order → LRU first
                bytes -= eldest.getValue().weight;
                it.remove();
                evictions.increment();
            }
        }

        synchronized void clear() { map.clear(); bytes = 0L; }
        synchronized long bytes()  { return bytes; }
        synchronized long size()   { return map.size(); }

        private record Weighted<V>(V value, long weight) {}
    }
}
