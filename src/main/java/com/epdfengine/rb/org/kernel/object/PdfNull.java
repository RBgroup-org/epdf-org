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

/** The PDF {@code null} object. Singleton (there is only one null value). */
public final class PdfNull implements PdfObject {

    public static final PdfNull INSTANCE = new PdfNull();

    private PdfNull() {}

    @Override public PdfType type() { return PdfType.NULL; }

    @Override public String toString() { return "null"; }
}
