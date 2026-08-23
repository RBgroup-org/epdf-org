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

/**
 * The CSS {@code vertical-align} values used for table-cell content alignment.
 * {@code BASELINE} is treated as {@code TOP} for the report-style content the
 * engine targets.
 */
public enum VerticalAlign {
    TOP, MIDDLE, BOTTOM, BASELINE;

    public static VerticalAlign parse(String v) {
        if (v == null) return BASELINE;
        return switch (v.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "top" -> TOP;
            case "middle" -> MIDDLE;
            case "bottom" -> BOTTOM;
            default -> BASELINE;
        };
    }
}
