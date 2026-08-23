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

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.read.PdfReader;
import com.epdfengine.rb.org.layout.LaidOutDocument;
import com.epdfengine.rb.org.layout.LayoutEngine;
import com.epdfengine.rb.org.layout.LayoutParams;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.TextBox;
import com.epdfengine.rb.org.render.Renderer;
import com.epdfengine.rb.org.testkit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Verifies the compressed writer ({@code /ObjStm} object streams + {@code /XRef} cross-reference
 * stream): the output is smaller than the classic form and round-trips through our own reader —
 * the first end-to-end exercise of the compressed write and read paths together.
 */
public final class CompressedWriteTest {

    public static void main(String[] args) throws Exception {
        sizeWinAndStructure();
        readerRoundTrip();
        System.out.println("PASS — compressed writer (/ObjStm + /XRef): size win + reader round-trip");
    }

    private static PdfDocument sampleDoc() {
        ComputedStyle root = new ComputedStyle().padding(Edges.of(24)).backgroundRgb(0xEFF3F8);
        BlockBox box = new BlockBox(root);
        ComputedStyle body = new ComputedStyle().fontSizePt(11).colorRgb(0x334E68);
        for (int i = 0; i < 12; i++) {
            box.add(new TextBox("Compressed object streams, line " + i + ", with words to fill the stream.", body));
        }
        LaidOutDocument laid = new LayoutEngine().layout(box, LayoutParams.a4(36));
        PdfDocument pdf = PdfDocument.create();
        new Renderer().render(laid, pdf);
        return pdf;
    }

    private static byte[] write(PdfDocument pdf, boolean compressed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.writeTo(out, compressed);
        return out.toByteArray();
    }

    private static PdfDictionary probe(int n) {
        PdfDictionary d = new PdfDictionary();
        d.put(PdfName.TYPE, PdfName.of("Probe"));
        d.put(PdfName.N, PdfNumber.of(n));
        return d;
    }

    private static void sizeWinAndStructure() throws IOException {
        PdfDocument pdf = sampleDoc();
        for (int i = 0; i < 300; i++) pdf.addObject(probe(i));   // many small dicts → exercise /ObjStm packing

        byte[] classic = write(pdf, false);
        byte[] comp = write(pdf, true);
        System.out.println("  classic=" + classic.length + "  compressed=" + comp.length
                + "  (" + (100 - comp.length * 100 / classic.length) + "% smaller)");
        Assert.isTrue(comp.length < classic.length, "compressed is smaller than classic");

        String s = new String(comp, StandardCharsets.ISO_8859_1);
        Assert.contains(s, "/ObjStm", "object stream present");
        Assert.contains(s, "/XRef", "cross-reference stream present");
        Assert.isTrue(!s.contains("\ntrailer\n"), "no classic trailer in compressed output");
        Assert.isTrue(s.startsWith("%PDF-1.7"), "PDF header");
        Assert.isTrue(s.trim().endsWith("%%EOF"), "EOF marker");
    }

    private static void readerRoundTrip() throws IOException {
        PdfDocument pdf = sampleDoc();
        int probeNum = pdf.addObject(probe(777));
        byte[] comp = write(pdf, true);

        PdfReader reader = new PdfReader(comp);
        PdfDictionary catalog = reader.catalog();
        Assert.isTrue(catalog != null, "catalog resolved via /XRef stream");
        Assert.equals(PdfName.of("Catalog"), catalog.get(PdfName.TYPE), "catalog /Type");
        Assert.equals(1L, reader.pages().size(), "one page read back");

        // A packed (type-2, inside /ObjStm) object round-trips.
        PdfObject probed = reader.get(probeNum);
        Assert.isTrue(probed instanceof PdfDictionary, "packed object read from /ObjStm");
        Assert.equals(PdfName.of("Probe"), ((PdfDictionary) probed).get(PdfName.TYPE), "packed /Type intact");
        Assert.isTrue(((PdfDictionary) probed).get(PdfName.N) instanceof PdfNumber, "packed value intact");

        // The page content stream decodes.
        PdfObject contents = reader.resolve(reader.pages().get(0).get(PdfName.of("Contents")));
        Assert.isTrue(contents instanceof PdfStream, "content stream present");
        String content = new String(reader.decoded((PdfStream) contents), StandardCharsets.ISO_8859_1);
        Assert.contains(content, "BT", "content stream has text operators");
    }
}
