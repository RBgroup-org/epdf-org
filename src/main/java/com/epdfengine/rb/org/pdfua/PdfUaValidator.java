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
package com.epdfengine.rb.org.pdfua;

import com.epdfengine.rb.org.layout.StructNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Structure-tree sanity checks mirroring the ISO 14289-1 (PDF/UA-1) requirements
 * that iText's heading checker enforces: numbered headings (H1–H6) must start at
 * H1 and must not skip a level going deeper (e.g. an H1 directly followed by an
 * H3). These are advisory checks over the {@link StructNode} registry; a clean
 * result does not replace an external validator such as veraPDF or PAC.
 */
public final class PdfUaValidator {

    private PdfUaValidator() {}

    public static List<String> validateHeadings(List<StructNode> nodes) {
        List<String> issues = new ArrayList<>();
        if (nodes == null) return issues;
        int previousLevel = 0;
        boolean sawHeading = false;
        for (StructNode n : nodes) {
            int level = headingLevel(n.role());
            if (level == 0) continue;
            if (!sawHeading) {
                if (level != 1) {
                    issues.add("PDF/UA: first heading is H" + level + " (documents should start at H1)");
                }
                sawHeading = true;
            } else if (level > previousLevel + 1) {
                issues.add("PDF/UA: heading level skips from H" + previousLevel + " to H" + level
                        + " (levels must not be skipped going deeper)");
            }
            previousLevel = level;
        }
        return issues;
    }

    private static int headingLevel(String role) {
        if (role != null && role.length() == 2 && role.charAt(0) == 'H'
                && role.charAt(1) >= '1' && role.charAt(1) <= '6') {
            return role.charAt(1) - '0';
        }
        return 0;
    }
}
