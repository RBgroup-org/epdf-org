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
 * A PDF numeric object. PDF distinguishes integers from reals only by textual
 * form, so we track whether the value should be written without a fraction.
 */
public final class PdfNumber implements PdfObject {

    public static final PdfNumber ZERO = PdfNumber.of(0L);

    private final double value;
    private final boolean integer;

    private PdfNumber(double value, boolean integer) {
        this.value = value;
        this.integer = integer;
    }

    public static PdfNumber of(int value)  { return new PdfNumber(value, true); }
    public static PdfNumber of(long value) { return new PdfNumber(value, true); }

    public static PdfNumber of(double value) {
        boolean isInt = !Double.isNaN(value) && !Double.isInfinite(value)
                && value == Math.rint(value)
                && Math.abs(value) < 9.007199254740992E15; // 2^53, exact-integer range
        return new PdfNumber(value, isInt);
    }

    public double doubleValue() { return value; }
    public long   longValue()   { return (long) value; }
    public int    intValue()    { return (int) value; }
    public boolean isInteger()  { return integer; }

    @Override public PdfType type() { return PdfType.NUMBER; }

    @Override public String toString() {
        return integer ? Long.toString((long) value) : Double.toString(value);
    }
}
