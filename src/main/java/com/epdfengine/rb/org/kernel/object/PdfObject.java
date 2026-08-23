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
package com.epdfengine.rb.org.kernel.object;

/**
 * Root of the PDF object model (ISO 32000-1 §7.3). A {@code sealed} hierarchy: the
 * complete, closed set of object kinds is known at compile time, which lets the
 * reader/writer switch exhaustively and prevents foreign implementations.
 *
 * <p>Objects are plain, mostly-immutable data. Serialization is the writer's job
 * (package {@code kernel.write}); parsing is the reader's job
 * ({@code kernel.read}). Keeping the model free of I/O keeps it testable and
 * allocation-light.</p>
 */
public sealed interface PdfObject
        permits PdfNull, PdfBoolean, PdfNumber, PdfString, PdfName,
                PdfArray, PdfDictionary, PdfStream, PdfIndirectReference {

    /** The kind of this object. */
    PdfType type();

    /** True only for an indirect reference ({@code N G R}). */
    default boolean isIndirect() {
        return this instanceof PdfIndirectReference;
    }

    /** True only for the PDF {@code null} object. */
    default boolean isNull() {
        return this instanceof PdfNull;
    }
}
