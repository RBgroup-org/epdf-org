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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small CSS parser: inline {@code style="..."} declarations and {@code <style>}
 * stylesheets. At-rules ({@code @media}, {@code @font-face}, {@code @page}) are
 * skipped for now. Each style rule is expanded to one {@link Rule} per selector so
 * specificity can be applied per selector.
 */
public final class CssParser {

    /** One selector paired with its declarations, plus source order for tie-breaking. */
    public record Rule(Selector selector, Map<String, String> declarations, int order) {}

    private CssParser() {}

    /** Parses a {@code style="k:v; k2:v2"} attribute into property → value. */
    public static Map<String, String> parseInline(String inline) {
        Map<String, String> out = new LinkedHashMap<>();
        if (inline == null || inline.isEmpty()) return out;
        parseDeclarations(inline, out);
        return out;
    }

    /** Parses a stylesheet's text into an ordered list of {@link Rule}s. */
    public static List<Rule> parseStylesheet(String css) {
        List<Rule> rules = new ArrayList<>();
        if (css == null || css.isEmpty()) return rules;
        String s = stripComments(css);

        // First pass: read every block, splitting custom properties (--x) from normal
        // declarations. Custom properties are collected globally (they are almost always
        // declared on :root in practice); var() references are substituted afterwards.
        Map<String, String> vars = new LinkedHashMap<>();
        List<PendingBlock> pending = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            int brace = s.indexOf('{', i);
            if (brace < 0) break;
            String selectorText = s.substring(i, brace).trim();
            int close = matchingBrace(s, brace);
            String body = s.substring(brace + 1, Math.min(close, n));
            i = close + 1;
            if (selectorText.startsWith("@")) continue;     // skip at-rules for now
            Map<String, String> decls = new LinkedHashMap<>();
            parseDeclarations(body, decls);
            Map<String, String> normal = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : decls.entrySet()) {
                if (e.getKey().startsWith("--")) vars.put(e.getKey(), e.getValue());
                else normal.put(e.getKey(), e.getValue());
            }
            if (!normal.isEmpty()) pending.add(new PendingBlock(selectorText, normal));
        }
        resolveVarsMap(vars);

        // Second pass: substitute var() in normal declarations and emit one rule per selector.
        int order = 0;
        for (PendingBlock pb : pending) {
            Map<String, String> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : pb.declarations.entrySet()) {
                resolved.put(e.getKey(), substituteVars(e.getValue(), vars));
            }
            for (String one : pb.selectorText.split(",")) {
                Selector sel = Selector.parse(one.trim());
                if (sel != null) rules.add(new Rule(sel, resolved, order++));
            }
        }
        return rules;
    }

    private record PendingBlock(String selectorText, Map<String, String> declarations) {}

    /** Parses all {@code @page} blocks (merged) into a {@link PageRule}, or {@code null} if none. */
    public static PageRule parseAtPage(String css) {
        if (css == null || css.isEmpty()) return null;
        String s = stripComments(css);
        String lower = s.toLowerCase(Locale.ROOT);
        PageRule rule = null;
        int idx = 0;
        while (true) {
            int at = lower.indexOf("@page", idx);
            if (at < 0) break;
            int brace = s.indexOf('{', at);
            if (brace < 0) break;
            int close = matchingBrace(s, brace);
            String body = s.substring(brace + 1, Math.min(close, s.length()));
            rule = PageRule.parse(body, rule);
            idx = close + 1;
        }
        return rule;
    }

    /** Resolves custom properties that reference other custom properties (bounded passes). */
    private static void resolveVarsMap(Map<String, String> vars) {
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;
            for (Map.Entry<String, String> e : vars.entrySet()) {
                String v = e.getValue();
                if (v != null && v.contains("var(")) {
                    String nv = substituteVars(v, vars);
                    if (!nv.equals(v)) { e.setValue(nv); changed = true; }
                }
            }
            if (!changed) break;
        }
    }

    /** Replaces {@code var(--name)} / {@code var(--name, fallback)} using the given map. */
    static String substituteVars(String value, Map<String, String> vars) {
        if (value == null || !value.contains("var(")) return value;
        StringBuilder out = new StringBuilder(value.length());
        int i = 0, n = value.length();
        while (i < n) {
            int v = value.indexOf("var(", i);
            if (v < 0) { out.append(value, i, n); break; }
            out.append(value, i, v);
            int open = v + 3;                       // position of '('
            int close = matchingParen(value, open);
            String inner = value.substring(open + 1, Math.min(close, n));
            String name, fallback = null;
            int comma = inner.indexOf(',');
            if (comma >= 0) { name = inner.substring(0, comma).trim(); fallback = inner.substring(comma + 1).trim(); }
            else name = inner.trim();
            String resolved = vars.get(name);
            if (resolved == null) resolved = (fallback != null) ? fallback : "";
            out.append(resolved);
            i = close + 1;
        }
        return out.toString();
    }

    private static int matchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return s.length();
    }

    // --- helpers ---

    private static void parseDeclarations(String body, Map<String, String> out) {
        for (String decl : body.split(";")) {
            int colon = decl.indexOf(':');
            if (colon <= 0) continue;
            String key = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = decl.substring(colon + 1).trim();
            if (value.toLowerCase(Locale.ROOT).endsWith("!important")) {
                value = value.substring(0, value.length() - "!important".length()).trim();
            }
            if (!key.isEmpty() && !value.isEmpty()) out.put(key, value);
        }
    }

    private static String stripComments(String css) {
        StringBuilder sb = new StringBuilder(css.length());
        int i = 0, n = css.length();
        while (i < n) {
            if (i + 1 < n && css.charAt(i) == '/' && css.charAt(i + 1) == '*') {
                int end = css.indexOf("*/", i + 2);
                i = (end < 0) ? n : end + 2;
            } else {
                sb.append(css.charAt(i++));
            }
        }
        return sb.toString();
    }

    private static int matchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return s.length();
    }

    /** One keyframe step: a stop position (0..1) and its declarations. */
    public record Keyframe(double stop, Map<String, String> declarations) {}

    /** Parses every {@code @keyframes NAME { ... }} block into name → ordered steps. */
    public static Map<String, List<Keyframe>> parseKeyframes(String css) {
        Map<String, List<Keyframe>> out = new LinkedHashMap<>();
        if (css == null || css.isEmpty()) return out;
        String s = stripComments(css)
                .replace("@-webkit-keyframes", "@keyframes")
                .replace("@-moz-keyframes", "@keyframes");
        String low = s.toLowerCase(Locale.ROOT);
        int idx = 0;
        while (true) {
            int at = low.indexOf("@keyframes", idx);
            if (at < 0) break;
            int brace = s.indexOf('{', at);
            if (brace < 0) break;
            String name = s.substring(at + "@keyframes".length(), brace).trim();
            int close = matchingBrace(s, brace);
            String body = s.substring(brace + 1, Math.min(close, s.length()));
            idx = close + 1;
            List<Keyframe> frames = parseKeyframeSteps(body);
            if (!name.isEmpty() && frames.size() >= 2) out.put(name, frames);
        }
        return out;
    }

    private static List<Keyframe> parseKeyframeSteps(String body) {
        List<Keyframe> frames = new ArrayList<>();
        int i = 0, n = body.length();
        while (i < n) {
            int brace = body.indexOf('{', i);
            if (brace < 0) break;
            String sel = body.substring(i, brace).trim();
            int close = matchingBrace(body, brace);
            Map<String, String> decls = new LinkedHashMap<>();
            parseDeclarations(body.substring(brace + 1, Math.min(close, n)), decls);
            i = close + 1;
            for (String one : sel.split(",")) {
                Double stop = keyframeStop(one.trim());
                if (stop != null) frames.add(new Keyframe(stop, decls));
            }
        }
        frames.sort((a, b) -> Double.compare(a.stop(), b.stop()));
        return frames;
    }

    private static Double keyframeStop(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.equals("from")) return 0.0;
        if (t.equals("to")) return 1.0;
        if (t.endsWith("%")) {
            try { return Double.parseDouble(t.substring(0, t.length() - 1)) / 100.0; }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
