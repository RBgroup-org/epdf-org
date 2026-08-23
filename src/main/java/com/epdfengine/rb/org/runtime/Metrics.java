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

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight, lock-free engine metrics: named counters plus a latency histogram with percentile
 * queries. The histogram uses power-of-two nanosecond buckets ({@code AtomicLongArray[64]}) so
 * recording is a single atomic increment — cheap enough to call on every render at a million
 * requests. A host publishes the snapshot to Micrometer/Prometheus via a {@code TelemetrySink}.
 */
public final class Metrics {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final AtomicLongArray latencyBuckets = new AtomicLongArray(64);   // bucket b = [2^b, 2^(b+1)) ns
    private final LongAdder latencyCount = new LongAdder();
    private final LongAdder latencySumNanos = new LongAdder();

    public void increment(String name)     { add(name, 1L); }
    public void add(String name, long v)   { counters.computeIfAbsent(name, k -> new LongAdder()).add(v); }
    public long count(String name)         { LongAdder a = counters.get(name); return a == null ? 0L : a.sum(); }

    public void recordLatencyNanos(long nanos) {
        if (nanos < 1) nanos = 1;
        int b = 63 - Long.numberOfLeadingZeros(nanos);
        latencyBuckets.incrementAndGet(Math.min(63, Math.max(0, b)));
        latencyCount.increment();
        latencySumNanos.add(nanos);
    }

    public long latencyCount() { return latencyCount.sum(); }

    public double meanMillis() {
        long n = latencyCount.sum();
        return n == 0 ? 0.0 : (latencySumNanos.sum() / (double) n) / 1_000_000.0;
    }

    /** The {@code p}-th percentile latency in milliseconds (bucket lower bound). */
    public double percentileMillis(double p) {
        long total = latencyCount.sum();
        if (total == 0) return 0.0;
        long target = (long) Math.ceil(Math.max(0, Math.min(100, p)) / 100.0 * total);
        long cumulative = 0;
        for (int b = 0; b < 64; b++) {
            cumulative += latencyBuckets.get(b);
            if (cumulative >= target) return (double) (1L << b) / 1_000_000.0;
        }
        return (double) (1L << 63) / 1_000_000.0;
    }

    /** An immutable snapshot of all counters (sorted by name). */
    public Map<String, Long> counters() {
        Map<String, Long> snap = new TreeMap<>();
        counters.forEach((k, v) -> snap.put(k, v.sum()));
        return snap;
    }
}
