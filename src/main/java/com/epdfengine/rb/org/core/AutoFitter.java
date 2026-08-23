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
package com.epdfengine.rb.org.core;

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.css.FitMode;
import com.epdfengine.rb.org.layout.Drawable;
import com.epdfengine.rb.org.layout.LaidOutDocument;
import com.epdfengine.rb.org.layout.LayoutEngine;
import com.epdfengine.rb.org.layout.LayoutParams;
import com.epdfengine.rb.org.layout.PaintedImage;
import com.epdfengine.rb.org.layout.PaintedRect;
import com.epdfengine.rb.org.layout.PaintedShadow;
import com.epdfengine.rb.org.layout.PaintedSvg;
import com.epdfengine.rb.org.layout.PositionedText;
import com.epdfengine.rb.org.layout.TextMeasurer;
import com.epdfengine.rb.org.layout.box.BlockBox;

/**
 * Fits a design laid out at its natural width onto the target page size by
 * uniformly scaling every drawable — the "render like an image" model. The design
 * is laid out at its natural width, then scaled so its width fills the page.
 *
 * <p>{@link FitMode#FIT_WIDTH} lays the design out on pages whose content height is
 * {@code targetContentHeight / scale}, so each design page scales <em>exactly</em>
 * into one target page — pagination therefore reuses the engine's own page-break
 * logic and never splits a drawable across the boundary. {@link FitMode#FIT_PAGE}
 * lays the whole design out on one tall page and scales both axes so it fits a
 * single page. Structure and proportions are preserved in both modes.
 */
final class AutoFitter {

    private AutoFitter() {}

    static LaidOutDocument fit(BlockBox root, LayoutParams page, FitMode mode,
                               double designWidthPt, TextMeasurer measurer) {
        Edges margins = page.margins();
        double ox = page.contentLeftPt();
        double oy = page.contentTopPt();
        double contentW = page.contentWidthPt();
        double contentH = Math.max(1, page.contentBottomPt() - page.contentTopPt());
        double designW = Math.max(1, designWidthPt);
        double s = contentW / designW;
        if (s <= 0 || Double.isNaN(s)) s = 1;

        if (mode == FitMode.FIT_PAGE) {
            LayoutParams tall = new LayoutParams(designW + margins.horizontal(), 1.0e9, margins);
            LaidOutDocument design = new LayoutEngine().layout(root, tall, measurer);
            double designHeight = Math.max(1, maxBottom(design) - oy);
            double sp = Math.min(s, contentH / designHeight);
            if (sp <= 0 || Double.isNaN(sp)) sp = 1;
            LaidOutDocument out = new LaidOutDocument(page.pageWidthPt(), page.pageHeightPt());
            for (Drawable d : design.drawables()) out.add(scale(d, ox, oy, sp, 0, 0));
            return out;
        }

        // FIT_WIDTH: paginate the design so each design page scales exactly into the
        // target content height, letting the engine's page breaks fall on real gaps.
        double designPageContentH = contentH / s;
        LayoutParams designParams = new LayoutParams(designW + margins.horizontal(),
                designPageContentH + margins.vertical(), margins);
        LaidOutDocument design = new LayoutEngine().layout(root, designParams, measurer);
        LaidOutDocument out = new LaidOutDocument(page.pageWidthPt(), page.pageHeightPt());
        for (Drawable d : design.drawables()) {
            out.add(scale(d, ox, oy, s, d.pageIndex(), 0));
        }
        return out;
    }

    private static Drawable scale(Drawable d, double ox, double oy, double s, int page, double pageShift) {
        if (d instanceof PaintedRect r) {
            Edges b = r.borderWidths();
            Edges nb = Edges.of(scaleBorder(b.top(), s), scaleBorder(b.right(), s),
                    scaleBorder(b.bottom(), s), scaleBorder(b.left(), s));
            double nx = sx(r.x(), ox, s), ny = sy(r.y(), oy, s) - pageShift;
            double nw = r.width() * s, nh = r.height() * s;
            if (r.hasPerSideStroke()) {
                return new PaintedRect(page, nx, ny, nw, nh, r.fillRgb(), r.strokeRgb(), nb,
                        r.cornerTl() * s, r.cornerTr() * s, r.cornerBr() * s, r.cornerBl() * s, r.borderStyle(),
                        r.strokeTopRaw(), r.strokeRightRaw(), r.strokeBottomRaw(), r.strokeLeftRaw(), r.gradient());
            }
            return new PaintedRect(page, nx, ny, nw, nh, r.fillRgb(), r.strokeRgb(), nb,
                    r.cornerTl() * s, r.cornerTr() * s, r.cornerBr() * s, r.cornerBl() * s, r.borderStyle(), r.gradient());
        }
        if (d instanceof PositionedText t) {
            ComputedStyle st = t.style().inheritedCopy();
            st.fontSizePt(Math.max(3.5, t.style().fontSizePt() * s));
            st.letterSpacingPt(t.style().letterSpacingPt() * s);
            return new PositionedText(page, sx(t.xPt(), ox, s), sy(t.baselineYFromTop(), oy, s) - pageShift, t.text(), st);
        }
        if (d instanceof PaintedImage im) {
            return new PaintedImage(page, sx(im.x(), ox, s), sy(im.y(), oy, s) - pageShift,
                    im.width() * s, im.height() * s, im.image());
        }
        if (d instanceof PaintedSvg sv) {
            return new PaintedSvg(page, sx(sv.x(), ox, s), sy(sv.y(), oy, s) - pageShift,
                    sv.width() * s, sv.height() * s, sv.svg(), sv.raster());
        }
        if (d instanceof PaintedShadow sh) {
            return new PaintedShadow(page, sx(sh.x(), ox, s), sy(sh.y(), oy, s) - pageShift,
                    sh.width() * s, sh.height() * s,
                    sh.cornerTl() * s, sh.cornerTr() * s, sh.cornerBr() * s, sh.cornerBl() * s,
                    sh.offsetX() * s, sh.offsetY() * s, sh.blur() * s, sh.spread() * s, sh.rgb(), sh.alpha());
        }
        return d;
    }

    private static double sx(double x, double ox, double s) { return ox + (x - ox) * s; }
    private static double sy(double y, double oy, double s) { return oy + (y - oy) * s; }

    /** Keep hairline borders crisp: don't shrink borders that were already ≤1pt. */
    private static double scaleBorder(double w, double s) {
        if (w <= 0) return 0;
        return (w <= 1.0) ? w : Math.max(w * s, 0.75);
    }

    private static double maxBottom(LaidOutDocument doc) {
        double mb = 0;
        for (Drawable d : doc.drawables()) {
            double bottom;
            if (d instanceof PaintedRect r) bottom = r.y() + r.height();
            else if (d instanceof PositionedText t) bottom = t.baselineYFromTop() + t.style().fontSizePt() * 0.25;
            else if (d instanceof PaintedImage im) bottom = im.y() + im.height();
            else if (d instanceof PaintedSvg sv) bottom = sv.y() + sv.height();
            else if (d instanceof PaintedShadow sh) bottom = sh.y() + sh.height();
            else bottom = 0;
            if (bottom > mb) mb = bottom;
        }
        return mb;
    }
}
