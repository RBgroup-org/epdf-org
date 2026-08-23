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

import com.epdfengine.rb.org.common.Deflate;
import com.epdfengine.rb.org.io.font.TrueTypeFont;
import com.epdfengine.rb.org.kernel.PdfDocument;
import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.object.PdfString;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedSet;

/**
 * Embeds a TrueType font into a {@link PdfDocument} as a Type0 / CIDFontType2 font
 * with Identity-H encoding, a {@code FontFile2} program, a {@code /W} width array
 * and a {@code ToUnicode} CMap. Lives in the render layer so the kernel needs no
 * font-format knowledge and {@code io.font} is not depended on upward.
 *
 * <p>Phase 2: the <b>full</b> font program is embedded (correct and portable).
 * Glyph subsetting is the immediate follow-up optimisation once the pipeline can
 * be executed and validated on JDK 21.</p>
 */
public final class FontEmbedder {

    private FontEmbedder() {}

    /**
     * Builds the Type0 font objects and returns the page-resource name to use in
     * a content stream's {@code Tf} operator.
     *
     * @param usedGids the glyph ids actually used (for the {@code /W} array)
     * @param gidToCp  glyph id → Unicode codepoint (for {@code ToUnicode})
     */
    public static String embed(PdfDocument doc, TrueTypeFont ttf,
                               SortedSet<Integer> usedGids, Map<Integer, Integer> gidToCp) {
        int upm = ttf.unitsPerEm();
        byte[] program = com.epdfengine.rb.org.io.font.TrueTypeSubsetter.subset(ttf.rawBytes(), usedGids);

        // FontFile2 (subset program, Flate-compressed; /Length1 = uncompressed length).
        PdfDictionary ffDict = new PdfDictionary()
                .put(PdfName.of("Length1"), PdfNumber.of(program.length))
                .put(PdfName.FILTER, PdfName.FLATE_DECODE);
        int fontFile = doc.allocate(new PdfStream(ffDict, Deflate.deflate(program)));

        String baseName = subsetTag(usedGids) + "+EPDFFont" + fontFile;

        int[] bb = ttf.fontBBoxFUnits();
        PdfArray bbox = new PdfArray(4)
                .addNumber(scale(bb[0], upm)).addNumber(scale(bb[1], upm))
                .addNumber(scale(bb[2], upm)).addNumber(scale(bb[3], upm));

        PdfDictionary descriptor = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("FontDescriptor"))
                .put(PdfName.of("FontName"), PdfName.of(baseName))
                .put(PdfName.of("Flags"), PdfNumber.of(32)) // nonsymbolic
                .put(PdfName.of("FontBBox"), bbox)
                .put(PdfName.of("ItalicAngle"), PdfNumber.of(0))
                .put(PdfName.of("Ascent"), PdfNumber.of(scale(ttf.ascenderFUnits(), upm)))
                .put(PdfName.of("Descent"), PdfNumber.of(scale(ttf.descenderFUnits(), upm)))
                .put(PdfName.of("CapHeight"), PdfNumber.of(scale(ttf.ascenderFUnits(), upm)))
                .put(PdfName.of("StemV"), PdfNumber.of(80))
                .put(PdfName.of("FontFile2"), doc.ref(fontFile));
        int descNum = doc.allocate(descriptor);

        PdfDictionary cidSystemInfo = new PdfDictionary()
                .put(PdfName.of("Registry"), PdfString.ofAscii("Adobe"))
                .put(PdfName.of("Ordering"), PdfString.ofAscii("Identity"))
                .put(PdfName.of("Supplement"), PdfNumber.of(0));

        PdfDictionary cidFont = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.FONT)
                .put(PdfName.SUBTYPE, PdfName.of("CIDFontType2"))
                .put(PdfName.of("BaseFont"), PdfName.of(baseName))
                .put(PdfName.of("CIDSystemInfo"), cidSystemInfo)
                .put(PdfName.of("FontDescriptor"), doc.ref(descNum))
                .put(PdfName.of("CIDToGIDMap"), PdfName.of("Identity"))
                .put(PdfName.of("DW"), PdfNumber.of(1000));
        PdfArray w = buildWidths(ttf, usedGids, upm);
        if (!w.isEmpty()) cidFont.put(PdfName.W, w);
        int cidNum = doc.allocate(cidFont);

        PdfDictionary tuDict = new PdfDictionary().put(PdfName.FILTER, PdfName.FLATE_DECODE);
        int toUnicode = doc.allocate(new PdfStream(tuDict, Deflate.deflate(buildToUnicode(gidToCp))));

        PdfArray descendants = new PdfArray(1).add(doc.ref(cidNum));
        PdfDictionary type0 = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.FONT)
                .put(PdfName.SUBTYPE, PdfName.of("Type0"))
                .put(PdfName.of("BaseFont"), PdfName.of(baseName))
                .put(PdfName.of("Encoding"), PdfName.of("Identity-H"))
                .put(PdfName.of("DescendantFonts"), descendants)
                .put(PdfName.of("ToUnicode"), doc.ref(toUnicode));
        int type0Num = doc.allocate(type0);

        String resourceName = doc.nextFontResourceName();
        doc.registerFontResource(resourceName, type0Num);
        return resourceName;
    }

    private static long scale(int fontUnits, int unitsPerEm) {
        return Math.round(fontUnits * 1000.0 / Math.max(1, unitsPerEm));
    }

    /** A deterministic 6-uppercase-letter subset tag (PDF 32000 §9.6.4). */
    private static String subsetTag(SortedSet<Integer> gids) {
        int h = 1;
        for (int g : gids) h = h * 31 + g;
        h &= 0x7fffffff;
        char[] c = new char[6];
        for (int i = 0; i < 6; i++) { c[i] = (char) ('A' + (h % 26)); h /= 26; }
        return new String(c);
    }

    /** Builds the {@code /W} array, grouping consecutive glyph ids: {@code gid [w w ...]}. */
    private static PdfArray buildWidths(TrueTypeFont ttf, SortedSet<Integer> gids, int upm) {
        PdfArray w = new PdfArray();
        Integer[] a = gids.toArray(new Integer[0]);
        int i = 0;
        while (i < a.length) {
            int start = a[i];
            int j = i;
            while (j + 1 < a.length && a[j + 1] == a[j] + 1) j++;
            w.addNumber(start);
            PdfArray widths = new PdfArray(j - i + 1);
            for (int k = i; k <= j; k++) widths.addNumber(scale(ttf.advanceFUnits(a[k]), upm));
            w.add(widths);
            i = j + 1;
        }
        return w;
    }

    /** Builds a ToUnicode CMap mapping 2-byte glyph codes to Unicode (UTF-16BE). */
    private static byte[] buildToUnicode(Map<Integer, Integer> gidToCp) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n");
        sb.append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n");
        sb.append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n");
        sb.append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n");

        java.util.List<Map.Entry<Integer, Integer>> entries = new java.util.ArrayList<>(gidToCp.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        int idx = 0;
        while (idx < entries.size()) {
            int count = Math.min(100, entries.size() - idx);
            sb.append(count).append(" beginbfchar\n");
            for (int i = 0; i < count; i++) {
                Map.Entry<Integer, Integer> e = entries.get(idx + i);
                sb.append('<').append(hex4(e.getKey())).append("> <").append(unicodeHex(e.getValue())).append(">\n");
            }
            sb.append("endbfchar\n");
            idx += count;
        }
        sb.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String hex4(int v) {
        return String.format("%04X", v & 0xFFFF);
    }

    private static String unicodeHex(int cp) {
        if (cp <= 0xFFFF) return hex4(cp);
        return hex4(Character.highSurrogate(cp)) + hex4(Character.lowSurrogate(cp));
    }
}
