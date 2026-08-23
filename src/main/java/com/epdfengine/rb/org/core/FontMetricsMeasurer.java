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
package com.epdfengine.rb.org.core;

import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.io.font.FontRegistry;
import com.epdfengine.rb.org.io.font.TrueTypeFont;
import com.epdfengine.rb.org.layout.TextMeasurer;

/**
 * A {@link TextMeasurer} backed by real font metrics from a {@link FontRegistry}.
 * Lives in the orchestration layer so {@code io.font} (below layout) is never
 * depended upon by the layout engine directly — the engine only sees the
 * {@code TextMeasurer} interface. Falls back to an em-fraction estimate when no
 * font is registered, so layout still works before any font is supplied.
 */
public final class FontMetricsMeasurer implements TextMeasurer {

    private static final double FALLBACK_CHAR_EM  = 0.50;
    private static final double FALLBACK_SPACE_EM = 0.28;

    private final FontRegistry fonts;

    public FontMetricsMeasurer(FontRegistry fonts) {
        this.fonts = fonts;
    }

    @Override public double textWidthPt(String text, ComputedStyle style) {
        if (text == null || text.isEmpty()) return 0;
        double ls = style.letterSpacingPt();
        if (fonts == null || fonts.isEmpty()) {
            return text.length() * FALLBACK_CHAR_EM * style.fontSizePt() + ls * text.length();
        }
        double size = style.fontSizePt();
        double sum = 0;
        int i = 0, count = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            TrueTypeFont f = fonts.fontForCodepoint(cp, style.fontFamily(), style.bold(), style.italic());
            sum += (f != null) ? f.advancePt(cp, size) : FALLBACK_CHAR_EM * size;
            i += Character.charCount(cp);
            count++;
        }
        if (ls != 0) sum += ls * count;
        return sum;
    }

    @Override public double spaceWidthPt(ComputedStyle style) {
        TrueTypeFont f = font(style);
        return (f != null) ? f.advancePt(' ', style.fontSizePt())
                           : FALLBACK_SPACE_EM * style.fontSizePt();
    }

    @Override public double ascentPt(ComputedStyle style) {
        TrueTypeFont f = font(style);
        return (f != null) ? f.ascentPt(style.fontSizePt()) : style.fontSizePt() * 0.80;
    }

    @Override public double descentPt(ComputedStyle style) {
        TrueTypeFont f = font(style);
        return (f != null) ? f.descentPt(style.fontSizePt()) : style.fontSizePt() * 0.20;
    }

    private TrueTypeFont font(ComputedStyle style) {
        return (fonts == null) ? null : fonts.resolve(style.fontFamily(), style.bold(), style.italic());
    }
}
