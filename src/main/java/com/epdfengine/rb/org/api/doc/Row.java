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
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * A table row — the Mode B counterpart of {@code <tr>}. Holds {@link Cell}s.
 */
public final class Row extends Styleable<Row> {

    private final List<Cell> cells = new ArrayList<>();

    private Row() { style.display(Display.TABLE_ROW); }

    public static Row create() { return new Row(); }

    /** A row of text cells. */
    public static Row of(String... cellTexts) {
        Row r = new Row();
        for (String t : cellTexts) r.cells.add(Cell.of(t));
        return r;
    }

    public Row add(Cell c) { if (c != null) cells.add(c); return this; }

    List<Cell> cells() { return cells; }

    @Override public Box toBox() {
        BlockBox row = new BlockBox(style);
        for (Cell c : cells) row.add(c.toBox());
        return row;
    }
}
