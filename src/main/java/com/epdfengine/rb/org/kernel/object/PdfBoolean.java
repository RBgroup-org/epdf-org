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

/** A PDF boolean ({@code true} / {@code false}). Two shared immutable instances. */
public final class PdfBoolean implements PdfObject {

    public static final PdfBoolean TRUE  = new PdfBoolean(true);
    public static final PdfBoolean FALSE = new PdfBoolean(false);

    private final boolean value;

    private PdfBoolean(boolean value) { this.value = value; }

    public static PdfBoolean of(boolean value) { return value ? TRUE : FALSE; }

    public boolean value() { return value; }

    @Override public PdfType type() { return PdfType.BOOLEAN; }

    @Override public String toString() { return value ? "true" : "false"; }
}
