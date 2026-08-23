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
package com.epdfengine.rb.org.layout.box;

import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.svg.SvgImage;

/**
 * A replaced (leaf) box holding a parsed SVG. Sized from the style width/height
 * when given, otherwise from the SVG's intrinsic size (aspect-preserving). Set
 * {@code raster} to draw it as a bitmap instead of vector operators.
 */
public final class SvgBox extends Box {

    private final SvgImage svg;
    private final boolean raster;

    public SvgBox(SvgImage svg, ComputedStyle style) {
        this(svg, style, false);
    }

    public SvgBox(SvgImage svg, ComputedStyle style, boolean raster) {
        super(style);
        this.svg = svg;
        this.raster = raster;
    }

    public SvgImage svg()   { return svg; }
    public boolean raster() { return raster; }

    @Override public Box add(Box child) {
        throw new UnsupportedOperationException("SvgBox is a replaced leaf and cannot have children");
    }

    @Override public boolean isBlockLevel()  { return true; }
    @Override public boolean isInlineLevel() { return false; }
}
