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
package com.epdfengine.rb.org.layout;

import com.epdfengine.rb.org.common.Edges;

/**
 * Page geometry for a layout run: the page size and its margins (points). The
 * content area is the page minus margins.
 */
public record LayoutParams(double pageWidthPt, double pageHeightPt, Edges margins) {

    /** The maximum page dimension a PDF viewer accepts (200 in = 14400 pt). */
    public static final double MAX_PAGE_DIMENSION_PT = 14400.0;

    /** A4 (595.28 × 841.89 pt) with uniform margins. */
    public static LayoutParams a4(double marginPt) {
        return new LayoutParams(595.28, 841.89, Edges.of(marginPt));
    }

    /** A3 (841.89 × 1190.55 pt) with uniform margins. */
    public static LayoutParams a3(double marginPt) { return named("a3", marginPt); }

    /** A5 (419.53 × 595.28 pt) with uniform margins. */
    public static LayoutParams a5(double marginPt) { return named("a5", marginPt); }

    /** US Letter (612 × 792 pt) with uniform margins. */
    public static LayoutParams letter(double marginPt) {
        return new LayoutParams(612.0, 792.0, Edges.of(marginPt));
    }

    /** US Legal (612 × 1008 pt) with uniform margins. */
    public static LayoutParams legal(double marginPt) { return named("legal", marginPt); }

    /** Tabloid / Ledger (792 × 1224 pt) with uniform margins. */
    public static LayoutParams tabloid(double marginPt) { return named("tabloid", marginPt); }

    /** An explicit custom size (points) with uniform margins. */
    public static LayoutParams of(double widthPt, double heightPt, double marginPt) {
        return new LayoutParams(widthPt, heightPt, Edges.of(marginPt));
    }

    /** An explicit custom size (points) with per-side margins. */
    public static LayoutParams of(double widthPt, double heightPt, Edges margins) {
        return new LayoutParams(widthPt, heightPt, margins != null ? margins : Edges.ZERO);
    }

    /**
     * A named page size (a4/a3/a5/b4/b5/letter/legal/tabloid), an explicit
     * {@code "<w> <h>"} with units (e.g. {@code "210mm 297mm"}), and/or an orientation
     * keyword ({@code landscape}/{@code portrait}) — with uniform margins.
     */
    public static LayoutParams named(String size, double marginPt) {
        double[] wh = com.epdfengine.rb.org.css.PageSize.parse(size);
        if (wh == null) wh = new double[]{ 595.28, 841.89 };
        return new LayoutParams(wh[0], wh[1], Edges.of(marginPt));
    }

    /**
     * A "long page" whose height is the PDF maximum (14400 pt) so content lays out on
     * a single page; pair with {@code LaidOutDocument.shrinkToContent()} to trim the
     * height to the content (auto/screenshot-style page).
     */
    public static LayoutParams longPage(double widthPt, double marginPt) {
        return new LayoutParams(widthPt, MAX_PAGE_DIMENSION_PT, Edges.of(marginPt));
    }

    /** A copy with a different page size (same margins). */
    public LayoutParams withPageSize(double widthPt, double heightPt) {
        return new LayoutParams(widthPt, heightPt, margins);
    }

    /** A copy rotated to landscape (wider than tall); no-op if already landscape. */
    public LayoutParams landscape() {
        return (pageWidthPt >= pageHeightPt) ? this : new LayoutParams(pageHeightPt, pageWidthPt, margins);
    }

    /** A copy rotated to portrait (taller than wide); no-op if already portrait. */
    public LayoutParams portrait() {
        return (pageHeightPt >= pageWidthPt) ? this : new LayoutParams(pageHeightPt, pageWidthPt, margins);
    }

    /** A copy with different margins (same page size). */
    public LayoutParams withMargins(Edges m) {
        return new LayoutParams(pageWidthPt, pageHeightPt, m != null ? m : margins);
    }

    public double contentLeftPt()   { return margins.left(); }
    public double contentTopPt()    { return margins.top(); }
    public double contentWidthPt()  { return pageWidthPt - margins.horizontal(); }
    public double contentBottomPt() { return pageHeightPt - margins.bottom(); }
}
