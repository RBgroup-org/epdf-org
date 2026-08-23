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

import com.epdfengine.rb.org.testkit.Assert;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * M1 smoke test: build a one-page PDF (a filled blue rectangle — no fonts needed)
 * and assert the structural landmarks are present. Run with:
 * {@code java -cp <build> com.epdfengine.rb.org.kernel.KernelSmokeTest}.
 */
public final class KernelSmokeTest {

    public static void main(String[] args) throws Exception {
        PdfDocument doc = PdfDocument.create();
        // Content stream: blue fill, rectangle at (100,100) size 200x300, fill.
        String content = "0 0 1 rg\n100 100 200 300 re\nf\n";
        doc.addPage(612, 792, content.getBytes(StandardCharsets.US_ASCII));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.writeTo(bos);
        byte[] pdf = bos.toByteArray();
        String s = new String(pdf, StandardCharsets.ISO_8859_1);

        Assert.isTrue(s.startsWith("%PDF-1.7"), "PDF header");
        Assert.contains(s, "/Type /Catalog", "catalog present");
        Assert.contains(s, "/Type /Pages", "page tree present");
        Assert.contains(s, "/Type /Page", "page present");
        Assert.contains(s, "/MediaBox [0 0 612 792]", "media box");
        Assert.contains(s, "stream", "content stream");
        Assert.contains(s, "xref", "xref table");
        Assert.contains(s, "trailer", "trailer");
        Assert.isTrue(s.trim().endsWith("%%EOF"), "EOF marker");

        Path outFile = Path.of("hello.pdf");
        Files.write(outFile, pdf);
        System.out.println("PASS — wrote " + pdf.length + " bytes to " + outFile.toAbsolutePath());
    }
}
