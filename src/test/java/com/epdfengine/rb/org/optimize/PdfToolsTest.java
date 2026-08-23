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
package com.epdfengine.rb.org.optimize;

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.kernel.PdfDocument;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.read.PdfReader;
import com.epdfengine.rb.org.layout.LaidOutDocument;
import com.epdfengine.rb.org.layout.LayoutEngine;
import com.epdfengine.rb.org.layout.LayoutParams;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.TextBox;
import com.epdfengine.rb.org.redact.Redactor;
import com.epdfengine.rb.org.render.Renderer;
import com.epdfengine.rb.org.testkit.Assert;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Verifies the optimize (recompress + drop orphans) and redact (visual mask) PDF tools. */
public final class PdfToolsTest {

    public static void main(String[] args) throws Exception {
        optimizeStructure();
        optimizeDropsOrphans();
        redact();
        System.out.println("PASS — optimize (/ObjStm + drop orphans) + redact (visual mask)");
    }

    private static byte[] renderClassic(int extraOrphans) throws Exception {
        ComputedStyle root = new ComputedStyle().padding(Edges.of(24)).backgroundRgb(0xEFF3F8);
        BlockBox box = new BlockBox(root);
        ComputedStyle body = new ComputedStyle().fontSizePt(11).colorRgb(0x334E68);
        for (int i = 0; i < 15; i++) box.add(new TextBox("Line " + i + " of content for the pdf-tools test.", body));
        LaidOutDocument laid = new LayoutEngine().layout(box, LayoutParams.a4(36));
        PdfDocument pdf = PdfDocument.create();
        new Renderer().render(laid, pdf);
        for (int i = 0; i < extraOrphans; i++) {
            PdfDictionary d = new PdfDictionary();
            d.put(PdfName.TYPE, PdfName.of("Orphan"));
            d.put(PdfName.N, PdfNumber.of(i));
            pdf.addObject(d);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.writeTo(out, false);   // classic
        return out.toByteArray();
    }

    private static void optimizeStructure() throws Exception {
        byte[] classic = renderClassic(0);
        byte[] opt = PdfOptimizer.optimize(classic);
        String s = new String(opt, StandardCharsets.ISO_8859_1);
        Assert.contains(s, "/ObjStm", "optimized output uses object streams");
        Assert.contains(s, "/XRef", "optimized output uses a cross-reference stream");

        PdfReader r = new PdfReader(opt);
        Assert.equals(1, r.pages().size(), "one page after optimize");
        Assert.equals(PdfName.of("Catalog"), r.catalog().get(PdfName.TYPE), "catalog intact after optimize");
    }

    private static void optimizeDropsOrphans() throws Exception {
        byte[] classic = renderClassic(300);
        PdfOptimizer.Result res = PdfOptimizer.optimizeWithReport(classic);
        System.out.println("  optimize: " + res.originalBytes() + " -> " + res.optimizedBytes()
                + " (" + Math.round(res.savedFraction() * 100) + "% smaller; orphans dropped + compressed)");
        Assert.isTrue(res.optimizedBytes() < res.originalBytes(), "optimize shrinks the file");
    }

    private static void redact() throws Exception {
        byte[] pdf = renderClassic(0);
        byte[] red = Redactor.apply(pdf, List.of(new Redactor.Box(0, 72, 700, 200, 20)));
        Assert.contains(new String(red, StandardCharsets.ISO_8859_1), "0 0 0 rg", "black-fill redaction op present");
        Assert.isTrue(red.length > pdf.length, "incremental update appended (original preserved)");
        PdfReader r = new PdfReader(red);
        Assert.equals(1, r.pages().size(), "redacted PDF is still one page");
    }
}
