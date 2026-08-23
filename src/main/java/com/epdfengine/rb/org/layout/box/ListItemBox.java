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
import com.epdfengine.rb.org.css.ListStyleType;

/**
 * A list item ({@code <li>}): a block that also carries a marker. Glyph markers
 * (disc/circle/square) are painted as vector shapes; ordinal markers are the
 * pre-formatted text (e.g. {@code "3."}). The layout engine draws the marker in
 * the indent to the left of the item's content, aligned to its first line.
 */
public final class ListItemBox extends BlockBox {

    private final ListStyleType markerType;
    private final String markerText;   // ordinal text (e.g. "3."); empty for glyph/none

    public ListItemBox(ComputedStyle style, ListStyleType markerType, String markerText) {
        super(style);
        this.markerType = (markerType != null) ? markerType : ListStyleType.DISC;
        this.markerText = (markerText != null) ? markerText : "";
    }

    public ListStyleType markerType() { return markerType; }
    public String markerText()        { return markerText; }
}
