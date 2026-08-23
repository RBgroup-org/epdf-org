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
import com.epdfengine.rb.org.css.Display;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.TableCellBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A table cell — the Mode B counterpart of {@code <td>}/{@code <th>}. Holds text or
 * any {@link Content}, and can span columns/rows.
 */
public final class Cell extends Styleable<Cell> {

    private final List<Content> children = new ArrayList<>();
    private int colSpan = 1;
    private int rowSpan = 1;

    private Cell() {
        style.display(Display.TABLE_CELL).padding(Edges.of(6));
    }

    /** A cell containing {@code text}. */
    public static Cell of(String text) {
        Cell c = new Cell();
        c.children.add(Text.of(text));
        return c;
    }

    /** An empty cell to fill with {@link #add(Content)}. */
    public static Cell empty() { return new Cell(); }

    public Cell add(Content c)  { if (c != null) children.add(c); return this; }
    public Cell colSpan(int n)  { this.colSpan = Math.max(1, n); return this; }
    public Cell rowSpan(int n)  { this.rowSpan = Math.max(1, n); return this; }

    @Override public Box toBox() {
        TableCellBox cell = new TableCellBox(style, colSpan, rowSpan);
        for (Content c : children) {
            Box cb = c.toBox();
            if (cb != null) cell.add(cb);
        }
        return cell;
    }
}
