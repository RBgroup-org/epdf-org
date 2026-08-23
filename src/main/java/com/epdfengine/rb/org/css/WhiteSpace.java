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
 * The CSS {@code white-space} property: controls whitespace collapsing and line
 * wrapping of inline content.
 *
 * <ul>
 *   <li>{@link #NORMAL}   — collapse runs of whitespace; wrap at box edges.</li>
 *   <li>{@link #NOWRAP}   — collapse whitespace; never wrap.</li>
 *   <li>{@link #PRE}      — preserve whitespace and newlines; never wrap.</li>
 *   <li>{@link #PRE_WRAP} — preserve whitespace and newlines; wrap at box edges.</li>
 *   <li>{@link #PRE_LINE} — collapse spaces but preserve newlines; wrap.</li>
 * </ul>
 */
public enum WhiteSpace {
    NORMAL, NOWRAP, PRE, PRE_WRAP, PRE_LINE;

    /** True if line wrapping is allowed under this rule. */
    public boolean wraps() {
        return this == NORMAL || this == PRE_WRAP || this == PRE_LINE;
    }

    /** True if literal newlines force a line break. */
    public boolean preservesNewlines() {
        return this == PRE || this == PRE_WRAP || this == PRE_LINE;
    }

    /** True if runs of spaces are preserved (not collapsed to a single space). */
    public boolean preservesSpaces() {
        return this == PRE || this == PRE_WRAP;
    }

    public static WhiteSpace parse(String v) {
        if (v == null) return NORMAL;
        return switch (v.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "nowrap" -> NOWRAP;
            case "pre" -> PRE;
            case "pre-wrap" -> PRE_WRAP;
            case "pre-line" -> PRE_LINE;
            default -> NORMAL;
        };
    }
}
