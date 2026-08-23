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

import com.epdfengine.rb.org.testkit.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Smoke test for the runtime foundation: concurrent job execution, backpressure when the
 * bounded queue overflows, and per-job resource limits. Run with
 * {@code java -cp <build> com.epdfengine.rb.org.runtime.RuntimeSmokeTest}.
 */
public final class RuntimeSmokeTest {

    private static final String HTML =
            "<html><head><style>h1{color:#123456}p{font-size:11pt}</style></head>"
            + "<body><h1>ePDF runtime</h1><p>Rendered through the bounded engine executor "
            + "to prove per-job isolation and thread safety under concurrency.</p></body></html>";

    public static void main(String[] args) throws Exception {
        happyPath();
        streaming();
        concurrentBatch();
        backpressure();
        inputLimit();
        runtimeStats();
        System.out.println("PASS — runtime foundation (concurrency + backpressure + limits + streaming + stats)");
    }

    private static void runtimeStats() {
        RuntimeStats s = RuntimeStats.snapshot();
        Assert.isTrue(s.availableProcessors() >= 1, "processors reported");
        Assert.isTrue(s.heapUsed() > 0, "heap used reported");
        Assert.isTrue(s.liveThreads() >= 1, "threads reported");
        double ratio = s.heapUsedRatio();
        Assert.isTrue(ratio == -1.0 || (ratio >= 0.0 && ratio <= 1.0), "heap ratio in range");
        Assert.isTrue(!s.underMemoryPressure(1.01), "not under pressure above 100% threshold");
        try (Epdf engine = Epdf.create()) {
            Assert.isTrue(engine.runtimeStats().availableProcessors() >= 1, "engine exposes runtime stats");
        }
    }

    private static void happyPath() {
        try (Epdf engine = Epdf.create()) {
            RenderResult r = engine.render(RenderRequest.of(HTML).build());
            String pdf = new String(r.bytes(), StandardCharsets.ISO_8859_1);
            Assert.isTrue(pdf.startsWith("%PDF-1.7"), "PDF header");
            Assert.isTrue(r.byteCount() > 400, "non-trivial PDF size");
            Assert.isTrue(r.elapsedMillis() >= 0, "elapsed recorded");
            Assert.equals(1L, engine.stats().completed(), "one completed");
        }
    }

    private static void streaming() {
        try (Epdf engine = Epdf.create()) {
            java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
            RenderResult streamed = engine.renderTo(RenderRequest.of(HTML).build(), sink);
            byte[] fromStream = sink.toByteArray();
            Assert.isTrue(fromStream.length > 400, "streamed a non-trivial PDF");
            Assert.isTrue(new String(fromStream, StandardCharsets.ISO_8859_1).startsWith("%PDF-1.7"),
                    "streamed PDF header");
            Assert.equals(fromStream.length, streamed.bytesWritten(), "bytesWritten matches stream size");
            Assert.equals(0L, streamed.byteCount(), "streamed result holds no in-memory payload");

            byte[] buffered = engine.render(RenderRequest.of(HTML).build()).bytes();
            Assert.equals(buffered.length, fromStream.length, "streamed == buffered byte count");
        }
    }

    private static void concurrentBatch() throws Exception {
        int n = 200;
        try (Epdf engine = Epdf.create(EngineConfig.defaults().withQueueCapacity(n + 64))) {
            List<CompletableFuture<RenderResult>> futures = new ArrayList<>(n);
            for (int i = 0; i < n; i++) futures.add(engine.submit(RenderRequest.of(HTML).build()));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<RenderResult> f : futures) {
                Assert.isTrue(f.join().byteCount() > 400, "each job produced a PDF");
            }
            Assert.equals(n, engine.stats().completed(), "all jobs completed");
        }
    }

    private static void backpressure() {
        // One worker, minimal backlog, heavy oversubscription -> the queue must overflow.
        EngineConfig tight = EngineConfig.defaults().withCpuThreads(1).withQueueCapacity(1);
        int submitted = 5000, rejected = 0;
        try (Epdf engine = Epdf.create(tight)) {
            List<CompletableFuture<RenderResult>> futures = new ArrayList<>(submitted);
            for (int i = 0; i < submitted; i++) futures.add(engine.submit(RenderRequest.of(HTML).build()));
            for (CompletableFuture<RenderResult> f : futures) {
                try { f.join(); }
                catch (Exception e) {
                    if (rootCause(e) instanceof BackpressureException) rejected++;
                }
            }
            Assert.isTrue(rejected > 0, "backpressure triggered under saturation (rejected=" + rejected + ")");
            Assert.isTrue(engine.stats().rejected() > 0, "rejected counter incremented");
        }
    }

    private static void inputLimit() {
        try (Epdf engine = Epdf.create()) {
            RenderRequest tiny = RenderRequest.of(HTML)
                    .limits(ResourceLimits.defaults().withMaxInputBytes(10))
                    .build();
            boolean threw = false;
            try { engine.render(tiny); }
            catch (ResourceLimitException e) { threw = true; }
            Assert.isTrue(threw, "input over limit is rejected");
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c;
    }
}
