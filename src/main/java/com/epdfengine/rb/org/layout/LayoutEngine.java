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
import com.epdfengine.rb.org.common.Length;
import com.epdfengine.rb.org.css.AlignItems;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.css.Display;
import com.epdfengine.rb.org.css.FlexDirection;
import com.epdfengine.rb.org.css.Gradient;
import com.epdfengine.rb.org.css.JustifyContent;
import com.epdfengine.rb.org.css.ListStyleType;
import com.epdfengine.rb.org.css.Shadow;
import com.epdfengine.rb.org.css.TextAlign;
import com.epdfengine.rb.org.css.VerticalAlign;
import com.epdfengine.rb.org.css.WhiteSpace;
import com.epdfengine.rb.org.io.image.DecodedImage;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.BreakBox;
import com.epdfengine.rb.org.layout.box.ImageBox;
import com.epdfengine.rb.org.layout.box.ListItemBox;
import com.epdfengine.rb.org.layout.box.SvgBox;
import com.epdfengine.rb.org.layout.box.TableCellBox;
import com.epdfengine.rb.org.layout.box.TextBox;
import com.epdfengine.rb.org.svg.SvgImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Block-and-inline layout pass (a block formatting context).
 *
 * <p>Block-level boxes stack vertically; each resolves its width against the
 * containing block, positions its content inside padding+border, then a
 * background/border {@link PaintedRect} is back-filled behind the content it
 * decorates. Inline/text content is greedily wrapped into lines using the
 * injected {@link TextMeasurer}. When the cursor runs past the page's content
 * bottom, layout advances to a new page (block start and per line).</p>
 *
 * <p>Stateless and reentrant: all mutable state lives in a per-call {@code Cursor},
 * so one instance serves any number of concurrent layouts.</p>
 *
 * <p>Known first-pass limitations (tracked for later milestones): no margin
 * collapsing, no float/positioned layers, rich per-run inline styling is
 * flattened to the block's style, and a container background is only painted when
 * the whole box fits on one page.</p>
 */
public final class LayoutEngine {

    /** Mutable layout position, confined to a single layout call. */
    private static final class Cursor {
        int page;
        double y;
    }

    public LaidOutDocument layout(Box root, LayoutParams params, TextMeasurer measurer) {
        LaidOutDocument doc = new LaidOutDocument(params.pageWidthPt(), params.pageHeightPt());
        Cursor cursor = new Cursor();
        cursor.page = 0;
        cursor.y = params.contentTopPt();
        layoutBlock(root, params.contentLeftPt(), params.contentWidthPt(), params, measurer, doc, cursor);
        return doc;
    }

    private void layoutBlock(Box box, double x, double availWidth,
                             LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        layoutBlock(box, x, availWidth, -1, -1, params, m, doc, cursor);
    }

    private void layoutBlock(Box box, double x, double availWidth, double forcedOuterWidth,
                             LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        layoutBlock(box, x, availWidth, forcedOuterWidth, -1, params, m, doc, cursor);
    }

    private void layoutBlock(Box box, double x, double availWidth, double forcedOuterWidth,
                             double forcedMinContentHeight,
                             LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        if (box instanceof ImageBox img) {
            layoutImage(img, x, availWidth, params, doc, cursor);
            return;
        }
        if (box instanceof SvgBox svgBox) {
            layoutSvg(svgBox, x, availWidth, params, doc, cursor);
            return;
        }
        if (box instanceof com.epdfengine.rb.org.layout.box.FormFieldBox ff) {
            layoutFormField(ff, x, availWidth, params, doc, cursor);
            return;
        }
        if (box.style().display() == Display.TABLE) {
            layoutTable(box, x, availWidth, params, m, doc, cursor);
            return;
        }
        ComputedStyle style = box.style();
        Edges margin = style.margin();
        Edges padding = style.padding();
        Edges border = style.border();

        // Forced page break before this block (page-break-before / break-before: always).
        boolean paged = params.pageHeightPt() < LayoutParams.MAX_PAGE_DIMENSION_PT - 1.0;
        if (paged && style.pageBreakBefore() && cursor.y > params.contentTopPt() + 0.5) {
            newPage(params, cursor);
        }

        double boxWidth = (forcedOuterWidth >= 0)
                ? forcedOuterWidth
                : resolveBoxWidth(style, availWidth, margin, padding, border);
        double contentWidth = Math.max(0, boxWidth - padding.horizontal() - border.horizontal());

        cursor.y += margin.top();
        if (cursor.y > params.contentBottomPt()) newPage(params, cursor);

        double boxLeft = x + margin.left();
        double boxTop = cursor.y;
        int boxPage = cursor.page;
        int insertIndex = doc.drawableCount();
        if (style.anchorId() != null) doc.recordAnchor(style.anchorId(), boxPage, boxTop);
        String hRole = style.structRole();
        if (hRole != null && hRole.length() == 2 && hRole.charAt(0) == 'H'
                && hRole.charAt(1) >= '1' && hRole.charAt(1) <= '6') {
            doc.recordBookmark(hRole.charAt(1) - '0', textOf(box), boxPage, boxTop);
        }

        double contentLeft = boxLeft + border.left() + padding.left();
        cursor.y = boxTop + border.top() + padding.top();

        if (box instanceof ListItemBox li) {
            emitListMarker(li, style, contentLeft, m, doc, cursor);
        }

        layoutChildren(box, style, contentLeft, contentWidth, params, m, doc, cursor);

        // Honour an explicit height (and any stretch-forced minimum) as a minimum content height.
        double minContentH = forcedMinContentHeight;
        if (!style.height().isAuto()) {
            double h = style.height().resolve(style.fontSizePt(), style.fontSizePt(), params.pageHeightPt());
            if (!Double.isNaN(h)) minContentH = Math.max(minContentH, h);
        }
        if (minContentH >= 0) {
            double minBottom = boxTop + border.top() + padding.top() + minContentH;
            if (cursor.y < minBottom) cursor.y = minBottom;
        }

        double boxBottom = cursor.y + padding.bottom() + border.bottom();

        // Background/border: only when the box stayed on one page (multi-page fill is a TODO).
        if (cursor.page == boxPage) {
            Integer fill = style.backgroundRgb();
            Gradient gradient = style.backgroundGradient();
            boolean hasBorder = border.top() > 0 || border.right() > 0
                    || border.bottom() > 0 || border.left() > 0;
            Integer stroke = hasBorder
                    ? (style.borderColorRgb() != null ? style.borderColorRgb() : style.colorRgb())
                    : null;
            if (fill != null || stroke != null || gradient != null) {
                double rTl = style.borderRadiusTlPt(), rTr = style.borderRadiusTrPt();
                double rBr = style.borderRadiusBrPt(), rBl = style.borderRadiusBlPt();
                PaintedRect rect;
                if (hasBorder && style.hasPerSideBorderColors()) {
                    rect = new PaintedRect(boxPage, boxLeft, boxTop, boxWidth, boxBottom - boxTop,
                            fill, stroke, border, rTl, rTr, rBr, rBl, style.borderStyle(),
                            style.borderTopColor(), style.borderRightColor(),
                            style.borderBottomColor(), style.borderLeftColor(), gradient);
                } else {
                    rect = new PaintedRect(boxPage, boxLeft, boxTop, boxWidth, boxBottom - boxTop,
                            fill, stroke, border, rTl, rTr, rBr, rBl, style.borderStyle(), gradient);
                }
                rect.fillAlpha(style.backgroundAlpha());
                if (style.backdropBlurPt() > 0) rect.frostBlurPt(style.backdropBlurPt());
                if (style.animFrames() != null) rect.animFrames(style.animFrames(), style.animMs());
                doc.insert(insertIndex, rect);
            }
            Shadow shadow = style.boxShadow();
            if (shadow != null && !shadow.inset()) {
                doc.insert(insertIndex, new PaintedShadow(
                        boxPage, boxLeft, boxTop, boxWidth, boxBottom - boxTop,
                        style.borderRadiusTlPt(), style.borderRadiusTrPt(),
                        style.borderRadiusBrPt(), style.borderRadiusBlPt(),
                        shadow.offsetXPt(), shadow.offsetYPt(), shadow.blurPt(), shadow.spreadPt(),
                        shadow.rgb(), shadow.alpha()));
            }
        }

        cursor.y = boxBottom + margin.bottom();
    }

    private void layoutChildren(Box box, ComputedStyle parentStyle, double contentLeft, double contentWidth,
                                LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        if (parentStyle.display() == Display.FLEX && !box.children().isEmpty()) {
            layoutFlexChildren(box, parentStyle, contentLeft, contentWidth, params, m, doc, cursor);
            return;
        }
        if (parentStyle.display() == Display.GRID && !box.children().isEmpty()) {
            layoutGridChildren(box, parentStyle, contentLeft, contentWidth, params, m, doc, cursor);
            return;
        }
        if (parentStyle.columnCount() > 1 && box.children().size() >= parentStyle.columnCount()
                && allBlockLevel(box.children())) {
            layoutMultiColumn(box, parentStyle, contentLeft, contentWidth, params, m, doc, cursor);
            return;
        }
        List<Box> kids = box.children();
        if (kids.isEmpty()) {
            if (box instanceof TextBox) {
                layoutInlineContext(List.of(box), box.style(), contentLeft, contentWidth, params, m, doc, cursor);
            }
            return;
        }
        int i = 0;
        while (i < kids.size()) {
            Box child = kids.get(i);
            if (child.isBlockLevel()) {
                layoutBlock(child, contentLeft, contentWidth, params, m, doc, cursor);
                i++;
            } else {
                // Group consecutive inline/text children and flow them as one styled run.
                List<Box> inline = new ArrayList<>();
                while (i < kids.size() && !kids.get(i).isBlockLevel()) {
                    inline.add(kids.get(i));
                    i++;
                }
                layoutInlineContext(inline, parentStyle, contentLeft, contentWidth, params, m, doc, cursor);
            }
        }
    }

    private static boolean allBlockLevel(List<Box> kids) {
        for (Box c : kids) if (!c.isBlockLevel()) return false;
        return true;
    }

    /** Concatenated text of a box subtree (used for heading bookmark titles). */
    private static String textOf(Box box) {
        StringBuilder sb = new StringBuilder();
        collectText(box, sb);
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static void collectText(Box b, StringBuilder sb) {
        if (b instanceof TextBox t) sb.append(t.text());
        else for (Box c : b.children()) collectText(c, sb);
    }

    /**
     * CSS multi-column ({@code column-count}/{@code columns}): balances the block children
     * across N equal-width columns by measured height (column-fill: balance), then lays each
     * column as an independent vertical stack. Sized for compact multi-column blocks; a column
     * whose content overflows the page flows to the next page independently.
     */
    private void layoutMultiColumn(Box box, ComputedStyle ps, double contentLeft, double contentWidth,
                                   LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        List<Box> cols = box.children();
        int n = Math.max(2, ps.columnCount());
        double gap = ps.columnGapPt() > 0 ? ps.columnGapPt() : 11.0;   // CSS default column-gap ~1em
        double colW = (contentWidth - gap * (n - 1)) / n;
        if (colW <= 0) {
            for (Box c : cols) layoutBlock(c, contentLeft, contentWidth, params, m, doc, cursor);
            return;
        }
        double[] h = new double[cols.size()];
        for (int i = 0; i < cols.size(); i++) h[i] = measureBlockOuterHeight(cols.get(i), colW, params, m);
        double target = optimalMaxColumnHeight(h, n);   // min-max balanced column height

        // Fill each column up to the balanced target; force-advance so every column gets an item.
        int[] colOf = new int[cols.size()];
        int k = cols.size();
        int col = 0; double run = 0;
        for (int i = 0; i < k; i++) {
            int emptyRemaining = (n - 1) - col;                          // columns after current still empty
            int itemsAfterThis = k - i - 1;
            boolean mustAdvance = itemsAfterThis < emptyRemaining;       // spread one per remaining column
            boolean overTarget = run > 0 && run + h[i] > target + 0.01;  // balance
            if (col < n - 1 && (mustAdvance || overTarget)) { col++; run = 0; }
            colOf[i] = col;
            run += h[i];
        }

        int startPage = cursor.page; double startY = cursor.y;
        int maxPage = startPage; double maxBottom = startY;
        for (int c = 0; c < n; c++) {
            double colX = contentLeft + c * (colW + gap);
            Cursor cc = new Cursor(); cc.page = startPage; cc.y = startY;
            for (int i = 0; i < cols.size(); i++) {
                if (colOf[i] == c) layoutBlock(cols.get(i), colX, colW, colW, params, m, doc, cc);
            }
            if (cc.page > maxPage || (cc.page == maxPage && cc.y > maxBottom)) { maxPage = cc.page; maxBottom = cc.y; }
        }
        cursor.page = maxPage; cursor.y = maxBottom;
    }

    /** Smallest possible height for the tallest column (binary search over contiguous partitions). */
    private static double optimalMaxColumnHeight(double[] h, int n) {
        double lo = 0, hi = 0;
        for (double v : h) { lo = Math.max(lo, v); hi += v; }
        if (n <= 1 || h.length <= n) return hi;
        for (int it = 0; it < 60 && hi - lo > 0.01; it++) {
            double mid = (lo + hi) / 2;
            if (fitsInColumns(h, n, mid)) hi = mid; else lo = mid;
        }
        return hi;
    }

    /** True when {@code h} splits into at most {@code n} contiguous columns none taller than {@code cap}. */
    private static boolean fitsInColumns(double[] h, int n, double cap) {
        int cols = 1; double run = 0;
        for (double v : h) {
            if (run > 0 && run + v > cap) { cols++; run = 0; if (cols > n) return false; }
            run += v;
        }
        return true;
    }

    /**
     * Lays out a run of inline-level boxes as an inline formatting context that
     * preserves each fragment's own style (bold/italic/colour/size), honours
     * {@code <br>} and the {@code white-space} rule, and wraps at the box width.
     */
    private void layoutInlineContext(List<Box> inlineBoxes, ComputedStyle parentStyle, double left, double width,
                                     LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        List<Frag> frags = new ArrayList<>();
        for (Box b : inlineBoxes) collectFrags(b, frags);
        if (frags.isEmpty()) return;
        List<PWord> words = new ArrayList<>();
        buildWords(frags, words);
        if (words.isEmpty()) return;
        layoutWords(words, parentStyle, left, width, params, m, doc, cursor);
    }

    /** Flattens the inline subtree into ordered fragments, each carrying its own style. */
    private static void collectFrags(Box box, List<Frag> out) {
        if (box instanceof BreakBox) {
            out.add(new Frag(null, box.style(), true, null));
        } else if (box instanceof TextBox t) {
            out.add(new Frag(t.text(), box.style(), false, null));
        } else if (box.style().display() == Display.INLINE_BLOCK) {
            out.add(Frag.atom(box));                 // atomic: painted as a mini-block on the line
        } else {
            for (Box c : box.children()) collectFrags(c, out);
        }
    }

    /** Tokenises fragments into words, resolving whitespace collapsing and forced breaks. */
    private void buildWords(List<Frag> frags, List<PWord> words) {
        boolean pendingSpace = false;
        for (Frag f : frags) {
            if (f.isBreak()) {
                words.add(PWord.hardBreak(f.style()));
                pendingSpace = false;
                continue;
            }
            if (f.atom() != null) {
                words.add(PWord.atom(f.atom(), pendingSpace));
                pendingSpace = false;
                continue;
            }
            WhiteSpace ws = f.style().whiteSpace();
            String text = (f.text() == null) ? "" : f.text();
            if (ws.preservesNewlines()) {
                String[] segs = text.split("\n", -1);
                for (int si = 0; si < segs.length; si++) {
                    if (si > 0) { words.add(PWord.hardBreak(f.style())); pendingSpace = false; }
                    pendingSpace = addSegment(segs[si], ws, f.style(), pendingSpace, words);
                }
            } else {
                pendingSpace = addSegment(text, ws, f.style(), pendingSpace, words);
            }
        }
    }

    /** Appends one text segment's words; returns whether a trailing space is pending. */
    private boolean addSegment(String seg, WhiteSpace ws, ComputedStyle style, boolean pendingSpace, List<PWord> words) {
        if (ws.preservesSpaces()) {
            if (seg.isEmpty()) return pendingSpace;
            words.add(PWord.literal(seg, style, pendingSpace, ws.wraps()));
            return false;
        }
        String collapsed = seg.replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) return pendingSpace;
        boolean lead = collapsed.startsWith(" ");
        boolean trail = collapsed.endsWith(" ");
        String core = collapsed.trim();
        if (core.isEmpty()) return true;                 // segment was all whitespace
        String[] parts = core.split(" ");
        for (int i = 0; i < parts.length; i++) {
            boolean spaceBefore = (i == 0) ? (pendingSpace || lead) : true;
            words.add(PWord.word(parts[i], style, spaceBefore, ws.wraps()));
        }
        return trail;
    }

    /** Greedily fills lines from the word stream, wrapping and honouring forced breaks. */
    private void layoutWords(List<PWord> words, ComputedStyle parentStyle, double left, double width,
                             LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        List<Placed> line = new ArrayList<>();
        double x = 0;
        for (PWord w : words) {
            if (w.hardBreak) {
                emitInlineLine(line, x, left, width, parentStyle, params, m, doc, cursor);
                line.clear();
                x = 0;
                continue;
            }
            if (w.atom != null) {
                ComputedStyle cs = w.atom.style();
                double aw = inlineBlockOuterWidth(w.atom, m, width);
                double ah = measureBlockOuterHeight(w.atom, aw, params, m);
                double ib = atomBaseline(w.atom, ah, m);
                double foot = cs.margin().left() + aw + cs.margin().right();
                double sp = w.spaceBefore ? m.spaceWidthPt(w.style) : 0;
                if (!line.isEmpty() && x + sp + foot > width) {
                    emitInlineLine(line, x, left, width, parentStyle, params, m, doc, cursor);
                    line.clear();
                    x = 0;
                    sp = 0;
                }
                double at = x + sp;
                line.add(new Placed(null, cs, at, sp > 0, w.atom, aw, ah, ib));
                x = at + foot;
                continue;
            }
            double sp = w.spaceBefore ? m.spaceWidthPt(w.style) : 0;
            double ww = m.textWidthPt(w.text, w.style);
            if (!line.isEmpty() && w.wrap && x + sp + ww > width) {
                emitInlineLine(line, x, left, width, parentStyle, params, m, doc, cursor);
                line.clear();
                x = 0;
                sp = 0;
            }
            double at = x + sp;
            line.add(new Placed(w.text, w.style, at, sp > 0, null, 0, 0, 0));
            x = at + ww;
        }
        if (!line.isEmpty()) emitInlineLine(line, x, left, width, parentStyle, params, m, doc, cursor);
    }

    /** Emits one laid-out line: baseline from the tallest run, grouped same-style segments. */
    private void emitInlineLine(List<Placed> line, double lineWidth, double left, double width,
                                ComputedStyle parentStyle, LayoutParams params, TextMeasurer m,
                                LaidOutDocument doc, Cursor cursor) {
        if (line.isEmpty()) {
            double lh = parentStyle.lineHeightPt();
            if (cursor.y + lh > params.contentBottomPt()) newPage(params, cursor);
            cursor.y += lh;
            return;
        }
        double above = 0, below = 0;
        for (Placed p : line) {
            if (p.atom != null) {
                above = Math.max(above, p.atomBaseline);
                below = Math.max(below, p.atomHeight - p.atomBaseline);
            } else {
                double a = m.ascentPt(p.style);
                above = Math.max(above, a);
                below = Math.max(below, Math.max(m.descentPt(p.style), p.style.lineHeightPt() - a));
            }
        }
        double lineHeight = Math.max(parentStyle.lineHeightPt(), above + below);
        if (cursor.y + lineHeight > params.contentBottomPt()) newPage(params, cursor);
        double baselineY = cursor.y + above;
        double alignOff = alignOffset(parentStyle.textAlign(), width, lineWidth);
        int i = 0;
        while (i < line.size()) {
            Placed start = line.get(i);
            double drawX = left + alignOff + start.x;
            if (start.atom != null) {
                Cursor sub = new Cursor();
                sub.page = cursor.page;
                sub.y = baselineY - start.atomBaseline;
                layoutBlock(start.atom, drawX, start.atomWidth, start.atomWidth, params, m, doc, sub);
                i++;
                continue;
            }
            StringBuilder sb = new StringBuilder(start.text);
            int j = i + 1;
            while (j < line.size() && line.get(j).atom == null
                    && line.get(j).style == start.style && line.get(j).spaceBefore) {
                sb.append(' ').append(line.get(j).text);
                j++;
            }
            doc.add(new PositionedText(cursor.page, drawX, baselineY, sb.toString(), start.style));
            i = j;
        }
        cursor.y += lineHeight;
    }

    /**
     * Draws a list item's marker in the indent left of its content, aligned to the
     * item's first line. Disc/circle/square are vector shapes (font-independent);
     * ordinal markers ("3.", "b.", "iv.") are drawn as text.
     */
    private void emitListMarker(ListItemBox li, ComputedStyle style, double contentLeft,
                                TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        ListStyleType type = li.markerType();
        if (type == ListStyleType.NONE) return;
        double em = style.fontSizePt();
        double ascent = m.ascentPt(style);
        double baselineY = cursor.y + ascent;
        double gap = 0.5 * em;
        int color = style.colorRgb();
        if (type.isGlyph()) {
            double s = 0.34 * em;
            double cx = contentLeft - gap - s;
            double top = (baselineY - 0.32 * em) - s / 2;
            switch (type) {
                case SQUARE -> doc.add(new PaintedRect(cursor.page, cx, top, s, s, color, null, Edges.ZERO, 0));
                case CIRCLE -> doc.add(new PaintedRect(cursor.page, cx, top, s, s, null, color, Edges.of(0.7), s / 2));
                default     -> doc.add(new PaintedRect(cursor.page, cx, top, s, s, color, null, Edges.ZERO, s / 2));
            }
        } else {
            String text = li.markerText();
            if (text.isEmpty()) return;
            double w = m.textWidthPt(text, style);
            doc.add(new PositionedText(cursor.page, contentLeft - gap - w, baselineY, text, style));
        }
    }

    /** An ordered inline fragment: text, an inline-block atom, or a forced break. */
    private record Frag(String text, ComputedStyle style, boolean isBreak, Box atom) {
        static Frag atom(Box box) { return new Frag(null, box.style(), false, box); }
    }

    /** A tokenised inline word (or a forced break) with its resolved style. */
    private static final class PWord {
        String text;
        ComputedStyle style;
        boolean spaceBefore;
        boolean wrap;
        boolean hardBreak;
        Box atom;                    // non-null: an inline-block atom

        static PWord word(String text, ComputedStyle style, boolean spaceBefore, boolean wrap) {
            PWord w = new PWord();
            w.text = text; w.style = style; w.spaceBefore = spaceBefore; w.wrap = wrap;
            return w;
        }
        static PWord literal(String text, ComputedStyle style, boolean spaceBefore, boolean wrap) {
            return word(text, style, spaceBefore, wrap);
        }
        static PWord atom(Box atom, boolean spaceBefore) {
            PWord w = new PWord();
            w.atom = atom; w.style = atom.style(); w.spaceBefore = spaceBefore; w.wrap = true;
            return w;
        }
        static PWord hardBreak(ComputedStyle style) {
            PWord w = new PWord();
            w.style = style; w.hardBreak = true;
            return w;
        }
    }

    /** A word (or an inline-block atom) placed on the current line at an x offset from content-left. */
    private record Placed(String text, ComputedStyle style, double x, boolean spaceBefore,
                          Box atom, double atomWidth, double atomHeight, double atomBaseline) {}

    private static double alignOffset(TextAlign align, double width, double lineWidth) {
        double slack = Math.max(0, width - lineWidth);
        return switch (align) {
            case CENTER -> slack / 2.0;
            case RIGHT, END -> slack;
            default -> 0.0; // LEFT/START/JUSTIFY (justify spacing is a later refinement)
        };
    }

    /** Shrink-to-fit outer (border-box) width of an inline-block, clamped to min/max-width. */
    private double inlineBlockOuterWidth(Box box, TextMeasurer m, double avail) {
        ComputedStyle cs = box.style();
        double chrome = cs.padding().horizontal() + cs.border().horizontal();
        double w;
        if (!cs.width().isAuto()) {
            double ww = cs.width().resolve(cs.fontSizePt(), cs.fontSizePt(), avail);
            w = Double.isNaN(ww) ? shrinkToFit(box, m, chrome, avail)
                                 : ww + (cs.isBorderBoxSizing() ? 0 : chrome);
        } else {
            w = shrinkToFit(box, m, chrome, avail);
        }
        double max = explicitWidth(cs.maxWidth(), cs, avail, Double.MAX_VALUE);
        double min = explicitWidth(cs.minWidth(), cs, avail, 0);
        return Math.max(0, Math.max(min, Math.min(w, max)));
    }

    /** CSS §10.3.5 shrink-to-fit: {@code min(max(min-content, available), max-content)} as outer widths. */
    private double shrinkToFit(Box box, TextMeasurer m, double chrome, double avail) {
        double maxC = prefContentWidth(box, m) + chrome;
        double minC = minContentWidth(box, m) + chrome;
        return Math.min(Math.max(minC, avail), maxC);
    }

    /** Baseline offset from the atom's margin-box top = its first text line's baseline, else full height. */
    private double atomBaseline(Box box, double outerH, TextMeasurer m) {
        ComputedStyle cs = box.style();
        double topChrome = cs.margin().top() + cs.border().top() + cs.padding().top();
        double innerAscent = firstTextAscent(box, m);
        return innerAscent > 0 ? Math.min(outerH, topChrome + innerAscent) : outerH;
    }

    private double firstTextAscent(Box box, TextMeasurer m) {
        if (box instanceof TextBox t) {
            String s = (t.text() == null) ? "" : t.text().trim();
            return s.isEmpty() ? 0 : m.ascentPt(t.style());
        }
        for (Box c : box.children()) {
            double a = firstTextAscent(c, m);
            if (a > 0) return a;
        }
        return 0;
    }

    private void layoutImage(ImageBox img, double x, double availWidth,
                             LayoutParams params, LaidOutDocument doc, Cursor cursor) {
        ComputedStyle style = img.style();
        Edges margin = style.margin();
        DecodedImage di = img.image();
        double intrinsicW = di.width() * 0.75;   // CSS px -> pt (96dpi)
        double intrinsicH = di.height() * 0.75;
        double w, h;
        if (!style.width().isAuto()) {
            w = resolveLen(style.width(), style, availWidth);
            h = !style.height().isAuto() ? resolveLen(style.height(), style, params.pageHeightPt())
                    : (di.aspect() > 0 ? w / di.aspect() : intrinsicH);
        } else if (!style.height().isAuto()) {
            h = resolveLen(style.height(), style, params.pageHeightPt());
            w = h * di.aspect();
        } else {
            w = Math.min(intrinsicW, availWidth - margin.horizontal());
            h = di.aspect() > 0 ? w / di.aspect() : intrinsicH;
        }
        cursor.y += margin.top();
        double pageContentHeight = params.contentBottomPt() - params.contentTopPt();
        if (cursor.y + h > params.contentBottomPt() && h <= pageContentHeight) newPage(params, cursor);
        doc.add(new PaintedImage(cursor.page, x + margin.left(), cursor.y, w, h, di)
                .tag(style.structRole(), style.structGroupId(), style.structAlt())
                .animFrames(style.animFrames(), style.animMs()));
        cursor.y += h + margin.bottom();
    }

    private static double resolveLen(Length len, ComputedStyle style, double basis) {
        double v = len.resolve(style.fontSizePt(), style.fontSizePt(), basis);
        return Double.isNaN(v) ? basis : v;
    }

    private void layoutFormField(com.epdfengine.rb.org.layout.box.FormFieldBox f, double x, double availWidth,
                                 LayoutParams params, LaidOutDocument doc, Cursor cursor) {
        ComputedStyle style = f.style();
        Edges margin = style.margin();
        var kind = f.kind();
        boolean small = kind == com.epdfengine.rb.org.layout.box.FormFieldBox.Kind.CHECKBOX
                || kind == com.epdfengine.rb.org.layout.box.FormFieldBox.Kind.RADIO;
        double defW = small ? 13 : (kind == com.epdfengine.rb.org.layout.box.FormFieldBox.Kind.BUTTON ? 90 : 200);
        double defH = small ? 13
                : kind == com.epdfengine.rb.org.layout.box.FormFieldBox.Kind.TEXTAREA ? 56
                : Math.max(16, style.fontSizePt() + 8);
        double w = !style.width().isAuto() ? resolveLen(style.width(), style, availWidth) : defW;
        double h = !style.height().isAuto() ? resolveLen(style.height(), style, params.pageHeightPt()) : defH;
        cursor.y += margin.top();
        double pageContentHeight = params.contentBottomPt() - params.contentTopPt();
        if (cursor.y + h > params.contentBottomPt() && h <= pageContentHeight) newPage(params, cursor);
        doc.add(new com.epdfengine.rb.org.layout.PositionedFormField(cursor.page, x + margin.left(), cursor.y, w, h,
                kind, f.fieldName(), f.value(), f.options(), f.checked(), style)
                .modern(f.placeholder(), f.maxLength(), f.readOnly(), f.required(), f.multiSelect())
                .advanced(f.min(), f.max(), f.step(), f.pattern(), f.comb(), f.editable()));
        cursor.y += h + margin.bottom();
    }

    private void layoutSvg(SvgBox box, double x, double availWidth,
                           LayoutParams params, LaidOutDocument doc, Cursor cursor) {
        ComputedStyle style = box.style();
        Edges margin = style.margin();
        SvgImage svg = box.svg();
        double aspect = svg.aspect();
        double intrinsicW = svg.intrinsicWidthPt() > 0 ? svg.intrinsicWidthPt() : svg.viewBoxWidth() * 0.75;
        double intrinsicH = svg.intrinsicHeightPt() > 0 ? svg.intrinsicHeightPt() : svg.viewBoxHeight() * 0.75;
        double w, h;
        if (!style.width().isAuto()) {
            w = resolveLen(style.width(), style, availWidth);
            h = !style.height().isAuto() ? resolveLen(style.height(), style, params.pageHeightPt())
                    : (aspect > 0 ? w / aspect : intrinsicH);
        } else if (!style.height().isAuto()) {
            h = resolveLen(style.height(), style, params.pageHeightPt());
            w = h * aspect;
        } else {
            w = Math.min(intrinsicW, availWidth - margin.horizontal());
            h = aspect > 0 ? w / aspect : intrinsicH;
        }
        cursor.y += margin.top();
        double pageContentHeight = params.contentBottomPt() - params.contentTopPt();
        if (cursor.y + h > params.contentBottomPt() && h <= pageContentHeight) newPage(params, cursor);
        doc.add(new PaintedSvg(cursor.page, x + margin.left(), cursor.y, w, h, svg, box.raster())
                .animFrames(style.animFrames(), style.animMs()));
        cursor.y += h + margin.bottom();
    }

    // --- tables (table / table-row-group / table-row / table-cell) ---

    /**
     * Lays out a CSS table: resolves column widths, then lays each row with cells
     * placed at their column offsets, row height equalised to the tallest cell,
     * per-cell {@code vertical-align}, row/cell backgrounds and borders, and a
     * whole-row page break when a row would not fit the remaining page.
     */
    private void layoutTable(Box table, double x, double availWidth,
                             LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        ComputedStyle ts = table.style();
        Edges margin = ts.margin(), padding = ts.padding(), border = ts.border();

        double tableOuterW = resolveBoxWidth(ts, availWidth, margin, padding, border);
        double innerW = Math.max(0, tableOuterW - padding.horizontal() - border.horizontal());

        cursor.y += margin.top();
        if (cursor.y > params.contentBottomPt()) newPage(params, cursor);
        double tableLeft = x + margin.left();
        double tableTop = cursor.y;
        int tablePage = cursor.page;
        int tableInsertIndex = doc.drawableCount();

        List<Box> rows = new ArrayList<>();
        collectRows(table, rows);
        if (rows.isEmpty()) { cursor.y = tableTop + margin.bottom(); return; }
        List<Box> headerRows = new ArrayList<>();
        collectHeaderRows(table, headerRows);

        int cols = 0;
        for (Box row : rows) {
            int c = 0;
            for (Box cell : cellsOf(row)) c += spanOf(cell);
            cols = Math.max(cols, c);
        }
        if (cols == 0) { cursor.y = tableTop + margin.bottom(); return; }

        boolean collapse = ts.borderCollapse();
        double spacing = collapse ? 0 : ts.borderSpacingPt();
        double[] colW = resolveColumnWidths(rows, cols, innerW, spacing);

        double contentLeft = tableLeft + border.left() + padding.left();
        cursor.y = tableTop + border.top() + padding.top() + spacing;

        List<PaintedRect> tableBorders = new ArrayList<>();
        for (int ri = 0; ri < rows.size(); ri++) {
            Box row = rows.get(ri);
            Box nextRow = (ri + 1 < rows.size()) ? rows.get(ri + 1) : null;
            // Repeat <thead> rows atop each new page for body rows that page-break (not for headers themselves).
            List<Box> repeat = (headerRows.isEmpty() || headerRows.contains(row)) ? null : headerRows;
            layoutTableRow(row, nextRow, ts, collapse, ri == 0, ri == rows.size() - 1,
                    contentLeft, colW, spacing, params, m, doc, tableBorders, cursor, repeat);
        }
        for (PaintedRect e : tableBorders) doc.add(e);   // grid lines on top of all row/cell backgrounds

        cursor.y += padding.bottom() + border.bottom();
        double tableBottom = cursor.y;

        if (cursor.page == tablePage) {
            // In collapse mode the cell edges form the frame; only paint the table's fill/gradient.
            PaintedRect dec = collapse
                    ? fillOnlyRect(tablePage, tableLeft, tableTop, tableOuterW, tableBottom - tableTop, ts)
                    : boxRect(tablePage, tableLeft, tableTop, tableOuterW, tableBottom - tableTop, ts);
            if (dec != null) doc.insert(tableInsertIndex, dec);
        }
        cursor.y = tableBottom + margin.bottom();
    }

    private void layoutTableRow(Box row, Box nextRow, ComputedStyle ts, boolean collapse, boolean firstRow, boolean lastRow,
                                double contentLeft, double[] colW, double spacing,
                                LayoutParams params, TextMeasurer m, LaidOutDocument doc,
                                List<PaintedRect> borderOut, Cursor cursor, List<Box> headerRepeat) {
        List<Box> cells = cellsOf(row);
        if (cells.isEmpty()) return;
        int n = cells.size();
        List<Box> nextCells = (nextRow != null) ? cellsOf(nextRow) : null;

        double[] cellX = new double[n];
        double[] cellW = new double[n];
        int col = 0;
        double xrun = contentLeft + spacing;
        for (int i = 0; i < n; i++) {
            int span = spanOf(cells.get(i));
            double w = 0;
            for (int s = 0; s < span && col + s < colW.length; s++) w += colW[col + s];
            w += (span - 1) * spacing;
            cellX[i] = xrun;
            cellW[i] = w;
            xrun += w + spacing;
            col += span;
        }

        double rowH = 0;
        double[] contentH = new double[n];
        for (int i = 0; i < n; i++) {
            ComputedStyle cs = cells.get(i).style();
            Edges cpad = cs.padding(), cbor = cs.border();
            double innerCellW = Math.max(0, cellW[i] - cpad.horizontal() - cbor.horizontal());
            contentH[i] = measureContentHeight(cells.get(i), innerCellW, params, m);
            rowH = Math.max(rowH, contentH[i] + cpad.vertical() + cbor.vertical());
        }
        double rowMinH = explicitLen(row.style().height(), row.style(), params.pageHeightPt());
        if (rowMinH > rowH) rowH = rowMinH;

        double pageContentH = params.contentBottomPt() - params.contentTopPt();
        if (cursor.y + rowH > params.contentBottomPt() && rowH <= pageContentH) {
            newPage(params, cursor);
            if (headerRepeat != null && !headerRepeat.isEmpty()) {
                for (Box hr : headerRepeat) {
                    layoutTableRow(hr, null, ts, collapse, true, false,
                            contentLeft, colW, spacing, params, m, doc, borderOut, cursor, null);
                }
            }
        }

        double rowTop = cursor.y;
        int rowPage = cursor.page;
        int rowInsertIndex = doc.drawableCount();

        for (int i = 0; i < n; i++) {
            Box cell = cells.get(i);
            ComputedStyle cs = cell.style();
            Edges cpad = cs.padding(), cbor = cs.border();
            if (collapse) {
                if (cs.backgroundRgb() != null || cs.backgroundGradient() != null) {
                    doc.add(new PaintedRect(rowPage, cellX[i], rowTop, cellW[i], rowH,
                            cs.backgroundRgb(), null, Edges.ZERO, 0, cs.backgroundGradient()));
                }
                Edges tbor = ts.border();
                Box left = (i > 0) ? cells.get(i - 1) : null;
                Box below = (nextCells != null && i < nextCells.size()) ? nextCells.get(i) : null;
                // Each grid line drawn ONCE, resolved against BOTH adjacent cells (wider wins) and the frame.
                if (firstRow) {
                    drawCollapseEdge(borderOut, rowPage, cellX[i], rowTop, cellW[i], true,
                            cbor.top(), cs.borderTopColor(), tbor.top(), ts.borderTopColor());
                }
                double lNbrW = (left != null) ? left.style().border().right() : tbor.left();
                int lNbrC = (left != null) ? left.style().borderRightColor() : ts.borderLeftColor();
                drawCollapseEdge(borderOut, rowPage, cellX[i], rowTop, rowH, false,
                        cbor.left(), cs.borderLeftColor(), lNbrW, lNbrC);
                double bNbrW; int bNbrC;
                if (below != null)     { bNbrW = below.style().border().top(); bNbrC = below.style().borderTopColor(); }
                else if (lastRow)      { bNbrW = tbor.bottom(); bNbrC = ts.borderBottomColor(); }
                else                   { bNbrW = 0; bNbrC = cs.borderBottomColor(); }
                drawCollapseEdge(borderOut, rowPage, cellX[i], rowTop + rowH, cellW[i], true,
                        cbor.bottom(), cs.borderBottomColor(), bNbrW, bNbrC);
                if (i == n - 1) {
                    drawCollapseEdge(borderOut, rowPage, cellX[i] + cellW[i], rowTop, rowH, false,
                            cbor.right(), cs.borderRightColor(), tbor.right(), ts.borderRightColor());
                }
            } else {
                PaintedRect dec = boxRect(rowPage, cellX[i], rowTop, cellW[i], rowH, cs);
                if (dec != null) doc.add(dec);
            }

            double innerCellW = Math.max(0, cellW[i] - cpad.horizontal() - cbor.horizontal());
            double availC = rowH - cpad.vertical() - cbor.vertical();
            double free = Math.max(0, availC - contentH[i]);
            double vaOff = switch (cs.verticalAlign()) {
                case MIDDLE -> free / 2;
                case BOTTOM -> free;
                default -> 0;
            };
            Cursor cc = new Cursor();
            cc.page = rowPage;
            cc.y = rowTop + cbor.top() + cpad.top() + vaOff;
            layoutChildren(cell, cs, cellX[i] + cbor.left() + cpad.left(), innerCellW, params, m, doc, cc);
        }

        PaintedRect rowBg = rowBackground(rowPage, cellX[0], rowTop,
                (cellX[n - 1] + cellW[n - 1]) - cellX[0], rowH, row.style());
        if (rowBg != null) doc.insert(rowInsertIndex, rowBg);

        cursor.page = rowPage;
        cursor.y = rowTop + rowH + spacing;
    }

    /**
     * Resolves per-column widths: columns with an explicit cell width use it,
     * remaining space is split among auto columns; if all columns are explicit any
     * slack is distributed evenly so a {@code width:100%} table fills its box.
     */
    private double[] resolveColumnWidths(List<Box> rows, int cols, double innerW, double spacing) {
        Double[] explicit = new Double[cols];
        for (Box row : rows) {
            int col = 0;
            for (Box cell : cellsOf(row)) {
                int span = spanOf(cell);
                if (span == 1 && col < cols) {
                    ComputedStyle cs = cell.style();
                    if (!cs.width().isAuto()) {
                        double w = cs.width().resolve(cs.fontSizePt(), cs.fontSizePt(), innerW);
                        if (!Double.isNaN(w) && w > 0) {
                            if (!cs.isBorderBoxSizing()) w += cs.padding().horizontal() + cs.border().horizontal();
                            explicit[col] = (explicit[col] == null) ? w : Math.max(explicit[col], w);
                        }
                    }
                }
                col += span;
            }
        }
        double totalSpacing = (cols + 1) * spacing;
        double available = Math.max(0, innerW - totalSpacing);
        double sumExplicit = 0;
        int autoCount = 0;
        for (int j = 0; j < cols; j++) {
            if (explicit[j] != null) sumExplicit += explicit[j];
            else autoCount++;
        }
        double[] w = new double[cols];
        if (autoCount == 0) {
            double scale = (sumExplicit > available && sumExplicit > 0) ? available / sumExplicit : 1;
            for (int j = 0; j < cols; j++) w[j] = explicit[j] * scale;
            if (sumExplicit < available) {
                double extra = (available - sumExplicit) / cols;
                for (int j = 0; j < cols; j++) w[j] += extra;
            }
        } else {
            double autoW = Math.max(0, (available - sumExplicit) / autoCount);
            for (int j = 0; j < cols; j++) w[j] = (explicit[j] != null) ? explicit[j] : autoW;
        }
        return w;
    }

    /** Lays a box's children into a throwaway document on a tall page to measure content height. */
    private double measureContentHeight(Box box, double innerWidth, LayoutParams params, TextMeasurer m) {
        LayoutParams tall = new LayoutParams(params.pageWidthPt(), 1e9, params.margins());
        LaidOutDocument tmp = new LaidOutDocument(tall.pageWidthPt(), tall.pageHeightPt());
        Cursor c = new Cursor();
        c.page = 0;
        c.y = 0;
        layoutChildren(box, box.style(), 0, innerWidth, tall, m, tmp, c);
        return c.y;
    }

    private static void collectRows(Box box, List<Box> rows) {
        for (Box c : box.children()) {
            Display d = c.style().display();
            if (d == Display.TABLE_ROW) rows.add(c);
            else if (d == Display.TABLE_ROW_GROUP) collectRows(c, rows);
        }
    }

    /** Collects the rows of any {@code <thead>} row-group (role THead) for repeat-on-page-break. */
    private static void collectHeaderRows(Box box, List<Box> out) {
        for (Box c : box.children()) {
            if (c.style().display() != Display.TABLE_ROW_GROUP) continue;
            if ("THead".equals(c.style().structRole())) collectRows(c, out);
            else collectHeaderRows(c, out);
        }
    }

    private static List<Box> cellsOf(Box row) {
        List<Box> out = new ArrayList<>();
        for (Box c : row.children()) {
            if (c instanceof TableCellBox || c.style().display() == Display.TABLE_CELL) out.add(c);
        }
        return out;
    }

    private static int spanOf(Box cell) {
        return (cell instanceof TableCellBox tc) ? tc.colSpan() : 1;
    }

    private static double explicitLen(Length len, ComputedStyle style, double basis) {
        if (len.isAuto()) return 0;
        double v = len.resolve(style.fontSizePt(), style.fontSizePt(), basis);
        return Double.isNaN(v) ? 0 : v;
    }

    /** Builds a background/border rect from a style, or {@code null} if nothing to paint. */
    private static PaintedRect boxRect(int page, double x, double y, double w, double h, ComputedStyle st) {
        Integer fill = st.backgroundRgb();
        Gradient grad = st.backgroundGradient();
        Edges b = st.border();
        boolean hasBorder = b.top() > 0 || b.right() > 0 || b.bottom() > 0 || b.left() > 0;
        Integer stroke = hasBorder ? (st.borderColorRgb() != null ? st.borderColorRgb() : st.colorRgb()) : null;
        if (fill == null && grad == null && stroke == null) return null;
        if (hasBorder && st.hasPerSideBorderColors()) {
            return new PaintedRect(page, x, y, w, h, fill, stroke, b,
                    st.borderRadiusTlPt(), st.borderRadiusTrPt(), st.borderRadiusBrPt(), st.borderRadiusBlPt(),
                    st.borderStyle(),
                    st.borderTopColor(), st.borderRightColor(), st.borderBottomColor(), st.borderLeftColor(), grad)
                    .fillAlpha(st.backgroundAlpha()).frostBlurPt(st.backdropBlurPt());
        }
        return new PaintedRect(page, x, y, w, h, fill, stroke, b,
                st.borderRadiusTlPt(), st.borderRadiusTrPt(), st.borderRadiusBrPt(), st.borderRadiusBlPt(),
                st.borderStyle(), grad).fillAlpha(st.backgroundAlpha()).frostBlurPt(st.backdropBlurPt());
    }

    private static PaintedRect rowBackground(int page, double x, double y, double w, double h, ComputedStyle st) {
        Integer fill = st.backgroundRgb();
        Gradient grad = st.backgroundGradient();
        if (fill == null && grad == null) return null;
        return new PaintedRect(page, x, y, w, h, fill, null, Edges.ZERO, 0, grad).fillAlpha(st.backgroundAlpha());
    }

    /** A fill-only rectangle (single grid-line strip / background); the painter snaps it to whole points. */
    private static PaintedRect edge(int page, double x, double y, double w, double h, int rgb) {
        return new PaintedRect(page, x, y, w, h, rgb, null, Edges.ZERO, 0);
    }

    /**
     * Draws one collapsed-table grid line, centered on the boundary, resolving the two
     * adjacent borders (wider wins; the winner supplies the colour). {@code x,y} is the
     * boundary position; {@code length} runs along the line.
     */
    private static void drawCollapseEdge(List<PaintedRect> out, int page, double x, double y, double length,
                                         boolean horizontal, double wa, int ca, double wb, int cb) {
        double width = Math.max(wa, wb);
        if (width <= 0) return;
        int color = (wb > wa) ? cb : ca;
        if (horizontal) out.add(edge(page, x, y - width / 2, length, width, color));
        else            out.add(edge(page, x - width / 2, y, width, length, color));
    }

    private static PaintedRect fillOnlyRect(int page, double x, double y, double w, double h, ComputedStyle st) {
        Integer fill = st.backgroundRgb();
        Gradient grad = st.backgroundGradient();
        if (fill == null && grad == null) return null;
        return new PaintedRect(page, x, y, w, h, fill, null, Edges.ZERO, 0, grad);
    }

    // --- flexbox (display:flex) ---

    /**
     * Lays out a flex container's children. Row direction resolves each item's base
     * size (flex-basis / width / intrinsic), distributes free space by
     * {@code flex-grow} or {@code flex-shrink}, positions the line with
     * {@code justify-content}, and wraps into lines when {@code flex-wrap:wrap}.
     * Column direction stacks items with {@code row-gap}. Items are cross-axis
     * start-aligned. Ports the research engine's flex formatting context.
     */
    private void layoutFlexChildren(Box parent, ComputedStyle ps, double x, double availWidth,
                                    LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        List<Box> items = new ArrayList<>();
        for (Box c : parent.children()) {
            if (c instanceof TextBox t && (t.text() == null || t.text().isBlank())) continue;
            items.add(c);
        }
        if (items.isEmpty()) return;

        double colGap = ps.columnGapPt();
        double rowGap = ps.rowGapPt();
        AlignItems align = ps.alignItems();

        if (ps.flexDirection() == FlexDirection.COLUMN) {
            layoutFlexColumn(items, ps, x, availWidth, rowGap, align, params, m, doc, cursor);
            return;
        }

        double parentCrossH = explicitLen(ps.height(), ps, params.pageHeightPt());
        double[] base = resolveFlexItemWidths(items, availWidth, m);
        double[] min = flexMinWidths(items, availWidth, m);
        double[] max = flexMaxWidths(items, availWidth);
        double[] marginMain = new double[items.size()];
        for (int i = 0; i < items.size(); i++) marginMain[i] = items.get(i).style().margin().horizontal();
        JustifyContent justify = ps.justifyContent();

        if (!ps.flexWrap()) {
            layoutFlexLine(items, base, min, max, marginMain, 0, items.size(), x, availWidth, colGap, justify,
                    align, parentCrossH, params, m, doc, cursor);
            return;
        }

        int idx = 0;
        int curPage = cursor.page;
        double rowTop = cursor.y;
        int lastPage = cursor.page;
        double lastBottom = cursor.y;
        while (idx < items.size()) {
            int lineStart = idx, lineCount = 0;
            double used = 0;
            while (idx < items.size()) {
                double outer = marginMain[idx] + Math.max(0, Math.min(availWidth, base[idx]));
                double need = lineCount == 0 ? outer : (colGap + outer);
                if (lineCount > 0 && used + need > availWidth + 1e-6) break;
                used += need;
                idx++;
                lineCount++;
            }
            if (lineCount == 0) { idx++; lineCount = 1; }

            Cursor lineCur = new Cursor();
            lineCur.page = curPage;
            lineCur.y = rowTop;
            layoutFlexLine(items, base, min, max, marginMain, lineStart, lineStart + lineCount, x, availWidth, colGap,
                    justify, align, 0, params, m, doc, lineCur);
            lastPage = lineCur.page;
            lastBottom = lineCur.y;
            curPage = lineCur.page;
            rowTop = lineCur.y + rowGap;
        }
        cursor.page = lastPage;
        cursor.y = lastBottom;
    }

    private void layoutFlexLine(List<Box> items, double[] base, double[] min, double[] max, double[] marginMain,
                                int start, int end,
                                double x, double availWidth, double colGap, JustifyContent justify, AlignItems align,
                                double parentCrossH, LayoutParams params, TextMeasurer m,
                                LaidOutDocument doc, Cursor cursor) {
        FlexLine fl = resolveFlexLine(items, base, min, max, marginMain, start, end, availWidth, colGap, justify);
        int basePage = cursor.page;
        double top = cursor.y;
        int count = end - start;

        double[] w = new double[count];
        double[] itemH = new double[count];
        double[] baseline = new double[count];
        double maxH = 0, maxBaseline = 0;
        boolean anyBaseline = false;
        for (int i = start; i < end; i++) {
            int k = i - start;
            w[k] = Math.max(0, Math.min(availWidth, fl.sizes()[k]));
            itemH[k] = measureBlockOuterHeight(items.get(i), w[k], params, m);
            if (itemH[k] > maxH) maxH = itemH[k];
            if (effAlign(items.get(i), align) == AlignItems.BASELINE) {
                baseline[k] = itemBaseline(items.get(i), m);
                if (baseline[k] > maxBaseline) maxBaseline = baseline[k];
                anyBaseline = true;
            }
        }
        double lineH = Math.max(maxH, parentCrossH);
        if (anyBaseline) {
            for (int i = start; i < end; i++) {
                int k = i - start;
                if (effAlign(items.get(i), align) == AlignItems.BASELINE) {
                    lineH = Math.max(lineH, (maxBaseline - baseline[k]) + itemH[k]);
                }
            }
        }

        double cx = x + fl.startOffset();
        for (int i = start; i < end; i++) {
            int k = i - start;
            AlignItems ea = effAlign(items.get(i), align);
            double off = (ea == AlignItems.BASELINE && anyBaseline)
                    ? maxBaseline - baseline[k]
                    : crossOffset(ea, lineH, itemH[k]);
            double minCH = (ea == AlignItems.STRETCH) ? Math.max(0, lineH - chromeV(items.get(i))) : -1;
            Cursor cc = new Cursor();
            cc.page = basePage;
            cc.y = top + off;
            layoutBlock(items.get(i), cx, w[k], w[k], minCH, params, m, doc, cc);
            cx += marginMain[i] + w[k] + colGap + fl.extraGap();
        }
        cursor.page = basePage;
        cursor.y = top + lineH;
    }

    /** First-baseline offset from an item's top: top chrome + the tallest ascent on the first line. */
    private double itemBaseline(Box item, TextMeasurer m) {
        ComputedStyle cs = item.style();
        double topChrome = cs.margin().top() + cs.border().top() + cs.padding().top();
        List<Frag> frags = new ArrayList<>();
        collectFrags(item, frags);
        double ascent = m.ascentPt(cs);
        boolean found = false;
        for (Frag f : frags) {
            if (f.isBreak()) break;                 // stop at the first line's end
            if (f.text() != null && !f.text().isBlank()) {
                double a = m.ascentPt(f.style());
                ascent = found ? Math.max(ascent, a) : a;
                found = true;
            }
        }
        return topChrome + ascent;
    }

    /** Column flex: items stack on the main (vertical) axis with justify-content; align-items is horizontal. */
    private void layoutFlexColumn(List<Box> items, ComputedStyle ps, double x, double availWidth, double rowGap,
                                  AlignItems align, LayoutParams params, TextMeasurer m,
                                  LaidOutDocument doc, Cursor cursor) {
        int n = items.size();
        double[] itemW = new double[n];
        double[] itemH = new double[n];
        for (int i = 0; i < n; i++) {
            ComputedStyle cs = items.get(i).style();
            AlignItems ea = effAlign(items.get(i), align);
            double w;
            if (!cs.width().isAuto()) {
                double ww = cs.width().resolve(cs.fontSizePt(), cs.fontSizePt(), availWidth);
                w = Double.isNaN(ww) ? availWidth : ww;
                if (!cs.isBorderBoxSizing()) w += cs.padding().horizontal() + cs.border().horizontal();
            } else if (ea == AlignItems.STRETCH) {
                w = availWidth;
            } else {
                w = prefContentWidth(items.get(i), m) + cs.padding().horizontal() + cs.border().horizontal();
            }
            itemW[i] = Math.max(0, Math.min(w, availWidth));
            itemH[i] = measureBlockOuterHeight(items.get(i), itemW[i], params, m);
        }
        double parentMainH = explicitLen(ps.height(), ps, params.pageHeightPt());
        double totalH = rowGap * Math.max(0, n - 1);
        for (double h : itemH) totalH += h;
        double free = parentMainH > 0 ? Math.max(0, parentMainH - totalH) : 0;
        double startOff = 0, gapExtra = 0;
        if (free > 1e-6) {
            switch (ps.justifyContent()) {
                case END -> startOff = free;
                case CENTER -> startOff = free / 2.0;
                case SPACE_BETWEEN -> gapExtra = n > 1 ? free / (n - 1) : 0;
                case SPACE_AROUND -> { gapExtra = free / n; startOff = gapExtra / 2.0; }
                case SPACE_EVENLY -> { gapExtra = free / (n + 1); startOff = gapExtra; }
                default -> { }
            }
        }
        double origY = cursor.y;
        double cy = cursor.y + startOff;
        int lastPage = cursor.page;
        double lastBottom = cy;
        for (int i = 0; i < n; i++) {
            double xoff = crossOffset(effAlign(items.get(i), align), availWidth, itemW[i]);
            Cursor cc = new Cursor();
            cc.page = cursor.page;
            cc.y = cy;
            layoutBlock(items.get(i), x + xoff, itemW[i], itemW[i], params, m, doc, cc);
            lastPage = cc.page;
            lastBottom = cc.y;
            cy = cc.y + rowGap + gapExtra;
        }
        cursor.page = lastPage;
        cursor.y = parentMainH > 0 ? origY + Math.max(parentMainH, totalH) : lastBottom;
    }

    private static double crossOffset(AlignItems align, double lineSize, double itemSize) {
        double free = Math.max(0, lineSize - itemSize);
        return switch (align) {
            case CENTER -> free / 2.0;
            case END -> free;
            default -> 0.0; // START / STRETCH (top) / BASELINE
        };
    }

    /** Lays a box as a block into a throwaway document on a tall page to measure its outer height. */
    private double measureBlockOuterHeight(Box box, double forcedW, LayoutParams params, TextMeasurer m) {
        LayoutParams tall = new LayoutParams(params.pageWidthPt(), 1e9, params.margins());
        LaidOutDocument tmp = new LaidOutDocument(tall.pageWidthPt(), tall.pageHeightPt());
        Cursor c = new Cursor();
        c.page = 0;
        c.y = 0;
        layoutBlock(box, 0, forcedW, forcedW, tall, m, tmp, c);
        return c.y;
    }

    /** Base main-axis size per item: flex-basis, else width, else max-content width. */
    private double[] resolveFlexItemWidths(List<Box> items, double availWidth, TextMeasurer m) {
        int n = items.size();
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            ComputedStyle cs = items.get(i).style();
            double chrome = cs.padding().horizontal() + cs.border().horizontal();
            double val;
            Length basis = cs.flexBasis();
            if (!basis.isAuto()) {
                double b = basis.resolve(cs.fontSizePt(), cs.fontSizePt(), availWidth);
                val = Double.isNaN(b) ? prefContentWidth(items.get(i), m) : b;
                if (!cs.isBorderBoxSizing()) val += chrome;
            } else if (!cs.width().isAuto()) {
                double ww = cs.width().resolve(cs.fontSizePt(), cs.fontSizePt(), availWidth);
                val = Double.isNaN(ww) ? prefContentWidth(items.get(i), m) : ww;
                if (!cs.isBorderBoxSizing()) val += chrome;
            } else {
                val = prefContentWidth(items.get(i), m) + chrome;
            }
            double max = explicitWidth(cs.maxWidth(), cs, availWidth, Double.MAX_VALUE);
            double min = explicitWidth(cs.minWidth(), cs, availWidth, 0);
            w[i] = Math.max(0, Math.max(min, Math.min(val, max)));
        }
        return w;
    }

    /** Per-item flex shrink floor: explicit min-width, else min-content (longest word) + chrome. */
    private double[] flexMinWidths(List<Box> items, double availWidth, TextMeasurer m) {
        double[] min = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ComputedStyle cs = items.get(i).style();
            double explicit = explicitWidth(cs.minWidth(), cs, availWidth, -1);
            if (explicit >= 0) { min[i] = explicit; continue; }
            double chrome = cs.padding().horizontal() + cs.border().horizontal();
            min[i] = minContentWidth(items.get(i), m) + chrome;
        }
        return min;
    }

    private double[] flexMaxWidths(List<Box> items, double availWidth) {
        double[] max = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ComputedStyle cs = items.get(i).style();
            max[i] = explicitWidth(cs.maxWidth(), cs, availWidth, Double.MAX_VALUE);
        }
        return max;
    }

    private static double explicitWidth(Length len, ComputedStyle cs, double basis, double def) {
        if (len == null || len.isAuto()) return def;
        double v = len.resolve(cs.fontSizePt(), cs.fontSizePt(), basis);
        if (Double.isNaN(v)) return def;
        if (!cs.isBorderBoxSizing()) v += cs.padding().horizontal() + cs.border().horizontal();
        return v;
    }

    /**
     * Resolves one flex line per CSS Flexbox §9.7 with iterative min/max clamping and
     * item freezing (matches iText FlexUtil), then positions with justify-content.
     */
    private FlexLine resolveFlexLine(List<Box> items, double[] base, double[] min, double[] max, double[] marginMain,
                                     int start, int end, double availWidth, double colGap, JustifyContent justify) {
        int count = Math.max(0, end - start);
        double[] sizes = new double[count];
        double[] lmin = new double[count];
        double[] lmax = new double[count];
        double[] grow = new double[count];
        double[] shrink = new double[count];
        double marginTotal = 0;
        for (int i = 0; i < count; i++) {
            sizes[i] = base[start + i];
            lmin[i] = min[start + i];
            lmax[i] = max[start + i];
            grow[i] = Math.max(0, items.get(start + i).style().flexGrow());
            shrink[i] = Math.max(0, items.get(start + i).style().flexShrink());
            marginTotal += marginMain[start + i];
        }
        double gapTotal = colGap * Math.max(0, count - 1);
        double reserved = gapTotal + marginTotal;
        double baseSum = 0;
        for (double s : sizes) baseSum += s;
        boolean growMode = (availWidth - reserved - baseSum) > 0;

        boolean[] frozen = new boolean[count];
        for (int i = 0; i < count; i++) {
            double factor = growMode ? grow[i] : shrink[i];
            if (factor <= 0) frozen[i] = true;   // inflexible item keeps its base size
        }

        for (int guard = 0; guard <= count; guard++) {
            double occupied = reserved;
            double factorSum = 0, scaledSum = 0;
            int unfrozen = 0;
            for (int i = 0; i < count; i++) {
                if (frozen[i]) { occupied += sizes[i]; continue; }
                occupied += base[start + i];
                unfrozen++;
                if (growMode) factorSum += grow[i];
                else scaledSum += shrink[i] * base[start + i];
            }
            if (unfrozen == 0) break;
            double freeSpace = availWidth - occupied;

            boolean anyViolation = false;
            for (int i = 0; i < count; i++) {
                if (frozen[i]) continue;
                double target = base[start + i];
                if (growMode && factorSum > 0) target += freeSpace * (grow[i] / factorSum);
                else if (!growMode && scaledSum > 0) target += freeSpace * (shrink[i] * base[start + i] / scaledSum);
                double clamped = Math.max(0, Math.max(lmin[i], Math.min(target, lmax[i])));
                if (Math.abs(clamped - target) > 1e-6) {
                    sizes[i] = clamped;
                    frozen[i] = true;
                    anyViolation = true;
                } else {
                    sizes[i] = target;
                }
            }
            if (!anyViolation) break;
        }

        double used = reserved;
        for (double s : sizes) used += s;
        double remaining = availWidth - used;
        double startOffset = 0, extraGap = 0;
        if (remaining > 1e-6 && count > 0) {
            switch (justify) {
                case END -> startOffset = remaining;
                case CENTER -> startOffset = remaining / 2.0;
                case SPACE_BETWEEN -> extraGap = count > 1 ? remaining / (count - 1) : 0;
                case SPACE_AROUND -> { extraGap = remaining / count; startOffset = extraGap / 2.0; }
                case SPACE_EVENLY -> { extraGap = remaining / (count + 1); startOffset = extraGap; }
                default -> { }
            }
        }
        return new FlexLine(sizes, startOffset, extraGap);
    }

    /** Max-content width: inline runs summed on one line, block children taken as the max. */
    private double prefContentWidth(Box box, TextMeasurer m) {
        if (box instanceof TextBox t) {
            String s = t.text() == null ? "" : t.text().replaceAll("\\s+", " ").trim();
            return s.isEmpty() ? 0 : m.textWidthPt(s, t.style());
        }
        double inlineSum = 0, blockMax = 0;
        for (Box c : box.children()) {
            if (c.isBlockLevel()) {
                ComputedStyle cs = c.style();
                double childChrome = cs.padding().horizontal() + cs.border().horizontal();
                blockMax = Math.max(blockMax, prefContentWidth(c, m) + childChrome);
            } else {
                inlineSum += prefContentWidth(c, m);
            }
        }
        return Math.max(inlineSum, blockMax);
    }

    /** Min-content width: the widest single unbreakable word among the box's text. */
    private double minContentWidth(Box box, TextMeasurer m) {
        List<Frag> frags = new ArrayList<>();
        collectFrags(box, frags);
        double max = 0;
        for (Frag f : frags) {
            if (f.isBreak() || f.text() == null) continue;
            for (String word : f.text().trim().split("\\s+")) {
                if (!word.isEmpty()) max = Math.max(max, m.textWidthPt(word, f.style()));
            }
        }
        return max;
    }

    /** One item's effective cross-axis alignment: its align-self, else the container's align-items. */
    private static AlignItems effAlign(Box item, AlignItems container) {
        AlignItems self = item.style().alignSelf();
        return self != null ? self : container;
    }

    private record FlexLine(double[] sizes, double startOffset, double extraGap) {}

    private static double chromeV(Box box) {
        ComputedStyle s = box.style();
        return s.margin().vertical() + s.padding().vertical() + s.border().vertical();
    }

    // --- grid (display:grid) ---

    /**
     * Lays out a grid container: resolves column track widths, packs items into rows
     * honouring {@code grid-column} spans, equalises each row to its tallest item,
     * applies {@code align-items} within the row, and breaks whole rows across pages.
     * Ports the research engine's track resolution and adds spans + row equalisation.
     */
    private void layoutGridChildren(Box parent, ComputedStyle ps, double x, double availWidth,
                                    LayoutParams params, TextMeasurer m, LaidOutDocument doc, Cursor cursor) {
        List<Box> items = new ArrayList<>();
        for (Box c : parent.children()) {
            if (c instanceof TextBox t && (t.text() == null || t.text().isBlank())) continue;
            items.add(c);
        }
        if (items.isEmpty()) return;

        double colGap = ps.columnGapPt();
        double rowGap = ps.rowGapPt();
        AlignItems align = ps.alignItems();
        boolean dense = ps.gridAutoFlowDense();
        boolean flowColumn = ps.gridAutoFlowColumn();
        java.util.Map<String, int[]> areas = parseGridAreas(ps.gridTemplateAreas());

        double[] colW;
        int[][] place = new int[items.size()][];   // {row, col, rowSpan, colSpan}
        int rowCount;

        if (flowColumn) {
            // Column flow: fill each column top-to-bottom (row count from grid-template-rows), then move right.
            int rows = Math.max(1, gridTrackCount(ps.gridTemplateRows()));
            java.util.List<boolean[]> cols = new java.util.ArrayList<>();   // each boolean[rows] is one column
            int cr = 0, cc = 0;
            for (int i = 0; i < items.size(); i++) {
                ComputedStyle cs = items.get(i).style();
                int colSpan = Math.max(1, cs.gridColumnSpan());
                int rowSpan = Math.max(1, Math.min(rows, cs.gridRowSpan()));
                int sr = dense ? 0 : cr;
                int sc = dense ? 0 : cc;
                int[] pos = findFreeCellColumn(cols, sr, sc, rows, colSpan, rowSpan);
                int r0 = pos[0], c0 = pos[1];
                if (!dense) {
                    cr = r0 + rowSpan;
                    cc = c0;
                    if (cr >= rows) { cr = 0; cc = c0 + 1; }
                }
                markOccColumn(cols, r0, c0, rowSpan, colSpan, rows);
                place[i] = new int[]{ r0, c0, rowSpan, colSpan };
            }
            colW = columnFlowWidths(ps, Math.max(1, cols.size()), availWidth, colGap);
            rowCount = rows;
        } else {
            // Row flow: named area, else row-major auto-placement (dense backfills holes).
            colW = resolveGridTrackWidths(ps, availWidth);
            int cols = Math.max(1, colW.length);
            java.util.List<boolean[]> occ = new java.util.ArrayList<>();
            int cursorR = 0, cursorC = 0;
            for (int i = 0; i < items.size(); i++) {
                ComputedStyle cs = items.get(i).style();
                int colSpan = Math.max(1, Math.min(cols, cs.gridColumnSpan()));
                int rowSpan = Math.max(1, cs.gridRowSpan());
                int[] area = (cs.gridArea() != null) ? areas.get(cs.gridArea()) : null;
                int r0, c0;
                if (area != null) {
                    r0 = area[0]; c0 = area[1];
                    rowSpan = area[2]; colSpan = Math.min(cols, area[3]);
                } else {
                    int sr = dense ? 0 : cursorR;
                    int sc = dense ? 0 : cursorC;
                    int[] pos = findFreeCell(occ, sr, sc, cols, colSpan, rowSpan);
                    r0 = pos[0]; c0 = pos[1];
                    if (!dense) {
                        cursorR = r0;
                        cursorC = c0 + colSpan;
                        if (cursorC >= cols) { cursorC = 0; cursorR = r0 + 1; }
                    }
                }
                markOcc(occ, r0, c0, rowSpan, colSpan, cols);
                place[i] = new int[]{ r0, c0, rowSpan, colSpan };
            }
            rowCount = occ.size();
        }
        if (rowCount == 0) return;

        // Row heights: explicit tracks (fixed) grown to content, then fr rows share the grid's spare height.
        double[][] rowSpec = gridRowTrackSpecs(ps, rowCount, availWidth);
        double[] rowH = rowSpec[0].clone();
        double[] rowFr = rowSpec[1];
        double[] itemH = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            int[] p = place[i];
            double w = trackSpanWidth(colW, colGap, p[1], p[3]);
            itemH[i] = measureBlockOuterHeight(items.get(i), w, params, m);
            if (p[2] == 1 && itemH[i] > rowH[p[0]]) rowH[p[0]] = itemH[i];
        }
        for (int i = 0; i < items.size(); i++) {
            int[] p = place[i];
            if (p[2] <= 1) continue;
            double span = 0;
            for (int r = p[0]; r < p[0] + p[2] && r < rowCount; r++) span += rowH[r];
            span += rowGap * (p[2] - 1);
            if (span < itemH[i]) {
                double deficit = (itemH[i] - span) / p[2];
                for (int r = p[0]; r < p[0] + p[2] && r < rowCount; r++) rowH[r] += deficit;
            }
        }
        double gridH = explicitLen(ps.height(), ps, params.pageHeightPt());
        double frTotal = 0;
        for (double f : rowFr) frTotal += f;
        if (gridH > 0 && frTotal > 0) {
            double usedH = rowGap * Math.max(0, rowCount - 1);
            for (double h : rowH) usedH += h;
            double leftover = gridH - usedH;
            if (leftover > 0) {
                for (int r = 0; r < rowCount; r++) if (rowFr[r] > 0) rowH[r] += leftover * (rowFr[r] / frTotal);
            }
        }

        // Baseline alignment groups: tallest first-baseline per row among baseline-aligned items.
        double[] rowBaseline = new double[rowCount];
        boolean[] rowHasBaseline = new boolean[rowCount];
        for (int i = 0; i < items.size(); i++) {
            if (effAlign(items.get(i), align) == AlignItems.BASELINE) {
                int r = place[i][0];
                double b = itemBaseline(items.get(i), m);
                if (b > rowBaseline[r]) rowBaseline[r] = b;
                rowHasBaseline[r] = true;
            }
        }

        // Assign each row a page + top y, breaking whole rows that don't fit.
        int[] rowPage = new int[rowCount];
        double[] rowY = new double[rowCount];
        int pg = cursor.page;
        double yy = cursor.y;
        double pageContentH = params.contentBottomPt() - params.contentTopPt();
        for (int r = 0; r < rowCount; r++) {
            if (r > 0) yy += rowGap;
            if (yy + rowH[r] > params.contentBottomPt() && rowH[r] <= pageContentH) {
                pg++;
                yy = params.contentTopPt();
            }
            rowPage[r] = pg;
            rowY[r] = yy;
            yy += rowH[r];
        }

        for (int i = 0; i < items.size(); i++) {
            int[] p = place[i];
            double cellX = x + trackOffset(colW, colGap, p[1]);
            double cellW = trackSpanWidth(colW, colGap, p[1], p[3]);
            double cellH = 0;
            int spanned = 0;
            for (int r = p[0]; r < p[0] + p[2] && r < rowCount; r++) { cellH += rowH[r]; spanned++; }
            cellH += rowGap * Math.max(0, spanned - 1);
            AlignItems ea = effAlign(items.get(i), align);
            double off = (ea == AlignItems.BASELINE && rowHasBaseline[p[0]])
                    ? rowBaseline[p[0]] - itemBaseline(items.get(i), m)
                    : crossOffset(ea, cellH, itemH[i]);
            double minCH = (ea == AlignItems.STRETCH) ? Math.max(0, cellH - chromeV(items.get(i))) : -1;
            Cursor cc = new Cursor();
            cc.page = rowPage[p[0]];
            cc.y = rowY[p[0]] + off;
            layoutBlock(items.get(i), cellX, cellW, cellW, minCH, params, m, doc, cc);
        }

        cursor.page = rowPage[rowCount - 1];
        cursor.y = rowY[rowCount - 1] + rowH[rowCount - 1];
    }

    /** Parses grid-template-areas quoted rows into name -> {row, col, rowSpan, colSpan}. */
    private static java.util.Map<String, int[]> parseGridAreas(String spec) {
        java.util.Map<String, int[]> map = new java.util.LinkedHashMap<>();
        if (spec == null || spec.isBlank()) return map;
        List<String> rows = new ArrayList<>();
        int i = 0;
        while (true) {
            int a = spec.indexOf('"', i);
            if (a < 0) break;
            int b = spec.indexOf('"', a + 1);
            if (b < 0) break;
            rows.add(spec.substring(a + 1, b));
            i = b + 1;
        }
        for (int r = 0; r < rows.size(); r++) {
            String[] toks = rows.get(r).trim().split("\\s+");
            for (int c = 0; c < toks.length; c++) {
                String name = toks[c];
                if (name.isEmpty() || name.equals(".")) continue;
                name = name.toLowerCase(java.util.Locale.ROOT);
                int[] rect = map.get(name);
                if (rect == null) map.put(name, new int[]{ r, c, r, c });
                else {
                    rect[0] = Math.min(rect[0], r);
                    rect[1] = Math.min(rect[1], c);
                    rect[2] = Math.max(rect[2], r);
                    rect[3] = Math.max(rect[3], c);
                }
            }
        }
        for (int[] rect : map.values()) {
            int r0 = rect[0], c0 = rect[1], r1 = rect[2], c1 = rect[3];
            rect[0] = r0; rect[1] = c0; rect[2] = r1 - r0 + 1; rect[3] = c1 - c0 + 1;
        }
        return map;
    }

    private static int[] findFreeCell(List<boolean[]> occ, int startR, int startC, int cols, int colSpan, int rowSpan) {
        int r = startR, c = startC;
        colSpan = Math.min(colSpan, cols);
        while (true) {
            if (c + colSpan > cols) { c = 0; r++; continue; }
            boolean fits = true;
            for (int rr = r; rr < r + rowSpan && fits; rr++) {
                ensureRow(occ, rr, cols);
                for (int cc = c; cc < c + colSpan; cc++) if (occ.get(rr)[cc]) { fits = false; break; }
            }
            if (fits) return new int[]{ r, c };
            c++;
        }
    }

    private static void ensureRow(List<boolean[]> occ, int r, int cols) {
        while (occ.size() <= r) occ.add(new boolean[cols]);
    }

    private static void markOcc(List<boolean[]> occ, int r0, int c0, int rowSpan, int colSpan, int cols) {
        for (int r = r0; r < r0 + rowSpan; r++) {
            ensureRow(occ, r, cols);
            for (int c = c0; c < c0 + colSpan && c < cols; c++) occ.get(r)[c] = true;
        }
    }

    private static int gridTrackCount(String spec) {
        if (spec == null || spec.isBlank()) return 0;
        return expandRepeatTokens(splitTrackTokens(spec)).size();
    }

    /** Column-major free-cell search: scans down a column (rows fixed), then moves right. */
    private static int[] findFreeCellColumn(List<boolean[]> cols, int startR, int startC, int rows,
                                            int colSpan, int rowSpan) {
        int r = startR, c = startC;
        rowSpan = Math.min(rowSpan, rows);
        while (true) {
            if (r + rowSpan > rows) { r = 0; c++; continue; }
            boolean fits = true;
            for (int cc = c; cc < c + colSpan && fits; cc++) {
                ensureCol(cols, cc, rows);
                for (int rr = r; rr < r + rowSpan; rr++) if (cols.get(cc)[rr]) { fits = false; break; }
            }
            if (fits) return new int[]{ r, c };
            r++;
        }
    }

    private static void ensureCol(List<boolean[]> cols, int c, int rows) {
        while (cols.size() <= c) cols.add(new boolean[rows]);
    }

    private static void markOccColumn(List<boolean[]> cols, int r0, int c0, int rowSpan, int colSpan, int rows) {
        for (int c = c0; c < c0 + colSpan; c++) {
            ensureCol(cols, c, rows);
            for (int r = r0; r < r0 + rowSpan && r < rows; r++) cols.get(c)[r] = true;
        }
    }

    /** Column widths for column-flow grids: explicit template widths first, rest share the remainder. */
    private double[] columnFlowWidths(ComputedStyle ps, int colCount, double avail, double colGap) {
        double[] w = new double[colCount];
        List<String> ctoks = (ps.gridTemplateColumns() != null)
                ? expandRepeatTokens(splitTrackTokens(ps.gridTemplateColumns())) : List.of();
        double budget = Math.max(0, avail - colGap * Math.max(0, colCount - 1));
        double[] fixedW = new double[colCount];
        boolean[] hasFixed = new boolean[colCount];
        double fixedSum = 0;
        int fixedCount = 0;
        for (int c = 0; c < colCount && c < ctoks.size(); c++) {
            double[] tr = parseGridTrackToken(ctoks.get(c), ps.fontSizePt(), avail);
            if (tr[0] > 0) { fixedW[c] = tr[0]; hasFixed[c] = true; fixedSum += tr[0]; fixedCount++; }
        }
        double autoEach = (colCount - fixedCount) > 0 ? Math.max(0, (budget - fixedSum) / (colCount - fixedCount)) : 0;
        for (int c = 0; c < colCount; c++) w[c] = hasFixed[c] ? fixedW[c] : autoEach;
        return w;
    }

    /** Row track {fixed[], fr[]}: fixed pt heights and fr weights; auto/content rows have fr 0. */
    private double[][] gridRowTrackSpecs(ComputedStyle st, int rowCount, double availWidth) {
        double[] fixed = new double[rowCount];
        double[] fr = new double[rowCount];
        List<String> tracks = (st.gridTemplateRows() != null)
                ? expandRepeatTokens(splitTrackTokens(st.gridTemplateRows())) : List.of();
        String autoTok = (st.gridAutoRows() != null && !st.gridAutoRows().isBlank())
                ? st.gridAutoRows().trim().toLowerCase(java.util.Locale.ROOT) : "auto";
        for (int r = 0; r < rowCount; r++) {
            String tok = (r < tracks.size())
                    ? tracks.get(r).trim().toLowerCase(java.util.Locale.ROOT) : autoTok;
            double[] tr = parseGridTrackToken(tok, st.fontSizePt(), availWidth);
            boolean contentSized = tok.equals("auto") || tok.equals("min-content") || tok.equals("max-content");
            fixed[r] = tr[0];
            fr[r] = contentSized ? 0 : tr[1];
        }
        return new double[][]{ fixed, fr };
    }

    private static double trackOffset(double[] colW, double colGap, int col) {
        double off = 0;
        for (int i = 0; i < col && i < colW.length; i++) off += colW[i] + colGap;
        return off;
    }

    private static double trackSpanWidth(double[] colW, double colGap, int col, int span) {
        double w = 0;
        for (int i = 0; i < span && col + i < colW.length; i++) w += colW[col + i];
        w += colGap * Math.max(0, span - 1);
        return w;
    }

    /** Resolves grid-template-columns into pt track widths (fixed/%/fr/repeat/minmax/auto). */
    private double[] resolveGridTrackWidths(ComputedStyle st, double availWidth) {
        String spec = st.gridTemplateColumns();
        if (spec == null || spec.trim().isEmpty()) return new double[]{ Math.max(0, availWidth) };

        List<String> tokens = expandRepeatTokens(splitTrackTokens(spec));
        if (tokens.isEmpty()) return new double[]{ Math.max(0, availWidth) };

        int cols = tokens.size();
        double[] fixed = new double[cols];
        double[] fr = new double[cols];
        double fixedTotal = 0, frTotal = 0;
        for (int i = 0; i < cols; i++) {
            double[] tr = parseGridTrackToken(tokens.get(i), st.fontSizePt(), availWidth);
            fixed[i] = tr[0];
            fr[i] = tr[1];
            fixedTotal += fixed[i];
            frTotal += fr[i];
        }

        double[] widths = new double[cols];
        double gapTotal = st.columnGapPt() * Math.max(0, cols - 1);
        double budget = Math.max(0, availWidth - gapTotal);
        if (fixedTotal > budget && fixedTotal > 0) {
            double scale = budget / fixedTotal;
            for (int i = 0; i < cols; i++) widths[i] = fixed[i] * scale;
            return widths;
        }
        double remaining = Math.max(0, budget - fixedTotal);
        if (frTotal > 0) {
            for (int i = 0; i < cols; i++) widths[i] = fixed[i] + remaining * (fr[i] / frTotal);
        } else {
            double extra = cols > 0 ? remaining / cols : 0;
            for (int i = 0; i < cols; i++) widths[i] = fixed[i] + extra;
        }
        return widths;
    }

    /** Parses a single grid track token, returning {fixedPt, frUnits}. */
    private static double[] parseGridTrackToken(String token, double emPt, double availWidth) {
        if (token == null) return new double[]{0, 1};
        String t = token.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty()) return new double[]{0, 1};
        if (t.startsWith("minmax(") && t.endsWith(")")) {
            String inner = t.substring(7, t.length() - 1).trim();
            int comma = findTopLevelComma(inner);
            if (comma > 0 && comma + 1 < inner.length()) t = inner.substring(comma + 1).trim();
        }
        if (t.startsWith("fit-content(") && t.endsWith(")")) {
            t = t.substring("fit-content(".length(), t.length() - 1).trim();
        }
        if (t.equals("auto") || t.equals("min-content") || t.equals("max-content")) return new double[]{0, 1};
        if (t.endsWith("fr")) {
            Double n = leadingDouble(t.substring(0, t.length() - 2));
            return new double[]{0, n != null ? n : 1};
        }
        if (t.endsWith("%")) {
            Double p = leadingDouble(t.substring(0, t.length() - 1));
            return new double[]{ p != null ? p / 100.0 * availWidth : 0, p != null ? 0 : 1 };
        }
        double fixed = trackLengthPt(t, emPt);
        return fixed >= 0 ? new double[]{fixed, 0} : new double[]{0, 1};
    }

    private static double trackLengthPt(String t, double emPt) {
        Double n = leadingDouble(t);
        if (n == null) return -1;
        double v = n;
        if (t.endsWith("px")) return v * 0.75;
        if (t.endsWith("pt")) return v;
        if (t.endsWith("pc")) return v * 12.0;
        if (t.endsWith("in")) return v * 72.0;
        if (t.endsWith("cm")) return v * 72.0 / 2.54;
        if (t.endsWith("mm")) return v * 72.0 / 25.4;
        if (t.endsWith("rem") || t.endsWith("em")) return v * emPt;
        return v * 0.75; // unitless -> px
    }

    private static Double leadingDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        int i = 0, n = t.length();
        if (i < n && (t.charAt(i) == '+' || t.charAt(i) == '-')) i++;
        int start = i;
        boolean dot = false;
        while (i < n) {
            char c = t.charAt(i);
            if (c == '.' && !dot) { dot = true; i++; }
            else if (Character.isDigit(c)) i++;
            else break;
        }
        if (i == start) return null;
        try { return Double.parseDouble(t.substring(0, i)); } catch (NumberFormatException e) { return null; }
    }

    private static List<String> splitTrackTokens(String spec) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < spec.length(); i++) {
            char c = spec.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;
            if (Character.isWhitespace(c) && depth == 0) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static List<String> expandRepeatTokens(List<String> tokens) {
        List<String> out = new ArrayList<>();
        for (String token : tokens) {
            String t = token == null ? "" : token.trim();
            if (t.regionMatches(true, 0, "repeat(", 0, 7) && t.endsWith(")")) {
                String inner = t.substring(7, t.length() - 1).trim();
                int comma = findTopLevelComma(inner);
                if (comma > 0 && comma + 1 < inner.length()) {
                    Double cnt = leadingDouble(inner.substring(0, comma).trim());
                    String body = inner.substring(comma + 1).trim();
                    List<String> pattern = splitTrackTokens(body);
                    if (cnt != null && cnt >= 1 && !pattern.isEmpty()) {
                        int count = (int) Math.round(cnt);
                        for (int i = 0; i < count; i++) out.addAll(pattern);
                        continue;
                    }
                }
            }
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static int findTopLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    private static double resolveBoxWidth(ComputedStyle style, double availWidth,
                                          Edges margin, Edges padding, Edges border) {
        Length w = style.width();
        if (w.isAuto()) {
            return Math.max(0, availWidth - margin.horizontal());
        }
        double resolved = w.resolve(style.fontSizePt(), style.fontSizePt(), availWidth);
        if (Double.isNaN(resolved)) return Math.max(0, availWidth - margin.horizontal());
        // content-box (default): the CSS width is the content width, so add padding+border.
        return style.isBorderBoxSizing() ? resolved : resolved + padding.horizontal() + border.horizontal();
    }

    private static void newPage(LayoutParams params, Cursor cursor) {
        cursor.page++;
        cursor.y = params.contentTopPt();
    }

    /** Convenience: layout with the built-in approximate measurer. */
    public LaidOutDocument layout(Box root, LayoutParams params) {
        return layout(root, params, new ApproxTextMeasurer());
    }

    // Kept to document intent; not part of the public surface yet.
    @SuppressWarnings("unused")
    private static List<Box> blockChildren(Box box) {
        List<Box> out = new ArrayList<>();
        for (Box c : box.children()) if (c.isBlockLevel()) out.add(c);
        return out;
    }
}
