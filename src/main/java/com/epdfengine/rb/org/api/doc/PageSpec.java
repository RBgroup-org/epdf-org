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
package com.epdfengine.rb.org.api.doc;

import com.epdfengine.rb.org.layout.LayoutParams;

/**
 * A named or custom page size for the programmatic (Mode B) model — the code-driven
 * equivalent of CSS {@code @page size}. Sizes are in PDF points (1/72 inch).
 */
public final class PageSpec {

    public static final PageSpec A4      = new PageSpec(595.28, 841.89);
    public static final PageSpec A3      = new PageSpec(841.89, 1190.55);
    public static final PageSpec A5      = new PageSpec(419.53, 595.28);
    public static final PageSpec LETTER  = new PageSpec(612.0, 792.0);
    public static final PageSpec LEGAL   = new PageSpec(612.0, 1008.0);
    public static final PageSpec TABLOID = new PageSpec(792.0, 1224.0);

    private final double widthPt;
    private final double heightPt;

    private PageSpec(double widthPt, double heightPt) {
        this.widthPt = widthPt;
        this.heightPt = heightPt;
    }

    /** A custom page size in points. */
    public static PageSpec of(double widthPt, double heightPt) { return new PageSpec(widthPt, heightPt); }

    /** A custom page size in millimetres. */
    public static PageSpec mm(double widthMm, double heightMm) {
        return new PageSpec(widthMm * 72.0 / 25.4, heightMm * 72.0 / 25.4);
    }

    /** This size rotated to landscape (wider than tall). */
    public PageSpec landscape() { return widthPt >= heightPt ? this : new PageSpec(heightPt, widthPt); }

    /** This size rotated to portrait (taller than wide). */
    public PageSpec portrait()  { return heightPt >= widthPt ? this : new PageSpec(heightPt, widthPt); }

    public double widthPt()  { return widthPt; }
    public double heightPt() { return heightPt; }

    /** Page geometry with the given uniform margin (points). */
    public LayoutParams toLayoutParams(double marginPt) { return LayoutParams.of(widthPt, heightPt, marginPt); }
}
