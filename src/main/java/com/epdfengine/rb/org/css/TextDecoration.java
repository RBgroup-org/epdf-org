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
package com.epdfengine.rb.org.css;

import java.util.Locale;

/** The CSS {@code text-decoration-line} values the painter draws under/through text. */
public enum TextDecoration {
    NONE, UNDERLINE, LINE_THROUGH, UNDERLINE_LINE_THROUGH;

    public boolean hasUnderline()   { return this == UNDERLINE || this == UNDERLINE_LINE_THROUGH; }
    public boolean hasLineThrough() { return this == LINE_THROUGH || this == UNDERLINE_LINE_THROUGH; }

    public static TextDecoration parse(String v) {
        if (v == null) return NONE;
        String s = v.toLowerCase(Locale.ROOT);
        boolean u = s.contains("underline");
        boolean l = s.contains("line-through");
        if (u && l) return UNDERLINE_LINE_THROUGH;
        if (u) return UNDERLINE;
        if (l) return LINE_THROUGH;
        return NONE;
    }
}
