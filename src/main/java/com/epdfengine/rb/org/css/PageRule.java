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

import com.epdfengine.rb.org.common.Edges;

import java.util.Locale;

/**
 * The resolved CSS {@code @page} rule: page size, margins, and the engine's
 * fit-to-page extensions ({@code -epdf-fit} / {@code -epdf-design-width}).
 * Any field may be {@code null}, meaning "keep the caller's value".
 */
public final class PageRule {

    private final Double widthPt;
    private final Double heightPt;
    private final Edges margins;        // null = keep caller margins
    private final FitMode fit;          // null = REFLOW
    private final Double designWidthPt; // null = engine default
    private final boolean autoHeight;   // true = "long page": height grows to content

    private PageRule(Double widthPt, Double heightPt, Edges margins, FitMode fit, Double designWidthPt, boolean autoHeight) {
        this.widthPt = widthPt;
        this.heightPt = heightPt;
        this.margins = margins;
        this.fit = fit;
        this.designWidthPt = designWidthPt;
        this.autoHeight = autoHeight;
    }

    public Double widthPt()        { return widthPt; }
    public Double heightPt()       { return heightPt; }
    public Edges margins()         { return margins; }
    public FitMode fit()           { return fit != null ? fit : FitMode.REFLOW; }
    public Double designWidthPt()  { return designWidthPt; }
    public boolean autoHeight()    { return autoHeight; }

    /** Parses the declaration body of an {@code @page} block (merging onto {@code prev}). */
    public static PageRule parse(String body, PageRule prev) {
        Double w = prev != null ? prev.widthPt : null;
        Double h = prev != null ? prev.heightPt : null;
        Edges margins = prev != null ? prev.margins : null;
        FitMode fit = prev != null ? prev.fit : null;
        Double designW = prev != null ? prev.designWidthPt : null;
        boolean autoHeight = prev != null && prev.autoHeight;

        for (String decl : body.split(";")) {
            int colon = decl.indexOf(':');
            if (colon <= 0) continue;
            String key = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String val = decl.substring(colon + 1).trim();
            switch (key) {
                case "size" -> {
                    String low = val.toLowerCase(Locale.ROOT);
                    if (low.contains("auto")) {
                        // "<width> auto" or "auto": fixed width (if any) + content-defined height.
                        autoHeight = true;
                        String wOnly = low.replace("auto", " ").trim();
                        if (!wOnly.isEmpty()) {
                            double[] wh = PageSize.parse(wOnly);
                            if (wh != null) w = wh[0];
                        }
                    } else {
                        double[] wh = PageSize.parse(val);
                        if (wh != null) { w = wh[0]; h = wh[1]; autoHeight = false; }
                    }
                }
                case "margin" -> margins = edges(val);
                case "-epdf-fit" -> {
                    String low = val.toLowerCase(Locale.ROOT);
                    if (low.contains("long") || low.equals("auto-height") || low.equals("content-height")) {
                        autoHeight = true;
                    } else {
                        fit = FitMode.parse(val);
                    }
                }
                case "-epdf-design-width" -> { double d = lengthPt(val); if (d > 0) designW = d; }
                default -> { }
            }
        }
        return new PageRule(w, h, margins, fit, designW, autoHeight);
    }

    private static Edges edges(String v) {
        String[] parts = v.trim().split("\\s+");
        double[] q = new double[parts.length];
        for (int i = 0; i < parts.length; i++) q[i] = Math.max(0, lengthPt(parts[i]));
        return switch (q.length) {
            case 1 -> Edges.of(q[0]);
            case 2 -> Edges.symmetric(q[0], q[1]);
            case 3 -> Edges.of(q[0], q[1], q[2], q[1]);
            default -> Edges.of(q[0], q[1], q[2], q[3]);
        };
    }

    private static double lengthPt(String t) {
        if (t == null) return -1;
        t = t.trim().toLowerCase(Locale.ROOT);
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
