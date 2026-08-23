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
package com.epdfengine.rb.org.pdfa;

/**
 * A conformance target for generated PDFs. PDF/A (archival) requires embedded
 * fonts, an sRGB output intent, and XMP metadata; PDF/UA (accessibility) requires
 * a tagged structure tree, a document language, and XMP metadata. The combined
 * value produces a file that is both.
 */
public enum Conformance {
    NONE(false, false, 0),
    PDF_A_2B(true, false, 2),
    PDF_UA_1(false, true, 0),
    PDF_A_2B_UA_1(true, true, 2);

    private final boolean pdfa;
    private final boolean pdfua;
    private final int pdfaPart;

    Conformance(boolean pdfa, boolean pdfua, int pdfaPart) {
        this.pdfa = pdfa;
        this.pdfua = pdfua;
        this.pdfaPart = pdfaPart;
    }

    public boolean isPdfA()  { return pdfa; }
    public boolean isPdfUa() { return pdfua; }
    public int pdfaPart()    { return pdfaPart; }
    public String pdfaConformanceLevel() { return "B"; }
}
