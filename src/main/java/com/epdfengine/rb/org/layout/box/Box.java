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

import com.epdfengine.rb.org.common.Rect;
import com.epdfengine.rb.org.css.ComputedStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the box tree — the single intermediate representation that both the
 * markup pipeline (HTML/CSS) and the programmatic API produce and the layout
 * engine consumes.
 *
 * <p>A box owns a {@link ComputedStyle} and child boxes. During layout the engine
 * fills in the four nested rectangles (margin ⊃ border ⊃ padding ⊃ content); the
 * painter reads them to draw backgrounds, borders, and content.</p>
 */
public abstract class Box {

    private final ComputedStyle style;
    private final List<Box> children = new ArrayList<>();

    // Geometry filled during layout (top-left origin, y downward).
    private Rect marginRect;
    private Rect borderRect;
    private Rect paddingRect;
    private Rect contentRect;

    protected Box(ComputedStyle style) {
        this.style = (style != null) ? style : new ComputedStyle();
    }

    public ComputedStyle style() { return style; }

    public List<Box> children() { return children; }

    /** Appends a child (ignored if {@code null}); returns this for chaining. */
    public Box add(Box child) {
        if (child != null) children.add(child);
        return this;
    }

    public boolean isLeaf() { return children.isEmpty(); }

    /** True for block-level boxes (participate in a block formatting context). */
    public abstract boolean isBlockLevel();

    /** True for inline-level boxes (participate in an inline formatting context). */
    public abstract boolean isInlineLevel();

    // --- layout geometry accessors ---
    public Rect marginRect()  { return marginRect; }
    public Rect borderRect()  { return borderRect; }
    public Rect paddingRect() { return paddingRect; }
    public Rect contentRect() { return contentRect; }

    public void setMarginRect(Rect r)  { this.marginRect = r; }
    public void setBorderRect(Rect r)  { this.borderRect = r; }
    public void setPaddingRect(Rect r) { this.paddingRect = r; }
    public void setContentRect(Rect r) { this.contentRect = r; }
}
