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
 * How an HTML design is fitted to the chosen page size (selected via the custom
 * {@code @page { -epdf-fit: … }} descriptor).
 *
 * <ul>
 *   <li>{@link #REFLOW}     — normal flow: content reflows to the page content width
 *       and paginates. (default)</li>
 *   <li>{@link #FIT_WIDTH}  — lay the design out at its natural width, then uniformly
 *       scale so that width fills the page width; taller designs paginate. Structure
 *       is unchanged (only scaled), like fitting an image to the page width.</li>
 *   <li>{@link #FIT_PAGE}   — scale the whole design uniformly so it fits a single page
 *       (both axes). Structure unchanged, like an image fitted onto one page.</li>
 * </ul>
 */
public enum FitMode {
    REFLOW, FIT_WIDTH, FIT_PAGE;

    public static FitMode parse(String v) {
        if (v == null) return REFLOW;
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "scale-width", "fit-width", "width" -> FIT_WIDTH;
            case "fit-page", "one-page", "scale", "contain" -> FIT_PAGE;
            default -> REFLOW;
        };
    }
}
