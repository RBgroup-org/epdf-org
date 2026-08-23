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

import java.util.Objects;

/**
 * A PDF stream object (ISO 32000-1 §7.3.8): a dictionary followed by a sequence
 * of bytes. This class holds the <em>raw</em> (unencoded) content; the writer
 * applies filters (e.g. FlateDecode) and sets {@code /Length} at serialization
 * time, so callers work with plain bytes.
 */
public final class PdfStream implements PdfObject {

    private final PdfDictionary dict;
    private final byte[] rawBytes;

    public PdfStream(byte[] rawBytes) {
        this(new PdfDictionary(), rawBytes);
    }

    public PdfStream(PdfDictionary dict, byte[] rawBytes) {
        this.dict = Objects.requireNonNull(dict, "dict");
        this.rawBytes = Objects.requireNonNull(rawBytes, "rawBytes").clone();
    }

    /** The stream's dictionary (mutable — add {@code /Subtype}, {@code /Filter}, etc.). */
    public PdfDictionary dict() { return dict; }

    /** A defensive copy of the raw, unencoded stream content. */
    public byte[] rawBytes() { return rawBytes.clone(); }

    /** Length of the raw content in bytes (before any filter is applied). */
    public int rawLength() { return rawBytes.length; }

    @Override public PdfType type() { return PdfType.STREAM; }
}
