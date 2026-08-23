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
import com.epdfengine.rb.org.kernel.object.PdfBoolean;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfIndirectReference;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNull;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.object.PdfString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Inflater;

/**
 * A lazy PDF reader (ISO 32000-1 §7): parses the cross-reference (classic
 * {@code xref} tables and {@code /XRef} streams, following {@code /Prev} chains),
 * resolves indirect objects on demand (including those packed in {@code /ObjStm}
 * object streams), and decodes {@code FlateDecode} streams (with PNG/TIFF
 * predictors). Clean-room implementation; no third-party code.
 */
public final class PdfReader {

    private static final int FREE = 0, UNCOMPRESSED = 1, COMPRESSED = 2;

    private final byte[] bytes;
    private final Map<Integer, long[]> xref = new HashMap<>();     // objNum -> {type, a, b}
    private final Map<Integer, PdfObject> cache = new HashMap<>();
    private final Map<Integer, Map<Integer, PdfObject>> objStmCache = new HashMap<>();
    private PdfDictionary trailer = new PdfDictionary();
    private int startXrefOffset = -1;

    public PdfReader(byte[] pdf) {
        this.bytes = pdf;
        parseAllXref();
    }

    // ---- public API --------------------------------------------------------

    public PdfDictionary trailer() { return trailer; }

    public PdfDictionary catalog() {
        PdfObject root = resolve(trailer.get(PdfName.ROOT));
        return root instanceof PdfDictionary d ? d : null;
    }

    /** Resolves an indirect reference; returns non-reference objects unchanged. */
    public PdfObject resolve(PdfObject o) {
        int guard = 0;
        while (o instanceof PdfIndirectReference r && guard++ < 64) o = get(r.objectNumber());
        return o == null ? PdfNull.INSTANCE : o;
    }

    /** Loads object {@code num} (lazy + cached). */
    public PdfObject get(int num) {
        PdfObject c = cache.get(num);
        if (c != null) return c;
        long[] e = xref.get(num);
        if (e == null) return PdfNull.INSTANCE;
        PdfObject o;
        if (e[0] == UNCOMPRESSED) o = parseIndirectAt((int) e[1]);
        else if (e[0] == COMPRESSED) o = fromObjStm((int) e[1], (int) e[2]);
        else o = PdfNull.INSTANCE;
        cache.put(num, o);
        return o;
    }

    /** All page dictionaries in reading order (walks the page tree). */
    public List<PdfDictionary> pages() {
        List<PdfDictionary> out = new ArrayList<>();
        PdfDictionary cat = catalog();
        if (cat == null) return out;
        collectPages(resolve(cat.get(PdfName.PAGES)), out, new HashSet<>());
        return out;
    }

    private void collectPages(PdfObject node, List<PdfDictionary> out, Set<PdfObject> seen) {
        if (!(node instanceof PdfDictionary d) || !seen.add(d)) return;
        PdfObject type = d.get(PdfName.TYPE);
        PdfObject kids = resolve(d.get(PdfName.KIDS));
        if (kids instanceof PdfArray a) {
            for (int i = 0; i < a.size(); i++) collectPages(resolve(a.get(i)), out, seen);
        } else if (type instanceof PdfName n && "Page".equals(n.value())) {
            out.add(d);
        } else if (d.has(PdfName.CONTENTS) || d.has(PdfName.MEDIA_BOX)) {
            out.add(d);   // tolerate a /Type-less leaf
        }
    }

    /** Object numbers of all pages in reading order (parallel to {@link #pages()}). */
    public List<Integer> pageNumbers() {
        List<Integer> out = new ArrayList<>();
        PdfDictionary cat = catalog();
        if (cat != null) collectPageNumbers(cat.get(PdfName.PAGES), out, new HashSet<>());
        return out;
    }

    private void collectPageNumbers(PdfObject node, List<Integer> out, Set<Integer> seen) {
        int num = node instanceof PdfIndirectReference r ? r.objectNumber() : -1;
        if (num >= 0 && !seen.add(num)) return;
        PdfObject resolved = resolve(node);
        if (!(resolved instanceof PdfDictionary d)) return;
        PdfObject kids = resolve(d.get(PdfName.KIDS));
        if (kids instanceof PdfArray a) {
            for (int i = 0; i < a.size(); i++) collectPageNumbers(a.get(i), out, seen);
        } else if (num >= 0) {
            out.add(num);
        }
    }

    /** Applies the stream's filters (FlateDecode + predictors); other filters pass through raw. */
    public byte[] decoded(PdfStream s) {
        byte[] data = s.rawBytes();
        PdfObject filter = resolve(s.dict().get(PdfName.FILTER));
        List<String> filters = new ArrayList<>();
        if (filter instanceof PdfName n) filters.add(n.value());
        else if (filter instanceof PdfArray a)
            for (int i = 0; i < a.size(); i++) if (resolve(a.get(i)) instanceof PdfName n) filters.add(n.value());
        PdfObject parms = resolve(s.dict().get(PdfName.of("DecodeParms")));
        for (String f : filters) {
            if ("FlateDecode".equals(f) || "Fl".equals(f)) {
                data = inflate(data);
                PdfDictionary p = parms instanceof PdfDictionary d ? d : null;
                if (p != null && p.has(PdfName.of("Predictor"))) data = unpredict(data, p);
            } else break;   // image/other filters left raw for the caller
        }
        return data;
    }

    public int objectCount() { return xref.size(); }

    /** Byte offset of the most recent cross-reference section (for incremental {@code /Prev}). */
    public int startXref() { return startXrefOffset; }

    /** {@code /Size} (one past the highest object number). */
    public int size() {
        int declared = trailer.get(PdfName.SIZE) instanceof PdfNumber n ? n.intValue() : 0;
        int max = 0;
        for (int k : xref.keySet()) max = Math.max(max, k);
        return Math.max(declared, max + 1);
    }

    /** The {@code /Root} reference from the trailer (unresolved). */
    public PdfObject rootRef() { return trailer.get(PdfName.ROOT); }

    /** The {@code /Info} reference from the trailer, or null. */
    public PdfObject infoRef() { return trailer.get(PdfName.INFO); }

    /** The {@code /ID} array from the trailer, or null. */
    public PdfObject idArray() { return trailer.get(PdfName.ID); }

    // ---- object parsing ----------------------------------------------------

    private PdfObject parseIndirectAt(int offset) {
        if (offset < 0 || offset >= bytes.length) return PdfNull.INSTANCE;
        Lexer lex = new Lexer(bytes);
        lex.seek(offset);
        lex.next(); lex.next();                    // objNum, gen
        Lexer.Token obj = lex.next();              // "obj"
        if (obj.kind != Lexer.Kind.KEYWORD) return PdfNull.INSTANCE;
        PdfObject value = parseValue(lex);
        int save = lex.pos();
        Lexer.Token kw = lex.next();
        if (kw.kind == Lexer.Kind.KEYWORD && "stream".equals(kw.text) && value instanceof PdfDictionary dict) {
            int p = lex.pos();
            if (p < bytes.length && bytes[p] == '\r') { p++; if (p < bytes.length && bytes[p] == '\n') p++; }
            else if (p < bytes.length && bytes[p] == '\n') p++;
            int length = resolveLength(dict);
            byte[] raw;
            if (length >= 0 && p + length <= bytes.length) {
                raw = slice(p, length);
            } else {
                int es = indexOf("endstream", p);
                int end = es < 0 ? bytes.length : es;
                while (end > p && (bytes[end - 1] == '\n' || bytes[end - 1] == '\r')) end--;
                raw = slice(p, Math.max(0, end - p));
            }
            return new PdfStream(dict, raw);
        }
        lex.seek(save);
        return value;
    }

    private PdfObject parseValue(Lexer lex) { return parseValueFrom(lex, lex.next()); }

    private PdfObject parseValueFrom(Lexer lex, Lexer.Token t) {
        switch (t.kind) {
            case NUMBER: {
                int save = lex.pos();
                Lexer.Token t2 = lex.next();
                if (t2.kind == Lexer.Kind.NUMBER) {
                    Lexer.Token t3 = lex.next();
                    if (t3.kind == Lexer.Kind.KEYWORD && "R".equals(t3.text))
                        return new PdfIndirectReference((int) parseLong(t.text), (int) parseLong(t2.text));
                }
                lex.seek(save);
                return numberOf(t.text);
            }
            case NAME:   return PdfName.of(t.text);
            case STRING: return PdfString.ofBytes(t.bytes);
            case ARRAY_OPEN: {
                PdfArray a = new PdfArray();
                while (true) {
                    Lexer.Token e = lex.next();
                    if (e.kind == Lexer.Kind.ARRAY_CLOSE || e.kind == Lexer.Kind.EOF) break;
                    a.add(parseValueFrom(lex, e));
                }
                return a;
            }
            case DICT_OPEN: {
                PdfDictionary d = new PdfDictionary();
                while (true) {
                    Lexer.Token k = lex.next();
                    if (k.kind == Lexer.Kind.DICT_CLOSE || k.kind == Lexer.Kind.EOF) break;
                    if (k.kind != Lexer.Kind.NAME) continue;
                    d.put(PdfName.of(k.text), parseValue(lex));
                }
                return d;
            }
            case KEYWORD:
                if ("true".equals(t.text)) return PdfBoolean.TRUE;
                if ("false".equals(t.text)) return PdfBoolean.FALSE;
                return PdfNull.INSTANCE;
            default: return PdfNull.INSTANCE;
        }
    }

    private int resolveLength(PdfDictionary dict) {
        PdfObject len = dict.get(PdfName.LENGTH);
        if (len instanceof PdfNumber n) return n.intValue();
        if (len instanceof PdfIndirectReference r) {
            PdfObject o = get(r.objectNumber());
            if (o instanceof PdfNumber n) return n.intValue();
        }
        return -1;
    }

    // ---- object streams (/ObjStm) -----------------------------------------

    private PdfObject fromObjStm(int stmNum, int index) {
        Map<Integer, PdfObject> objs = objStmCache.get(stmNum);
        if (objs == null) {
            objs = new HashMap<>();
            PdfObject so = parseIndirectAt(xref.get(stmNum) != null ? (int) xref.get(stmNum)[1] : -1);
            if (so instanceof PdfStream st) {
                byte[] data = decoded(st);
                int n = intOf(st.dict().get(PdfName.N));
                int first = intOf(st.dict().get(PdfName.FIRST));
                Lexer head = new Lexer(data);
                int[] nums = new int[n], offs = new int[n];
                for (int i = 0; i < n; i++) {
                    nums[i] = (int) parseLong(head.next().text);
                    offs[i] = (int) parseLong(head.next().text);
                }
                for (int i = 0; i < n; i++) {
                    Lexer l = new Lexer(data);
                    l.seek(first + offs[i]);
                    objs.put(i, parseValueFrom(l, l.next()));
                }
            }
            objStmCache.put(stmNum, objs);
        }
        PdfObject o = objs.get(index);
        return o == null ? PdfNull.INSTANCE : o;
    }

    // ---- cross-reference ---------------------------------------------------

    private void parseAllXref() {
        int start = findStartxref();
        this.startXrefOffset = start;
        Set<Integer> seen = new HashSet<>();
        int off = start;
        while (off >= 0 && seen.add(off)) off = parseXrefSection(off);
        if (xref.isEmpty()) reconstructByScan();   // damaged/linearized fallback
    }

    /** @return the /Prev offset, or -1. */
    private int parseXrefSection(int off) {
        Lexer lex = new Lexer(bytes);
        lex.seek(off);
        Lexer.Token t = lex.next();
        if (t.kind == Lexer.Kind.KEYWORD && "xref".equals(t.text)) return parseClassicXref(lex);
        return parseXrefStream(off);
    }

    private int parseClassicXref(Lexer lex) {
        while (true) {
            int save = lex.pos();
            Lexer.Token a = lex.next();
            if (a.kind != Lexer.Kind.NUMBER) { lex.seek(save); break; }
            int start = (int) parseLong(a.text);
            int count = (int) parseLong(lex.next().text);
            for (int i = 0; i < count; i++) {
                long offset = parseLong(lex.next().text);
                lex.next();                                   // generation
                String type = lex.next().text;
                int num = start + i;
                if ("n".equals(type) && !xref.containsKey(num))
                    xref.put(num, new long[]{ UNCOMPRESSED, offset, 0 });
            }
        }
        Lexer.Token tr = lex.next();                          // "trailer"
        PdfDictionary t = parseValue(lex) instanceof PdfDictionary d ? d : new PdfDictionary();
        mergeTrailer(t);
        if (t.has(PdfName.of("XRefStm"))) parseXrefSection(intOf(t.get(PdfName.of("XRefStm"))));
        return t.has(PdfName.PREV) ? intOf(t.get(PdfName.PREV)) : -1;
    }

    private int parseXrefStream(int off) {
        PdfObject o = parseIndirectAt(off);
        if (!(o instanceof PdfStream st)) return -1;
        PdfDictionary d = st.dict();
        mergeTrailer(d);
        byte[] data = decoded(st);
        PdfArray w = (PdfArray) resolve(d.get(PdfName.W));
        int w0 = intOf(w.get(0)), w1 = intOf(w.get(1)), w2 = intOf(w.get(2));
        int size = intOf(d.get(PdfName.SIZE));
        int[] index;
        PdfObject idx = resolve(d.get(PdfName.INDEX));
        if (idx instanceof PdfArray ia) {
            index = new int[ia.size()];
            for (int i = 0; i < ia.size(); i++) index[i] = intOf(ia.get(i));
        } else index = new int[]{ 0, size };
        int rec = w0 + w1 + w2, p = 0;
        for (int s = 0; s + 1 < index.length; s += 2) {
            int startNum = index[s], cnt = index[s + 1];
            for (int i = 0; i < cnt && p + rec <= data.length; i++) {
                long f0 = w0 == 0 ? 1 : readBE(data, p, w0);
                long f1 = readBE(data, p + w0, w1);
                long f2 = readBE(data, p + w0 + w1, w2);
                p += rec;
                int num = startNum + i;
                if (xref.containsKey(num)) continue;
                if (f0 == UNCOMPRESSED) xref.put(num, new long[]{ UNCOMPRESSED, f1, f2 });
                else if (f0 == COMPRESSED) xref.put(num, new long[]{ COMPRESSED, f1, f2 });
            }
        }
        return d.has(PdfName.PREV) ? intOf(d.get(PdfName.PREV)) : -1;
    }

    private void mergeTrailer(PdfDictionary t) {
        for (Map.Entry<PdfName, PdfObject> e : t.entries())
            if (!trailer.has(e.getKey())) trailer.put(e.getKey(), e.getValue());
    }

    private int findStartxref() {
        int i = lastIndexOf("startxref");
        if (i < 0) return -1;
        Lexer lex = new Lexer(bytes);
        lex.seek(i + "startxref".length());
        Lexer.Token t = lex.next();
        return t.kind == Lexer.Kind.NUMBER ? (int) parseLong(t.text) : -1;
    }

    /** Last-resort recovery: scan for every "N G obj" and index it. */
    private void reconstructByScan() {
        for (int i = 0; i + 3 < bytes.length; i++) {
            if (bytes[i] == 'o' && bytes[i + 1] == 'b' && bytes[i + 2] == 'j'
                    && (i + 3 >= bytes.length || isDelimOrWs(bytes[i + 3]))) {
                int j = i - 1;
                while (j >= 0 && isWs(bytes[j])) j--;
                int genEnd = j + 1; while (j >= 0 && isDigit(bytes[j])) j--;
                int genStart = j + 1;
                while (j >= 0 && isWs(bytes[j])) j--;
                int numEnd = j + 1; while (j >= 0 && isDigit(bytes[j])) j--;
                int numStart = j + 1;
                if (numStart < numEnd && genStart < genEnd) {
                    int num = (int) parseLong(new String(bytes, numStart, numEnd - numStart,
                            java.nio.charset.StandardCharsets.ISO_8859_1));
                    xref.put(num, new long[]{ UNCOMPRESSED, numStart, 0 });
                }
            }
        }
        int ti = lastIndexOf("trailer");
        if (ti >= 0) {
            Lexer lex = new Lexer(bytes); lex.seek(ti + 7);
            if (parseValue(lex) instanceof PdfDictionary d) mergeTrailer(d);
        }
        if (!trailer.has(PdfName.ROOT))
            for (Integer num : xref.keySet()) {
                PdfObject o = get(num);
                if (o instanceof PdfDictionary d && d.get(PdfName.TYPE) instanceof PdfName n && "Catalog".equals(n.value()))
                    trailer.put(PdfName.ROOT, PdfIndirectReference.of(num));
            }
    }

    // ---- filters -----------------------------------------------------------

    private static byte[] inflate(byte[] in) {
        Inflater inf = new Inflater();
        inf.setInput(in);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(Math.max(64, in.length * 3));
        byte[] buf = new byte[8192];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) { if (inf.needsInput() || inf.needsDictionary()) break; }
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            // return whatever inflated so far (some producers append junk)
        } finally { inf.end(); }
        return out.toByteArray();
    }

    private byte[] unpredict(byte[] data, PdfDictionary parms) {
        int predictor = intOf(parms.get(PdfName.of("Predictor")));
        if (predictor < 2) return data;
        int colors = parms.has(PdfName.of("Colors")) ? intOf(parms.get(PdfName.of("Colors"))) : 1;
        int bpc = parms.has(PdfName.of("BitsPerComponent")) ? intOf(parms.get(PdfName.of("BitsPerComponent"))) : 8;
        int columns = parms.has(PdfName.of("Columns")) ? intOf(parms.get(PdfName.of("Columns"))) : 1;
        int bpp = Math.max(1, colors * bpc / 8);
        int rowLen = Math.max(1, colors * bpc * columns / 8);
        if (predictor == 2) return tiffPredictor(data, rowLen, bpp);
        return pngPredictor(data, rowLen, bpp);
    }

    private static byte[] pngPredictor(byte[] data, int rowLen, int bpp) {
        int rows = data.length / (rowLen + 1);
        byte[] out = new byte[rows * rowLen];
        byte[] prev = new byte[rowLen];
        int in = 0, o = 0;
        for (int r = 0; r < rows; r++) {
            int ft = data[in++] & 0xFF;
            byte[] cur = new byte[rowLen];
            for (int i = 0; i < rowLen; i++) {
                int x = data[in + i] & 0xFF;
                int a = i >= bpp ? cur[i - bpp] & 0xFF : 0;
                int b = prev[i] & 0xFF;
                int c = i >= bpp ? prev[i - bpp] & 0xFF : 0;
                int v;
                switch (ft) {
                    case 1: v = x + a; break;
                    case 2: v = x + b; break;
                    case 3: v = x + ((a + b) >> 1); break;
                    case 4: v = x + paeth(a, b, c); break;
                    default: v = x;
                }
                cur[i] = (byte) v;
            }
            System.arraycopy(cur, 0, out, o, rowLen);
            prev = cur; in += rowLen; o += rowLen;
        }
        return out;
    }

    private static byte[] tiffPredictor(byte[] data, int rowLen, int bpp) {
        int rows = data.length / rowLen;
        for (int r = 0; r < rows; r++) {
            int base = r * rowLen;
            for (int i = bpp; i < rowLen; i++) data[base + i] += data[base + i - bpp];
        }
        return data;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        return (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
    }

    // ---- low-level helpers -------------------------------------------------

    private PdfObject numberOf(String s) {
        return (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0)
                ? PdfNumber.of(parseDouble(s)) : PdfNumber.of(parseLong(s));
    }

    private int intOf(PdfObject o) {
        o = resolve(o);
        return o instanceof PdfNumber n ? n.intValue() : 0;
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim().replace("+", "")); } catch (Exception e) { return (long) parseDouble(s); }
    }
    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim().replace("+", "")); } catch (Exception e) { return 0; }
    }

    private static long readBE(byte[] b, int off, int len) {
        long v = 0;
        for (int i = 0; i < len; i++) v = (v << 8) | (b[off + i] & 0xFF);
        return v;
    }

    private byte[] slice(int off, int len) {
        byte[] r = new byte[len];
        System.arraycopy(bytes, off, r, 0, len);
        return r;
    }

    private int indexOf(String needle, int from) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        outer:
        for (int i = Math.max(0, from); i <= bytes.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) if (bytes[i + j] != n[j]) continue outer;
            return i;
        }
        return -1;
    }

    private int lastIndexOf(String needle) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        outer:
        for (int i = bytes.length - n.length; i >= 0; i--) {
            for (int j = 0; j < n.length; j++) if (bytes[i + j] != n[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static boolean isWs(byte c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f' || c == 0; }
    private static boolean isDigit(byte c) { return c >= '0' && c <= '9'; }
    private static boolean isDelimOrWs(byte c) {
        return isWs(c) || c == '<' || c == '[' || c == '(' || c == '/' || c == '%';
    }
}
