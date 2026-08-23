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
package com.epdfengine.rb.org.layout;

import com.epdfengine.rb.org.css.ComputedStyle;

/**
 * Supplies font metrics to the layout engine. A real implementation reads a
 * registered font's cmap/hmtx (see {@code io.font}); until then an approximate
 * measurer keeps layout functional. Kept as an interface so the engine never
 * depends on a concrete font backend.
 */
public interface TextMeasurer {

    /** Advance width of {@code text} in points at the given style. */
    double textWidthPt(String text, ComputedStyle style);

    /** Advance width of a single space in points at the given style. */
    double spaceWidthPt(ComputedStyle style);

    /** Distance from the line's top to the baseline in points. */
    default double ascentPt(ComputedStyle style) { return style.fontSizePt() * 0.80; }

    /** Distance from the baseline to the line's bottom in points. */
    default double descentPt(ComputedStyle style) { return style.fontSizePt() * 0.20; }
}
