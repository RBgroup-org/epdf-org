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

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A PDF string object (ISO 32000-1 §7.3.4). Holds raw bytes plus whether the
 * writer should emit it as a hexadecimal string {@code <...>} (safest for binary
 * / UTF-16BE) or a literal string {@code (...)}.
 *
 * <p>Escaping of literal strings and byte-to-hex encoding are performed by the
 * writer; this class only carries the bytes and the preferred form.</p>
 */
public final class PdfString implements PdfObject {

    private final byte[] bytes;
    private final boolean hex;

    public PdfString(byte[] bytes, boolean hex) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        this.hex = hex;
    }

    /** ASCII/Latin-1 literal string, e.g. for simple identifiers. */
    public static PdfString ofAscii(String s) {
        return new PdfString(s.getBytes(StandardCharsets.ISO_8859_1), false);
    }

    /** Unicode text string encoded as UTF-16BE with BOM, emitted as hex. */
    public static PdfString ofText(String s) {
        byte[] u = s.getBytes(StandardCharsets.UTF_16BE);
        byte[] withBom = new byte[u.length + 2];
        withBom[0] = (byte) 0xFE;
        withBom[1] = (byte) 0xFF;
        System.arraycopy(u, 0, withBom, 2, u.length);
        return new PdfString(withBom, true);
    }

    public static PdfString ofBytes(byte[] raw) { return new PdfString(raw, false); }
    public static PdfString ofHex(byte[] raw)   { return new PdfString(raw, true); }

    /** A defensive copy of the raw bytes. */
    public byte[] bytes() { return bytes.clone(); }

    public boolean isHex() { return hex; }

    @Override public PdfType type() { return PdfType.STRING; }
}
