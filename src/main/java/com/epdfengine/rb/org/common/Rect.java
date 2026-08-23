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
 * An axis-aligned rectangle in points, with the origin at the top-left and y
 * increasing downward (layout space). The painter converts to PDF's bottom-left
 * origin at emit time.
 */
public record Rect(double x, double y, double width, double height) {

    public static Rect of(double x, double y, double width, double height) {
        return new Rect(x, y, width, height);
    }

    public double right()  { return x + width; }
    public double bottom() { return y + height; }

    /** Shrinks this rectangle inward by the given edges (e.g. content = border − padding). */
    public Rect inset(Edges e) {
        return new Rect(x + e.left(), y + e.top(),
                Math.max(0, width - e.horizontal()),
                Math.max(0, height - e.vertical()));
    }

    /** Grows this rectangle outward by the given edges. */
    public Rect outset(Edges e) {
        return new Rect(x - e.left(), y - e.top(),
                width + e.horizontal(), height + e.vertical());
    }
}
