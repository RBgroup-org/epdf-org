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
import com.epdfengine.rb.org.kernel.object.PdfName;
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
import java.nio.charset.StandardCharsets;

/** Verifies the linearized (fast-web-view) writer: valid structure, correct /L, reader round-trip. */
public final class LinearizeTest {

    public static void main(String[] args) throws Exception {
        byte[] classic = renderClassic();
        byte[] lin = Linearizer.linearize(classic);
        String s = new String(lin, StandardCharsets.ISO_8859_1);

        Assert.contains(s, "/Linearized 1", "linearization parameter dictionary present");
        int linPos = s.indexOf("/Linearized");
        int catPos = s.indexOf("/Type /Catalog");
        Assert.isTrue(catPos < 0 || linPos < catPos, "linearization dict is the first object (before the catalog)");

        int lp = s.indexOf("/L ");
        long declaredL = Long.parseLong(s.substring(lp + 3, lp + 13).trim());
        Assert.equals(lin.length, declaredL, "/L equals the actual file length");
        Assert.contains(s, "/N 1", "/N page count");
        Assert.isTrue(s.trim().endsWith("%%EOF"), "ends with %%EOF");

        // The engine's own reader must read the linearized file (dual xref via /Prev).
        PdfReader r = new PdfReader(lin);
        Assert.equals(PdfName.of("Catalog"), r.catalog().get(PdfName.of("Type")), "catalog intact");
        Assert.equals(1, r.pages().size(), "page count preserved");
        PdfObject contents = r.resolve(r.pages().get(0).get(PdfName.of("Contents")));
        Assert.isTrue(contents instanceof PdfStream, "page content present");
        Assert.contains(new String(r.decoded((PdfStream) contents), StandardCharsets.ISO_8859_1), "BT",
                "content stream decodes");

        System.out.println("  linearized: " + classic.length + " -> " + lin.length
                + " bytes (first-page-first, /Linearized dict, dual xref)");
        System.out.println("PASS — linearization (fast web view): valid structure + reader round-trip");
    }

    private static byte[] renderClassic() throws Exception {
        ComputedStyle root = new ComputedStyle().padding(Edges.of(24)).backgroundRgb(0xEFF3F8);
        BlockBox box = new BlockBox(root);
        ComputedStyle body = new ComputedStyle().fontSizePt(11).colorRgb(0x334E68);
        for (int i = 0; i < 12; i++) box.add(new TextBox("Linearization test content line " + i + ".", body));
        LaidOutDocument laid = new LayoutEngine().layout(box, LayoutParams.a4(36));
        PdfDocument pdf = PdfDocument.create();
        new Renderer().render(laid, pdf);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.writeTo(out, false);
        return out.toByteArray();
    }
}
