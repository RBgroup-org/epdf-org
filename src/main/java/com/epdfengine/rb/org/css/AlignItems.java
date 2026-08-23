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

/** The CSS {@code align-items} cross-axis alignment for flex/grid containers. */
public enum AlignItems {
    STRETCH, START, CENTER, END, BASELINE;

    public static AlignItems parse(String v) {
        if (v == null) return STRETCH;
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "flex-start", "start" -> START;
            case "center" -> CENTER;
            case "flex-end", "end" -> END;
            case "baseline" -> BASELINE;
            default -> STRETCH;
        };
    }
}
