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
package com.epdfengine.rb.org.runtime;

import com.epdfengine.rb.org.api.doc.Document;
import com.epdfengine.rb.org.core.HtmlRenderer;
import com.epdfengine.rb.org.core.ResourceLoader;
import com.epdfengine.rb.org.io.font.FontRegistry;
import com.epdfengine.rb.org.layout.LaidOutDocument;
import com.epdfengine.rb.org.layout.LayoutParams;
import com.epdfengine.rb.org.pdfa.Conformance;
import com.epdfengine.rb.org.pdfa.DocumentMetadata;
import com.epdfengine.rb.org.render.Renderer;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An immutable description of one render job submitted to {@link Epdf}. Built with a
 * fluent {@link Builder}; carries only the inputs, so the same request can be submitted
 * repeatedly and safely from multiple threads.
 */
public final class RenderRequest {

    private final String html;
    private final Document document;           // Mode B source (nullable when html is set)
    private final LayoutParams params;
    private final FontRegistry fonts;         // nullable → base-14 fonts
    private final ResourceLoader resources;   // nullable → no external resources
    private final Conformance conformance;    // nullable → no PDF/A-UA target
    private final DocumentMetadata meta;      // nullable
    private final ResourceLimits limits;      // nullable → engine defaults

    private RenderRequest(Builder b) {
        this.html = b.html;
        this.document = b.document;
        this.params = (b.params != null) ? b.params : LayoutParams.a4(36);
        this.fonts = b.fonts;
        this.resources = b.resources;
        this.conformance = b.conformance;
        this.meta = b.meta;
        this.limits = b.limits;
    }

    /** Starts a builder for {@code html} (Mode A — layout defined in markup). A4 geometry unless overridden. */
    public static Builder of(String html) { return new Builder(html, null); }

    /** Starts a builder for a programmatic {@link Document} (Mode B — layout defined in Java). */
    public static Builder of(Document document) { return new Builder(null, document); }

    public ResourceLimits limits() { return limits; }

    /** Runs the render on the calling worker thread within the job {@code ctx}. */
    byte[] execute(RenderContext ctx) throws IOException {
        if (document != null) return executeDocument(ctx);
        if (html == null) throw new EngineException("render request has no HTML source");
        ctx.limits().checkInputBytes((long) html.length() * 2L);   // UTF-16 upper bound
        ctx.checkAlive();
        HtmlRenderer renderer = new HtmlRenderer();
        PipelineTrace tr = ctx.trace();
        if (tr == null) {
            byte[] out = (conformance != null)
                    ? renderer.render(html, params, fonts, resources, conformance, meta)
                    : renderer.render(html, params, fonts, resources);
            ctx.checkAlive();
            return out;
        }
        try {
            if (conformance != null) {
                tr.enter("render");
                byte[] out = renderer.render(html, params, fonts, resources, conformance, meta);
                tr.exit();
                ctx.checkAlive();
                return out;
            }
            tr.enter("layout");
            LaidOutDocument laid = renderer.layout(html, params, fonts, resources);
            tr.exit();
            ctx.checkAlive();
            tr.enter("serialize");
            byte[] out = new Renderer().renderToPdf(laid, fonts);
            tr.exit();
            return out;
        } catch (RuntimeException | IOException e) {
            tr.fail(e);
            throw e;
        }
    }

    /** Mode B: lay out the programmatic document, then serialize to PDF bytes. */
    private byte[] executeDocument(RenderContext ctx) throws IOException {
        ctx.checkAlive();
        Conformance conf = (conformance != null) ? conformance : document.conformance();
        DocumentMetadata dm = (meta != null) ? meta : document.metadata();
        FontRegistry df = (fonts != null) ? fonts : document.fontRegistry();
        PipelineTrace tr = ctx.trace();
        try {
            if (tr != null) tr.enter("layout");
            LaidOutDocument laid = document.layout();
            if (tr != null) tr.exit();
            ctx.checkAlive();
            if (tr != null) tr.enter("serialize");
            Renderer r = new Renderer();
            byte[] out = (conf != null) ? r.renderToPdf(laid, df, conf, dm) : r.renderToPdf(laid, df);
            if (tr != null) tr.exit();
            return out;
        } catch (RuntimeException | IOException e) {
            if (tr != null) tr.fail(e);
            throw e;
        }
    }

    /** Streams the render straight to {@code out}, returning the number of bytes written. */
    long executeTo(RenderContext ctx, OutputStream out) throws IOException {
        if (document != null) return executeDocumentTo(ctx, out);
        if (html == null) throw new EngineException("render request has no HTML source");
        ctx.limits().checkInputBytes((long) html.length() * 2L);
        ctx.checkAlive();
        CountingOutputStream counting = new CountingOutputStream(out);
        HtmlRenderer renderer = new HtmlRenderer();
        PipelineTrace tr = ctx.trace();
        if (tr == null) {
            if (conformance != null) renderer.renderTo(html, params, fonts, resources, conformance, meta, counting);
            else                     renderer.renderTo(html, params, fonts, resources, counting);
            ctx.checkAlive();
            return counting.count();
        }
        try {
            if (conformance != null) {
                tr.enter("render");
                renderer.renderTo(html, params, fonts, resources, conformance, meta, counting);
                tr.exit();
            } else {
                tr.enter("layout");
                LaidOutDocument laid = renderer.layout(html, params, fonts, resources);
                tr.exit();
                ctx.checkAlive();
                tr.enter("serialize");
                new Renderer().renderTo(laid, fonts, counting);
                tr.exit();
            }
            ctx.checkAlive();
            return counting.count();
        } catch (RuntimeException | IOException e) {
            tr.fail(e);
            throw e;
        }
    }

    /** Mode B streamed: lay out the programmatic document and write PDF straight to {@code out}. */
    private long executeDocumentTo(RenderContext ctx, OutputStream out) throws IOException {
        ctx.checkAlive();
        Conformance conf = (conformance != null) ? conformance : document.conformance();
        DocumentMetadata dm = (meta != null) ? meta : document.metadata();
        FontRegistry df = (fonts != null) ? fonts : document.fontRegistry();
        CountingOutputStream counting = new CountingOutputStream(out);
        LaidOutDocument laid = document.layout();
        ctx.checkAlive();
        Renderer r = new Renderer();
        if (conf != null) r.renderTo(laid, df, conf, dm, counting);
        else              r.renderTo(laid, df, counting);
        ctx.checkAlive();
        return counting.count();
    }

    /** Counts bytes forwarded to the delegate stream (for the streamed-result size). */
    private static final class CountingOutputStream extends java.io.FilterOutputStream {
        private long count;
        CountingOutputStream(OutputStream out) { super(out); }
        long count() { return count; }
        @Override public void write(int b) throws IOException { out.write(b); count++; }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); count += len; }
    }

    /** Fluent builder for {@link RenderRequest}. */
    public static final class Builder {
        private final String html;
        private final Document document;
        private LayoutParams params;
        private FontRegistry fonts;
        private ResourceLoader resources;
        private Conformance conformance;
        private DocumentMetadata meta;
        private ResourceLimits limits;

        private Builder(String html, Document document) { this.html = html; this.document = document; }

        public Builder params(LayoutParams p)      { this.params = p; return this; }
        public Builder fonts(FontRegistry f)       { this.fonts = f; return this; }
        public Builder resources(ResourceLoader r) { this.resources = r; return this; }
        public Builder conformance(Conformance c)  { this.conformance = c; return this; }
        public Builder metadata(DocumentMetadata m){ this.meta = m; return this; }
        public Builder limits(ResourceLimits l)    { this.limits = l; return this; }

        public RenderRequest build() { return new RenderRequest(this); }
    }
}
