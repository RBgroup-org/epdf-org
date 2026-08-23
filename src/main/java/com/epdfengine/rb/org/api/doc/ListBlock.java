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
import com.epdfengine.rb.org.css.Display;
import com.epdfengine.rb.org.css.ListStyleType;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.ListItemBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A bulleted or numbered list — the Mode B counterpart of {@code <ul>}/{@code <ol>}.
 * Ordered lists number their items automatically; bulleted lists draw a disc marker.
 */
public final class ListBlock extends Styleable<ListBlock> {

    private final boolean ordered;
    private final List<Content> items = new ArrayList<>();

    private ListBlock(boolean ordered) {
        this.ordered = ordered;
        style.padding(Edges.of(0, 0, 0, 18));   // indent for markers
    }

    /** A numbered ({@code 1. 2. 3.}) list. */
    public static ListBlock ordered() { return new ListBlock(true); }

    /** A bulleted (disc) list. */
    public static ListBlock bulleted() { return new ListBlock(false); }

    public ListBlock item(String text)   { items.add(Text.of(text)); return this; }
    public ListBlock item(Content c)     { if (c != null) items.add(c); return this; }

    @Override public Box toBox() {
        BlockBox container = new BlockBox(style);
        int i = 1;
        for (Content c : items) {
            ComputedStyle itemStyle = new ComputedStyle().display(Display.LIST_ITEM);
            ListStyleType type = ordered ? ListStyleType.DECIMAL : ListStyleType.DISC;
            String marker = ordered ? type.ordinal(i) : "";
            ListItemBox li = new ListItemBox(itemStyle, type, marker);
            Box cb = c.toBox();
            if (cb != null) li.add(cb);
            container.add(li);
            i++;
        }
        return container;
    }
}
