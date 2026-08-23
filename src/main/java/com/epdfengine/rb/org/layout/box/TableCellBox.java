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
package com.epdfengine.rb.org.layout.box;

import com.epdfengine.rb.org.css.ComputedStyle;

/**
 * A table cell ({@code <td>}/{@code <th>}). Extends {@link BlockBox} and carries
 * the {@code colspan}/{@code rowspan} counts from the markup so the table layout
 * can size and position it across columns/rows.
 */
public final class TableCellBox extends BlockBox {

    private final int colSpan;
    private final int rowSpan;

    public TableCellBox(ComputedStyle style, int colSpan, int rowSpan) {
        super(style);
        this.colSpan = Math.max(1, colSpan);
        this.rowSpan = Math.max(1, rowSpan);
    }

    public int colSpan() { return colSpan; }
    public int rowSpan() { return rowSpan; }
}
