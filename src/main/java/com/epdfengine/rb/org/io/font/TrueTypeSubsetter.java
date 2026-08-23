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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Produces a subset of a TrueType {@code glyf}-flavoured font that keeps only the
 * used glyphs (plus the components any composite glyph references, and glyph 0).
 * Clean-room implementation of the OpenType/ISO 14496-22 table model.
 *
 * <p>Glyph ids are <b>retained</b> (unused glyphs become empty outlines), so the
 * embedding stays valid with an Identity {@code CIDToGIDMap}. The output keeps the
 * required tables — {@code head, hhea, maxp, hmtx, loca, glyf} plus {@code cvt ,
 * fpgm, prep} when present — and drops the rest ({@code cmap, name, post, OS/2,
 * GSUB, GPOS, …}) for size. {@code loca} is rewritten in long format.</p>
 */
public final class TrueTypeSubsetter {

    private TrueTypeSubsetter() {}

    private static final String[] KEEP = { "cvt ", "fpgm", "prep" };

    /** Returns a subset font program containing only {@code usedGids} (+ closure + gid 0). */
    public static byte[] subset(byte[] font, Set<Integer> usedGids) {
        Map<String, int[]> dir = readDirectory(font);            // tag -> {offset, length}
        int[] head = dir.get("head"), maxp = dir.get("maxp"), locaT = dir.get("loca"), glyfT = dir.get("glyf");
        if (head == null || maxp == null || locaT == null || glyfT == null) return font;  // not glyf-based

        int numGlyphs = u16(font, maxp[0] + 4);
        boolean longLoca = s16(font, head[0] + 50) != 0;
        int[] loca = new int[numGlyphs + 1];
        for (int i = 0; i <= numGlyphs; i++)
            loca[i] = longLoca ? (int) u32(font, locaT[0] + i * 4) : u16(font, locaT[0] + i * 2) * 2;

        // Closure: keep glyph 0, requested gids, and every component of composite glyphs.
        Set<Integer> keep = new TreeSet<>();
        keep.add(0);
        for (int g : usedGids) if (g >= 0 && g < numGlyphs) keep.add(g);
        List<Integer> stack = new ArrayList<>(keep);
        while (!stack.isEmpty()) {
            int g = stack.remove(stack.size() - 1);
            int start = glyfT[0] + loca[g], end = glyfT[0] + loca[g + 1];
            if (end <= start) continue;
            if (s16(font, start) >= 0) continue;                 // simple glyph, no components
            int p = start + 10;                                  // skip numberOfContours + bbox
            boolean more = true;
            while (more) {
                int flags = u16(font, p);
                int compGid = u16(font, p + 2);
                p += 4;
                p += (flags & 0x0001) != 0 ? 4 : 2;              // ARG_1_AND_2_ARE_WORDS
                if ((flags & 0x0008) != 0) p += 2;               // WE_HAVE_A_SCALE
                else if ((flags & 0x0040) != 0) p += 4;          // X_AND_Y_SCALE
                else if ((flags & 0x0080) != 0) p += 8;          // TWO_BY_TWO
                if (keep.add(compGid)) stack.add(compGid);
                more = (flags & 0x0020) != 0;                    // MORE_COMPONENTS
            }
        }

        // New glyf (only kept glyphs, gid numbering retained) + new long loca.
        java.io.ByteArrayOutputStream glyf = new java.io.ByteArrayOutputStream();
        int[] newLoca = new int[numGlyphs + 1];
        for (int g = 0; g < numGlyphs; g++) {
            newLoca[g] = glyf.size();
            if (keep.contains(g)) {
                int start = glyfT[0] + loca[g], len = loca[g + 1] - loca[g];
                glyf.write(font, start, len);
                while (glyf.size() % 2 != 0) glyf.write(0);      // word-align
            }
        }
        newLoca[numGlyphs] = glyf.size();
        byte[] glyfBytes = glyf.toByteArray();

        byte[] locaBytes = new byte[(numGlyphs + 1) * 4];
        for (int i = 0; i <= numGlyphs; i++) writeU32(locaBytes, i * 4, newLoca[i]);

        // head copy with indexToLocFormat = 1 (long) and checkSumAdjustment zeroed for now.
        byte[] headBytes = slice(font, head[0], head[1]);
        headBytes[50] = 0; headBytes[51] = 1;
        writeU32(headBytes, 8, 0);

        Map<String, byte[]> out = new LinkedHashMap<>();
        out.put("head", headBytes);
        out.put("hhea", slice(font, dir.get("hhea")[0], dir.get("hhea")[1]));
        out.put("maxp", slice(font, maxp[0], maxp[1]));
        out.put("hmtx", slice(font, dir.get("hmtx")[0], dir.get("hmtx")[1]));
        for (String t : KEEP) if (dir.containsKey(t)) out.put(t, slice(font, dir.get(t)[0], dir.get(t)[1]));
        out.put("loca", locaBytes);
        out.put("glyf", glyfBytes);

        return assemble(font, out);
    }

    // ---- sfnt assembly -----------------------------------------------------

    private static byte[] assemble(byte[] orig, Map<String, byte[]> tables) {
        int n = tables.size();
        int dirEnd = 12 + n * 16;
        int total = dirEnd;
        Map<String, Integer> offsets = new HashMap<>();
        List<String> tags = new ArrayList<>(tables.keySet());
        java.util.Collections.sort(tags);                        // directory sorted by tag
        for (String tag : tags) { offsets.put(tag, total); total += pad4(tables.get(tag).length); }

        byte[] o = new byte[total];
        System.arraycopy(orig, 0, o, 0, 4);                      // sfnt version
        writeU16(o, 4, n);
        int maxPow = Integer.highestOneBit(n);
        int entrySel = Integer.numberOfTrailingZeros(maxPow);
        writeU16(o, 6, maxPow * 16);
        writeU16(o, 8, entrySel);
        writeU16(o, 10, n * 16 - maxPow * 16);

        int d = 12;
        for (String tag : tags) {
            byte[] data = tables.get(tag);
            int off = offsets.get(tag);
            System.arraycopy(data, 0, o, off, data.length);
            for (int i = 0; i < 4; i++) o[d + i] = (byte) tag.charAt(i);
            writeU32(o, d + 4, checksum(o, off, pad4(data.length)));
            writeU32(o, d + 8, off);
            writeU32(o, d + 12, data.length);
            d += 16;
        }

        long sum = checksum(o, 0, o.length) & 0xFFFFFFFFL;       // whole-font checksum
        int headOff = offsets.get("head");
        writeU32(o, headOff + 8, (int) (0xB1B0AFBAL - sum));     // head.checkSumAdjustment
        return o;
    }

    private static Map<String, int[]> readDirectory(byte[] f) {
        int numTables = u16(f, 4);
        Map<String, int[]> dir = new HashMap<>();
        for (int i = 0; i < numTables; i++) {
            int rec = 12 + i * 16;
            String tag = new String(new char[]{ (char) u8(f, rec), (char) u8(f, rec + 1),
                    (char) u8(f, rec + 2), (char) u8(f, rec + 3) });
            dir.put(tag, new int[]{ (int) u32(f, rec + 8), (int) u32(f, rec + 12) });
        }
        return dir;
    }

    private static int checksum(byte[] b, int off, int len) {
        long s = 0;
        for (int i = 0; i < len; i += 4) s += u32(b, off + i) & 0xFFFFFFFFL;
        return (int) s;
    }

    private static int pad4(int n) { return (n + 3) & ~3; }
    private static byte[] slice(byte[] b, int off, int len) {
        byte[] r = new byte[len]; System.arraycopy(b, off, r, 0, len); return r;
    }
    private static int u8(byte[] b, int o)  { return b[o] & 0xFF; }
    private static int u16(byte[] b, int o) { return (u8(b, o) << 8) | u8(b, o + 1); }
    private static int s16(byte[] b, int o) { return (short) u16(b, o); }
    private static long u32(byte[] b, int o) {
        return ((long) u8(b, o) << 24) | (u8(b, o + 1) << 16) | (u8(b, o + 2) << 8) | u8(b, o + 3);
    }
    private static void writeU16(byte[] b, int o, int v) { b[o] = (byte) (v >> 8); b[o + 1] = (byte) v; }
    private static void writeU32(byte[] b, int o, int v) {
        b[o] = (byte) (v >> 24); b[o + 1] = (byte) (v >> 16); b[o + 2] = (byte) (v >> 8); b[o + 3] = (byte) v;
    }
}
