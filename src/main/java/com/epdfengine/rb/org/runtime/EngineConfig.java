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

/**
 * Immutable configuration for an {@link Epdf} engine instance.
 *
 * @param cpuThreads    worker threads for CPU-bound layout/render (default = available processors)
 * @param queueCapacity bounded backlog before {@link BackpressureException} is signalled
 * @param defaultLimits per-job {@link ResourceLimits} applied when a request omits its own
 * @param traceSink     debug-mode pipeline trace consumer, or {@code null} to disable tracing
 */
public record EngineConfig(int cpuThreads, int queueCapacity, ResourceLimits defaultLimits, TraceSink traceSink) {

    public EngineConfig {
        if (cpuThreads < 1) throw new IllegalArgumentException("cpuThreads must be >= 1");
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be >= 1");
        if (defaultLimits == null) defaultLimits = ResourceLimits.defaults();
    }

    /** CPU threads = cores, backlog = cores * 8, default resource limits, no tracing. */
    public static EngineConfig defaults() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return new EngineConfig(cores, cores * 8, ResourceLimits.defaults(), null);
    }

    public EngineConfig withCpuThreads(int n)    { return new EngineConfig(n, queueCapacity, defaultLimits, traceSink); }
    public EngineConfig withQueueCapacity(int n) { return new EngineConfig(cpuThreads, n, defaultLimits, traceSink); }
    public EngineConfig withDefaultLimits(ResourceLimits l) { return new EngineConfig(cpuThreads, queueCapacity, l, traceSink); }
    public EngineConfig withTraceSink(TraceSink s) { return new EngineConfig(cpuThreads, queueCapacity, defaultLimits, s); }

    /** Enables debug pipeline tracing to an external log file (created if absent). */
    public EngineConfig withDebugLog(java.nio.file.Path path) { return withTraceSink(TraceSink.toFile(path)); }
    /** Enables debug pipeline tracing to {@code System.err}. */
    public EngineConfig withDebugConsole() { return withTraceSink(TraceSink.toConsole()); }
    /** Whether debug tracing is enabled. */
    public boolean debugEnabled() { return traceSink != null; }
}
