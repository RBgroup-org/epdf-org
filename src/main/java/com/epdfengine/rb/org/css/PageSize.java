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
package com.epdfengine.rb.org.css;

import java.util.Locale;

/**
 * CSS {@code @page size} resolution: named page sizes (A3/A4/A5/Letter/Legal/
 * Tabloid), explicit {@code <length> <length>}, and {@code landscape}/{@code portrait}
 * orientation. All values are returned in PostScript points.
 */
public final class PageSize {

    private PageSize() {}

    /** Parses a {@code size} value into {@code {widthPt, heightPt}}, or {@code null} if unrecognised. */
    public static double[] parse(String spec) {
        if (spec == null) return null;
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("auto")) return null;

        boolean landscape = s.contains("landscape");
        boolean portrait = s.contains("portrait");
        String cleaned = s.replace("landscape", " ").replace("portrait", " ").trim();

        double[] wh = named(cleaned);
        if (wh == null) {
            // explicit lengths: "210mm 297mm" or "600pt 800pt"
            String[] parts = cleaned.split("\\s+");
            if (parts.length >= 2) {
                double w = lengthPt(parts[0]);
                double h = lengthPt(parts[1]);
                if (w > 0 && h > 0) wh = new double[]{ w, h };
            } else if (parts.length == 1 && !parts[0].isEmpty()) {
                double v = lengthPt(parts[0]);
                if (v > 0) wh = new double[]{ v, v };
            }
        }
        if (wh == null) {
            // just an orientation keyword: default to A4 in that orientation
            wh = named("a4");
        }
        if (landscape && wh[0] < wh[1]) wh = new double[]{ wh[1], wh[0] };
        if (portrait && wh[0] > wh[1]) wh = new double[]{ wh[1], wh[0] };
        return wh;
    }

    private static double[] named(String name) {
        return switch (name.trim()) {
            case "a3" -> new double[]{ mm(297), mm(420) };
            case "a4" -> new double[]{ mm(210), mm(297) };
            case "a5" -> new double[]{ mm(148), mm(210) };
            case "b4" -> new double[]{ mm(250), mm(353) };
            case "b5" -> new double[]{ mm(176), mm(250) };
            case "letter" -> new double[]{ 612, 792 };
            case "legal" -> new double[]{ 612, 1008 };
            case "tabloid", "ledger" -> new double[]{ 792, 1224 };
            default -> null;
        };
    }

    private static double mm(double v) { return v * 72.0 / 25.4; }

    private static double lengthPt(String t) {
        if (t == null || t.isEmpty()) return -1;
        int i = 0;
        while (i < t.length() && (Character.isDigit(t.charAt(i)) || t.charAt(i) == '.')) i++;
        if (i == 0) return -1;
        double n;
        try { n = Double.parseDouble(t.substring(0, i)); } catch (NumberFormatException e) { return -1; }
        String unit = t.substring(i);
        return switch (unit) {
            case "pt" -> n;
            case "pc" -> n * 12.0;
            case "in" -> n * 72.0;
            case "cm" -> n * 72.0 / 2.54;
            case "mm" -> n * 72.0 / 25.4;
            case "", "px" -> n * 0.75;
            default -> n * 0.75;
        };
    }
}
