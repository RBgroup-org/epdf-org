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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;

/**
 * A point-in-time snapshot of JVM heap, GC and thread state (via {@code java.lang.management},
 * part of the JDK — no dependency). A host can poll {@link #heapUsedRatio()} to drive
 * <b>memory-aware load shedding</b>: start rejecting new jobs before the heap is exhausted,
 * rather than risking an {@link OutOfMemoryError} under a million-request burst.
 */
public record RuntimeStats(
        long heapUsed, long heapCommitted, long heapMax,
        long nonHeapUsed,
        long gcCount, long gcTimeMillis,
        int liveThreads, int daemonThreads,
        int availableProcessors) {

    public static RuntimeStats snapshot() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();

        long gcCount = 0, gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount(); if (c > 0) gcCount += c;
            long t = gc.getCollectionTime();  if (t > 0) gcTime += t;
        }

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        return new RuntimeStats(
                heap.getUsed(), heap.getCommitted(), heap.getMax(),
                nonHeap.getUsed(),
                gcCount, gcTime,
                threads.getThreadCount(), threads.getDaemonThreadCount(),
                Runtime.getRuntime().availableProcessors());
    }

    /** Fraction of max heap in use (0..1), or -1 when the max is unbounded/undefined. */
    public double heapUsedRatio() {
        return heapMax > 0 ? (double) heapUsed / heapMax : -1.0;
    }

    /** True when heap usage is at or above {@code threshold} (e.g. 0.85) — a load-shed signal. */
    public boolean underMemoryPressure(double threshold) {
        double ratio = heapUsedRatio();
        return ratio >= 0 && ratio >= threshold;
    }
}
