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
package com.epdfengine.rb.org.render;

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.layout.LaidOutDocument;
import com.epdfengine.rb.org.layout.LayoutEngine;
import com.epdfengine.rb.org.layout.LayoutParams;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.TextBox;
import com.epdfengine.rb.org.testkit.Assert;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * End-to-end Mode B smoke test: build a box tree in Java, lay it out, render it to
 * a PDF, and assert the structural landmarks. Run with
 * {@code java -cp <build> com.epdfengine.rb.org.render.RenderSmokeTest}.
 */
public final class RenderSmokeTest {

    public static void main(String[] args) throws Exception {
        // Page background card with padding.
        ComputedStyle rootStyle = new ComputedStyle().padding(Edges.of(24)).backgroundRgb(0xEFF3F8);
        BlockBox root = new BlockBox(rootStyle);

        ComputedStyle heading = new ComputedStyle().fontSizePt(20).bold(true).colorRgb(0x102A43);
        root.add(new TextBox("ePDF Engine \u2014 Mode B render", heading));

        ComputedStyle body = new ComputedStyle().fontSizePt(11).colorRgb(0x334E68);
        root.add(new TextBox(
                "This PDF was produced from a Java box tree: layout then render into the kernel "
                + "writer. This long paragraph wraps across the content width to exercise the greedy "
                + "line breaker and page-break logic in the layout engine.", body));

        ComputedStyle card = new ComputedStyle()
                .backgroundRgb(0xFFFFFF).border(Edges.of(1)).borderColorRgb(0xBCCCDC).padding(Edges.of(12));
        BlockBox cardBox = new BlockBox(card);
        cardBox.add(new TextBox("Card content inside a bordered white box.", body));
        root.add(cardBox);

        LaidOutDocument laid = new LayoutEngine().layout(root, LayoutParams.a4(36));
        byte[] pdf = new Renderer().renderToPdf(laid);
        String s = new String(pdf, StandardCharsets.ISO_8859_1);

        Assert.isTrue(s.startsWith("%PDF-1.7"), "PDF header");
        Assert.contains(s, "/Type /Page", "page present");
        Assert.contains(s, "/FlateDecode", "content compressed");
        Assert.contains(s, "/BaseFont /Helvetica", "standard font present");
        Assert.contains(s, "xref", "xref table");
        Assert.isTrue(s.trim().endsWith("%%EOF"), "EOF marker");

        Path out = Path.of("render-modeb.pdf");
        Files.write(out, pdf);
        System.out.println("PASS — pages=" + laid.pageCount() + ", bytes=" + pdf.length
                + " -> " + out.toAbsolutePath());
    }
}
