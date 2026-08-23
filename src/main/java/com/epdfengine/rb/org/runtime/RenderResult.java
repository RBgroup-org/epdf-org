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

/**
 * The outcome of a completed render job. A <b>buffered</b> render carries the PDF
 * {@code bytes}; a <b>streamed</b> render (written straight to a caller {@link java.io.OutputStream})
 * carries no payload, only {@code bytesWritten}. Primitive {@code long} fields keep the
 * hot completion path allocation-free.
 */
public record RenderResult(String jobId, byte[] bytes, long elapsedMillis, long bytesWritten) {

    private static final byte[] EMPTY = new byte[0];

    /** Result for a buffered render that returns the PDF bytes in memory. */
    public static RenderResult buffered(String jobId, byte[] bytes, long elapsedMillis) {
        return new RenderResult(jobId, bytes != null ? bytes : EMPTY, elapsedMillis,
                bytes != null ? bytes.length : 0L);
    }

    /** Result for a render streamed directly to an output stream (no payload retained). */
    public static RenderResult streamed(String jobId, long bytesWritten, long elapsedMillis) {
        return new RenderResult(jobId, EMPTY, elapsedMillis, bytesWritten);
    }

    /** Size of the in-memory payload (0 for a streamed result — see {@link #bytesWritten()}). */
    public int byteCount() { return bytes != null ? bytes.length : 0; }
}
