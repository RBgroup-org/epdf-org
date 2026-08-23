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
package com.epdfengine.rb.org.kernel.read;

import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.write.ObjectSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read → modify → write bridge using PDF <b>incremental updates</b> (ISO 32000-1
 * §7.5.6): the original bytes are preserved verbatim and only changed/new objects
 * plus a fresh cross-reference section and trailer (with {@code /Prev} pointing at
 * the previous xref) are appended. This is how form filling / annotation edits are
 * applied without rewriting — and it keeps any existing signatures intact.
 */
public final class PdfEditor {

    private final byte[] original;
    private final PdfReader reader;
    private final TreeMap<Integer, PdfObject> overrides = new TreeMap<>();
    private int nextNew;

    public PdfEditor(byte[] pdf) {
        this.original = pdf;
        this.reader = new PdfReader(pdf);
        this.nextNew = reader.size();
    }

    public PdfReader reader() { return reader; }

    /** Replaces (or defines) object {@code num} with {@code obj}. */
    public void setObject(int num, PdfObject obj) { overrides.put(num, obj); }

    /** Allocates a new object number and stores {@code obj}; returns the number. */
    public int addObject(PdfObject obj) { int n = nextNew++; overrides.put(n, obj); return n; }

    /** Serializes the original document plus an incremental update of all changes. */
    public byte[] save() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(original.length + 4096);
            out.write(original, 0, original.length);
            if (original.length > 0 && original[original.length - 1] != '\n') out.write('\n');

            TreeMap<Integer, Integer> offsets = new TreeMap<>();
            for (Map.Entry<Integer, PdfObject> e : overrides.entrySet()) {
                offsets.put(e.getKey(), out.size());
                ObjectSerializer.writeIndirect(out, e.getKey(), 0, e.getValue());
            }

            int xrefOffset = out.size();
            writeXref(out, offsets);

            int size = Math.max(reader.size(), overrides.isEmpty() ? 0 : overrides.lastKey() + 1);
            PdfDictionary tr = new PdfDictionary()
                    .put(PdfName.SIZE, PdfNumber.of(size))
                    .put(PdfName.PREV, PdfNumber.of(reader.startXref()));
            if (reader.rootRef() != null) tr.put(PdfName.ROOT, reader.rootRef());
            if (reader.infoRef() != null) tr.put(PdfName.INFO, reader.infoRef());
            if (reader.idArray() instanceof PdfObject id) tr.put(PdfName.ID, id);

            ascii(out, "trailer\n");
            ObjectSerializer.writeValue(out, tr);
            ascii(out, "\nstartxref\n" + xrefOffset + "\n%%EOF\n");
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("incremental save failed", e);
        }
    }

    /** Classic xref table with one subsection per run of consecutive object numbers. */
    private static void writeXref(ByteArrayOutputStream out, TreeMap<Integer, Integer> offsets) throws IOException {
        ascii(out, "xref\n");
        List<Integer> nums = new ArrayList<>(offsets.keySet());
        int i = 0;
        while (i < nums.size()) {
            int start = nums.get(i), j = i;
            while (j + 1 < nums.size() && nums.get(j + 1) == nums.get(j) + 1) j++;
            ascii(out, start + " " + (j - i + 1) + "\n");
            for (int k = i; k <= j; k++)
                ascii(out, String.format("%010d %05d n\r\n", offsets.get(nums.get(k)), 0));
            i = j + 1;
        }
    }

    private static void ascii(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b, 0, b.length);
    }
}
