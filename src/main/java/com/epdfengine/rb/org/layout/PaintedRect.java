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
import com.epdfengine.rb.org.css.BorderStyle;
import com.epdfengine.rb.org.css.Gradient;

/**
 * A rectangle to fill and/or stroke — used for box backgrounds and borders.
 * Coordinates are top-left origin, in points; the painter converts to PDF space.
 * Colours are packed 0xRRGGBB, or {@code null} to skip that operation.
 */
public final class PaintedRect implements Drawable {

    private final int page;
    private final double x, y, width, height;
    private final Integer fillRgb;    // null = no fill
    private final Integer strokeRgb;  // null = no border stroke
    private final Edges borderWidths; // per-side widths in pt
    private final double cornerTl, cornerTr, cornerBr, cornerBl;
    private final BorderStyle borderStyle;
    private final Integer strokeTop, strokeRight, strokeBottom, strokeLeft; // per-side; null = use strokeRgb
    private final Gradient gradient;  // non-null: paint as axial gradient fill
    private double fillAlpha = 1.0;    // background opacity (rgba / #rrggbbaa); 1 = opaque
    private double frostBlurPt = 0;    // backdrop-filter: blur(); >0 = draw a frosted-glass veil
    private com.epdfengine.rb.org.css.AnimFrame[] animFrames = null;  // @keyframes frames; null = static
    private double animMs = 0;            // total animation cycle in ms

    public PaintedRect(int page, double x, double y, double width, double height,
                       Integer fillRgb, Integer strokeRgb, Edges borderWidths, double cornerRadiusPt) {
        this(page, x, y, width, height, fillRgb, strokeRgb, borderWidths, cornerRadiusPt, null);
    }

    public PaintedRect(int page, double x, double y, double width, double height,
                       Integer fillRgb, Integer strokeRgb, Edges borderWidths, double cornerRadiusPt,
                       Gradient gradient) {
        this(page, x, y, width, height, fillRgb, strokeRgb, borderWidths,
                cornerRadiusPt, cornerRadiusPt, cornerRadiusPt, cornerRadiusPt, BorderStyle.SOLID, gradient);
    }

    public PaintedRect(int page, double x, double y, double width, double height,
                       Integer fillRgb, Integer strokeRgb, Edges borderWidths,
                       double cornerTl, double cornerTr, double cornerBr, double cornerBl,
                       BorderStyle borderStyle, Gradient gradient) {
        this(page, x, y, width, height, fillRgb, strokeRgb, borderWidths,
                cornerTl, cornerTr, cornerBr, cornerBl, borderStyle,
                null, null, null, null, gradient);
    }

    public PaintedRect(int page, double x, double y, double width, double height,
                       Integer fillRgb, Integer strokeRgb, Edges borderWidths,
                       double cornerTl, double cornerTr, double cornerBr, double cornerBl,
                       BorderStyle borderStyle,
                       Integer strokeTop, Integer strokeRight, Integer strokeBottom, Integer strokeLeft,
                       Gradient gradient) {
        this.page = page;
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.fillRgb = fillRgb;
        this.strokeRgb = strokeRgb;
        this.borderWidths = (borderWidths != null) ? borderWidths : Edges.ZERO;
        this.cornerTl = Math.max(0, cornerTl);
        this.cornerTr = Math.max(0, cornerTr);
        this.cornerBr = Math.max(0, cornerBr);
        this.cornerBl = Math.max(0, cornerBl);
        this.borderStyle = (borderStyle != null) ? borderStyle : BorderStyle.SOLID;
        this.strokeTop = strokeTop;
        this.strokeRight = strokeRight;
        this.strokeBottom = strokeBottom;
        this.strokeLeft = strokeLeft;
        this.gradient = gradient;
    }

    @Override public int pageIndex() { return page; }

    public double x()      { return x; }
    public double y()      { return y; }
    public double width()  { return width; }
    public double height() { return height; }

    public Integer fillRgb()   { return fillRgb; }
    public Integer strokeRgb() { return strokeRgb; }
    public Integer strokeTop()    { return strokeTop != null ? strokeTop : strokeRgb; }
    public Integer strokeRight()  { return strokeRight != null ? strokeRight : strokeRgb; }
    public Integer strokeBottom() { return strokeBottom != null ? strokeBottom : strokeRgb; }
    public Integer strokeLeft()   { return strokeLeft != null ? strokeLeft : strokeRgb; }
    public boolean hasPerSideStroke() { return strokeTop != null || strokeRight != null || strokeBottom != null || strokeLeft != null; }
    public Integer strokeTopRaw()    { return strokeTop; }
    public Integer strokeRightRaw()  { return strokeRight; }
    public Integer strokeBottomRaw() { return strokeBottom; }
    public Integer strokeLeftRaw()   { return strokeLeft; }
    public Edges borderWidths(){ return borderWidths; }
    public double cornerRadiusPt() { return Math.max(Math.max(cornerTl, cornerTr), Math.max(cornerBr, cornerBl)); }
    public double cornerTl() { return cornerTl; }
    public double cornerTr() { return cornerTr; }
    public double cornerBr() { return cornerBr; }
    public double cornerBl() { return cornerBl; }
    public boolean hasRoundedCorners() { return cornerTl > 0 || cornerTr > 0 || cornerBr > 0 || cornerBl > 0; }
    public BorderStyle borderStyle() { return borderStyle; }
    public Gradient gradient() { return gradient; }
    public double fillAlpha() { return fillAlpha; }
    public PaintedRect fillAlpha(double a) { this.fillAlpha = a; return this; }
    public double frostBlurPt() { return frostBlurPt; }
    public PaintedRect frostBlurPt(double b) { this.frostBlurPt = b; return this; }
    public com.epdfengine.rb.org.css.AnimFrame[] animFrames() { return animFrames; }
    public double animMs() { return animMs; }
    public PaintedRect animFrames(com.epdfengine.rb.org.css.AnimFrame[] frames, double ms) { this.animFrames = frames; this.animMs = ms; return this; }
    public boolean isAnimated() { return animFrames != null && animFrames.length > 1; }

    public boolean hasFill()   { return fillRgb != null || gradient != null; }
    public boolean hasBorder() { return strokeRgb != null && (borderWidths.top() > 0
            || borderWidths.right() > 0 || borderWidths.bottom() > 0 || borderWidths.left() > 0); }
}
