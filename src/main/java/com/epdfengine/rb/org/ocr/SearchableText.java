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
package com.epdfengine.rb.org.ocr;

import java.util.List;

/**
 * Builds the <b>invisible text layer</b> that turns a scanned-image page into a searchable,
 * selectable PDF. Given the OCR words (in image pixels, top-left origin) and where the image is
 * drawn on the page (PDF points, bottom-left origin), it emits a content-stream fragment that
 * paints each word with text render mode 3 (invisible) positioned over its glyphs — the standard
 * OCR-overlay technique. The caller places this fragment after the image draw and supplies a font
 * resource (e.g. base-14 Helvetica) present in the page resources.
 */
public final class SearchableText {

    private SearchableText() {}

    /**
     * @param words        recognised words (pixels, top-left origin)
     * @param imgPxWidth   source image width in pixels
     * @param imgPxHeight  source image height in pixels
     * @param drawX        image left edge on the page (pt)
     * @param drawBottomY  image bottom edge on the page (pt, bottom-left origin)
     * @param drawWidthPt  image drawn width (pt)
     * @param drawHeightPt image drawn height (pt)
     * @param fontResource font resource name in the page's /Font dict (e.g. {@code "F1"})
     */
    public static String overlay(List<OcrWord> words, int imgPxWidth, int imgPxHeight,
                                 double drawX, double drawBottomY, double drawWidthPt, double drawHeightPt,
                                 String fontResource) {
        if (words == null || words.isEmpty() || imgPxWidth <= 0 || imgPxHeight <= 0) return "";
        double sx = drawWidthPt / imgPxWidth;
        double sy = drawHeightPt / imgPxHeight;

        StringBuilder sb = new StringBuilder(words.size() * 48);
        sb.append("BT\n3 Tr\n");                       // invisible render mode
        for (OcrWord w : words) {
            if (w.text() == null || w.text().isEmpty()) continue;
            double fs = Math.max(1.0, w.height() * sy);                 // fit box height
            double x = drawX + w.x() * sx;
            double y = drawBottomY + drawHeightPt - (w.y() + w.height()) * sy;   // baseline at box bottom
            double natural = Math.max(1.0, w.text().length() * fs * 0.5);        // ~0.5em/char estimate
            double hz = Math.max(1.0, (w.width() * sx) / natural * 100.0);       // Tz horizontal fit
            sb.append('/').append(fontResource).append(' ').append(num(fs)).append(" Tf\n")
              .append(num(hz)).append(" Tz\n")
              .append("1 0 0 1 ").append(num(x)).append(' ').append(num(y)).append(" Tm\n")
              .append('(').append(escape(w.text())).append(") Tj\n");
        }
        sb.append("ET\n");
        return sb.toString();
    }

    private static String num(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        boolean neg = v < 0;
        if (neg) v = -v;
        long scaled = Math.round(v * 1000.0);
        int f = (int) (scaled % 1000);
        return (neg ? "-" : "") + (scaled / 1000) + "." + pad3(f);
    }

    private static String pad3(int f) {
        return (char) ('0' + f / 100) + "" + (char) ('0' + (f / 10) % 10) + (char) ('0' + f % 10);
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '(' -> b.append("\\(");
                case ')' -> b.append("\\)");
                default -> b.append(c <= 126 && c >= 32 ? c : '?');
            }
        }
        return b.toString();
    }
}
