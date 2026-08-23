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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Smoke test for the byte-bounded cache and the caching resource loader. */
public final class CacheSmokeTest {

    public static void main(String[] args) throws Exception {
        evictionByBytes();
        computeIfAbsentDedup();
        cachingLoader();
        concurrency();
        System.out.println("PASS — byte-bounded cache (eviction + dedup + caching loader + concurrency)");
    }

    private static void evictionByBytes() {
        // Single shard, 1000-byte budget, twenty 100-byte entries -> only the newest ten survive.
        ByteBoundedCache<String, byte[]> cache = new ByteBoundedCache<>(1000, b -> b.length, 1);
        for (int i = 0; i < 20; i++) cache.put("k" + i, new byte[100]);

        ByteBoundedCache.Stats s = cache.stats();
        Assert.isTrue(s.bytes() <= 1000, "bytes within budget (" + s.bytes() + ")");
        Assert.isTrue(s.evictions() >= 10, "evictions happened (" + s.evictions() + ")");
        Assert.isTrue(cache.get("k19") != null, "most-recent key retained");
        Assert.isTrue(cache.get("k0") == null, "oldest key evicted");
    }

    private static void computeIfAbsentDedup() {
        ByteBoundedCache<String, byte[]> cache = new ByteBoundedCache<>(1 << 20, b -> b.length);
        AtomicInteger loads = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            byte[] v = cache.computeIfAbsent("same", k -> { loads.incrementAndGet(); return new byte[64]; });
            Assert.isTrue(v.length == 64, "value returned");
        }
        Assert.equals(1L, loads.get(), "loader ran once for repeated key");
        Assert.isTrue(cache.stats().hitRate() > 0.5, "mostly hits");
    }

    private static void cachingLoader() {
        AtomicInteger delegateCalls = new AtomicInteger();
        CachingResourceLoader loader = new CachingResourceLoader(
                ref -> { delegateCalls.incrementAndGet(); return ("bytes:" + ref).getBytes(); },
                1 << 20);

        byte[] a1 = loader.load("logo.png");
        byte[] a2 = loader.load("logo.png");
        byte[] b1 = loader.load("font.ttf");
        Assert.isTrue(a1 != null && a2 != null && b1 != null, "all resolved");
        Assert.equals(2L, delegateCalls.get(), "delegate called once per distinct reference");
        Assert.isTrue(loader.cacheStats().hits() >= 1, "cache served a hit");
        Assert.isTrue(loader.load(null) == null, "null reference tolerated");
    }

    private static void concurrency() throws Exception {
        ByteBoundedCache<Integer, byte[]> cache = new ByteBoundedCache<>(1 << 20, b -> b.length);
        int threads = 16, iters = 20_000;
        CountDownLatch start = new CountDownLatch(1);
        Thread[] pool = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            pool[t] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException ignored) { return; }
                for (int i = 0; i < iters; i++) {
                    int key = i & 3;   // 4 hot keys
                    byte[] v = cache.computeIfAbsent(key, k -> new byte[128]);
                    if (v.length != 128) throw new AssertionError("corrupt value");
                }
            });
            pool[t].start();
        }
        start.countDown();
        for (Thread th : pool) th.join();
        Assert.isTrue(cache.stats().entries() <= 4, "bounded to hot keys (" + cache.stats().entries() + ")");
        Assert.isTrue(cache.stats().hits() > 0, "concurrent hits recorded");
    }
}
