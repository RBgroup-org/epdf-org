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

/**
 * The four edges (top, right, bottom, left) of a box, in points. Used for
 * margins, padding, and border widths after resolution.
 */
public record Edges(double top, double right, double bottom, double left) {

    public static final Edges ZERO = new Edges(0, 0, 0, 0);

    public static Edges of(double all) { return new Edges(all, all, all, all); }

    public static Edges of(double top, double right, double bottom, double left) {
        return new Edges(top, right, bottom, left);
    }

    /** CSS two-value shorthand: vertical (top/bottom) and horizontal (left/right). */
    public static Edges symmetric(double vertical, double horizontal) {
        return new Edges(vertical, horizontal, vertical, horizontal);
    }

    /** A uniform edge specified in millimetres, converted to points. */
    public static Edges mm(double all) {
        double v = all * 72.0 / 25.4;
        return new Edges(v, v, v, v);
    }

    public double horizontal() { return left + right; }
    public double vertical()   { return top + bottom; }
}
