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
package com.epdfengine.rb.org.api.doc;

import com.epdfengine.rb.org.css.TextAlign;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.read.PdfReader;
import com.epdfengine.rb.org.runtime.Epdf;
import com.epdfengine.rb.org.runtime.RenderRequest;
import com.epdfengine.rb.org.runtime.RenderResult;
import com.epdfengine.rb.org.testkit.Assert;

import java.nio.charset.StandardCharsets;

/** Verifies the Mode B (programmatic) API renders a valid PDF through the {@link Epdf} engine. */
public final class ModeBTest {

    public static void main(String[] args) throws Exception {
        String svg = "<svg viewBox='0 0 100 30'><rect width='100' height='30' rx='6' fill='#2563eb'/></svg>";

        Document doc = Document.of(PageSpec.A4).margins(40).background(0xF8FAFC)
            .add(Div.row().gap(12).padding(12).background(0x0F172A).borderRadius(8)
                    .add(SvgGraphic.of(svg).width(100).height(30))
                    .add(Paragraph.of("Mode B Invoice").color(0xFFFFFF).fontSize(18).bold().grow(1)))
            .add(Paragraph.heading(2, "Programmatic demo"))
            .add(Paragraph.of("Built with Java calls — no HTML.").color(0x334155))
            .add(Paragraph.empty().add(Text.of("Mixed ")).add(Text.bold("bold"))
                    .add(Text.of(" and ")).add(Text.colored("red", 0xDC2626)))
            .add(Div.grid("1fr 1fr 1fr").gap(10)
                    .add(card("A", "1")).add(card("B", "2")).add(card("C", "3")))
            .add(Table.columns("60%", "40%")
                    .header("Item", "Amount")
                    .row("Widget", "$9.99")
                    .add(Row.create().add(Cell.of("Total").bold())
                            .add(Cell.of("$9.99").bold().align(TextAlign.RIGHT))))
            .add(ListBlock.bulleted().item("One").item("Two").item("Three"))
            .add(Div.row().gap(16)
                    .add(Barcode.qr("https://example.org/x").width(80).height(80))
                    .add(Barcode.code128("ABC-123").width(160).height(50)));

        byte[] pdf;
        try (Epdf engine = Epdf.create()) {
            RenderResult r = engine.render(RenderRequest.of(doc).build());
            pdf = r.bytes();
            Assert.isTrue(r.byteCount() > 1000, "produced a non-trivial PDF");
        }

        String head = new String(pdf, 0, Math.min(16, pdf.length), StandardCharsets.ISO_8859_1);
        Assert.isTrue(head.startsWith("%PDF-"), "starts with the PDF header");

        PdfReader reader = new PdfReader(pdf);
        Assert.equals(PdfName.of("Catalog"), reader.catalog().get(PdfName.of("Type")), "catalog present");
        Assert.equals(1, reader.pages().size(), "single page");
        PdfObjectContentCheck(reader);

        System.out.println("  Mode B: " + pdf.length + " bytes, 1 page, catalog + content verified");
        System.out.println("PASS — Mode B programmatic API renders a valid PDF via the Epdf engine");
    }

    private static void PdfObjectContentCheck(PdfReader reader) {
        var contents = reader.resolve(reader.pages().get(0).get(PdfName.of("Contents")));
        Assert.isTrue(contents instanceof PdfStream, "page has a content stream");
        String cs = new String(reader.decoded((PdfStream) contents), StandardCharsets.ISO_8859_1);
        Assert.contains(cs, "BT", "content stream draws text");
    }

    private static Div card(String label, String value) {
        return Div.create().padding(10).background(0xFFFFFF).border(1, 0xE2E8F0).borderRadius(6)
                .add(Paragraph.of(label).fontSize(9).color(0x64748B))
                .add(Paragraph.of(value).fontSize(16).bold());
    }
}
