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
 * The box tree — the single intermediate representation both the markup pipeline
 * and the programmatic (Mode B) API produce and the layout engine consumes.
 *
 * <p>{@code Box} (abstract: style + children + the four nested rectangles
 * margin ⊃ border ⊃ padding ⊃ content), {@code BlockBox}, {@code InlineBox},
 * {@code TextBox} (leaf text run), {@code ImageBox} (raster), {@code SvgBox}
 * (vector/raster SVG), {@code TableCellBox} (colspan/rowspan), {@code ListItemBox}
 * (marker), {@code FormFieldBox} (AcroForm control) and {@code BreakBox}
 * ({@code <br>}). Flex and grid are expressed by the {@code display} on a
 * {@code BlockBox}; the layout engine interprets it.</p>
 */
package com.epdfengine.rb.org.layout.box;
