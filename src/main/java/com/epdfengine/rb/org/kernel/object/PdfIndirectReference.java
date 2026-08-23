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
 * An indirect reference ({@code objNumber gen R}, ISO 32000-1 §7.3.10). Points at
 * an indirect object stored in the document's object table; resolution is the
 * document's responsibility, not the reference's.
 */
public record PdfIndirectReference(int objectNumber, int generation) implements PdfObject {

    public PdfIndirectReference {
        if (objectNumber < 1) {
            throw new IllegalArgumentException("objectNumber must be >= 1: " + objectNumber);
        }
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be >= 0: " + generation);
        }
    }

    /** Convenience for the common generation-0 case. */
    public static PdfIndirectReference of(int objectNumber) {
        return new PdfIndirectReference(objectNumber, 0);
    }

    @Override public PdfType type() { return PdfType.REFERENCE; }

    @Override public String toString() { return objectNumber + " " + generation + " R"; }
}
