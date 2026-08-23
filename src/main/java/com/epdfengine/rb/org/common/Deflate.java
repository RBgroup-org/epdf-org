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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * zlib/deflate compression (PDF {@code /FlateDecode}) using only the JDK's
 * {@link Deflater}. Used to shrink content streams and other stream objects.
 * Level is a balance of size and CPU suitable for a high-throughput service;
 * raise it for archival output.
 *
 * <p>{@link Deflater} holds native zlib state that is costly to allocate/free, and a single
 * render compresses many streams. A small <b>bounded, thread-safe pool</b> reuses deflaters
 * (reset between uses) so per-render compression does not thrash native memory — the one place
 * pooling clearly pays off. Short-lived Java scratch (box nodes, builders) is intentionally left
 * to the generational GC, which reclaims it faster than a hand-rolled pool would.</p>
 */
public final class Deflate {

    /** Balanced default: good ratio without the CPU cost of maximum compression. */
    public static final int DEFAULT_LEVEL = Deflater.DEFAULT_COMPRESSION;

    private static final int POOL_MAX = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    private static final ConcurrentLinkedDeque<Deflater> POOL = new ConcurrentLinkedDeque<>();
    private static final AtomicInteger POOL_SIZE = new AtomicInteger();
    private static final LongAdder REUSES = new LongAdder();
    private static final LongAdder CREATES = new LongAdder();

    private Deflate() {}

    public static byte[] deflate(byte[] data) {
        return deflate(data, DEFAULT_LEVEL);
    }

    public static byte[] deflate(byte[] data, int level) {
        if (data == null) data = new byte[0];
        Deflater deflater = borrow(level);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        try (DeflaterOutputStream dos = new DeflaterOutputStream(out, deflater)) {  // does not end() our deflater
            dos.write(data);
        } catch (IOException e) {
            throw new IllegalStateException("Deflate failed", e);
        } finally {
            release(deflater);
        }
        return out.toByteArray();
    }

    /** Reused/created deflater counts — for pool observability and tests. */
    public static long poolReuses()  { return REUSES.sum(); }
    public static long poolCreates() { return CREATES.sum(); }

    private static Deflater borrow(int level) {
        Deflater d = POOL.pollFirst();
        if (d != null) { POOL_SIZE.decrementAndGet(); REUSES.increment(); }
        else           { d = new Deflater(level); CREATES.increment(); }
        d.setLevel(level);
        d.reset();               // clears any prior (finished) state; applies the level
        return d;
    }

    private static void release(Deflater d) {
        if (POOL_SIZE.get() < POOL_MAX) { POOL.offerFirst(d); POOL_SIZE.incrementAndGet(); }
        else d.end();            // over capacity → free native state now
    }
}

