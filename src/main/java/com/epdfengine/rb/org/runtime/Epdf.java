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

import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The scalable engine facade. Each PDF render is one job dispatched onto a <b>bounded</b>
 * pool of CPU worker threads sized to the host's cores; a full backlog signals
 * {@link BackpressureException} instead of exhausting memory. A separate
 * {@link Executors#newVirtualThreadPerTaskExecutor() virtual-thread} pool is exposed for
 * I/O-bound stages (remote font/image fetches) so blocking waits never tie up a carrier
 * thread — the design that lets a single instance sustain very high request volume.
 *
 * <p>The engine holds <b>no per-render mutable state</b>: everything a job needs lives in
 * its {@link RenderContext} and {@link RenderRequest}. It is thread-safe and reusable;
 * call {@link #close()} once at shutdown.</p>
 *
 * <pre>{@code
 * try (Epdf engine = Epdf.create()) {
 *     RenderResult r = engine.render(RenderRequest.of(html).build());
 *     Files.write(path, r.bytes());
 * }
 * }</pre>
 */
public final class Epdf implements AutoCloseable {

    private final EngineConfig config;
    private final ThreadPoolExecutor cpuPool;
    private final ExecutorService ioPool;
    private final AtomicLong jobSeq   = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed    = new AtomicLong();
    private final AtomicLong rejected  = new AtomicLong();
    private final Metrics metrics     = new Metrics();

    private Epdf(EngineConfig config) {
        this.config = config;
        this.cpuPool = new ThreadPoolExecutor(
                config.cpuThreads(), config.cpuThreads(),
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.queueCapacity()),
                namedFactory("epdf-cpu-"),
                new ThreadPoolExecutor.AbortPolicy());
        this.cpuPool.allowCoreThreadTimeOut(true);
        this.ioPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    public static Epdf create()                    { return new Epdf(EngineConfig.defaults()); }
    public static Epdf create(EngineConfig config) { return new Epdf(config); }

    /** The virtual-thread executor for I/O-bound work (e.g. resource fetching) inside a job. */
    public ExecutorService ioExecutor() { return ioPool; }

    /**
     * Concurrently prefetches an HTML document's external resources on the virtual-thread pool and
     * returns a loader that serves them from memory — so the subsequent render pays one round-trip
     * for many assets instead of fetching them one at a time during layout.
     */
    public com.epdfengine.rb.org.core.ResourceLoader prefetch(
            String html, com.epdfengine.rb.org.core.ResourceLoader loader) {
        return com.epdfengine.rb.org.core.ResourcePrefetcher.prefetching(html, loader, ioPool);
    }

    public EngineConfig config() { return config; }

    /**
     * Submits a render job. The returned future completes with the {@link RenderResult}
     * or fails with {@link BackpressureException} (queue full), {@link JobTimeoutException}
     * (deadline exceeded) or {@link EngineException} (render error).
     */
    public CompletableFuture<RenderResult> submit(RenderRequest request) {
        RenderContext ctx = newContext(request);
        return dispatch(ctx, () -> runJob(request, ctx));
    }

    /**
     * Submits a job that streams its PDF straight to {@code out} (no {@code byte[]} held in
     * memory). The caller owns {@code out} and must keep it open until the future completes.
     */
    public CompletableFuture<RenderResult> submitTo(RenderRequest request, OutputStream out) {
        java.util.Objects.requireNonNull(out, "out");
        RenderContext ctx = newContext(request);
        return dispatch(ctx, () -> runJobTo(request, ctx, out));
    }

    /** Blocking convenience: submit and wait, unwrapping engine exceptions. */
    public RenderResult render(RenderRequest request) {
        try {
            return submit(request).join();
        } catch (CompletionException ce) {
            throw unwrap(ce);
        }
    }

    /** Blocking convenience: stream the render to {@code out} and wait. */
    public RenderResult renderTo(RenderRequest request, OutputStream out) {
        try {
            return submitTo(request, out).join();
        } catch (CompletionException ce) {
            throw unwrap(ce);
        }
    }

    private RenderContext newContext(RenderRequest request) {
        ResourceLimits limits = (request.limits() != null) ? request.limits() : config.defaultLimits();
        String jobId = "job-" + jobSeq.incrementAndGet();
        PipelineTrace trace = (config.traceSink() != null) ? new PipelineTrace(jobId) : null;
        return new RenderContext(jobId, limits, trace);
    }

    /** Shared dispatch: bounded submit (backpressure), deadline guard, completion counters. */
    private CompletableFuture<RenderResult> dispatch(RenderContext ctx,
                                                     java.util.function.Supplier<RenderResult> body) {
        CompletableFuture<RenderResult> future;
        try {
            future = CompletableFuture.supplyAsync(body, cpuPool);
        } catch (RejectedExecutionException rex) {
            rejected.incrementAndGet();
            return CompletableFuture.failedFuture(
                    new BackpressureException("engine saturated: " + queueDepth() + " queued, "
                            + cpuPool.getActiveCount() + " active"));
        }
        long timeout = ctx.limits().jobTimeoutMillis();
        CompletableFuture<RenderResult> guarded =
                (timeout > 0) ? future.orTimeout(timeout, TimeUnit.MILLISECONDS) : future;
        return guarded.whenComplete((result, error) -> {
            if (error != null) { ctx.cancel(); failed.incrementAndGet(); }
            else               { completed.incrementAndGet(); }
            metrics.recordLatencyNanos(ctx.elapsedNanos());
            metrics.increment(error != null ? "renders.failed" : "renders.ok");
            TraceSink sink = config.traceSink();
            if (sink != null && ctx.trace() != null) {
                try { sink.accept(ctx.trace()); } catch (RuntimeException ignored) { /* never let logging break a render */ }
            }
        });
    }

    private RenderResult runJob(RenderRequest request, RenderContext ctx) {
        try {
            ctx.checkAlive();
            byte[] bytes = request.execute(ctx);
            return RenderResult.buffered(ctx.jobId(), bytes, ctx.elapsedMillis());
        } catch (EngineException ee) {
            throw ee;
        } catch (Exception e) {
            throw new EngineException("render failed for " + ctx.jobId(), e);
        }
    }

    private RenderResult runJobTo(RenderRequest request, RenderContext ctx, OutputStream out) {
        try {
            ctx.checkAlive();
            long written = request.executeTo(ctx, out);
            return RenderResult.streamed(ctx.jobId(), written, ctx.elapsedMillis());
        } catch (EngineException ee) {
            throw ee;
        } catch (Exception e) {
            throw new EngineException("render failed for " + ctx.jobId(), e);
        }
    }

    private static EngineException unwrap(Throwable t) {
        Throwable cause = (t instanceof CompletionException && t.getCause() != null) ? t.getCause() : t;
        if (cause instanceof EngineException ee) return ee;
        if (cause instanceof TimeoutException)   return new JobTimeoutException("render exceeded deadline");
        return new EngineException("render failed", cause);
    }

    // --- observability (feeds the metrics/scale work) ---

    public int  queueDepth()      { return cpuPool.getQueue().size(); }
    public Stats stats() {
        return new Stats(cpuPool.getActiveCount(), queueDepth(),
                completed.get(), failed.get(), rejected.get());
    }

    /** A live snapshot of JVM heap/GC/thread state — poll {@code heapUsedRatio()} for load shedding. */
    public RuntimeStats runtimeStats() { return RuntimeStats.snapshot(); }

    /** Engine metrics: render counters and a latency histogram (percentiles). */
    public Metrics metrics() { return metrics; }

    /** A point-in-time snapshot of engine load and throughput counters. */
    public record Stats(int active, int queued, long completed, long failed, long rejected) {}

    @Override
    public void close() {
        cpuPool.shutdown();
        ioPool.shutdown();
        try {
            if (!cpuPool.awaitTermination(30, TimeUnit.SECONDS)) cpuPool.shutdownNow();
        } catch (InterruptedException e) {
            cpuPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicLong n = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, prefix + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
