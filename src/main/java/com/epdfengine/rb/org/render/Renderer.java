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
package com.epdfengine.rb.org.render;

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.io.font.FontRegistry;
import com.epdfengine.rb.org.io.font.TrueTypeFont;
import com.epdfengine.rb.org.io.image.DecodedImage;
import com.epdfengine.rb.org.css.Gradient;
import com.epdfengine.rb.org.css.BorderStyle;
import com.epdfengine.rb.org.kernel.PdfDocument;
import com.epdfengine.rb.org.kernel.StandardFont;
import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfString;
import com.epdfengine.rb.org.pdfa.Conformance;
import com.epdfengine.rb.org.pdfa.ConformanceApplier;
import com.epdfengine.rb.org.pdfa.DocumentMetadata;
import com.epdfengine.rb.org.tagged.StructTreeBuilder;
import com.epdfengine.rb.org.layout.Drawable;
import com.epdfengine.rb.org.layout.LaidOutDocument;
import com.epdfengine.rb.org.layout.PaintedImage;
import com.epdfengine.rb.org.layout.PaintedRect;
import com.epdfengine.rb.org.layout.PaintedSvg;
import com.epdfengine.rb.org.layout.PaintedShadow;
import com.epdfengine.rb.org.layout.PositionedText;
import com.epdfengine.rb.org.svg.SvgImage;
import com.epdfengine.rb.org.svg.SvgPath;
import com.epdfengine.rb.org.svg.SvgRasterizer;
import com.epdfengine.rb.org.render.content.ContentStreamBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Turns a {@link LaidOutDocument} into PDF pages by walking its drawables in paint
 * order and emitting content-stream operators into a {@link PdfDocument}.
 *
 * <p>Until {@code io.font} provides embedded fonts, text is drawn with the base-14
 * Helvetica family, chosen per run from {@code bold}/{@code italic}. Non-Latin-1
 * characters fall back to '?' (they need an embedded font). Stateless.</p>
 */
public final class Renderer {

    /** Renders using base-14 fonts only (no custom-font embedding). */
    public void render(LaidOutDocument doc, PdfDocument pdf) {
        render(doc, pdf, null);
    }

    /**
     * Renders the laid-out document into {@code pdf}. When a {@link FontRegistry}
     * is supplied, text is drawn with the user's fonts (embedded as Type0/
     * CIDFontType2, Identity-H); otherwise the base-14 Helvetica family is used.
     * Font is chosen per text run from its style family/weight; per-codepoint
     * draw-fallback within a run is a later refinement.
     */
    public void render(LaidOutDocument doc, PdfDocument pdf, FontRegistry fonts) {
        render(doc, pdf, fonts, false);
    }

    /**
     * Renders into {@code pdf}, optionally emitting a tagged logical structure
     * (marked content + {@code /StructTreeRoot}) for PDF/UA when {@code tagged} is true.
     */
    public void render(LaidOutDocument doc, PdfDocument pdf, FontRegistry fonts, boolean tagged) {
        // Base-14 fonts are registered lazily (see needStandard below) so a fully
        // embedded document carries no standard fonts — a PDF/A requirement.
        String regular = null, bold = null, oblique = null, boldOblique = null;

        // Pass 1: collect the glyphs each custom font actually uses, then embed once.
        // Font is chosen PER CODEPOINT (fontForCodepoint) so symbol/CJK glyphs missing from
        // the body font are collected from a covering fallback font registered alongside it.
        Map<TrueTypeFont, java.util.TreeSet<Integer>> usedGids = new java.util.IdentityHashMap<>();
        Map<TrueTypeFont, Map<Integer, Integer>> gidToCp = new java.util.IdentityHashMap<>();
        if (fonts != null && !fonts.isEmpty()) {
            for (Drawable d : doc.drawables()) {
                if (!(d instanceof PositionedText t)) continue;
                ComputedStyle st = t.style();
                String s = t.text();
                int i = 0;
                while (i < s.length()) {
                    int cp = s.codePointAt(i);
                    TrueTypeFont f = fonts.fontForCodepoint(cp, st.fontFamily(), st.bold(), st.italic());
                    if (f != null && f.covers(cp)) {
                        int gid = f.glyphId(cp);
                        usedGids.computeIfAbsent(f, k -> new java.util.TreeSet<>()).add(gid);
                        gidToCp.computeIfAbsent(f, k -> new java.util.HashMap<>()).putIfAbsent(gid, cp);
                    }
                    i += Character.charCount(cp);
                }
            }
        }
        Map<TrueTypeFont, String> embedded = new java.util.IdentityHashMap<>();
        for (Map.Entry<TrueTypeFont, java.util.TreeSet<Integer>> e : usedGids.entrySet()) {
            embedded.put(e.getKey(), FontEmbedder.embed(pdf, e.getKey(), e.getValue(), gidToCp.get(e.getKey())));
        }

        // Register base-14 fonts only if some text run falls back to them (no embedded
        // font resolved). Otherwise the document stays free of standard fonts (PDF/A).
        boolean needStandard = (fonts == null || fonts.isEmpty());
        if (!needStandard) {
            outer:
            for (Drawable d : doc.drawables()) {
                if (!(d instanceof PositionedText t)) continue;
                ComputedStyle st = t.style();
                String s = t.text();
                int i = 0;
                while (i < s.length()) {
                    int cp = s.codePointAt(i);
                    if (drawFontFor(cp, fonts, embedded, st) == null) { needStandard = true; break outer; }
                    i += Character.charCount(cp);
                }
            }
        }
        if (needStandard) {
            regular     = pdf.useStandardFont(StandardFont.HELVETICA);
            bold        = pdf.useStandardFont(StandardFont.HELVETICA_BOLD);
            oblique     = pdf.useStandardFont(StandardFont.HELVETICA_OBLIQUE);
            boldOblique = pdf.useStandardFont(StandardFont.HELVETICA_BOLD_OBLIQUE);
        }

        // Embed each unique image once (before pages so resources are attached).
        Map<DecodedImage, String> imageRes = new java.util.IdentityHashMap<>();
        for (Drawable d : doc.drawables()) {
            if (d instanceof PaintedImage p && !imageRes.containsKey(p.image())) {
                imageRes.put(p.image(), embedImage(pdf, p.image()));
            }
        }

        // Rasterize + embed any raster-mode SVGs (before pages).
        Map<PaintedSvg, String> svgRasterRes = new java.util.IdentityHashMap<>();
        for (Drawable d : doc.drawables()) {
            if (d instanceof PaintedSvg p && p.raster()) {
                DecodedImage di = SvgRasterizer.rasterize(p.svg(), p.width(), p.height());
                svgRasterRes.put(p, embedImage(pdf, di));
            }
        }

        // backdrop-filter: blur() — render the drawables behind each glass panel, Gaussian-blur
        // them, and embed the result to draw (clipped) under the panel's translucent fill.
        Map<PaintedRect, Object[]> frostBackdrop = new java.util.IdentityHashMap<>();
        java.util.List<Drawable> allDraw = doc.drawables();
        for (int i = 0; i < allDraw.size(); i++) {
            if (!(allDraw.get(i) instanceof PaintedRect r) || r.frostBlurPt() <= 0) continue;
            java.util.List<Drawable> back = new java.util.ArrayList<>();
            for (int j = 0; j < i; j++) {
                Drawable d = allDraw.get(j);
                if (d.pageIndex() == r.pageIndex() && intersects(d, r)) back.add(d);
            }
            if (back.isEmpty()) continue;
            double blur = r.frostBlurPt();
            double maxExt = Math.max(r.width(), r.height()) + 3 * blur;
            double scale = Math.max(0.75, Math.min(2.0, 1400.0 / Math.max(1, maxExt)));
            BackdropRasterizer.Result res = BackdropRasterizer.rasterize(r, back, blur, scale);
            if (res == null) continue;
            String name = pdf.addRgbImage(res.rgb, res.width, res.height);
            frostBackdrop.put(r, new Object[]{ name, res.padPt });
        }

        // Pass 2: emit page content.
        int pageCount = doc.pageCount();
        int[] pageObjByIndex = new int[pageCount];
        // Tagging state: the structure-node registry (from the box tree) plus the
        // marked-content ids collected per leaf node. Untagged content gets a synthetic P node.
        java.util.List<com.epdfengine.rb.org.layout.StructNode> nodes =
                (tagged && doc.structTree() != null)
                        ? new java.util.ArrayList<>(doc.structTree())
                        : new java.util.ArrayList<>();
        java.util.Map<Integer, java.util.List<StructTreeBuilder.ContentRef>> contentByLeaf =
                new java.util.HashMap<>();
        // Link annotations: link node id -> href, and link node id -> (page -> union rect in top-down coords).
        java.util.Map<Integer, String> linkHrefByNode = new java.util.HashMap<>();
        java.util.Map<Integer, java.util.Map<Integer, double[]>> linkRectByNodePage = new java.util.HashMap<>();
        int[] syntheticId = { Integer.MAX_VALUE };
        java.util.List<com.epdfengine.rb.org.layout.PositionedFormField> formFields = new java.util.ArrayList<>();
        java.util.List<Object[]> animOcg = new java.util.ArrayList<>();   // {pageIndex, resName, ocgNum, frameIndex}
        int[] animRes = { 0 }, animElem = { 0 };
        double[] animInterval = { 0 };
        for (int page = 0; page < pageCount; page++) {
            ContentStreamBuilder cb = new ContentStreamBuilder(doc.pageHeightPt());
            int[] mcid = { 0 };                          // per-page marked-content id counter
            for (Drawable d : doc.drawables()) {
                if (d.pageIndex() != page) continue;
                if (d instanceof PaintedShadow s) {
                    if (tagged) cb.beginArtifact();
                    paintShadow(cb, s, pdf);
                    if (tagged) cb.endMarkedContent();
                } else if (d instanceof PaintedRect r) {
                    if (tagged) cb.beginArtifact();
                    if (r.isAnimated()) {
                        paintAnimatedRect(cb, r, pdf, doc.pageHeightPt(), page, animElem[0]++, animRes, animOcg, animInterval);
                    } else {
                        Object[] fb = frostBackdrop.get(r);
                        paintRect(cb, r, pdf, doc.pageHeightPt(), true,
                                fb != null ? (String) fb[0] : null, fb != null ? (double) fb[1] : 0);
                    }
                    if (tagged) cb.endMarkedContent();
                } else if (d instanceof PositionedText t) {
                    ComputedStyle st = t.style();
                    double ls = st.letterSpacingPt();
                    boolean useEmbedded = (fonts != null && !fonts.isEmpty());
                    double runW = useEmbedded ? styledRunWidth(fonts, t.text(), st)
                                              : runWidth(null, t.text(), st);
                    if (st.linkHref() != null && !st.linkHref().isBlank() && st.structGroupId() >= 0) {
                        double top = t.baselineYFromTop() - st.fontSizePt() * 0.8;
                        double bot = t.baselineYFromTop() + st.fontSizePt() * 0.25;
                        accumulateLinkRect(linkRectByNodePage, st.structGroupId(), page,
                                t.xPt(), top, t.xPt() + runW, bot);
                        linkHrefByNode.put(st.structGroupId(), st.linkHref());
                    }
                    if (tagged) {
                        String role = (st.structRole() != null) ? st.structRole() : "P";
                        int leaf = leafId(st.structGroupId(), role, nodes, syntheticId);
                        contentByLeaf.computeIfAbsent(leaf, k -> new java.util.ArrayList<>())
                                .add(new StructTreeBuilder.ContentRef(page, mcid[0]));
                        cb.beginMarkedContent(role, mcid[0]++);
                    }
                    if (useEmbedded) {
                        emitStyledText(cb, t, st, fonts, embedded, regular, bold, oblique, boldOblique, ls);
                    } else {
                        paintText(cb, t, regular, bold, oblique, boldOblique, ls);
                    }
                    if (st.textDecoration() != com.epdfengine.rb.org.css.TextDecoration.NONE) {
                        paintTextDecoration(cb, t, st, runW);
                    }
                    if (tagged) cb.endMarkedContent();
                } else if (d instanceof PaintedImage p) {
                    String imgRes = imageRes.get(p.image());
                    if (p.isAnimated()) {
                        paintAnimatedImage(cb, p, pdf, doc.pageHeightPt(), page, animElem[0]++, animRes, animOcg, animInterval, imgRes);
                    } else {
                        if (tagged) {
                            int leaf = leafId(p.structGroupId(), "Figure", nodes, syntheticId);
                            contentByLeaf.computeIfAbsent(leaf, k -> new java.util.ArrayList<>())
                                    .add(new StructTreeBuilder.ContentRef(page, mcid[0]));
                            cb.beginMarkedContent("Figure", mcid[0]++);
                        }
                        cb.drawImage(imgRes, p.x(), p.y(), p.width(), p.height());
                        if (tagged) cb.endMarkedContent();
                    }
                } else if (d instanceof PaintedSvg p) {
                    if (p.isAnimated()) {
                        paintAnimatedSvg(cb, p, pdf, doc.pageHeightPt(), page, animElem[0]++, animRes, animOcg, animInterval, svgRasterRes.get(p));
                    } else {
                        if (tagged) cb.beginArtifact();
                        if (p.raster()) {
                            String name = svgRasterRes.get(p);
                            if (name != null) cb.drawImage(name, p.x(), p.y(), p.width(), p.height());
                        } else {
                            paintVectorSvg(cb, p);
                        }
                        if (tagged) cb.endMarkedContent();
                    }
                } else if (d instanceof com.epdfengine.rb.org.layout.PositionedFormField ff) {
                    formFields.add(ff);   // becomes an AcroForm field after the page loop
                }
            }
            Integer structParents = (tagged && mcid[0] > 0) ? page : null;
            pageObjByIndex[page] = pdf.addPage(doc.pageWidthPt(), doc.pageHeightPt(), cb.toBytes(), structParents);
        }

        // Emit Link annotations (clickable in any PDF; tagged with OBJR + StructParent for PDF/UA).
        java.util.Map<Integer, java.util.List<StructTreeBuilder.LinkAnnot>> linkAnnots = new java.util.HashMap<>();
        int nextParentKey = pageCount;
        for (Map.Entry<Integer, java.util.Map<Integer, double[]>> e : linkRectByNodePage.entrySet()) {
            int leaf = e.getKey();
            String href = linkHrefByNode.get(leaf);
            double[] dest = (href != null && href.startsWith("#")) ? doc.anchor(href.substring(1)) : null;
            for (Map.Entry<Integer, double[]> pe : e.getValue().entrySet()) {
                int pg = pe.getKey();
                int key = tagged ? nextParentKey++ : -1;
                PdfDictionary annot = (dest != null)
                        ? buildGoToAnnot(pdf, doc.pageHeightPt(), pe.getValue(), pageObjByIndex[(int) dest[0]], dest[1], key)
                        : buildLinkAnnot(doc.pageHeightPt(), pe.getValue(), href, key);
                int annotNum = pdf.addAnnotation(pageObjByIndex[pg], annot);
                if (tagged) {
                    linkAnnots.computeIfAbsent(leaf, k -> new java.util.ArrayList<>())
                            .add(new StructTreeBuilder.LinkAnnot(annotNum, pg, key));
                }
            }
        }

        if (tagged && !contentByLeaf.isEmpty()) {
            int structTreeRoot = StructTreeBuilder.build(pdf, nodes, contentByLeaf, linkAnnots,
                    pageObjByIndex, pageCount, nextParentKey);
            pdf.setStructTreeRoot(structTreeRoot);
        }

        // Document outline (bookmarks): nest headings (H1..H6) by level into a /Outlines tree.
        java.util.List<LaidOutDocument.Bookmark> bms = doc.bookmarks();
        if (!bms.isEmpty()) {
            java.util.List<PdfDocument.OutlineItem> roots = new java.util.ArrayList<>();
            java.util.ArrayDeque<Object[]> stack = new java.util.ArrayDeque<>();   // {level, OutlineItem}
            for (LaidOutDocument.Bookmark bm : bms) {
                if (bm.page() < 0 || bm.page() >= pageCount) continue;
                PdfDocument.OutlineItem item = new PdfDocument.OutlineItem(
                        bm.title(), pageObjByIndex[bm.page()], doc.pageHeightPt() - bm.yTopPt());
                while (!stack.isEmpty() && (int) stack.peek()[0] >= bm.level()) stack.pop();
                if (stack.isEmpty()) roots.add(item);
                else ((PdfDocument.OutlineItem) stack.peek()[1]).children.add(item);
                stack.push(new Object[]{ bm.level(), item });
            }
            pdf.setOutline(roots);
        }

        emitFormFields(pdf, doc, pageObjByIndex, formFields);

        // CSS @keyframes background animation: register the per-frame layers + the timer script.
        if (!animOcg.isEmpty()) {
            java.util.List<Integer> onFrame0 = new java.util.ArrayList<>();
            for (Object[] e : animOcg) {
                int pi = (Integer) e[0]; String resName = (String) e[1]; int ocg = (Integer) e[2]; int frame = (Integer) e[3];
                pdf.addPageOcgProperty(pageObjByIndex[pi], resName, ocg);
                if (frame == 0) onFrame0.add(ocg);
            }
            pdf.setOptionalContentConfig(onFrame0);
            pdf.setOpenActionJavaScript(
                    com.epdfengine.rb.org.multimedia.PdfAnimation.bgFrameCycleScript((int) Math.round(animInterval[0])));
        }
    }

    /** Paints an animated background: one OCG-gated frame (colour/opacity/transform) per frame. */
    private static void paintAnimatedRect(ContentStreamBuilder cb, PaintedRect r, PdfDocument pdf, double pageHeightPt,
                                          int pageIndex, int elemId, int[] resSeq,
                                          java.util.List<Object[]> defer, double[] intervalOut) {
        com.epdfengine.rb.org.css.AnimFrame[] frames = r.animFrames();
        int n = frames.length;
        for (int k = 0; k < n; k++) {
            int ocg = pdf.addOptionalContentGroup("epdfanim." + elemId + ".f" + k);
            String resName = "OCA" + (resSeq[0]++);
            defer.add(new Object[]{ pageIndex, resName, ocg, k });
            cb.beginOptionalContent(resName);
            paintAnimFrame(cb, r, pdf, pageHeightPt, frames[k]);
            cb.endMarkedContent();
        }
        double ms = r.animMs() > 0 ? r.animMs() : 2000;
        intervalOut[0] = Math.max(60, ms / n);
    }

    private static void paintAnimFrame(ContentStreamBuilder cb, PaintedRect r, PdfDocument pdf, double pageHeightPt,
                                       com.epdfengine.rb.org.css.AnimFrame af) {
        boolean rounded = r.hasRoundedCorners();
        double x = r.x(), y = r.y(), w = r.width(), h = r.height();
        beginAnimFrameState(cb, pdf, af, x, y, w, h, pageHeightPt, r.fillAlpha());
        int rgb = af.rgb() >= 0 ? af.rgb() : (r.fillRgb() != null ? r.fillRgb() : 0xFFFFFF);
        if (rounded) cb.fillRoundedRect(x, y, w, h, r.cornerTl(), r.cornerTr(), r.cornerBr(), r.cornerBl(), rgb);
        else cb.fillRect(x, y, w, h, rgb);
        Edges bw = r.borderWidths();
        if (r.hasBorder() && bw.top() > 0 && bw.top() == bw.right() && bw.right() == bw.bottom()
                && bw.bottom() == bw.left() && !r.hasPerSideStroke() && r.strokeRgb() != null) {
            if (rounded) cb.strokeRoundedRect(x, y, w, h, r.cornerTl(), r.cornerTr(), r.cornerBr(), r.cornerBl(), bw.top(), r.strokeRgb());
            else cb.strokeRect(x, y, w, h, bw.top(), r.strokeRgb());
        }
        cb.restoreState();
    }

    /** Saves state, applies the frame's opacity and 2D transform (pivoted at the box centre). */
    private static void beginAnimFrameState(ContentStreamBuilder cb, PdfDocument pdf, com.epdfengine.rb.org.css.AnimFrame af,
                                            double x, double y, double w, double h, double pageHeightPt, double extraAlpha) {
        cb.saveState();
        double alpha = af.alpha() * extraAlpha;
        if (alpha < 1.0) cb.setGraphicsState(pdf.gStateForAlpha(alpha));
        if (af.hasTransform()) {
            double px = x + w / 2.0, py = pageHeightPt - (y + h / 2.0);
            double th = Math.toRadians(-af.rotateDeg());              // CSS clockwise -> PDF CCW
            double s = af.scale();
            double a = s * Math.cos(th), b = s * Math.sin(th), c = -s * Math.sin(th), d = s * Math.cos(th);
            double e = px - (a * px + c * py) + af.tx();
            double fp = py - (b * px + d * py) - af.ty();
            cb.transform(a, b, c, d, e, fp);
        }
    }

    /** An animated {@code <img>}: one OCG-gated draw per frame with the frame's opacity/transform. */
    private static void paintAnimatedImage(ContentStreamBuilder cb, PaintedImage p, PdfDocument pdf, double pageHeightPt,
                                           int pageIndex, int elemId, int[] resSeq,
                                           java.util.List<Object[]> defer, double[] intervalOut, String imgRes) {
        com.epdfengine.rb.org.css.AnimFrame[] frames = p.animFrames();
        int n = frames.length;
        for (int k = 0; k < n; k++) {
            int ocg = pdf.addOptionalContentGroup("epdfanim." + elemId + ".f" + k);
            String resName = "OCA" + (resSeq[0]++);
            defer.add(new Object[]{ pageIndex, resName, ocg, k });
            cb.beginOptionalContent(resName);
            beginAnimFrameState(cb, pdf, frames[k], p.x(), p.y(), p.width(), p.height(), pageHeightPt, 1.0);
            if (imgRes != null) cb.drawImage(imgRes, p.x(), p.y(), p.width(), p.height());
            cb.restoreState();
            cb.endMarkedContent();
        }
        double ms = p.animMs() > 0 ? p.animMs() : 2000;
        intervalOut[0] = Math.max(60, ms / n);
    }

    /** An animated {@code <svg>}: one OCG-gated draw per frame with the frame's opacity/transform. */
    private static void paintAnimatedSvg(ContentStreamBuilder cb, PaintedSvg p, PdfDocument pdf, double pageHeightPt,
                                         int pageIndex, int elemId, int[] resSeq,
                                         java.util.List<Object[]> defer, double[] intervalOut, String rasterRes) {
        com.epdfengine.rb.org.css.AnimFrame[] frames = p.animFrames();
        int n = frames.length;
        for (int k = 0; k < n; k++) {
            int ocg = pdf.addOptionalContentGroup("epdfanim." + elemId + ".f" + k);
            String resName = "OCA" + (resSeq[0]++);
            defer.add(new Object[]{ pageIndex, resName, ocg, k });
            cb.beginOptionalContent(resName);
            beginAnimFrameState(cb, pdf, frames[k], p.x(), p.y(), p.width(), p.height(), pageHeightPt, 1.0);
            if (p.raster()) { if (rasterRes != null) cb.drawImage(rasterRes, p.x(), p.y(), p.width(), p.height()); }
            else paintVectorSvg(cb, p);
            cb.restoreState();
            cb.endMarkedContent();
        }
        double ms = p.animMs() > 0 ? p.animMs() : 2000;
        intervalOut[0] = Math.max(60, ms / n);
    }

    /** Paints a CSS {@code conic-gradient} as a pure-vector Type-4 Gouraud triangle-fan mesh. */
    private static void paintConic(ContentStreamBuilder cb, PaintedRect r, Gradient g, PdfDocument pdf,
                                   double pageHeightPt, boolean rounded, double tl, double tr, double br, double bl) {
        double x = r.x(), y = r.y(), w = r.width(), h = r.height();
        double cxTop = x + w * g.centerXFrac(), cyTop = y + h * g.centerYFrac();
        double cx = cxTop, cyPdf = pageHeightPt - cyTop;
        double radius = 0;
        for (double[] c : new double[][]{ {x, y}, {x + w, y}, {x, y + h}, {x + w, y + h} }) {
            radius = Math.max(radius, Math.hypot(c[0] - cxTop, c[1] - cyTop));
        }
        radius *= 1.03;
        double startDeg = g.angleDeg();
        java.util.List<Gradient.Stop> st = g.stops();
        double xMin = cx - radius, xMax = cx + radius, yMin = cyPdf - radius, yMax = cyPdf + radius;

        int n = 180;
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream(n * 24);
        for (int i = 0; i < n; i++) {
            double a0 = 360.0 * i / n, a1 = 360.0 * (i + 1) / n, am = (a0 + a1) / 2;
            int[] p0 = perim(cx, cyPdf, radius, a0), p1 = perim(cx, cyPdf, radius, a1);
            meshVertex(data, cx, cyPdf, sampleStops(st, wrap01((am - startDeg) / 360.0)), xMin, xMax, yMin, yMax);
            meshVertex(data, p0[0], p0[1], sampleStops(st, wrap01((a0 - startDeg) / 360.0)), xMin, xMax, yMin, yMax);
            meshVertex(data, p1[0], p1[1], sampleStops(st, wrap01((a1 - startDeg) / 360.0)), xMin, xMax, yMin, yMax);
        }
        String sh = pdf.addMeshShading(xMin, xMax, yMin, yMax, data.toByteArray());
        cb.saveState();
        if (rounded) cb.clipRoundedRect(x, y, w, h, tl, tr, br, bl);
        else cb.clipRect(x, y, w, h);
        cb.paintShading(sh);
        cb.restoreState();
    }

    private static int[] perim(double cx, double cyPdf, double radius, double deg) {
        double th = Math.toRadians(deg);
        return new int[]{ (int) Math.round(cx + radius * Math.sin(th)),
                          (int) Math.round(cyPdf + radius * Math.cos(th)) };   // 0deg = top, clockwise
    }

    static double wrap01(double t) { t %= 1.0; return t < 0 ? t + 1 : t; }

    private static void meshVertex(java.io.ByteArrayOutputStream out, double xPdf, double yPdf, int rgb,
                                   double xMin, double xMax, double yMin, double yMax) {
        int xi = (int) Math.round((xPdf - xMin) / (xMax - xMin) * 65535);
        int yi = (int) Math.round((yPdf - yMin) / (yMax - yMin) * 65535);
        xi = Math.max(0, Math.min(65535, xi));
        yi = Math.max(0, Math.min(65535, yi));
        out.write(0);                                     // edge flag: start a new triangle
        out.write((xi >> 8) & 0xFF); out.write(xi & 0xFF);
        out.write((yi >> 8) & 0xFF); out.write(yi & 0xFF);
        out.write((rgb >> 16) & 0xFF); out.write((rgb >> 8) & 0xFF); out.write(rgb & 0xFF);
    }

    static int sampleStops(java.util.List<Gradient.Stop> stops, double t) {
        if (stops.isEmpty()) return 0xFFFFFF;
        Gradient.Stop prev = stops.get(0);
        if (t <= prev.position()) return prev.rgb();
        for (int i = 1; i < stops.size(); i++) {
            Gradient.Stop s = stops.get(i);
            if (t <= s.position()) {
                double span = s.position() - prev.position();
                double f = span <= 0 ? 0 : (t - prev.position()) / span;
                return lerpColor(prev.rgb(), s.rgb(), f);
            }
            prev = s;
        }
        return prev.rgb();
    }

    static int lerpColor(int a, int b, double f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * f);
        int gg = (int) Math.round(ag + (bg - ag) * f);
        int bl = (int) Math.round(ab + (bb - ab) * f);
        return (r << 16) | (gg << 8) | bl;
    }

    /** Emits collected HTML form controls as a real AcroForm (styled from CSS). */
    private static void emitFormFields(PdfDocument pdf, LaidOutDocument doc, int[] pageObjByIndex,
                                       java.util.List<com.epdfengine.rb.org.layout.PositionedFormField> fields) {
        if (fields.isEmpty()) return;
        double pageH = doc.pageHeightPt();
        com.epdfengine.rb.org.forms.AcroForm af = com.epdfengine.rb.org.forms.AcroForm.on(pdf);
        java.util.Map<String, java.util.List<com.epdfengine.rb.org.layout.PositionedFormField>> radios =
                new java.util.LinkedHashMap<>();
        for (com.epdfengine.rb.org.layout.PositionedFormField ff : fields) {
            int pageObj = pageObjByIndex[ff.pageIndex()];
            ComputedStyle st = ff.style();
            double y = pageH - (ff.yTop() + ff.height());
            double fs = Math.max(8, st.fontSizePt());
            int border = st.borderColorRgb() != null ? st.borderColorRgb() : 0xD9D9D9;
            int bg = st.backgroundRgb() != null ? st.backgroundRgb() : 0xFFFFFF;
            int text = st.colorRgb();
            double bw = st.border() != null ? Math.max(st.border().top(), Math.max(st.border().right(),
                    Math.max(st.border().bottom(), st.border().left()))) : 1.0;
            double radius = st.borderRadiusPt();
            if (bw == 0 && st.backgroundRgb() == null) { bw = 0.75; border = 0xC7C7C7; }  // keep unstyled fields visible
            int accent = st.accentColorRgb() != null ? st.accentColorRgb() : -1;
            switch (ff.kind()) {
                case TEXT, PASSWORD, TEXTAREA -> {
                    com.epdfengine.rb.org.forms.AcroForm.TextOpts o = new com.epdfengine.rb.org.forms.AcroForm.TextOpts();
                    o.fontSize = fs;
                    o.multiline = ff.kind() == com.epdfengine.rb.org.layout.box.FormFieldBox.Kind.TEXTAREA;
                    o.password = ff.kind() == com.epdfengine.rb.org.layout.box.FormFieldBox.Kind.PASSWORD;
                    o.borderRgb = border; o.bgRgb = bg; o.textRgb = text;
                    o.borderWidth = bw; o.radius = radius;
                    o.placeholder = ff.placeholder(); o.maxLen = ff.maxLength();
                    o.readOnly = ff.readOnly(); o.required = ff.required();
                    o.comb = ff.comb(); o.pattern = ff.pattern();
                    o.min = ff.min(); o.max = ff.max(); o.step = ff.step();
                    af.textField(pageObj, ff.fieldName(), ff.x(), y, ff.width(), ff.height(), ff.value(), o);
                }
                case CHECKBOX -> af.checkBox(pageObj, ff.fieldName(), ff.x(), y,
                        Math.min(ff.width(), ff.height()), ff.checked(), accent, border);
                case CHOICE -> af.choice(pageObj, ff.fieldName(), ff.x(), y, ff.width(), ff.height(),
                        ff.options(), ff.value(), !ff.multiSelect(), border, bg, text, bw, radius,
                        ff.placeholder(), ff.readOnly(), ff.required(), ff.multiSelect(), ff.editable());
                case BUTTON -> af.pushButton(pageObj, ff.fieldName(), ff.x(), y, ff.width(), ff.height(),
                        ff.value(), bg, border, text, bw, radius);
                case RADIO -> radios.computeIfAbsent(ff.fieldName(), k -> new java.util.ArrayList<>()).add(ff);
            }
        }
        for (Map.Entry<String, java.util.List<com.epdfengine.rb.org.layout.PositionedFormField>> e : radios.entrySet()) {
            java.util.List<com.epdfengine.rb.org.layout.PositionedFormField> g = e.getValue();
            String[] opts = new String[g.size()];
            double[][] rects = new double[g.size()][];
            int selected = -1;
            int pageObj = pageObjByIndex[g.get(0).pageIndex()];
            for (int i = 0; i < g.size(); i++) {
                com.epdfengine.rb.org.layout.PositionedFormField ff = g.get(i);
                opts[i] = ff.value() == null || ff.value().isBlank() ? "opt" + i : ff.value();
                double y = pageH - (ff.yTop() + ff.height());
                rects[i] = new double[]{ ff.x(), y, Math.min(ff.width(), ff.height()) };
                if (ff.checked()) selected = i;
            }
            ComputedStyle rst = g.get(0).style();
            int racc = rst.accentColorRgb() != null ? rst.accentColorRgb() : -1;
            af.radioGroup(pageObj, e.getKey(), opts, rects, selected, racc);
        }
        af.write();
    }

    private static void accumulateLinkRect(java.util.Map<Integer, java.util.Map<Integer, double[]>> map,
                                           int nodeId, int page, double x0, double top, double x1, double bot) {
        java.util.Map<Integer, double[]> byPage = map.computeIfAbsent(nodeId, k -> new java.util.HashMap<>());
        double[] r = byPage.get(page);
        if (r == null) {
            byPage.put(page, new double[]{x0, top, x1, bot});
        } else {
            r[0] = Math.min(r[0], x0); r[1] = Math.min(r[1], top);
            r[2] = Math.max(r[2], x1); r[3] = Math.max(r[3], bot);
        }
    }

    /** Builds a URI Link annotation with a top-down rect converted to PDF coordinates. */
    private static PdfDictionary buildLinkAnnot(double pageH, double[] r, String href, int structParentKey) {
        double x0 = r[0], topTop = r[1], x1 = r[2], topBot = r[3];
        PdfArray rect = new PdfArray();
        rect.addNumber(x0).addNumber(pageH - topBot).addNumber(x1).addNumber(pageH - topTop);
        PdfArray border = new PdfArray();
        border.addNumber(0L).addNumber(0L).addNumber(0L);
        PdfDictionary action = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("Action"))
                .put(PdfName.of("S"), PdfName.of("URI"))
                .put(PdfName.of("URI"), PdfString.ofAscii(href != null ? href : ""));
        PdfDictionary annot = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("Annot"))
                .put(PdfName.SUBTYPE, PdfName.of("Link"))
                .put(PdfName.of("Rect"), rect)
                .put(PdfName.of("Border"), border)
                .put(PdfName.of("A"), action)
                .put(PdfName.of("F"), PdfNumber.of(4));   // Print flag (required by PDF/A)
        if (structParentKey >= 0) annot.put(PdfName.of("StructParent"), PdfNumber.of(structParentKey));
        return annot;
    }

    /** Builds an internal GoTo Link annotation whose destination is another page position. */
    private static PdfDictionary buildGoToAnnot(PdfDocument pdf, double pageH, double[] r,
                                                int targetPageObj, double targetYTop, int structParentKey) {
        double x0 = r[0], topTop = r[1], x1 = r[2], topBot = r[3];
        PdfArray rect = new PdfArray();
        rect.addNumber(x0).addNumber(pageH - topBot).addNumber(x1).addNumber(pageH - topTop);
        PdfArray border = new PdfArray();
        border.addNumber(0L).addNumber(0L).addNumber(0L);
        PdfArray dest = new PdfArray();
        dest.add(pdf.ref(targetPageObj));
        dest.add(PdfName.of("FitH"));
        dest.addNumber(pageH - Math.max(0, targetYTop));
        PdfDictionary action = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("Action"))
                .put(PdfName.of("S"), PdfName.of("GoTo"))
                .put(PdfName.of("D"), dest);
        PdfDictionary annot = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("Annot"))
                .put(PdfName.SUBTYPE, PdfName.of("Link"))
                .put(PdfName.of("Rect"), rect)
                .put(PdfName.of("Border"), border)
                .put(PdfName.of("A"), action)
                .put(PdfName.of("F"), PdfNumber.of(4));
        if (structParentKey >= 0) annot.put(PdfName.of("StructParent"), PdfNumber.of(structParentKey));
        return annot;
    }

    /** Resolves a leaf structure-node id; synthesizes a top-level node for untagged content. */
    private static int leafId(int styleGroupId, String role,
                              java.util.List<com.epdfengine.rb.org.layout.StructNode> nodes, int[] syntheticId) {
        if (styleGroupId >= 0) return styleGroupId;
        int id = syntheticId[0]--;
        nodes.add(new com.epdfengine.rb.org.layout.StructNode(id, role, -1, null, null));
        return id;
    }

    /** Convenience: render into a fresh document (base-14 only) and return the PDF bytes. */
    public byte[] renderToPdf(LaidOutDocument doc) throws IOException {
        return renderToPdf(doc, null);
    }

    /** Convenience: render into a fresh document with a font registry and return the PDF bytes. */
    public byte[] renderToPdf(LaidOutDocument doc, FontRegistry fonts) throws IOException {
        PdfDocument pdf = PdfDocument.create();
        render(doc, pdf, fonts);
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        pdf.writeTo(out);
        return out.toByteArray();
    }

    /**
     * Builds a document, applies a PDF/A or PDF/UA conformance target, and returns the
     * still-open {@link PdfDocument} so the caller can {@link com.epdfengine.rb.org.pdfa.ConformanceValidator
     * validate} it before writing.
     */
    public PdfDocument buildPdf(LaidOutDocument doc, FontRegistry fonts,
                                Conformance conformance, DocumentMetadata meta) {
        PdfDocument pdf = PdfDocument.create();
        boolean tagged = conformance != null && conformance.isPdfUa();
        render(doc, pdf, fonts, tagged);
        ConformanceApplier.apply(pdf, conformance, meta);
        return pdf;
    }

    /** Render with a conformance target (PDF/A-2b, PDF/UA-1, or both) and return the PDF bytes. */
    public byte[] renderToPdf(LaidOutDocument doc, FontRegistry fonts,
                              Conformance conformance, DocumentMetadata meta) throws IOException {
        PdfDocument pdf = buildPdf(doc, fonts, conformance, meta);
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        pdf.writeTo(out);
        return out.toByteArray();
    }

    /**
     * Streams the rendered PDF straight to {@code out} (base-14 or embedded fonts), without
     * ever holding the whole file in memory — the writer keeps only cross-reference offsets.
     * The caller owns {@code out} and should close it; this method only flushes.
     */
    public void renderTo(LaidOutDocument doc, FontRegistry fonts, OutputStream out) throws IOException {
        PdfDocument pdf = PdfDocument.create();
        render(doc, pdf, fonts);
        writeStreaming(pdf, out);
    }

    /** Streams a conformance-targeted PDF (PDF/A-2b / PDF/UA-1) straight to {@code out}. */
    public void renderTo(LaidOutDocument doc, FontRegistry fonts,
                         Conformance conformance, DocumentMetadata meta, OutputStream out) throws IOException {
        PdfDocument pdf = buildPdf(doc, fonts, conformance, meta);
        writeStreaming(pdf, out);
    }

    private static void writeStreaming(PdfDocument pdf, OutputStream out) throws IOException {
        OutputStream buffered = (out instanceof java.io.BufferedOutputStream)
                ? out : new java.io.BufferedOutputStream(out, 32 * 1024);
        pdf.writeTo(buffered);
        buffered.flush();
    }

    /** The covering, embedded font for {@code cp}, or {@code null} to fall back to base-14. */
    private static TrueTypeFont drawFontFor(int cp, FontRegistry fonts,
            Map<TrueTypeFont, String> embedded, ComputedStyle st) {
        TrueTypeFont f = fonts.fontForCodepoint(cp, st.fontFamily(), st.bold(), st.italic());
        if (f != null && f.covers(cp) && embedded.get(f) != null) return f;
        return null;
    }

    /** Run width using the same per-codepoint font selection the layout measurer uses. */
    private static double styledRunWidth(FontRegistry fonts, String s, ComputedStyle st) {
        double size = st.fontSizePt(), ls = st.letterSpacingPt(), sum = 0;
        int i = 0, n = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            TrueTypeFont f = fonts.fontForCodepoint(cp, st.fontFamily(), st.bold(), st.italic());
            sum += (f != null) ? f.advancePt(cp, size) : size * 0.5;
            i += Character.charCount(cp);
            n++;
        }
        if (ls != 0) sum += ls * n;
        return sum;
    }

    /**
     * Draws a text run splitting it into sub-runs by covering font: consecutive codepoints
     * sharing one embedded font are shown together via {@code Tj} glyph ids; codepoints no
     * registered font covers fall back to the base-14 face. The pen advances by the same
     * per-codepoint advance the measurer used, so sub-run x-positions stay aligned.
     */
    private static void emitStyledText(ContentStreamBuilder cb, PositionedText t, ComputedStyle st,
            FontRegistry fonts, Map<TrueTypeFont, String> embedded,
            String regular, String bold, String oblique, String boldOblique, double ls) {
        String s = t.text();
        double size = st.fontSizePt();
        double baseX = t.xPt(), baseline = t.baselineYFromTop();
        int color = st.colorRgb();
        String stdFont = st.bold() ? (st.italic() ? boldOblique : bold)
                                   : (st.italic() ? oblique : regular);
        int i = 0;
        double dx = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            TrueTypeFont df = drawFontFor(cp, fonts, embedded, st);
            double groupStart = dx;
            if (df != null) {
                java.util.List<Integer> gids = new java.util.ArrayList<>();
                while (i < s.length()) {
                    int c2 = s.codePointAt(i);
                    if (drawFontFor(c2, fonts, embedded, st) != df) break;
                    gids.add(df.glyphId(c2));
                    dx += df.advancePt(c2, size) + ls;
                    i += Character.charCount(c2);
                }
                int[] g = new int[gids.size()];
                for (int k = 0; k < g.length; k++) g[k] = gids.get(k);
                cb.glyphs(embedded.get(df), size, baseX + groupStart, baseline, g, color, ls);
            } else {
                StringBuilder run = new StringBuilder();
                while (i < s.length()) {
                    int c2 = s.codePointAt(i);
                    if (drawFontFor(c2, fonts, embedded, st) != null) break;
                    run.appendCodePoint(c2);
                    TrueTypeFont sf = fonts.fontForCodepoint(c2, st.fontFamily(), st.bold(), st.italic());
                    dx += ((sf != null) ? sf.advancePt(c2, size) : size * 0.5) + ls;
                    i += Character.charCount(c2);
                }
                cb.text(stdFont, size, baseX + groupStart, baseline, run.toString(), color, ls);
            }
        }
    }

    private static String embedImage(PdfDocument pdf, DecodedImage di) {
        return switch (di.kind()) {
            case JPEG -> pdf.addJpegImage(di.data(), di.width(), di.height(), di.components());
            case RGB  -> pdf.addRgbImage(di.data(), di.width(), di.height());
            case RGBA -> pdf.addRgbaImage(di.data(), di.alpha(), di.width(), di.height());
        };
    }

    private static void paintVectorSvg(ContentStreamBuilder cb, PaintedSvg p) {
        SvgImage svg = p.svg();
        double vbW = svg.viewBoxWidth(), vbH = svg.viewBoxHeight();
        if (vbW <= 0 || vbH <= 0) return;
        double sx = p.width() / vbW, sy = p.height() / vbH, avg = (sx + sy) / 2.0;
        double minX = svg.viewBoxMinX(), minY = svg.viewBoxMinY();
        for (SvgPath path : svg.paths()) {
            if (!path.hasFill() && !path.hasStroke()) continue;
            cb.saveState();
            if (path.hasFill()) cb.setFillRgb(path.fillRgb());
            if (path.hasStroke()) { cb.setStrokeRgb(path.strokeRgb()); cb.setLineWidth(path.strokeWidth() * avg); }
            for (SvgPath.Seg s : path.segments()) {
                double[] q = s.pts();
                switch (s.kind()) {
                    case SvgPath.MOVE  -> cb.moveTo(p.x() + (q[0] - minX) * sx, p.y() + (q[1] - minY) * sy);
                    case SvgPath.LINE  -> cb.lineTo(p.x() + (q[0] - minX) * sx, p.y() + (q[1] - minY) * sy);
                    case SvgPath.CUBIC -> cb.curveTo(
                            p.x() + (q[0] - minX) * sx, p.y() + (q[1] - minY) * sy,
                            p.x() + (q[2] - minX) * sx, p.y() + (q[3] - minY) * sy,
                            p.x() + (q[4] - minX) * sx, p.y() + (q[5] - minY) * sy);
                    case SvgPath.CLOSE -> cb.closePath();
                    default -> { }
                }
            }
            if (path.hasFill() && path.hasStroke()) cb.fillAndStroke();
            else if (path.hasFill()) cb.fill();
            else cb.stroke();
            cb.restoreState();
        }
    }

    private static void paintRect(ContentStreamBuilder cb, PaintedRect r, PdfDocument pdf, double pageHeightPt) {
        paintRect(cb, r, pdf, pageHeightPt, true, null, 0);
    }

    private static void paintRect(ContentStreamBuilder cb, PaintedRect r, PdfDocument pdf, double pageHeightPt, boolean paintFill) {
        paintRect(cb, r, pdf, pageHeightPt, paintFill, null, 0);
    }

    private static void paintRect(ContentStreamBuilder cb, PaintedRect r, PdfDocument pdf, double pageHeightPt,
                                  boolean paintFill, String frostRes, double frostPad) {
        boolean rounded = r.hasRoundedCorners();
        double tl = r.cornerTl(), tr = r.cornerTr(), br = r.cornerBr(), bl = r.cornerBl();
        double rx = r.x(), ry = r.y(), rw = r.width(), rh = r.height();
        if (!rounded) {   // snap extent to whole points so thin borders don't shimmer at zoom
            double x2 = Math.round(rx);
            rw = Math.round(rx + rw) - x2;
            rx = x2;
            double y2 = Math.round(ry);
            rh = Math.round(ry + rh) - y2;
            ry = y2;
            if (rw <= 0 && r.width() > 0) rw = 1;    // keep a thin vertical rule from vanishing
            if (rh <= 0 && r.height() > 0) rh = 1;   // keep a thin horizontal rule from vanishing
        }
        Gradient g = r.gradient();
        boolean alpha = r.fillAlpha() < 1.0;
        // Real backdrop blur sits UNDER the panel's translucent fill (which tints it).
        if (paintFill && r.frostBlurPt() > 0 && frostRes != null) {
            double px = r.x(), py = r.y(), pw = r.width(), ph = r.height();
            cb.saveState();
            if (rounded) cb.clipRoundedRect(px, py, pw, ph, tl, tr, br, bl);
            else cb.clipRect(px, py, pw, ph);
            cb.drawImage(frostRes, px - frostPad, py - frostPad, pw + 2 * frostPad, ph + 2 * frostPad);
            cb.restoreState();
        }
        if (paintFill && ((g != null && !g.stops().isEmpty()) || r.fillRgb() != null)) {
            if (alpha) { cb.saveState(); cb.setGraphicsState(pdf.gStateForAlpha(r.fillAlpha())); }
            if (g != null && !g.stops().isEmpty()) {
                paintGradient(cb, r, g, pdf, pageHeightPt, rounded, tl, tr, br, bl);
            } else if (rounded) {
                cb.fillRoundedRect(rx, ry, rw, rh, tl, tr, br, bl, r.fillRgb());
            } else {
                cb.fillRect(rx, ry, rw, rh, r.fillRgb());
            }
            if (alpha) cb.restoreState();
        }
        if (paintFill && r.frostBlurPt() > 0) paintFrostVeil(cb, r, pdf, rx, ry, rw, rh, rounded, tl, tr, br, bl, frostRes != null);
        if (!r.hasBorder()) return;
        BorderStyle bs = r.borderStyle();
        if (bs == BorderStyle.NONE) return;
        Edges bw = r.borderWidths();
        boolean uniform = bw.top() > 0 && bw.top() == bw.right()
                && bw.right() == bw.bottom() && bw.bottom() == bw.left() && !r.hasPerSideStroke();
        if (uniform) {
            double width = bw.top();
            int stroke = r.strokeRgb();
            boolean dash = bs != BorderStyle.SOLID;
            if (dash) {
                cb.saveState();
                if (bs == BorderStyle.DASHED) cb.setDash(3 * width, 2 * width);
                else { cb.setLineCap(1); cb.setDash(0, 2 * width); }   // dotted = round dots
            }
            if (rounded) cb.strokeRoundedRect(rx, ry, rw, rh, tl, tr, br, bl, width, stroke);
            else cb.strokeRect(rx, ry, rw, rh, width, stroke);
            if (dash) cb.restoreState();
        } else if (rounded) {
            // Per-side borders on a rounded box: fill each present side's portion of the
            // CSS border ring (outer rounded rect minus a per-side-inset inner edge with
            // elliptical corners), clipped to its miter wedge. A thick accent side (e.g.
            // border-left) thins smoothly to the thin sides' width around the corner.
            double wl = bw.left(), wt = bw.top(), wr = bw.right(), wb = bw.bottom();
            if (wt > 0 && r.strokeTop() != null)    fillBorderSide(cb, rx, ry, rw, rh, tl, tr, br, bl, wl, wt, wr, wb, 't', r.strokeTop());
            if (wr > 0 && r.strokeRight() != null)  fillBorderSide(cb, rx, ry, rw, rh, tl, tr, br, bl, wl, wt, wr, wb, 'r', r.strokeRight());
            if (wb > 0 && r.strokeBottom() != null) fillBorderSide(cb, rx, ry, rw, rh, tl, tr, br, bl, wl, wt, wr, wb, 'b', r.strokeBottom());
            if (wl > 0 && r.strokeLeft() != null)   fillBorderSide(cb, rx, ry, rw, rh, tl, tr, br, bl, wl, wt, wr, wb, 'l', r.strokeLeft());
        } else {
            // Per-side widths/colours (e.g. an accent border-left): paint each present edge as a strip.
            if (bw.top() > 0    && r.strokeTop() != null)    cb.fillRect(rx, ry, rw, bw.top(), r.strokeTop());
            if (bw.bottom() > 0 && r.strokeBottom() != null) cb.fillRect(rx, ry + rh - bw.bottom(), rw, bw.bottom(), r.strokeBottom());
            if (bw.left() > 0   && r.strokeLeft() != null)   cb.fillRect(rx, ry, bw.left(), rh, r.strokeLeft());
            if (bw.right() > 0  && r.strokeRight() != null)  cb.fillRect(rx + rw - bw.right(), ry, bw.right(), rh, r.strokeRight());
        }
    }

    /**
     * Paints the glass sheen for {@code backdrop-filter: blur()}. When a real blurred backdrop
     * image has already been drawn ({@code hasRealBlur}) this adds only a soft top highlight so
     * the blurred content shows through the panel's translucent fill; otherwise (nothing paintable
     * behind) it first lays a full frost veil. The highlight is a feathered top-to-transparent fade
     * (nested white layers each at a tiny alpha) — never a hard-edged band — so no seam appears.
     */
    private static void paintFrostVeil(ContentStreamBuilder cb, PaintedRect r, PdfDocument pdf,
            double rx, double ry, double rw, double rh, boolean rounded,
            double tl, double tr, double br, double bl, boolean hasRealBlur) {
        if (rw <= 0 || rh <= 0) return;
        double veilBase = Math.min(0.30, 0.12 + r.frostBlurPt() * 0.010);
        if (!hasRealBlur) {
            cb.saveState();
            cb.setGraphicsState(pdf.gStateForAlpha(veilBase));
            if (rounded) cb.fillRoundedRect(rx, ry, rw, rh, tl, tr, br, bl, 0xFFFFFF);
            else         cb.fillRect(rx, ry, rw, rh, 0xFFFFFF);
            cb.restoreState();
        }
        // Feathered top highlight: nested white layers (shared top edge) each at a tiny alpha
        // build a smooth top->transparent fade — no hard band edge, so no seam across the panel.
        double peak = hasRealBlur ? 0.12 : veilBase * 0.6;
        double bandH = rh * 0.6;
        int strips = 18;
        double layer = 1.0 - Math.pow(1.0 - peak, 1.0 / strips);   // per-layer alpha
        cb.saveState();
        if (rounded) cb.clipRoundedRect(rx, ry, rw, rh, tl, tr, br, bl);
        else         cb.clipRect(rx, ry, rw, rh);
        cb.setGraphicsState(pdf.gStateForAlpha(layer));
        for (int i = 1; i <= strips; i++) {
            cb.fillRect(rx, ry, rw, bandH * i / strips, 0xFFFFFF);
        }
        cb.restoreState();
    }

    /** True when {@code d}'s bounds overlap the panel {@code r} (backdrop-blur source test). */
    private static boolean intersects(Drawable d, PaintedRect r) {
        double[] b = boundsOf(d);
        if (b == null) return false;
        return b[0] < r.x() + r.width() && b[0] + b[2] > r.x()
            && b[1] < r.y() + r.height() && b[1] + b[3] > r.y();
    }

    private static double[] boundsOf(Drawable d) {
        if (d instanceof PaintedRect r) return new double[]{ r.x(), r.y(), r.width(), r.height() };
        if (d instanceof PaintedImage p) return new double[]{ p.x(), p.y(), p.width(), p.height() };
        if (d instanceof PaintedSvg s) return new double[]{ s.x(), s.y(), s.width(), s.height() };
        if (d instanceof PositionedText t) {
            double fs = t.style().fontSizePt();
            double w = t.text().length() * fs * 0.6;   // generous estimate for the intersection gate
            return new double[]{ t.xPt(), t.baselineYFromTop() - fs, w, fs * 1.3 };
        }
        return null;
    }

    /** Fills one side's portion of the CSS border ring, clipped to its 45°-mitered corner wedge. */
    private static void fillBorderSide(ContentStreamBuilder cb, double rx, double ry, double rw, double rh,
            double tl, double tr, double br, double bl,
            double wl, double wt, double wr, double wb, char side, int color) {
        double x2 = rx + rw, y2 = ry + rh;
        cb.saveState();
        // A corner's miter extent is the corner radius or the ADJACENT side's width — never the
        // side's own width, so an accent border on one square-cornered side isn't notched at 45°.
        switch (side) {
            case 't' -> { double kl = Math.max(tl, wl), kr = Math.max(tr, wr);
                double xl = Math.max(wt, kl), xr = Math.max(wt, kr);
                cb.clipPolygon(new double[]{ rx, x2, x2 - kr, rx + kl }, new double[]{ ry, ry, ry + xr, ry + xl }); }
            case 'b' -> { double kl = Math.max(bl, wl), kr = Math.max(br, wr);
                double xl = Math.max(wb, kl), xr = Math.max(wb, kr);
                cb.clipPolygon(new double[]{ rx, x2, x2 - kr, rx + kl }, new double[]{ y2, y2, y2 - xr, y2 - xl }); }
            case 'l' -> { double kt = Math.max(tl, wt), kb = Math.max(bl, wb);
                double xt = Math.max(wl, kt), xb = Math.max(wl, kb);
                cb.clipPolygon(new double[]{ rx, rx + xt, rx + xb, rx }, new double[]{ ry, ry + kt, y2 - kb, y2 }); }
            case 'r' -> { double kt = Math.max(tr, wt), kb = Math.max(br, wb);
                double xt = Math.max(wr, kt), xb = Math.max(wr, kb);
                cb.clipPolygon(new double[]{ x2, x2 - xt, x2 - xb, x2 }, new double[]{ ry, ry + kt, y2 - kb, y2 }); }
            default -> { }
        }
        cb.fillBorderRing(rx, ry, rw, rh, tl, tr, br, bl, wl, wt, wr, wb, color);
        cb.restoreState();
    }

    /**
     * Paints a CSS linear-gradient background: registers a Type-2 axial shading spanning
     * the box along the gradient angle, clips to the (optionally rounded) box, and paints.
     * The angle follows CSS convention (0deg = to top, 90deg = to right); the direction
     * vector in PDF (y-up) space is {@code (sin a, cos a)}.
     */
    private static void paintGradient(ContentStreamBuilder cb, PaintedRect r, Gradient g,
                                      PdfDocument pdf, double pageHeightPt,
                                      boolean rounded, double tl, double tr, double br, double bl) {
        double x = r.x(), y = r.y(), w = r.width(), h = r.height();
        if (g.type() == Gradient.Type.CONIC) { paintConic(cb, r, g, pdf, pageHeightPt, rounded, tl, tr, br, bl); return; }
        java.util.List<Gradient.Stop> stops = g.stops();
        double[][] arr = new double[stops.size()][4];
        for (int i = 0; i < stops.size(); i++) {
            Gradient.Stop s = stops.get(i);
            int rgb = s.rgb();
            arr[i][0] = s.position();
            arr[i][1] = ((rgb >> 16) & 0xFF) / 255.0;
            arr[i][2] = ((rgb >> 8) & 0xFF) / 255.0;
            arr[i][3] = (rgb & 0xFF) / 255.0;
        }
        String sh;
        if (g.type() == Gradient.Type.RADIAL) {
            double cxBox = x + w * g.centerXFrac();
            double cyPdf = pageHeightPt - (y + h * g.centerYFrac());
            double radius = 0.75 * Math.max(w, h);                 // ~farthest-side coverage
            sh = pdf.addRadialShading(cxBox, cyPdf, radius, arr);
        } else {
            double a = Math.toRadians(g.angleDeg());
            double dirX = Math.sin(a), dirY = Math.cos(a);
            double cx = x + w / 2.0;
            double cyPdf = pageHeightPt - (y + h / 2.0);
            double len = Math.abs(w * Math.sin(a)) + Math.abs(h * Math.cos(a));
            double halfX = dirX * len / 2.0, halfY = dirY * len / 2.0;
            sh = pdf.addAxialShading(cx - halfX, cyPdf - halfY, cx + halfX, cyPdf + halfY, arr);
        }
        cb.saveState();
        if (rounded) cb.clipRoundedRect(x, y, w, h, tl, tr, br, bl);
        else cb.clipRect(x, y, w, h);
        cb.paintShading(sh);
        cb.restoreState();
    }

    private static void paintText(ContentStreamBuilder cb, PositionedText t,
                                  String regular, String bold, String oblique, String boldOblique, double ls) {
        ComputedStyle s = t.style();
        String font = s.bold()
                ? (s.italic() ? boldOblique : bold)
                : (s.italic() ? oblique : regular);
        cb.text(font, s.fontSizePt(), t.xPt(), t.baselineYFromTop(), t.text(), s.colorRgb(), ls);
    }

    private static double runWidth(TrueTypeFont f, String s, ComputedStyle st) {
        double w = (f != null) ? f.textWidthPt(s, st.fontSizePt()) : s.length() * st.fontSizePt() * 0.5;
        if (st.letterSpacingPt() != 0) w += st.letterSpacingPt() * s.codePointCount(0, s.length());
        return w;
    }

    private static void paintTextDecoration(ContentStreamBuilder cb, PositionedText t, ComputedStyle st, double runW) {
        double fs = st.fontSizePt();
        double thick = Math.max(0.4, fs * 0.06);
        int color = st.colorRgb();
        if (st.textDecoration().hasUnderline()) {
            cb.fillRect(t.xPt(), t.baselineYFromTop() + Math.max(1.0, fs * 0.12), runW, thick, color);
        }
        if (st.textDecoration().hasLineThrough()) {
            cb.fillRect(t.xPt(), t.baselineYFromTop() - fs * 0.30, runW, thick, color);
        }
    }

    /** Paints a drop shadow: true Gaussian blur via a rasterized soft mask, or a sharp alpha fill. */
    private static void paintShadow(ContentStreamBuilder cb, PaintedShadow s, PdfDocument pdf) {
        double x = s.x() + s.offsetX() - s.spread();
        double y = s.y() + s.offsetY() - s.spread();
        double w = s.width() + 2 * s.spread();
        double h = s.height() + 2 * s.spread();
        if (w <= 0 || h <= 0) return;
        double tl = s.cornerTl(), tr = s.cornerTr(), br = s.cornerBr(), bl = s.cornerBl();
        int col = s.rgb();
        double blur = Math.max(0, s.blur());

        if (blur <= 0.5) {   // sharp shadow: a single true-alpha rounded fill
            String gs = pdf.gStateForAlpha(s.alpha());
            cb.saveState();
            cb.setGraphicsState(gs);
            cb.fillRoundedRect(x, y, w, h, tl, tr, br, bl, col);
            cb.restoreState();
            return;
        }

        // Blurred shadow: rasterize the (rounded) box, Gaussian-blur it, embed as a soft-masked image.
        double avgRadius = (tl + tr + br + bl) / 4.0;
        double maxExt = Math.max(w, h) + 3 * blur;
        double scale = Math.max(0.75, Math.min(2.0, 1400.0 / Math.max(1, maxExt)));
        ShadowRasterizer.Result r = ShadowRasterizer.rasterize(w, h, avgRadius, blur, col, s.alpha(), scale);
        String name = pdf.addRgbaImage(r.rgb, r.alpha, r.width, r.height);
        cb.drawImage(name, x - r.padPt, y - r.padPt, w + 2 * r.padPt, h + 2 * r.padPt);
    }
}
