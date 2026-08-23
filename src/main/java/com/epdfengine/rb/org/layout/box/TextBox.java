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
 * A leaf inline box holding a run of text. Text boxes carry no children; the
 * inline formatting context breaks their text into lines using font metrics.
 */
public final class TextBox extends Box {

    private final String text;

    public TextBox(String text, ComputedStyle style) {
        super(style);
        this.text = (text != null) ? text : "";
    }

    public String text() { return text; }

    @Override public Box add(Box child) {
        throw new UnsupportedOperationException("TextBox is a leaf and cannot have children");
    }

    @Override public boolean isBlockLevel()  { return false; }
    @Override public boolean isInlineLevel() { return true; }
}
