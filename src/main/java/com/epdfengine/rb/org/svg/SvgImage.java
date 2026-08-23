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
 * A parsed SVG image: its {@code viewBox} (user-space), an intrinsic display size
 * in points, and the flattened list of {@link SvgPath}s to draw. The renderer maps
 * the viewBox onto the box the SVG occupies.
 */
public final class SvgImage {

    private final double[] viewBox;      // {minX, minY, width, height}
    private final double intrinsicWidthPt;
    private final double intrinsicHeightPt;
    private final List<SvgPath> paths;

    public SvgImage(double[] viewBox, double intrinsicWidthPt, double intrinsicHeightPt, List<SvgPath> paths) {
        this.viewBox = viewBox;
        this.intrinsicWidthPt = intrinsicWidthPt;
        this.intrinsicHeightPt = intrinsicHeightPt;
        this.paths = paths;
    }

    public double viewBoxMinX()   { return viewBox[0]; }
    public double viewBoxMinY()   { return viewBox[1]; }
    public double viewBoxWidth()  { return viewBox[2]; }
    public double viewBoxHeight() { return viewBox[3]; }

    public double intrinsicWidthPt()  { return intrinsicWidthPt; }
    public double intrinsicHeightPt() { return intrinsicHeightPt; }

    public List<SvgPath> paths() { return paths; }

    /** Intrinsic aspect ratio (width/height) from the viewBox. */
    public double aspect() {
        return viewBox[3] == 0 ? 1.0 : viewBox[2] / viewBox[3];
    }
}
