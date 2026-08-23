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
package com.epdfengine.rb.org.api.doc;

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.css.TextAlign;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.TextBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A block of text — the Mode B counterpart of {@code <p>}/{@code <h1>}. Text
 * properties (font, size, weight, colour) apply to the primary run; block
 * properties (align, background, padding, margin, border) apply to the block. Use
 * {@link #add(Text)} / {@link #append(String)} to compose mixed inline formatting.
 */
public final class Paragraph implements Content {

    private final ComputedStyle block = new ComputedStyle();
    private final List<Run> runs = new ArrayList<>();
    private ComputedStyle primary;

    private record Run(String text, ComputedStyle style) {}

    private Paragraph() { block.margin(Edges.of(0, 0, 6, 0)); }   // small default paragraph spacing

    /** A paragraph containing {@code text} as its primary run. */
    public static Paragraph of(String text) {
        Paragraph p = new Paragraph();
        p.primary = new ComputedStyle();
        p.runs.add(new Run(text, p.primary));
        return p;
    }

    /** An empty paragraph (useful as a spacer). */
    public static Paragraph empty() { return new Paragraph(); }

    /** A heading (level 1–6): bold, sized by level, tagged {@code H1}…{@code H6} for accessibility. */
    public static Paragraph heading(int level, String text) {
        double[] sizes = {26, 22, 18, 15, 13, 12};
        int i = Math.max(1, Math.min(6, level)) - 1;
        Paragraph p = of(text);
        p.primary.fontSizePt(sizes[i]).bold(true);
        p.primary.setStructRole("H" + (i + 1));
        p.block.setStructRole("H" + (i + 1));
        p.block.margin(Edges.of(0, 0, 8, 0));
        return p;
    }

    private ComputedStyle primary() {
        if (primary == null) { primary = new ComputedStyle(); runs.add(new Run("", primary)); }
        return primary;
    }

    // --- text style (primary run) ---
    public Paragraph fontSize(double pt)   { primary().fontSizePt(pt); return this; }
    public Paragraph bold()                { primary().bold(true); return this; }
    public Paragraph italic()              { primary().italic(true); return this; }
    public Paragraph color(int rgb)        { primary().colorRgb(rgb); return this; }
    public Paragraph font(String family)   { primary().fontFamily(family); return this; }

    // --- block style ---
    public Paragraph align(TextAlign a)    { block.textAlign(a); if (primary != null) primary.textAlign(a); return this; }
    public Paragraph lineHeight(double f)  { block.lineHeightFactor(f); primary().lineHeightFactor(f); return this; }
    public Paragraph background(int rgb)   { block.backgroundRgb(rgb); return this; }
    public Paragraph padding(double v)     { block.padding(Edges.of(v)); return this; }
    public Paragraph padding(Edges e)      { block.padding(e); return this; }
    public Paragraph margin(double v)      { block.margin(Edges.of(v)); return this; }
    public Paragraph margin(Edges e)       { block.margin(e); return this; }
    public Paragraph border(double w, int rgb) { block.border(Edges.of(w)); block.borderColorRgb(rgb); return this; }
    public Paragraph borderRadius(double r){ block.borderRadiusPt(r); return this; }
    public Paragraph grow(double factor)   { block.flexGrow(factor); return this; }
    public Paragraph id(String anchor)     { block.setAnchorId(anchor); return this; }
    public Paragraph role(String r)        { block.setStructRole(r); return this; }

    // --- rich inline composition ---
    public Paragraph add(Text run)         { runs.add(new Run(run.value(), run.style())); return this; }
    public Paragraph append(String s)      { runs.add(new Run(s, (primary != null ? primary.inheritedCopy() : new ComputedStyle()))); return this; }

    /** The block's computed style (for advanced tweaks). */
    public ComputedStyle style() { return block; }

    @Override public Box toBox() {
        BlockBox b = new BlockBox(block);
        for (Run r : runs) b.add(new TextBox(r.text(), r.style()));
        return b;
    }
}
