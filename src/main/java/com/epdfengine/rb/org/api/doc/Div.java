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

import com.epdfengine.rb.org.css.Display;
import com.epdfengine.rb.org.css.FlexDirection;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic container — the Mode B counterpart of {@code <div>}. Stacks its children
 * vertically by default; {@link #row()}/{@link #column()} switch it to flexbox and
 * {@link #grid(String)} to CSS grid, so multi-column and card layouts are one call.
 */
public final class Div extends Styleable<Div> {

    private final List<Content> children = new ArrayList<>();

    private Div() {}

    /** A block container (vertical stack). */
    public static Div create() { return new Div(); }

    /** A horizontal flex row. */
    public static Div row() {
        Div d = new Div();
        d.style.display(Display.FLEX).flexDirection(FlexDirection.ROW);
        return d;
    }

    /** A vertical flex column. */
    public static Div column() {
        Div d = new Div();
        d.style.display(Display.FLEX).flexDirection(FlexDirection.COLUMN);
        return d;
    }

    /** A CSS grid with the given column track list (e.g. {@code "1fr 1fr 1fr"} or {@code "repeat(3, 1fr)"}). */
    public static Div grid(String templateColumns) {
        Div d = new Div();
        d.style.display(Display.GRID).gridTemplateColumns(templateColumns);
        return d;
    }

    /** Gap between flex/grid children (both row and column gap). */
    public Div gap(double pt) { style.columnGapPt(pt); style.rowGapPt(pt); return this; }

    /** How much this item grows to fill a flex row/column. */
    public Div grow(double factor) { style.flexGrow(factor); return this; }

    /** Number of columns this item spans in a grid. */
    public Div columnSpan(int n) { style.gridColumnSpan(n); return this; }

    public Div add(Content child) { if (child != null) children.add(child); return this; }

    @Override public Box toBox() {
        BlockBox b = new BlockBox(style);
        for (Content c : children) {
            Box cb = c.toBox();
            if (cb != null) b.add(cb);
        }
        return b;
    }
}
