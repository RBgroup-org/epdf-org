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
 * A CSS {@code box-shadow}: {@code [inset] offset-x offset-y [blur] [spread] color}.
 * Colour is packed 0xRRGGBB with a separate 0..1 alpha (from {@code rgba()}).
 */
public final class Shadow {

    private final double offsetXPt;
    private final double offsetYPt;
    private final double blurPt;
    private final double spreadPt;
    private final boolean inset;
    private final int rgb;
    private final double alpha;

    public Shadow(double offsetXPt, double offsetYPt, double blurPt, double spreadPt,
                  boolean inset, int rgb, double alpha) {
        this.offsetXPt = offsetXPt;
        this.offsetYPt = offsetYPt;
        this.blurPt = blurPt;
        this.spreadPt = spreadPt;
        this.inset = inset;
        this.rgb = rgb;
        this.alpha = alpha;
    }

    public double offsetXPt() { return offsetXPt; }
    public double offsetYPt() { return offsetYPt; }
    public double blurPt()    { return blurPt; }
    public double spreadPt()  { return spreadPt; }
    public boolean inset()    { return inset; }
    public int rgb()          { return rgb; }
    public double alpha()     { return alpha; }

    /** Parses the first shadow of a (possibly comma-separated) box-shadow value; null if none. */
    public static Shadow parse(String value, double emPt) {
        if (value == null) return null;
        String first = splitTopLevel(value);
        if (first == null) return null;
        String v = first.trim();
        if (v.isEmpty() || v.equalsIgnoreCase("none")) return null;

        boolean inset = false;
        if (v.toLowerCase(Locale.ROOT).matches(".*\\binset\\b.*")) {
            inset = true;
            v = v.replaceAll("(?i)\\binset\\b", " ").trim();
        }

        int rgb = 0x000000;
        double alpha = 1.0;
        int paren = indexOfIgnoreCase(v, "rgb");
        if (paren >= 0) {
            int close = v.indexOf(')', paren);
            if (close > 0) {
                String colorTok = v.substring(paren, close + 1);
                Integer c = CssColor.parse(colorTok);
                if (c != null) rgb = c;
                alpha = rgbaAlpha(colorTok);
                v = (v.substring(0, paren) + " " + v.substring(close + 1)).trim();
            }
        } else if (v.startsWith("#")) {
            int sp = v.indexOf(' ');
            String colorTok = sp < 0 ? v : v.substring(0, sp);
            Integer c = CssColor.parse(colorTok);
            if (c != null) rgb = c;
            v = (sp < 0 ? "" : v.substring(sp)).trim();
        } else {
            String[] parts = v.split("\\s+");
            if (parts.length > 0) {
                Integer c = CssColor.parse(parts[parts.length - 1]);
                if (c != null) {
                    rgb = c;
                    v = v.substring(0, v.lastIndexOf(parts[parts.length - 1])).trim();
                }
            }
        }

        String[] nums = v.isEmpty() ? new String[0] : v.split("\\s+");
        double ox = nums.length > 0 ? lengthPt(nums[0], emPt) : 0;
        double oy = nums.length > 1 ? lengthPt(nums[1], emPt) : 0;
        double blur = nums.length > 2 ? lengthPt(nums[2], emPt) : 0;
        double spread = nums.length > 3 ? lengthPt(nums[3], emPt) : 0;
        return new Shadow(ox, oy, blur, spread, inset, rgb, alpha);
    }

    private static double rgbaAlpha(String rgbFunc) {
        int open = rgbFunc.indexOf('(');
        int close = rgbFunc.indexOf(')');
        if (open < 0 || close < 0) return 1.0;
        String[] parts = rgbFunc.substring(open + 1, close).split(",");
        if (parts.length >= 4) {
            try { return Math.max(0, Math.min(1, Double.parseDouble(parts[3].trim()))); }
            catch (NumberFormatException e) { return 1.0; }
        }
        return 1.0;
    }

    private static double lengthPt(String s, double emPt) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        double sign = 1;
        if (t.startsWith("-")) { sign = -1; t = t.substring(1); }
        else if (t.startsWith("+")) t = t.substring(1);
        int i = 0;
        while (i < t.length() && (Character.isDigit(t.charAt(i)) || t.charAt(i) == '.')) i++;
        if (i == 0) return 0;
        double n;
        try { n = Double.parseDouble(t.substring(0, i)); } catch (NumberFormatException e) { return 0; }
        String unit = t.substring(i);
        double pt = switch (unit) {
            case "pt" -> n;
            case "pc" -> n * 12.0;
            case "in" -> n * 72.0;
            case "cm" -> n * 72.0 / 2.54;
            case "mm" -> n * 72.0 / 25.4;
            case "em", "rem" -> n * emPt;
            default -> n * 0.75;   // px / unitless
        };
        return sign * pt;
    }

    /** The first comma-separated shadow, respecting parentheses (so rgb(...) commas are safe). */
    private static String splitTopLevel(String v) {
        int depth = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return v.substring(0, i);
        }
        return v;
    }

    private static int indexOfIgnoreCase(String hay, String needle) {
        return hay.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }
}
