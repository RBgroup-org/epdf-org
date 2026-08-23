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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Drives a burst of renders through the engine and reports throughput + latency percentiles. */
public final class LoadHarnessTest {

    private static final String HTML =
            "<html><head><style>h1{color:#123456}p{font-size:11pt}</style></head>"
            + "<body><h1>Load harness</h1><p>Throughput and latency measurement across the bounded "
            + "engine pool, proving the scalability path end to end.</p></body></html>";

    public static void main(String[] args) throws Exception {
        int n = 5000;
        try (Epdf engine = Epdf.create(EngineConfig.defaults().withQueueCapacity(n + 256))) {
            long t0 = System.nanoTime();
            List<CompletableFuture<RenderResult>> futures = new ArrayList<>(n);
            for (int i = 0; i < n; i++) futures.add(engine.submit(RenderRequest.of(HTML).build()));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            double secs = (System.nanoTime() - t0) / 1e9;

            long ok = 0;
            for (CompletableFuture<RenderResult> f : futures) if (f.join().byteCount() > 0) ok++;

            Metrics m = engine.metrics();
            System.out.printf("  %d renders in %.2fs = %.0f/s | p50=%.1fms p99=%.1fms mean=%.2fms%n",
                    n, secs, n / secs, m.percentileMillis(50), m.percentileMillis(99), m.meanMillis());

            Assert.equals(n, ok, "all renders produced output");
            Assert.equals(n, engine.stats().completed(), "all jobs completed");
            Assert.equals(n, m.count("renders.ok"), "metrics counted ok renders");
            Assert.equals(n, m.latencyCount(), "latency histogram populated");
            Assert.isTrue(m.percentileMillis(99) >= m.percentileMillis(50), "p99 >= p50");
        }
        System.out.println("PASS — metrics + load harness (5000 renders)");
    }
}
