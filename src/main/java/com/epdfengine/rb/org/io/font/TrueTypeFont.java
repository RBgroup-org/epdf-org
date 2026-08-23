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
package com.epdfengine.rb.org.io.font;

/**
 * A parsed TrueType / OpenType (sfnt) font — Phase 1: <b>metrics and character
 * mapping only</b> (head, maxp, hhea, hmtx, cmap). Enough to measure text
 * accurately for layout. Glyph outlines, subsetting, and embedding arrive in a
 * later phase.
 *
 * <p>Implemented clean-room from the OpenType specification (ISO/IEC 14496-22).
 * Reads big-endian sfnt structures directly from the font bytes; character-to-
 * glyph lookups are performed on demand against the chosen {@code cmap} subtable
 * (formats 4 and 12), so no large maps are materialised — important for big CJK
 * fonts.</p>
 */
public final class TrueTypeFont {

    private final byte[] d;                 // raw font bytes (retained for on-demand cmap lookups)
    private final int unitsPerEm;
    private final int numGlyphs;
    private final int numberOfHMetrics;
    private final int hmtxOffset;
    private final int ascender;             // font units
    private final int descender;            // font units (typically negative)
    private final int lineGap;              // font units
    private final boolean hasGlyf;          // true for TrueType outlines (needed later to embed)
    private final int headOffset;           // offset of the head table (for FontBBox)

    private final int cmapOffset;           // chosen subtable offset
    private final int cmapFormat;           // 4 or 12 (0 = unusable)

    private TrueTypeFont(byte[] data, int unitsPerEm, int numGlyphs, int numberOfHMetrics,
                         int hmtxOffset, int ascender, int descender, int lineGap,
                         boolean hasGlyf, int cmapOffset, int cmapFormat, int headOffset) {
        this.d = data;
        this.unitsPerEm = unitsPerEm <= 0 ? 1000 : unitsPerEm;
        this.numGlyphs = numGlyphs;
        this.numberOfHMetrics = Math.max(1, numberOfHMetrics);
        this.hmtxOffset = hmtxOffset;
        this.ascender = ascender;
        this.descender = descender;
        this.lineGap = lineGap;
        this.hasGlyf = hasGlyf;
        this.cmapOffset = cmapOffset;
        this.cmapFormat = cmapFormat;
        this.headOffset = headOffset;
    }

    /** Parses sfnt font bytes. Throws {@link IllegalArgumentException} on unsupported input. */
    public static TrueTypeFont parse(byte[] font) {
        if (font == null || font.length < 12) {
            throw new IllegalArgumentException("Not a font: too few bytes");
        }
        long tag = u32(font, 0);
        // 0x00010000 = TrueType outlines; 'true' = Apple; 'OTTO' = CFF outlines (metrics still readable).
        if (tag == 0x74746366L) { // 'ttcf'
            throw new IllegalArgumentException("TrueType Collections (.ttc) are not supported yet");
        }
        boolean known = tag == 0x00010000L || tag == 0x74727565L /*true*/ || tag == 0x4F54544FL /*OTTO*/;
        if (!known) {
            throw new IllegalArgumentException("Unrecognised sfnt version: 0x" + Long.toHexString(tag));
        }

        int numTables = u16(font, 4);
        int dir = 12;
        int headOff = -1, maxpOff = -1, hheaOff = -1, hmtxOff = -1, cmapOff = -1;
        boolean glyf = false;
        for (int i = 0; i < numTables; i++) {
            int rec = dir + i * 16;
            if (rec + 16 > font.length) break;
            long t = u32(font, rec);
            int off = (int) u32(font, rec + 8);
            if (t == tagOf("head")) headOff = off;
            else if (t == tagOf("maxp")) maxpOff = off;
            else if (t == tagOf("hhea")) hheaOff = off;
            else if (t == tagOf("hmtx")) hmtxOff = off;
            else if (t == tagOf("cmap")) cmapOff = off;
            else if (t == tagOf("glyf")) glyf = true;
        }
        if (headOff < 0 || maxpOff < 0 || hheaOff < 0 || hmtxOff < 0 || cmapOff < 0) {
            throw new IllegalArgumentException("Missing required sfnt table(s)");
        }

        int unitsPerEm = u16(font, headOff + 18);
        int numGlyphs = u16(font, maxpOff + 4);
        int ascender = s16(font, hheaOff + 4);
        int descender = s16(font, hheaOff + 6);
        int lineGap = s16(font, hheaOff + 8);
        int numberOfHMetrics = u16(font, hheaOff + 34);

        int[] chosen = selectCmap(font, cmapOff);
        return new TrueTypeFont(font, unitsPerEm, numGlyphs, numberOfHMetrics, hmtxOff,
                ascender, descender, lineGap, glyf, chosen[0], chosen[1], headOff);
    }

    // --- public metrics API ---

    public int unitsPerEm()   { return unitsPerEm; }
    public int numGlyphs()    { return numGlyphs; }
    public boolean hasGlyf()  { return hasGlyf; }

    /** Glyph id for a Unicode codepoint, or 0 (.notdef) if unmapped. */
    public int glyphId(int codepoint) {
        return switch (cmapFormat) {
            case 4 -> lookupFormat4(codepoint);
            case 12 -> lookupFormat12(codepoint);
            default -> 0;
        };
    }

    /** True if this font can render the codepoint (maps to a non-zero glyph). */
    public boolean covers(int codepoint) { return glyphId(codepoint) != 0; }

    /** Advance width of a glyph in font units. */
    public int advanceFUnits(int glyphId) {
        int gid = glyphId < 0 ? 0 : glyphId;
        int index = gid < numberOfHMetrics ? gid : numberOfHMetrics - 1;
        int pos = hmtxOffset + index * 4;
        if (pos + 2 > d.length) return unitsPerEm / 2;
        return u16(d, pos);
    }

    /** Advance width of a codepoint at a given font size, in points. */
    public double advancePt(int codepoint, double fontSizePt) {
        return advanceFUnits(glyphId(codepoint)) * fontSizePt / unitsPerEm;
    }

    /** Total advance width of a string at a given font size, in points. */
    public double textWidthPt(String text, double fontSizePt) {
        if (text == null || text.isEmpty()) return 0;
        double sum = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            sum += advancePt(cp, fontSizePt);
            i += Character.charCount(cp);
        }
        return sum;
    }

    public double ascentPt(double fontSizePt)  { return ascender  * fontSizePt / unitsPerEm; }
    public double descentPt(double fontSizePt) { return Math.abs(descender) * fontSizePt / unitsPerEm; }
    public double lineGapPt(double fontSizePt) { return lineGap   * fontSizePt / unitsPerEm; }

    // --- data needed by the PDF font embedder (kernel) ---

    /** The raw sfnt bytes (for FontFile2 embedding). Do not mutate. */
    public byte[] rawBytes() { return d; }

    /** Ascender in font units. */
    public int ascenderFUnits()  { return ascender; }

    /** Descender in font units (typically negative). */
    public int descenderFUnits() { return descender; }

    /** Font bounding box in font units: {xMin, yMin, xMax, yMax} from the head table. */
    public int[] fontBBoxFUnits() {
        return new int[]{
                s16(d, headOffset + 36), s16(d, headOffset + 38),
                s16(d, headOffset + 40), s16(d, headOffset + 42)
        };
    }

    // --- cmap selection & lookups ---

    /** @return {chosenSubtableOffset, format}. Prefers full-Unicode (format 12), else BMP (format 4). */
    private static int[] selectCmap(byte[] f, int cmapOff) {
        int numTables = u16(f, cmapOff + 2);
        int bestOff = -1, bestScore = -1;
        for (int i = 0; i < numTables; i++) {
            int rec = cmapOff + 4 + i * 8;
            int platform = u16(f, rec);
            int encoding = u16(f, rec + 2);
            int subOff = cmapOff + (int) u32(f, rec + 4);
            if (subOff + 2 > f.length) continue;
            int format = u16(f, subOff);
            int score = scoreCmap(platform, encoding, format);
            if (score > bestScore) { bestScore = score; bestOff = subOff; }
        }
        if (bestOff < 0) return new int[]{0, 0};
        return new int[]{bestOff, u16(f, bestOff)};
    }

    private static int scoreCmap(int platform, int encoding, int format) {
        // Prefer Windows UCS-4 (3,10) fmt12, then Windows BMP (3,1) fmt4, then Unicode, then symbol.
        if (format != 4 && format != 12) return -1;
        int base = format == 12 ? 40 : 20;
        if (platform == 3 && encoding == 10) return base + 9;
        if (platform == 0 && (encoding == 4 || encoding == 6)) return base + 8;
        if (platform == 3 && encoding == 1) return base + 7;
        if (platform == 0) return base + 6;
        if (platform == 3 && encoding == 0) return base + 1; // symbol
        return base;
    }

    private int lookupFormat12(int cp) {
        int nGroups = (int) u32(d, cmapOffset + 12);
        int groups = cmapOffset + 16;
        int lo = 0, hi = nGroups - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int g = groups + mid * 12;
            long start = u32(d, g);
            long end = u32(d, g + 4);
            if (cp < start) hi = mid - 1;
            else if (cp > end) lo = mid + 1;
            else {
                long startGid = u32(d, g + 8);
                return (int) (startGid + (cp - start));
            }
        }
        return 0;
    }

    private int lookupFormat4(int cp) {
        if (cp > 0xFFFF) return 0;
        int segX2 = u16(d, cmapOffset + 6);
        int segCount = segX2 / 2;
        int endStart = cmapOffset + 14;
        int startStart = endStart + segX2 + 2;      // + reservedPad
        int deltaStart = startStart + segX2;
        int rangeStart = deltaStart + segX2;

        // Binary search for the first segment whose endCode >= cp.
        int lo = 0, hi = segCount - 1, seg = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int end = u16(d, endStart + mid * 2);
            if (end >= cp) { seg = mid; hi = mid - 1; } else lo = mid + 1;
        }
        if (seg < 0) return 0;

        int start = u16(d, startStart + seg * 2);
        if (start > cp) return 0;
        int idDelta = s16(d, deltaStart + seg * 2);
        int idRangeOffsetPos = rangeStart + seg * 2;
        int idRangeOffset = u16(d, idRangeOffsetPos);
        if (idRangeOffset == 0) {
            return (cp + idDelta) & 0xFFFF;
        }
        // glyphIdArray address = (idRangeOffset field position) + idRangeOffset + 2*(cp - start)
        int gidPos = idRangeOffsetPos + idRangeOffset + 2 * (cp - start);
        if (gidPos + 2 > d.length) return 0;
        int gid = u16(d, gidPos);
        if (gid == 0) return 0;
        return (gid + idDelta) & 0xFFFF;
    }

    // --- big-endian readers ---

    private static int u8(byte[] b, int o)  { return b[o] & 0xFF; }
    private static int u16(byte[] b, int o) { return (u8(b, o) << 8) | u8(b, o + 1); }
    private static int s16(byte[] b, int o) { return (short) u16(b, o); }
    private static long u32(byte[] b, int o) {
        return ((long) u16(b, o) << 16) | (u16(b, o + 2) & 0xFFFFL);
    }

    private static long tagOf(String s) {
        return ((s.charAt(0) & 0xFFL) << 24) | ((s.charAt(1) & 0xFFL) << 16)
                | ((s.charAt(2) & 0xFFL) << 8) | (s.charAt(3) & 0xFFL);
    }
}
