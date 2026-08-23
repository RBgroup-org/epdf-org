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

import com.epdfengine.rb.org.common.Length;
import com.epdfengine.rb.org.css.Display;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * A table — the Mode B counterpart of {@code <table>}. Declare column widths once
 * with {@link #columns(String...)}, then add a {@link #header(String...)} and data
 * {@link #row(String...)}s; widths ({@code "50%"}, {@code "120pt"}) apply per column.
 */
public final class Table extends Styleable<Table> {

    private final List<Row> rows = new ArrayList<>();
    private String[] columnWidths;

    private Table() { style.display(Display.TABLE).borderCollapse(true); }

    public static Table create() { return new Table(); }

    /** A table whose columns have the given widths (percent or points). */
    public static Table columns(String... widths) {
        Table t = new Table();
        t.columnWidths = widths;
        return t;
    }

    /** Adds a bold, shaded header row. */
    public Table header(String... cells) {
        Row r = Row.create();
        for (String s : cells) r.add(Cell.of(s).bold().background(0xF1F5F9));
        return addRow(r);
    }

    /** Adds a data row of text cells. */
    public Table row(String... cells) {
        Row r = Row.create();
        for (String s : cells) r.add(Cell.of(s));
        return addRow(r);
    }

    /** Adds a fully built row (for styled/spanning cells). */
    public Table add(Row r) { return addRow(r); }

    private Table addRow(Row r) {
        rows.add(r);
        applyWidths(r);
        return this;
    }

    private void applyWidths(Row r) {
        if (columnWidths == null) return;
        List<Cell> cs = r.cells();
        for (int i = 0; i < cs.size() && i < columnWidths.length; i++) {
            cs.get(i).width(parseWidth(columnWidths[i]));
        }
    }

    private static Length parseWidth(String w) {
        String s = w.trim();
        if (s.endsWith("%"))  return Length.percent(Double.parseDouble(s.substring(0, s.length() - 1)));
        if (s.endsWith("pt")) return Length.pt(Double.parseDouble(s.substring(0, s.length() - 2)));
        return Length.pt(Double.parseDouble(s));
    }

    @Override public Box toBox() {
        BlockBox table = new BlockBox(style);
        for (Row r : rows) table.add(r.toBox());
        return table;
    }
}
