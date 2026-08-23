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
package com.epdfengine.rb.org.svg;

import java.util.List;

/**
 * A resolved SVG path: a flat list of segments (in the SVG viewBox coordinate
 * space, with any element transforms already baked in) plus resolved paint. Shapes
 * such as rect/circle/line/polygon are converted to segments at parse time so the
 * renderer treats everything uniformly.
 */
public final class SvgPath {

    public static final int MOVE = 0, LINE = 1, CUBIC = 2, CLOSE = 3;

    /** One path segment. {@code pts} holds: MOVE/LINE = {x,y}; CUBIC = {x1,y1,x2,y2,x,y}; CLOSE = {}. */
    public record Seg(int kind, double[] pts) {}

    private final List<Seg> segments;
    private final Integer fillRgb;    // null = no fill
    private final Integer strokeRgb;  // null = no stroke
    private final double strokeWidth; // in viewBox units

    public SvgPath(List<Seg> segments, Integer fillRgb, Integer strokeRgb, double strokeWidth) {
        this.segments = segments;
        this.fillRgb = fillRgb;
        this.strokeRgb = strokeRgb;
        this.strokeWidth = strokeWidth;
    }

    public List<Seg> segments() { return segments; }
    public Integer fillRgb()    { return fillRgb; }
    public Integer strokeRgb()  { return strokeRgb; }
    public double strokeWidth() { return strokeWidth; }

    public boolean hasFill()   { return fillRgb != null; }
    public boolean hasStroke() { return strokeRgb != null; }
}
