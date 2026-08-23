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

import com.epdfengine.rb.org.kernel.PdfDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * A lightweight, generation-side conformance checker. It verifies the invariants
 * epdf-org controls while producing a document (embedded fonts, metadata, output
 * intent, marking, language, structure tree). It is not a full external PDF/A or
 * PDF/UA verifier — a document that passes these checks is still worth validating
 * with veraPDF for archival use.
 */
public final class ConformanceValidator {

    private ConformanceValidator() {}

    /** Returns the list of issues; an empty list means the checked invariants hold. */
    public static List<String> validate(PdfDocument doc, Conformance c) {
        List<String> issues = new ArrayList<>();
        if (doc == null || c == null || c == Conformance.NONE) return issues;

        if (c.isPdfA()) {
            if (doc.usesStandardFonts())
                issues.add("PDF/A: non-embedded standard-14 fonts in use — all fonts must be embedded");
            if (!doc.hasXmpMetadata())
                issues.add("PDF/A: missing XMP metadata packet");
            if (!doc.hasOutputIntent())
                issues.add("PDF/A: missing sRGB OutputIntent");
        }
        if (c.isPdfUa()) {
            if (!doc.isTaggedMarked())
                issues.add("PDF/UA: document not marked (/MarkInfo /Marked true)");
            if (!doc.hasLanguage())
                issues.add("PDF/UA: missing document /Lang");
            if (!doc.hasDisplayDocTitle())
                issues.add("PDF/UA: missing /ViewerPreferences /DisplayDocTitle true");
            if (!doc.hasStructTreeRoot())
                issues.add("PDF/UA: missing StructTreeRoot (tagged content structure)");
        }
        return issues;
    }

    public static boolean isConformant(PdfDocument doc, Conformance c) {
        return validate(doc, c).isEmpty();
    }
}
