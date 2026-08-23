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
/**
 * Low-level PDF content-stream construction.
 *
 * <p><b>Implemented:</b> {@code ContentStreamBuilder} — builds a page's content
 * bytes from high-level calls: filled/stroked rectangles, text (base-14 literal
 * strings and Identity-H glyph-id hex strings), image {@code Do} with a transform,
 * and vector path primitives (save/restore state, move/line/curve, close, set
 * fill/stroke colour and line width, fill/stroke/fill+stroke). All take top-left
 * coordinates and flip y into PDF space; numbers are written without exponents.</p>
 */
package com.epdfengine.rb.org.render.content;
