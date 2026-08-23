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
package com.epdfengine.rb.org.common;

import com.epdfengine.rb.org.testkit.Assert;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Inflater;

/** Verifies the pooled {@link Deflate}: correct round-trips, deflater reuse, and thread safety. */
public final class DeflatePoolTest {

    public static void main(String[] args) throws Exception {
        roundTrips();
        reuse();
        concurrency();
        System.out.println("PASS — pooled Deflate (round-trip + reuse + concurrency)");
    }

    private static byte[] inflate(byte[] z) throws Exception {
        Inflater inf = new Inflater();
        inf.setInput(z);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, z.length * 2));
        byte[] buf = new byte[8192];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0 && (inf.finished() || inf.needsInput())) break;
            out.write(buf, 0, n);
        }
        inf.end();
        return out.toByteArray();
    }

    private static void roundTrips() throws Exception {
        Random rnd = new Random(11);
        byte[][] inputs = {
            new byte[0],
            "hello".getBytes(),
            "BT /F1 12 Tf 72 720 Td (repeated repeated repeated) Tj ET".getBytes(),
            repeat((byte) 'A', 100_000),                 // highly compressible
            randomBytes(rnd, 250_000),                   // incompressible
        };
        for (int level = 0; level <= 9; level += 3) {
            for (byte[] in : inputs) {
                byte[] z = Deflate.deflate(in, level);
                Assert.isTrue(Arrays.equals(in, inflate(z)), "round-trip level " + level + " len " + in.length);
            }
        }
    }

    private static void reuse() {
        long before = Deflate.poolReuses();
        for (int i = 0; i < 50; i++) Deflate.deflate(("payload " + i).getBytes());
        Assert.isTrue(Deflate.poolReuses() > before, "deflaters reused from the pool");
    }

    private static void concurrency() throws Exception {
        int threads = 16, iters = 3_000;
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread[] pool = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            pool[t] = new Thread(() -> {
                try {
                    start.await();
                    Random rnd = new Random(seed);
                    for (int i = 0; i < iters; i++) {
                        byte[] in = randomBytes(rnd, 200 + rnd.nextInt(4000));
                        if (!Arrays.equals(in, inflate(Deflate.deflate(in)))) {
                            throw new AssertionError("corrupt round-trip");
                        }
                    }
                } catch (Throwable e) { error.compareAndSet(null, e); }
            });
            pool[t].start();
        }
        start.countDown();
        for (Thread th : pool) th.join();
        Assert.isTrue(error.get() == null, "no corruption under concurrency: " + error.get());
    }

    private static byte[] repeat(byte b, int n) { byte[] a = new byte[n]; Arrays.fill(a, b); return a; }
    private static byte[] randomBytes(Random rnd, int n) { byte[] a = new byte[n]; rnd.nextBytes(a); return a; }
}
