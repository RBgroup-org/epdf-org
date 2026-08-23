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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The scope of a single render job: its id, resource limits, wall-clock deadline and a
 * cooperative cancellation flag. Exactly one instance exists per job, is owned by the
 * thread running that job, and is never shared or mutated by other jobs — this is what
 * keeps the stateless engine safe under massive concurrency.
 *
 * <p>Long-running stages call {@link #checkAlive()} at safe points so a cancelled or
 * timed-out job stops promptly instead of holding a worker thread.</p>
 */
public final class RenderContext {

    private final String jobId;
    private final ResourceLimits limits;
    private final long startNanos;
    private final long deadlineNanos;     // 0 = no deadline
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final PipelineTrace trace;    // null unless debug tracing is enabled

    public RenderContext(String jobId, ResourceLimits limits) {
        this(jobId, limits, null);
    }

    public RenderContext(String jobId, ResourceLimits limits, PipelineTrace trace) {
        this.jobId = jobId;
        this.limits = (limits != null) ? limits : ResourceLimits.defaults();
        this.startNanos = System.nanoTime();
        long ms = this.limits.jobTimeoutMillis();
        this.deadlineNanos = (ms > 0) ? startNanos + ms * 1_000_000L : 0L;
        this.trace = trace;
    }

    public String jobId()          { return jobId; }
    public ResourceLimits limits() { return limits; }
    /** The per-job pipeline trace, or {@code null} when debug tracing is off. */
    public PipelineTrace trace()   { return trace; }

    /** Requests cooperative cancellation; the next {@link #checkAlive()} will abort. */
    public void cancel()           { cancelled.set(true); }
    public boolean isCancelled()   { return cancelled.get(); }

    public boolean isExpired()     { return deadlineNanos != 0L && System.nanoTime() > deadlineNanos; }
    public long elapsedMillis()    { return (System.nanoTime() - startNanos) / 1_000_000L; }
    public long elapsedNanos()     { return System.nanoTime() - startNanos; }

    public long remainingMillis() {
        if (deadlineNanos == 0L) return Long.MAX_VALUE;
        return Math.max(0L, (deadlineNanos - System.nanoTime()) / 1_000_000L);
    }

    /** Cooperative checkpoint: throws if the job was cancelled or ran past its deadline. */
    public void checkAlive() {
        if (cancelled.get()) throw new JobCancelledException(jobId + " cancelled after " + elapsedMillis() + " ms");
        if (isExpired())     throw new JobTimeoutException(jobId + " exceeded deadline after " + elapsedMillis() + " ms");
    }
}
