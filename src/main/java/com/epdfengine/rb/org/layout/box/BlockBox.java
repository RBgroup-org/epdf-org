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
 * A block-level box: it stacks vertically among its siblings and establishes (or
 * participates in) a block formatting context. Divs, paragraphs, headings, and
 * table structure boxes are all block-level.
 */
public class BlockBox extends Box {

    public BlockBox() { super(new ComputedStyle()); }

    public BlockBox(ComputedStyle style) { super(style); }

    @Override public boolean isBlockLevel()  { return true; }
    @Override public boolean isInlineLevel() { return false; }
}
