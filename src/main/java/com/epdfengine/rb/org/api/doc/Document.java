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

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.core.FontMetricsMeasurer;
import com.epdfengine.rb.org.io.font.FontRegistry;
import com.epdfengine.rb.org.layout.ApproxTextMeasurer;
import com.epdfengine.rb.org.layout.LaidOutDocument;
import com.epdfengine.rb.org.layout.LayoutEngine;
import com.epdfengine.rb.org.layout.LayoutParams;
import com.epdfengine.rb.org.layout.TextMeasurer;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.pdfa.Conformance;
import com.epdfengine.rb.org.pdfa.DocumentMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * The root of a programmatically built document (Mode B) — the code-driven
 * equivalent of an HTML page plus its {@code @page} rule. Set the page size and
 * margins with functions, add {@link Content} blocks, and hand it to the engine:
 *
 * <pre>{@code
 * Document doc = Document.of(PageSpec.A4).margins(36)
 *     .add(Paragraph.heading(1, "Invoice #42"))
 *     .add(Table.columns("50%", "25%", "25%")
 *              .header("Item", "Qty", "Amount")
 *              .row("Widget", "2", "$9.99"))
 *     .add(Barcode.qr("https://example.org/inv/42").width(72));
 *
 * try (Epdf engine = Epdf.create()) {
 *     byte[] pdf = engine.render(RenderRequest.of(doc).build()).bytes();
 * }
 * }</pre>
 *
 * <p>Both modes converge on the same box tree, so Mode B shares Mode A's layout,
 * fonts, images, SVG, barcodes and PDF writer.</p>
 */
public final class Document {

    private LayoutParams params;
    private FontRegistry fonts;
    private Conformance conformance;
    private DocumentMetadata metadata;
    private Integer pageBackground;
    private final List<Content> children = new ArrayList<>();

    private Document(LayoutParams params) { this.params = params; }

    /** A document with the given page size and a 36pt margin. */
    public static Document of(PageSpec spec) { return new Document(spec.toLayoutParams(36)); }

    /** A4 with a 36pt margin — the common default. */
    public static Document a4() { return of(PageSpec.A4); }

    /** Uniform margin (points). */
    public Document margins(double all) { params = params.withMargins(Edges.of(all)); return this; }

    /** Per-side margins. */
    public Document margins(Edges m) { params = params.withMargins(m); return this; }

    /** Embed and use these fonts (required for PDF/A); otherwise the base-14 fonts are used. */
    public Document fonts(FontRegistry f) { this.fonts = f; return this; }

    /** Target an archival/accessible conformance level (PDF/A-2b and/or PDF/UA-1). */
    public Document conformance(Conformance c) { this.conformance = c; return this; }

    /** Document metadata (title/author/subject/language) for the Info dictionary + XMP. */
    public Document metadata(DocumentMetadata m) { this.metadata = m; return this; }

    /** A full-page background colour (0xRRGGBB). */
    public Document background(int rgb) { this.pageBackground = rgb; return this; }

    /** Appends a content block. */
    public Document add(Content c) { if (c != null) children.add(c); return this; }

    // --- accessors consumed by the render pipeline ---
    public LayoutParams params()          { return params; }
    public FontRegistry fontRegistry()    { return fonts; }
    public Conformance conformance()      { return conformance; }
    public DocumentMetadata metadata()    { return metadata; }

    /** Builds the box-tree root that the layout engine consumes. */
    public BlockBox buildRoot() {
        ComputedStyle rootStyle = new ComputedStyle().padding(Edges.of(0));
        if (pageBackground != null) rootStyle.backgroundRgb(pageBackground);
        BlockBox root = new BlockBox(rootStyle);
        for (Content c : children) {
            Box b = c.toBox();
            if (b != null) root.add(b);
        }
        return root;
    }

    /** Lays the document out (the CPU-heavy stage), producing a {@link LaidOutDocument}. */
    public LaidOutDocument layout() {
        TextMeasurer measurer = (fonts != null) ? new FontMetricsMeasurer(fonts) : new ApproxTextMeasurer();
        return new LayoutEngine().layout(buildRoot(), params, measurer);
    }
}
