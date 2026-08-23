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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of layout: page dimensions plus an ordered list of drawables. Order
 * is paint order, so a straightforward pass paints backgrounds/borders before the
 * text on top. {@link #insert} lets the engine back-fill a container's background
 * rectangle after its children are laid out (its height is only known then), while
 * keeping that rectangle behind the child content it decorates.
 */
public final class LaidOutDocument {

    private final double pageWidthPt;
    private final double pageHeightPt;
    private final List<Drawable> drawables = new ArrayList<>();
    private int pageCount = 1;
    private List<StructNode> structTree;          // logical-structure registry for tagged PDF (may be null)
    private final java.util.Map<String, double[]> anchors = new java.util.LinkedHashMap<>();  // id -> {page, yTopPt}
    private final List<Bookmark> bookmarks = new ArrayList<>();   // heading outline in document order

    /** One heading captured for the PDF outline: {@code level} 1..6, title, page and top offset. */
    public record Bookmark(int level, String title, int page, double yTopPt) {}

    public LaidOutDocument(double pageWidthPt, double pageHeightPt) {
        this.pageWidthPt = pageWidthPt;
        this.pageHeightPt = pageHeightPt;
    }

    public double pageWidthPt()  { return pageWidthPt; }
    public double pageHeightPt() { return pageHeightPt; }
    public int pageCount()       { return pageCount; }

    public List<StructNode> structTree()          { return structTree; }
    public void setStructTree(List<StructNode> t)  { this.structTree = t; }

    /** Records where an element {@code id} landed, for resolving {@code #id} link destinations. */
    public void recordAnchor(String id, int page, double yTopPt) {
        if (id != null && !id.isBlank()) anchors.putIfAbsent(id, new double[]{ page, yTopPt });
    }

    /** The {@code {page, yTopPt}} an id landed at, or {@code null} if unknown. */
    public double[] anchor(String id) { return id == null ? null : anchors.get(id); }

    /** Records a heading for the PDF outline (bookmarks). */
    public void recordBookmark(int level, String title, int page, double yTopPt) {
        if (title != null && !title.isBlank()) bookmarks.add(new Bookmark(level, title.trim(), page, yTopPt));
    }

    public List<Bookmark> bookmarks() { return bookmarks; }

    public void add(Drawable d) {
        drawables.add(d);
        bump(d);
    }

    /** Inserts at {@code index}; used to place a container background behind its content. */
    public void insert(int index, Drawable d) {
        drawables.add(index, d);
        bump(d);
    }

    /** Current number of drawables — capture this as the insertion index for a container. */
    public int drawableCount() { return drawables.size(); }

    public List<Drawable> drawables() { return Collections.unmodifiableList(drawables); }

    /** The bottom-most content coordinate (points from the page top) across all drawables. */
    public double contentBottomPt() {
        double mb = 0;
        for (Drawable d : drawables) {
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

    /**
     * Returns a single-page copy whose height is trimmed to the content plus
     * {@code marginBottomPt} (capped at the PDF maximum). Used for auto/"long page"
     * (screenshot-style) output where the page grows to fit its content. Drawables
     * are reused unchanged because their coordinates are page-top relative.
     */
    public LaidOutDocument shrinkToContent(double marginBottomPt) {
        double h = contentBottomPt() + Math.max(0, marginBottomPt);
        h = Math.max(1, Math.min(h, 14400.0));
        LaidOutDocument out = new LaidOutDocument(pageWidthPt, h);
        for (Drawable d : drawables) out.add(d);
        out.structTree = this.structTree;
        out.anchors.putAll(this.anchors);
        out.bookmarks.addAll(this.bookmarks);
        return out;
    }

    private void bump(Drawable d) {
        if (d.pageIndex() + 1 > pageCount) pageCount = d.pageIndex() + 1;
    }
}
