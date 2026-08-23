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
package com.epdfengine.rb.org.core;

import com.epdfengine.rb.org.runtime.Epdf;
import com.epdfengine.rb.org.testkit.Assert;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Verifies the virtual-thread resource prefetcher: scanning, concurrency, cache, engine wiring. */
public final class PrefetchTest {

    public static void main(String[] args) throws Exception {
        scan();
        concurrency();
        cacheFallback();
        engineIntegration();
        System.out.println("PASS — virtual-thread resource prefetch");
    }

    private static void scan() {
        String html = "<html><head><style>.b{background:url('bg.png')}</style></head>"
                + "<body><img src=\"a.png\"><img src='b.jpg'><img src=\"data:image/png;base64,AAAA\"></body></html>";
        Set<String> refs = ResourcePrefetcher.scanReferences(html);
        Assert.isTrue(refs.contains("a.png") && refs.contains("b.jpg") && refs.contains("bg.png"), "scanned: " + refs);
        Assert.isTrue(refs.stream().noneMatch(r -> r.startsWith("data:")), "data URI skipped");
    }

    private static void concurrency() {
        int n = 20;
        ResourceLoader slow = ref -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { }
            return ref.getBytes();
        };
        Set<String> refs = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) refs.add("r" + i + ".png");

        try (ExecutorService io = Executors.newVirtualThreadPerTaskExecutor()) {
            long t0 = System.nanoTime();
            Map<String, byte[]> pre = ResourcePrefetcher.prefetch(refs, slow, io);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("  " + n + " x 50ms fetches concurrently in " + ms + "ms (serial would be " + (n * 50) + "ms)");
            Assert.equals(n, pre.size(), "all resources fetched");
            Assert.isTrue(ms < 500, "prefetch is concurrent, not serial (" + ms + "ms)");
        }
    }

    private static void cacheFallback() {
        AtomicInteger calls = new AtomicInteger();
        ResourceLoader delegate = ref -> { calls.incrementAndGet(); return ("d:" + ref).getBytes(); };
        Map<String, byte[]> pre = new HashMap<>();
        pre.put("a.png", new byte[]{1, 2, 3});
        PrefetchedResourceLoader loader = new PrefetchedResourceLoader(pre, delegate);

        Assert.equals(3L, loader.load("a.png").length, "served from prefetch cache");
        Assert.equals(0L, calls.get(), "delegate not called for a prefetched reference");
        Assert.isTrue(loader.load("miss.png") != null, "delegate fallback for a cache miss");
        Assert.equals(1L, calls.get(), "delegate called once for the miss");
    }

    private static void engineIntegration() {
        AtomicInteger calls = new AtomicInteger();
        ResourceLoader base = ref -> { calls.incrementAndGet(); return ("x:" + ref).getBytes(); };
        String html = "<img src=\"logo.png\"><img src=\"icon.svg\">";
        try (Epdf engine = Epdf.create()) {
            ResourceLoader pf = engine.prefetch(html, base);
            Assert.equals(2L, calls.get(), "both resources prefetched on the io pool");
            Assert.isTrue(pf.load("logo.png") != null && pf.load("icon.svg") != null, "served from cache");
            Assert.equals(2L, calls.get(), "no extra delegate calls for cached references");
        }
    }
}
