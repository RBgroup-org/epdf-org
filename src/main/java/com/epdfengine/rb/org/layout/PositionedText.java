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

import com.epdfengine.rb.org.css.ComputedStyle;

/**
 * One run of styled text placed on a page. {@code baselineYFromTop} is the text
 * baseline measured from the top of the page (points); the painter flips to PDF's
 * bottom-left origin at emit time. The style carries font/size/colour.
 */
public final class PositionedText implements Drawable {

    private final int page;
    private final double xPt;
    private final double baselineYFromTop;
    private final String text;
    private final ComputedStyle style;

    public PositionedText(int page, double xPt, double baselineYFromTop, String text, ComputedStyle style) {
        this.page = page;
        this.xPt = xPt;
        this.baselineYFromTop = baselineYFromTop;
        this.text = (text != null) ? text : "";
        this.style = (style != null) ? style : new ComputedStyle();
    }

    @Override public int pageIndex() { return page; }

    public double xPt()              { return xPt; }
    public double baselineYFromTop() { return baselineYFromTop; }
    public String text()             { return text; }
    public ComputedStyle style()     { return style; }
}
