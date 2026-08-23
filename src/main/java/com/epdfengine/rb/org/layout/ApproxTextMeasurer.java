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
 * A crude, font-free text measurer used until {@code io.font} provides real
 * metrics. Approximates every glyph as a fixed fraction of the em, which is good
 * enough to exercise wrapping and pagination but not for final typographic
 * fidelity. Bold widens slightly.
 */
public final class ApproxTextMeasurer implements TextMeasurer {

    private static final double CHAR_EM  = 0.50; // average glyph advance ≈ 0.5em
    private static final double SPACE_EM = 0.28;

    @Override public double textWidthPt(String text, ComputedStyle style) {
        if (text == null || text.isEmpty()) return 0;
        double per = CHAR_EM * style.fontSizePt() * (style.bold() ? 1.06 : 1.0);
        double w = text.length() * per;
        if (style.letterSpacingPt() != 0) w += style.letterSpacingPt() * text.length();
        return w;
    }

    @Override public double spaceWidthPt(ComputedStyle style) {
        return SPACE_EM * style.fontSizePt();
    }
}
