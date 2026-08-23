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
package com.epdfengine.rb.org.kernel;

/**
 * The 14 standard Type1 base fonts guaranteed by every PDF viewer (ISO 32000-1
 * §9.6.2.2). Referencing them needs no embedding, so they carry no font files in
 * the jar — consistent with the "no bundled fonts" policy. Custom/embedded fonts
 * (with real metrics and Unicode) arrive with the {@code io.font} subsystem.
 */
public enum StandardFont {

    HELVETICA("Helvetica"),
    HELVETICA_BOLD("Helvetica-Bold"),
    HELVETICA_OBLIQUE("Helvetica-Oblique"),
    HELVETICA_BOLD_OBLIQUE("Helvetica-BoldOblique"),

    TIMES_ROMAN("Times-Roman"),
    TIMES_BOLD("Times-Bold"),
    TIMES_ITALIC("Times-Italic"),
    TIMES_BOLD_ITALIC("Times-BoldItalic"),

    COURIER("Courier"),
    COURIER_BOLD("Courier-Bold"),
    COURIER_OBLIQUE("Courier-Oblique"),
    COURIER_BOLD_OBLIQUE("Courier-BoldOblique"),

    SYMBOL("Symbol"),
    ZAPF_DINGBATS("ZapfDingbats");

    private final String baseFont;

    StandardFont(String baseFont) { this.baseFont = baseFont; }

    /** The PDF {@code /BaseFont} name. */
    public String baseFont() { return baseFont; }
}
