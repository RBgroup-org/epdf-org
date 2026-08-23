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

/** The CSS {@code justify-content} main-axis distribution for flex/grid containers. */
public enum JustifyContent {
    START, END, CENTER, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY;

    public static JustifyContent parse(String v) {
        if (v == null) return START;
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "flex-end", "end", "right" -> END;
            case "center" -> CENTER;
            case "space-between" -> SPACE_BETWEEN;
            case "space-around" -> SPACE_AROUND;
            case "space-evenly" -> SPACE_EVENLY;
            default -> START;
        };
    }
}
