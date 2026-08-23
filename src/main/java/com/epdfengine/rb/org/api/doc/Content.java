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

import com.epdfengine.rb.org.layout.box.Box;

/**
 * A piece of document content in the programmatic (Mode B) model. Every builder —
 * {@link Div}, {@link Paragraph}, {@link Table}, {@link Image}, {@link Barcode},
 * {@link SvgGraphic}, {@link ListBlock} — produces a {@link Box}, so Mode B and the
 * markup pipeline (Mode A) converge on the same box tree and share the same
 * layout/render path.
 */
public interface Content {

    /** Materialises this content as a layout {@link Box}. May decode images/encode barcodes. */
    Box toBox();
}
