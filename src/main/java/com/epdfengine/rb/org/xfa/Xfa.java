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
package com.epdfengine.rb.org.xfa;

import com.epdfengine.rb.org.forms.FormFiller;
import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfIndirectReference;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNull;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.object.PdfString;
import com.epdfengine.rb.org.kernel.read.PdfEditor;
import com.epdfengine.rb.org.kernel.read.PdfReader;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XFA (XML Forms Architecture) detection and flattening. XFA data lives in the
 * AcroForm {@code /XFA} entry — either a single XDP stream or an array of
 * {@code (name) stream} pairs (config, template, datasets, form, …). "Flattening"
 * strips the XFA layer so the document renders as its static AcroForm in any
 * viewer (the practical, viewer-independent behaviour; full dynamic-XFA re-layout
 * is out of scope). Utilities to detect XFA and extract its XML packets are also
 * provided.
 */
public final class Xfa {

    private static final PdfName ACRO = PdfName.of("AcroForm");
    private static final PdfName XFA = PdfName.of("XFA");
    private static final PdfName NEEDS_RENDERING = PdfName.of("NeedsRendering");

    private Xfa() {}

    /** True when the document carries an XFA form in {@code /AcroForm /XFA}. */
    public static boolean isXfa(byte[] pdf) {
        PdfReader r = new PdfReader(pdf);
        PdfDictionary acro = acroForm(r);
        return acro != null && !(r.resolve(acro.get(XFA)) instanceof PdfNull) && acro.get(XFA) != null;
    }

    /** True for <b>dynamic</b> XFA (catalog {@code /NeedsRendering true}) — needs an XFA engine to lay out. */
    public static boolean isDynamic(byte[] pdf) {
        PdfReader r = new PdfReader(pdf);
        PdfDictionary cat = r.catalog();
        return cat != null && r.resolve(cat.get(NEEDS_RENDERING)) instanceof com.epdfengine.rb.org.kernel.object.PdfBoolean b && b.value();
    }

    /** Extracts the XFA packets as XML text keyed by packet name (e.g. {@code template}, {@code datasets}). */
    public static Map<String, String> packets(byte[] pdf) {
        PdfReader r = new PdfReader(pdf);
        Map<String, String> out = new LinkedHashMap<>();
        PdfDictionary acro = acroForm(r);
        if (acro == null) return out;
        PdfObject xfa = r.resolve(acro.get(XFA));
        if (xfa instanceof PdfStream s) {
            out.put("xdp", text(r.decoded(s)));
        } else if (xfa instanceof PdfArray a) {
            for (int i = 0; i + 1 < a.size(); i += 2) {
                String name = str(r.resolve(a.get(i)));
                PdfObject part = r.resolve(a.get(i + 1));
                if (name != null && part instanceof PdfStream ps) out.put(name, text(r.decoded(ps)));
            }
        }
        return out;
    }

    /** The XFA {@code datasets} packet (the form data), or null. */
    public static String datasets(byte[] pdf) { return packets(pdf).get("datasets"); }

    /** Removes the XFA layer, leaving the static AcroForm (incremental update). */
    public static byte[] removeXfa(byte[] pdf) {
        PdfEditor ed = new PdfEditor(pdf);
        PdfReader r = ed.reader();
        PdfDictionary cat = r.catalog();
        if (cat == null) return pdf;

        PdfObject acroRef = cat.get(ACRO);
        if (acroRef instanceof PdfIndirectReference ar && r.get(ar.objectNumber()) instanceof PdfDictionary acro) {
            if (acro.remove(XFA) != null) ed.setObject(ar.objectNumber(), acro);
        }
        if (cat.remove(NEEDS_RENDERING) != null && r.rootRef() instanceof PdfIndirectReference cr)
            ed.setObject(cr.objectNumber(), cat);
        return ed.save();
    }

    /** Strips XFA and then flattens the remaining AcroForm to fully static content. */
    public static byte[] flatten(byte[] pdf) {
        return FormFiller.flatten(removeXfa(pdf));
    }

    // ---- helpers -----------------------------------------------------------

    private static PdfDictionary acroForm(PdfReader r) {
        PdfDictionary cat = r.catalog();
        return cat != null && r.resolve(cat.get(ACRO)) instanceof PdfDictionary d ? d : null;
    }

    private static String text(byte[] b) {
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF)
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF)
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String str(PdfObject o) {
        if (!(o instanceof PdfString s)) return null;
        byte[] b = s.bytes();
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF)
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        return new String(b, StandardCharsets.ISO_8859_1);
    }
}
