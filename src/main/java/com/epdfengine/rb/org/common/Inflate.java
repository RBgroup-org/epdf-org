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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Bounded zlib/inflate — the defence against <b>decompression bombs</b>. Inflating stops and
 * throws once the output would exceed an absolute byte cap or an expansion-ratio cap, so a tiny
 * hostile stream cannot balloon into gigabytes of heap.
 */
public final class Inflate {

    private Inflate() {}

    /** Thrown when inflated output would exceed the configured caps. */
    public static final class BombException extends RuntimeException {
        public BombException(String message) { super(message); }
    }

    /**
     * Inflates {@code data} with caps: output may not exceed {@code maxOutputBytes} (absolute) nor
     * {@code maxRatio}× the input size. A cap of {@code 0} disables that check.
     */
    public static byte[] inflate(byte[] data, long maxOutputBytes, int maxRatio) {
        if (data == null || data.length == 0) return new byte[0];
        long ratioCap = (maxRatio > 0) ? (long) data.length * maxRatio : Long.MAX_VALUE;
        long cap = min(maxOutputBytes > 0 ? maxOutputBytes : Long.MAX_VALUE, ratioCap);

        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(1 << 16, Math.max(64, data.length * 2)));
        byte[] buf = new byte[1 << 14];
        try {
            long total = 0;
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) {
                    if (inflater.finished() || inflater.needsInput() || inflater.needsDictionary()) break;
                    continue;
                }
                total += n;
                if (total > cap) throw new BombException("inflate exceeded cap " + cap + " bytes (decompression bomb?)");
                out.write(buf, 0, n);
            }
        } catch (DataFormatException e) {
            throw new IllegalStateException("Inflate failed: bad zlib data", e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    private static long min(long a, long b) { return Math.min(a, b); }
}
