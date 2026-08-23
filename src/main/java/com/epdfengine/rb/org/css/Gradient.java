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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A CSS {@code linear-gradient(...)}. The angle follows CSS conventions
 * (0deg = to top, 90deg = to right, 180deg = to bottom); stop positions are
 * normalized to 0..1 along the gradient line. The renderer maps this onto a PDF
 * axial shading.
 */
public final class Gradient {

    public enum Type { LINEAR, RADIAL, CONIC }

    /** A colour stop: position 0..1 along the line, colour packed 0xRRGGBB. */
    public record Stop(double position, int rgb) {}

    private final Type type;
    private final double angleDeg;          // LINEAR: CSS angle; CONIC: start angle
    private final double centerXFrac, centerYFrac;  // RADIAL/CONIC: centre as a fraction of the box
    private final List<Stop> stops;

    public Gradient(double angleDeg, List<Stop> stops) {
        this.type = Type.LINEAR;
        this.angleDeg = angleDeg;
        this.centerXFrac = 0.5; this.centerYFrac = 0.5;
        this.stops = stops;
    }

    public Gradient(List<Stop> stops, double centerXFrac, double centerYFrac) {
        this.type = Type.RADIAL;
        this.angleDeg = 0;
        this.centerXFrac = centerXFrac; this.centerYFrac = centerYFrac;
        this.stops = stops;
    }

    public Gradient(List<Stop> stops, double centerXFrac, double centerYFrac, double startAngleDeg) {
        this.type = Type.CONIC;
        this.angleDeg = startAngleDeg;
        this.centerXFrac = centerXFrac; this.centerYFrac = centerYFrac;
        this.stops = stops;
    }

    public Type type()         { return type; }
    public double angleDeg()   { return angleDeg; }
    public double centerXFrac(){ return centerXFrac; }
    public double centerYFrac(){ return centerYFrac; }
    public List<Stop> stops()  { return stops; }

    /** Parses a {@code linear-gradient(...)}, {@code radial-gradient(...)} or {@code conic-gradient(...)}. */
    public static Gradient parse(String value) {
        Gradient g = parseLinear(value);
        if (g != null) return g;
        g = parseRadial(value);
        return g != null ? g : parseConic(value);
    }

    /**
     * Parses a {@code conic-gradient(...)} value: an optional {@code from <angle>}
     * and {@code at <pos>} prefix, then angular colour stops ({@code red 0deg}).
     */
    public static Gradient parseConic(String value) {
        if (value == null) return null;
        String v = value.trim();
        String low = v.toLowerCase(Locale.ROOT);
        if (!low.startsWith("conic-gradient(") || !v.endsWith(")")) return null;
        String inner = v.substring("conic-gradient(".length(), v.length() - 1);
        List<String> parts = splitTopLevel(inner);
        if (parts.isEmpty()) return null;

        double cx = 0.5, cy = 0.5, start = 0;
        int startIdx = 0;
        String first = parts.get(0).trim().toLowerCase(Locale.ROOT);
        if (first.startsWith("from") || first.startsWith("at ") || first.contains(" at ")) {
            int fromPos = first.indexOf("from");
            int atPos = first.indexOf(" at ");
            if (atPos < 0 && first.startsWith("at ")) atPos = 0;
            if (fromPos >= 0) {
                String fromPart = (atPos > 0 ? first.substring(fromPos + 4, atPos) : first.substring(fromPos + 4)).trim();
                if (fromPart.endsWith("deg")) { Double a = num(fromPart.substring(0, fromPart.length() - 3)); if (a != null) start = a; }
            }
            if (atPos >= 0) {
                String atPart = first.startsWith("at ") ? first.substring(3) : first.substring(atPos + 4);
                double[] c = parsePosition(atPart.trim());
                cx = c[0]; cy = c[1];
            }
            startIdx = 1;
        }
        List<Stop> stops = new ArrayList<>();
        for (int i = startIdx; i < parts.size(); i++) {
            Stop s = parseConicStop(parts.get(i).trim());
            if (s != null) stops.add(s);
        }
        if (stops.size() < 2) return null;
        normalize(stops);
        return new Gradient(stops, cx, cy, start);
    }

    private static Stop parseConicStop(String token) {
        int sp = token.lastIndexOf(' ');
        Double pos = null;
        String colorText = token;
        if (sp > 0) {
            String tail = token.substring(sp + 1).trim().toLowerCase(Locale.ROOT);
            if (tail.endsWith("deg")) {
                Double d = num(tail.substring(0, tail.length() - 3));
                if (d != null) { pos = d / 360.0; colorText = token.substring(0, sp).trim(); }
            } else if (tail.endsWith("%")) {
                Double p = num(tail.substring(0, tail.length() - 1));
                if (p != null) { pos = p / 100.0; colorText = token.substring(0, sp).trim(); }
            }
        }
        Integer rgb = CssColor.parse(colorText);
        if (rgb == null) return null;
        return new Stop(pos == null ? Double.NaN : pos, rgb);
    }

    /**
     * Parses a {@code radial-gradient(...)} value. Supports an optional shape/size/
     * position prefix ({@code circle at 20% 30%}) then two or more colour stops.
     */
    public static Gradient parseRadial(String value) {
        if (value == null) return null;
        String v = value.trim();
        String low = v.toLowerCase(Locale.ROOT);
        if (!low.startsWith("radial-gradient(") || !v.endsWith(")")) return null;
        String inner = v.substring("radial-gradient(".length(), v.length() - 1);
        List<String> parts = splitTopLevel(inner);
        if (parts.isEmpty()) return null;

        double cx = 0.5, cy = 0.5;
        int start = 0;
        String first = parts.get(0).trim().toLowerCase(Locale.ROOT);
        boolean isPrefix = first.startsWith("circle") || first.startsWith("ellipse")
                || first.contains(" at ") || first.contains("closest") || first.contains("farthest");
        if (isPrefix) {
            int at = first.indexOf(" at ");
            if (at >= 0) { double[] c = parsePosition(first.substring(at + 4).trim()); cx = c[0]; cy = c[1]; }
            start = 1;
        }
        List<Stop> stops = new ArrayList<>();
        for (int i = start; i < parts.size(); i++) {
            Stop s = parseStop(parts.get(i).trim());
            if (s != null) stops.add(s);
        }
        if (stops.size() < 2) return null;
        normalize(stops);
        return new Gradient(stops, cx, cy);
    }

    private static double[] parsePosition(String pos) {
        String[] t = pos.trim().split("\\s+");
        double x = t.length >= 1 ? posFrac(t[0]) : 0.5;
        double y = t.length >= 2 ? posFrac(t[1]) : 0.5;
        return new double[]{ x, y };
    }

    private static double posFrac(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith("%")) { Double p = num(s.substring(0, s.length() - 1)); return p == null ? 0.5 : p / 100.0; }
        return switch (s) {
            case "left", "top" -> 0.0;
            case "right", "bottom" -> 1.0;
            default -> 0.5;
        };
    }

    /**
     * Parses a {@code linear-gradient(...)} value. Returns {@code null} for other
     * or unparseable values. Supports an optional leading angle ({@code 135deg})
     * or direction ({@code to bottom right}), then two or more colour stops each
     * with an optional percentage position.
     */
    public static Gradient parseLinear(String value) {
        if (value == null) return null;
        String v = value.trim();
        String low = v.toLowerCase(Locale.ROOT);
        if (!low.startsWith("linear-gradient(") || !v.endsWith(")")) return null;
        String inner = v.substring("linear-gradient(".length(), v.length() - 1);
        List<String> parts = splitTopLevel(inner);
        if (parts.isEmpty()) return null;

        double angle = 180; // CSS default: to bottom
        int start = 0;
        String first = parts.get(0).trim().toLowerCase(Locale.ROOT);
        if (first.endsWith("deg")) {
            Double a = num(first.substring(0, first.length() - 3));
            if (a != null) angle = a;
            start = 1;
        } else if (first.startsWith("to ")) {
            angle = directionToAngle(first.substring(3).trim());
            start = 1;
        }

        List<Stop> stops = new ArrayList<>();
        for (int i = start; i < parts.size(); i++) {
            Stop s = parseStop(parts.get(i).trim());
            if (s != null) stops.add(s);
        }
        if (stops.size() < 2) return null;
        normalize(stops);
        return new Gradient(angle, stops);
    }

    // --- helpers ---

    private static Stop parseStop(String token) {
        // "<color> [<pos>%]" — the position is the last whitespace-separated field if it ends with %.
        int sp = token.lastIndexOf(' ');
        Double pos = null;
        String colorText = token;
        if (sp > 0 && token.endsWith("%")) {
            Double p = num(token.substring(sp + 1, token.length() - 1));
            if (p != null) { pos = p / 100.0; colorText = token.substring(0, sp).trim(); }
        }
        Integer rgb = CssColor.parse(colorText);
        if (rgb == null) return null;
        return new Stop(pos == null ? Double.NaN : pos, rgb);
    }

    /** Fills unset (NaN) positions: ends default to 0 and 1, interior gaps interpolate. */
    private static void normalize(List<Stop> stops) {
        int n = stops.size();
        if (Double.isNaN(stops.get(0).position())) stops.set(0, new Stop(0.0, stops.get(0).rgb()));
        if (Double.isNaN(stops.get(n - 1).position())) stops.set(n - 1, new Stop(1.0, stops.get(n - 1).rgb()));
        int i = 0;
        while (i < n) {
            if (!Double.isNaN(stops.get(i).position())) { i++; continue; }
            int j = i;
            while (j < n && Double.isNaN(stops.get(j).position())) j++;
            double before = stops.get(i - 1).position();
            double after = stops.get(j).position();
            int gaps = j - i + 1;
            for (int k = i; k < j; k++) {
                double p = before + (after - before) * (k - i + 1) / gaps;
                stops.set(k, new Stop(p, stops.get(k).rgb()));
            }
            i = j;
        }
    }

    private static double directionToAngle(String dir) {
        return switch (dir) {
            case "top" -> 0;
            case "right" -> 90;
            case "bottom" -> 180;
            case "left" -> 270;
            case "top right", "right top" -> 45;
            case "bottom right", "right bottom" -> 135;
            case "bottom left", "left bottom" -> 225;
            case "top left", "left top" -> 315;
            default -> 180;
        };
    }

    /** Splits on top-level commas, ignoring commas inside parentheses (e.g. rgb(...)). */
    private static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) { out.add(s.substring(start, i)); start = i + 1; }
        }
        out.add(s.substring(start));
        return out;
    }

    private static Double num(String s) {
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
