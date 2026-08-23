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
package com.epdfengine.rb.org.redact;

import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfIndirectReference;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.read.PdfEditor;
import com.epdfengine.rb.org.kernel.read.PdfReader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paints opaque black rectangles over regions of an existing PDF (via an incremental update, so
 * the original bytes are preserved and appended to). This is <b>visual redaction</b>: it masks
 * content but does <b>not</b> remove the underlying text/images from the file — do not rely on it
 * to strip extractable sensitive data. (Secure content-removal redaction is a separate, heavier
 * operation that rewrites content streams.)
 */
public final class Redactor {

    private static final PdfName CONTENTS = PdfName.of("Contents");

    private Redactor() {}

    /** A region to mask, in PDF points with a bottom-left origin. */
    public record Box(int page, double x, double y, double width, double height) {}

    /** Returns {@code pdf} with black masks painted over {@code boxes} (incremental update). */
    public static byte[] apply(byte[] pdf, List<Box> boxes) {
        PdfEditor editor = new PdfEditor(pdf);
        PdfReader reader = editor.reader();
        List<Integer> pageNums = reader.pageNumbers();

        Map<Integer, List<Box>> byPage = new LinkedHashMap<>();
        for (Box b : boxes) byPage.computeIfAbsent(b.page(), k -> new ArrayList<>()).add(b);

        for (Map.Entry<Integer, List<Box>> e : byPage.entrySet()) {
            int pageIndex = e.getKey();
            if (pageIndex < 0 || pageIndex >= pageNums.size()) continue;
            int pageNum = pageNums.get(pageIndex);
            if (!(reader.get(pageNum) instanceof PdfDictionary page)) continue;

            StringBuilder c = new StringBuilder("q 0 0 0 rg ");
            for (Box b : e.getValue()) {
                c.append(num(b.x())).append(' ').append(num(b.y())).append(' ')
                 .append(num(b.width())).append(' ').append(num(b.height())).append(" re ");
            }
            c.append("f Q\n");
            int streamNum = editor.addObject(new PdfStream(new PdfDictionary(),
                    c.toString().getBytes(StandardCharsets.ISO_8859_1)));

            PdfArray contents = new PdfArray();
            PdfObject existing = reader.resolve(page.get(CONTENTS));
            if (existing instanceof PdfArray arr) {
                for (PdfObject o : arr.items()) contents.add(o);
            } else if (page.get(CONTENTS) != null) {
                contents.add(page.get(CONTENTS));                 // single content-stream ref
            }
            contents.add(new PdfIndirectReference(streamNum, 0)); // mask drawn last = on top

            PdfDictionary updated = new PdfDictionary();
            for (Map.Entry<PdfName, PdfObject> en : page.entries()) {
                if (!en.getKey().equals(CONTENTS)) updated.put(en.getKey(), en.getValue());
            }
            updated.put(CONTENTS, contents);
            editor.setObject(pageNum, updated);
        }
        return editor.save();
    }

    private static String num(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        boolean neg = v < 0;
        if (neg) v = -v;
        long s = Math.round(v * 1000.0);
        int f = (int) (s % 1000);
        return (neg ? "-" : "") + (s / 1000) + "."
                + (char) ('0' + f / 100) + (char) ('0' + (f / 10) % 10) + (char) ('0' + f % 10);
    }
}
