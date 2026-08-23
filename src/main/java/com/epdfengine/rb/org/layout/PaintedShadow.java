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

/**
 * A drop shadow for a box (CSS {@code box-shadow}, outset only). The painter draws
 * it behind the box as an offset, spread-expanded rounded rectangle; the blur is
 * approximated with a few concentric alpha layers. Coordinates are the box's own
 * top-left/size in points; offset/spread/blur are applied at paint time.
 */
public final class PaintedShadow implements Drawable {

    private final int page;
    private final double x, y, width, height;
    private final double cornerTl, cornerTr, cornerBr, cornerBl;
    private final double offsetX, offsetY, blur, spread;
    private final int rgb;
    private final double alpha;

    public PaintedShadow(int page, double x, double y, double width, double height,
                         double cornerTl, double cornerTr, double cornerBr, double cornerBl,
                         double offsetX, double offsetY, double blur, double spread,
                         int rgb, double alpha) {
        this.page = page;
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.cornerTl = cornerTl; this.cornerTr = cornerTr;
        this.cornerBr = cornerBr; this.cornerBl = cornerBl;
        this.offsetX = offsetX; this.offsetY = offsetY;
        this.blur = blur; this.spread = spread;
        this.rgb = rgb; this.alpha = alpha;
    }

    @Override public int pageIndex() { return page; }

    public double x()        { return x; }
    public double y()        { return y; }
    public double width()    { return width; }
    public double height()   { return height; }
    public double cornerTl() { return cornerTl; }
    public double cornerTr() { return cornerTr; }
    public double cornerBr() { return cornerBr; }
    public double cornerBl() { return cornerBl; }
    public double offsetX()  { return offsetX; }
    public double offsetY()  { return offsetY; }
    public double blur()     { return blur; }
    public double spread()   { return spread; }
    public int rgb()         { return rgb; }
    public double alpha()    { return alpha; }
}
