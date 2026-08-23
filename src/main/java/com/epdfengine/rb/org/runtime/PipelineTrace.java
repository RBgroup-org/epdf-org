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

import java.util.ArrayList;
import java.util.List;

/**
 * A per-job, thread-confined record of the render pipeline's stages — the backbone of debug-mode
 * diagnostics. Each stage is timed; if a stage throws, {@link #fail(Throwable)} records exactly
 * where the pipeline broke and why. A {@link TraceSink} writes the finished trace to an external
 * log (a file the host chooses — never inside the jar), so a failed generation can be pinpointed
 * to a stage + error after the fact.
 */
public final class PipelineTrace {

    /** One pipeline stage with its wall-clock span and outcome. */
    public record Stage(String name, long startNanos, long endNanos, boolean ok, String error) {
        public long micros() { return (endNanos - startNanos) / 1_000L; }
    }

    private final String jobId;
    private final List<Stage> stages = new ArrayList<>();
    private String openName;
    private long openStart;

    public PipelineTrace(String jobId) { this.jobId = jobId; }

    public String jobId() { return jobId; }
    public List<Stage> stages() { return stages; }

    /** Begins a stage (auto-closing any previous open one as ok). */
    public void enter(String name) {
        if (openName != null) exit();
        openName = name;
        openStart = System.nanoTime();
    }

    /** Ends the current stage successfully. */
    public void exit() {
        if (openName == null) return;
        stages.add(new Stage(openName, openStart, System.nanoTime(), true, null));
        openName = null;
    }

    /** Ends the current stage as failed, capturing the error — the pinpoint of the failure. */
    public void fail(Throwable t) {
        if (openName == null) return;
        String msg = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
        stages.add(new Stage(openName, openStart, System.nanoTime(), false, msg));
        openName = null;
    }

    public boolean failed() {
        for (Stage s : stages) if (!s.ok()) return true;
        return false;
    }

    /** Renders the trace as human-readable log lines. */
    public void writeTo(Appendable out) throws java.io.IOException {
        out.append("job ").append(jobId).append(failed() ? " FAILED" : " ok").append('\n');
        for (Stage s : stages) {
            out.append("  ").append(s.ok() ? "ok   " : "FAIL ")
               .append(s.name()).append(" (").append(Long.toString(s.micros())).append(" us)");
            if (!s.ok()) out.append(" -> ").append(s.error());
            out.append('\n');
        }
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        try { writeTo(sb); } catch (java.io.IOException ignored) { }
        return sb.toString();
    }
}
