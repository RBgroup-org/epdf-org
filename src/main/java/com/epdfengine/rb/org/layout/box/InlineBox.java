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
 * An inline-level box (e.g. a {@code <span>} or {@code <a>}) that flows within a
 * line alongside text and other inline boxes rather than starting a new block.
 */
public class InlineBox extends Box {

    public InlineBox() { super(new ComputedStyle()); }

    public InlineBox(ComputedStyle style) { super(style); }

    @Override public boolean isBlockLevel()  { return false; }
    @Override public boolean isInlineLevel() { return true; }
}
