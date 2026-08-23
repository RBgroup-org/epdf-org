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

/**
 * The CSS {@code list-style-type} property. Glyph markers (disc/circle/square)
 * are painted as small vector shapes so they render without a font that carries
 * bullet glyphs; ordinal markers (decimal/alpha/roman) are emitted as text.
 */
public enum ListStyleType {
    NONE, DISC, CIRCLE, SQUARE,
    DECIMAL, DECIMAL_LEADING_ZERO,
    LOWER_ALPHA, UPPER_ALPHA,
    LOWER_ROMAN, UPPER_ROMAN;

    /** True for the bullet shapes drawn as vectors (disc/circle/square). */
    public boolean isGlyph() {
        return this == DISC || this == CIRCLE || this == SQUARE;
    }

    /** True for numbered/lettered markers rendered as text. */
    public boolean isOrdinal() {
        return this != NONE && !isGlyph();
    }

    public static ListStyleType parse(String v) {
        if (v == null) return DISC;
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "none" -> NONE;
            case "circle" -> CIRCLE;
            case "square" -> SQUARE;
            case "decimal" -> DECIMAL;
            case "decimal-leading-zero" -> DECIMAL_LEADING_ZERO;
            case "lower-alpha", "lower-latin" -> LOWER_ALPHA;
            case "upper-alpha", "upper-latin" -> UPPER_ALPHA;
            case "lower-roman" -> LOWER_ROMAN;
            case "upper-roman" -> UPPER_ROMAN;
            default -> DISC;
        };
    }

    /** Formats the ordinal text (with trailing dot) for numbered/lettered markers. */
    public String ordinal(int n) {
        return switch (this) {
            case DECIMAL -> n + ".";
            case DECIMAL_LEADING_ZERO -> (n < 10 ? "0" + n : Integer.toString(n)) + ".";
            case LOWER_ALPHA -> alpha(n).toLowerCase(Locale.ROOT) + ".";
            case UPPER_ALPHA -> alpha(n) + ".";
            case LOWER_ROMAN -> roman(n).toLowerCase(Locale.ROOT) + ".";
            case UPPER_ROMAN -> roman(n) + ".";
            default -> "";
        };
    }

    private static String alpha(int n) {
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int r = (n - 1) % 26;
            sb.insert(0, (char) ('A' + r));
            n = (n - 1) / 26;
        }
        return sb.length() == 0 ? "A" : sb.toString();
    }

    private static String roman(int n) {
        if (n <= 0) return Integer.toString(n);
        int[] vals = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] sym = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length && n > 0; i++) {
            while (n >= vals[i]) { sb.append(sym[i]); n -= vals[i]; }
        }
        return sb.toString();
    }
}
