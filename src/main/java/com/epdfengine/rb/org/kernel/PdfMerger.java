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

import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfIndirectReference;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.read.PdfReader;
import com.epdfengine.rb.org.kernel.write.ObjectSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combines pages from one or more source PDFs into a new document. Each selected
 * page's object graph (resources, fonts, images, content, annotations) is deep-
 * copied into a fresh object table with remapped numbers, inherited page
 * attributes ({@code /Resources}, {@code /MediaBox}, …) are materialised onto the
 * page, and a new page tree + catalog are written. This is the basis for both
 * merge (many docs → one) and split (subset of pages → new doc).
 */
public final class PdfMerger {

    private static final PdfName CROP_BOX = PdfName.of("CropBox");
    private static final PdfName ROTATE = PdfName.of("Rotate");
    private static final PdfName[] INHERITABLE = { PdfName.RESOURCES, PdfName.MEDIA_BOX, CROP_BOX, ROTATE };

    private final List<PdfObject> objects = new ArrayList<>();   // 1-based; slot 0 is the free head
    private final List<Integer> pageObjs = new ArrayList<>();

    public PdfMerger() { objects.add(null); }

    // ---- convenience -------------------------------------------------------

    /** Merges whole documents in order. */
    public static byte[] merge(byte[]... pdfs) {
        PdfMerger m = new PdfMerger();
        for (byte[] p : pdfs) m.add(p);
        return m.build();
    }

    /** Extracts the given (0-based) page indices from a single PDF into a new document. */
    public static byte[] extractPages(byte[] pdf, int... pageIndices) {
        return new PdfMerger().add(pdf, pageIndices).build();
    }

    // ---- assembly ----------------------------------------------------------

    /** Adds every page of {@code pdf}. */
    public PdfMerger add(byte[] pdf) { return add(pdf, null); }

    /** Adds the selected (0-based) pages of {@code pdf}; {@code null} = all pages. */
    public PdfMerger add(byte[] pdf, int[] pageIndices) {
        PdfReader r = new PdfReader(pdf);
        List<Integer> allPages = r.pageNumbers();
        int[] idx = pageIndices != null ? pageIndices : allRange(allPages.size());

        Map<Integer, Integer> cache = new HashMap<>();
        int[] newNums = new int[idx.length];
        for (int i = 0; i < idx.length; i++) {                    // reserve first (forward refs / cross links)
            if (idx[i] < 0 || idx[i] >= allPages.size()) continue;
            int srcNum = allPages.get(idx[i]);
            newNums[i] = reserve();
            cache.put(srcNum, newNums[i]);
        }
        for (int i = 0; i < idx.length; i++) {
            if (newNums[i] == 0) continue;
            copyPageInto(r, allPages.get(idx[i]), newNums[i], cache);
            pageObjs.add(newNums[i]);
        }
        return this;
    }

    /** Serializes the assembled document to PDF bytes (classic cross-reference table). */
    public byte[] build() {
        return serialize(assembleCatalog());
    }

    /** Serializes the assembled document with object streams + a compressed {@code /XRef} (smaller). */
    public byte[] buildCompressed() {
        return serializeCompressed(assembleCatalog());
    }

    private int assembleCatalog() {
        int pagesNum = reserve();
        PdfArray kids = new PdfArray();
        for (int p : pageObjs) {
            kids.add(ref(p));
            ((PdfDictionary) objects.get(p)).put(PdfName.PARENT, ref(pagesNum));
        }
        PdfDictionary pages = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.PAGES)
                .put(PdfName.KIDS, kids)
                .put(PdfName.COUNT, PdfNumber.of(pageObjs.size()));
        set(pagesNum, pages);

        int catNum = alloc(new PdfDictionary()
                .put(PdfName.TYPE, PdfName.CATALOG)
                .put(PdfName.PAGES, ref(pagesNum)));
        return catNum;
    }

    // ---- deep copy ---------------------------------------------------------

    private void copyPageInto(PdfReader r, int srcPageNum, int newNum, Map<Integer, Integer> cache) {
        PdfObject po = r.get(srcPageNum);
        if (!(po instanceof PdfDictionary page)) { set(newNum, new PdfDictionary().put(PdfName.TYPE, PdfName.PAGE)); return; }
        PdfDictionary copy = new PdfDictionary();
        for (Map.Entry<PdfName, PdfObject> e : page.entries()) {
            if (e.getKey().equals(PdfName.PARENT)) continue;      // reparented under the new page tree
            copy.put(e.getKey(), copyValue(r, e.getValue(), cache));
        }
        for (PdfName k : INHERITABLE)                             // materialise inherited attributes
            if (!copy.has(k)) {
                PdfObject v = inherited(r, page, k);
                if (v != null) copy.put(k, copyValue(r, v, cache));
            }
        copy.put(PdfName.TYPE, PdfName.PAGE);
        set(newNum, copy);
    }

    private PdfObject copyValue(PdfReader r, PdfObject o, Map<Integer, Integer> cache) {
        if (o instanceof PdfIndirectReference ref) {
            Integer mapped = cache.get(ref.objectNumber());
            if (mapped != null) return ref(mapped);
            int newNum = reserve();
            cache.put(ref.objectNumber(), newNum);
            set(newNum, copyDirect(r, r.resolve(ref), cache));
            return ref(newNum);
        }
        return copyDirect(r, o, cache);
    }

    private PdfObject copyDirect(PdfReader r, PdfObject o, Map<Integer, Integer> cache) {
        if (o instanceof PdfDictionary d) {
            PdfDictionary nd = new PdfDictionary();
            for (Map.Entry<PdfName, PdfObject> e : d.entries()) nd.put(e.getKey(), copyValue(r, e.getValue(), cache));
            return nd;
        }
        if (o instanceof PdfArray a) {
            PdfArray na = new PdfArray(a.size());
            for (int i = 0; i < a.size(); i++) na.add(copyValue(r, a.get(i), cache));
            return na;
        }
        if (o instanceof PdfStream s) {
            PdfDictionary nd = (PdfDictionary) copyDirect(r, s.dict(), cache);
            return new PdfStream(nd, s.rawBytes());
        }
        return o;   // names, numbers, strings, booleans, null are immutable
    }

    private PdfObject inherited(PdfReader r, PdfDictionary page, PdfName key) {
        PdfObject node = page;
        Set<PdfObject> seen = new HashSet<>();
        while (node instanceof PdfDictionary d && seen.add(d)) {
            PdfObject v = d.get(key);
            if (v != null) return v;
            node = r.resolve(d.get(PdfName.PARENT));
        }
        return null;
    }

    // ---- serialize ---------------------------------------------------------

    private byte[] serialize(int catNum) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            ascii(out, "%PDF-1.7\n");
            out.write(new byte[]{ '%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n' });

            int n = objects.size();
            int[] offsets = new int[n];
            for (int i = 1; i < n; i++) {
                offsets[i] = out.size();
                ObjectSerializer.writeIndirect(out, i, 0, objects.get(i));
            }
            int xref = out.size();
            ascii(out, "xref\n0 " + n + "\n0000000000 65535 f \r\n");
            for (int i = 1; i < n; i++) ascii(out, String.format("%010d %05d n\r\n", offsets[i], 0));

            PdfDictionary tr = new PdfDictionary()
                    .put(PdfName.SIZE, PdfNumber.of(n))
                    .put(PdfName.ROOT, ref(catNum));
            ascii(out, "trailer\n");
            ObjectSerializer.writeValue(out, tr);
            ascii(out, "\nstartxref\n" + xref + "\n%%EOF\n");
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("merge serialize failed", e);
        }
    }

    private byte[] serializeCompressed(int catNum) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
            byte[] fileId = new byte[16];
            new java.util.Random(objects.size() * 0x9E3779B97F4A7C15L).nextBytes(fileId);
            new com.epdfengine.rb.org.kernel.write.PdfWriter()
                    .writeCompressed(out, objects.subList(1, objects.size()), catNum, 0, fileId);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("merge serialize (compressed) failed", e);
        }
    }

    // ---- object table ------------------------------------------------------

    private int reserve() { objects.add(null); return objects.size() - 1; }
    private int alloc(PdfObject o) { objects.add(o); return objects.size() - 1; }
    private void set(int num, PdfObject o) { objects.set(num, o); }
    private static PdfIndirectReference ref(int num) { return PdfIndirectReference.of(num); }

    private static int[] allRange(int count) {
        int[] a = new int[count];
        for (int i = 0; i < count; i++) a[i] = i;
        return a;
    }

    private static void ascii(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b, 0, b.length);
    }
}
