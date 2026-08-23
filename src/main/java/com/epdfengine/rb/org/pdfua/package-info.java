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
/**
 * PDF/UA-1 (ISO 14289-1) accessibility. The logical structure tree, marked
 * content and MCID management live in {@code tagged}; catalog conformance bits
 * (MarkInfo/Marked, Lang, ViewerPreferences/DisplayDocTitle) and validation are
 * applied by {@code pdfa} via the combined {@code PDF_A_2B_UA_1} conformance.
 * This package adds the PDF/UA-specific structure checks — see
 * {@link com.epdfengine.rb.org.pdfua.PdfUaValidator} (heading hierarchy).
 */
package com.epdfengine.rb.org.pdfua;
