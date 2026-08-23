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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Applies a {@link Conformance} target to a built {@link PdfDocument}: writes a
 * matching Info dictionary and XMP metadata packet, a document language, an sRGB
 * output intent (PDF/A), and the tagged-document mark (PDF/UA). Must be called
 * after content is added and before the document is written.
 */
public final class ConformanceApplier {

    private ConformanceApplier() {}

    public static void apply(PdfDocument doc, Conformance c, DocumentMetadata m) {
        if (doc == null || c == null || c == Conformance.NONE) return;
        if (m == null) m = new DocumentMetadata();

        OffsetDateTime now = OffsetDateTime.now();
        String xmpDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
        String pdfDate = "D:" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + pdfOffset(now.getOffset());

        doc.setDocumentInfo(m.title(), m.author(), m.producer(), pdfDate);
        doc.setXmpMetadata(XmpMetadata.build(c, m, xmpDate).getBytes(StandardCharsets.UTF_8));
        doc.setLanguage(m.language());
        if (c.isPdfA())  doc.addSrgbOutputIntent(SrgbProfile.iccBytes(), 3, "sRGB IEC61966-2.1");
        if (c.isPdfUa()) {
            doc.setMarked(true);
            doc.setDisplayDocTitle(true);   // ISO 14289: readers must show the title, not the file name
        }
    }

    /** PDF date timezone suffix, e.g. {@code +05'30'} or {@code Z}. */
    private static String pdfOffset(ZoneOffset off) {
        int s = off.getTotalSeconds();
        if (s == 0) return "Z";
        String sign = s < 0 ? "-" : "+";
        s = Math.abs(s);
        return String.format("%s%02d'%02d'", sign, s / 3600, (s % 3600) / 60);
    }
}
