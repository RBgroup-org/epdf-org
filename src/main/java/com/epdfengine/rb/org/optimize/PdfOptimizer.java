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
package com.epdfengine.rb.org.optimize;

import com.epdfengine.rb.org.kernel.PdfMerger;

/**
 * Shrinks an existing PDF: it is read, its page graph is deep-copied (dropping objects nothing
 * references), and re-serialized with <b>object streams</b> and a compressed {@code /XRef} stream.
 * A single facade over the reader + compressed writer — the "reduce file size" tool.
 */
public final class PdfOptimizer {

    private PdfOptimizer() {}

    /** Returns a size-optimized copy of {@code pdf} (object streams + compressed cross-reference). */
    public static byte[] optimize(byte[] pdf) {
        return new PdfMerger().add(pdf).buildCompressed();
    }

    /** Optimizes {@code pdf} and reports the size delta. */
    public static Result optimizeWithReport(byte[] pdf) {
        byte[] out = optimize(pdf);
        return new Result(out, pdf.length, out.length);
    }

    /** Optimization outcome: the bytes plus original/optimized sizes. */
    public record Result(byte[] bytes, int originalBytes, int optimizedBytes) {
        public double savedFraction() {
            return originalBytes == 0 ? 0.0 : 1.0 - (double) optimizedBytes / originalBytes;
        }
    }
}
