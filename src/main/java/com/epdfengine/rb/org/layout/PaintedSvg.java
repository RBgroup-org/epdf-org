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

import com.epdfengine.rb.org.svg.SvgImage;

/**
 * An SVG placed on a page. Coordinates are top-left, in points. When
 * {@code raster} is false the renderer draws the SVG as PDF vector operators
 * (resolution-independent); when true it rasterizes the SVG to a bitmap first.
 */
public final class PaintedSvg implements Drawable {

    private final int page;
    private final double x, y, width, height;
    private final SvgImage svg;
    private final boolean raster;
    private com.epdfengine.rb.org.css.AnimFrame[] animFrames = null;
    private double animMs = 0;

    public PaintedSvg(int page, double x, double y, double width, double height, SvgImage svg, boolean raster) {
        this.page = page;
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.svg = svg;
        this.raster = raster;
    }

    @Override public int pageIndex() { return page; }

    public double x()      { return x; }
    public double y()      { return y; }
    public double width()  { return width; }
    public double height() { return height; }
    public SvgImage svg()  { return svg; }
    public boolean raster(){ return raster; }

    public PaintedSvg animFrames(com.epdfengine.rb.org.css.AnimFrame[] frames, double ms) { this.animFrames = frames; this.animMs = ms; return this; }
    public com.epdfengine.rb.org.css.AnimFrame[] animFrames() { return animFrames; }
    public double animMs() { return animMs; }
    public boolean isAnimated() { return animFrames != null && animFrames.length > 1; }
}
