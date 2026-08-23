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

import com.epdfengine.rb.org.html.HtmlNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A CSS selector supporting type ({@code p}), class ({@code .x}), id ({@code #y}),
 * the universal selector ({@code *}), compound combinations ({@code p.note}),
 * the descendant ({@code a b}), child ({@code a > b}), adjacent-sibling
 * ({@code a + b}) and general-sibling ({@code a ~ b}) combinators, and the
 * structural pseudo-classes {@code :first-child}, {@code :last-child},
 * {@code :nth-child(An+B | even | odd | k)} and {@code :root}.
 *
 * <p>Matching walks the element's ancestor/sibling chain (via
 * {@link HtmlNode#parent()}), so combinators and positional pseudo-classes are
 * evaluated correctly. Pseudo-elements ({@code ::before}/{@code ::after}) never
 * match a real element and are ignored.</p>
 */
public final class Selector {

    private enum Comb { DESCENDANT, CHILD, ADJACENT, GENERAL }

    /** A compound simple selector: tag + classes + id + attributes + optional structural pseudo. */
    private static final class Simple {
        String tag;                 // null = any (universal)
        final List<String> classes = new ArrayList<>();
        final List<Attr> attributes = new ArrayList<>();
        String id;                  // null = none
        int[] nth;                  // {a, b} for :nth-child(An+B); null = none
        boolean firstChild;
        boolean lastChild;
        boolean root;               // :root
        boolean pseudoElement;      // ::before/::after -> never matches
    }

    /** An attribute selector: {@code [name]}, {@code [name=value]}, {@code [name~=value]}, etc. */
    private record Attr(String name, String op, String value) {}

    private final Simple[] simples; // left-to-right; last is the subject
    private final Comb[] combs;     // combs[j] = combinator to the LEFT of simples[j] (combs[0] unused)
    private final int specificity;

    private Selector(Simple[] simples, Comb[] combs, int specificity) {
        this.simples = simples;
        this.combs = combs;
        this.specificity = specificity;
    }

    /** Parses a single selector (no comma). Returns {@code null} if empty/invalid. */
    public static Selector parse(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;

        List<Simple> simples = new ArrayList<>();
        List<Comb> combs = new ArrayList<>();
        combs.add(Comb.DESCENDANT);         // placeholder aligned with simples[0]

        Comb pending = Comb.DESCENDANT;
        int i = 0, n = t.length();
        boolean first = true;
        while (i < n) {
            boolean sawSpace = false;
            while (i < n && Character.isWhitespace(t.charAt(i))) { sawSpace = true; i++; }
            if (i >= n) break;
            char c = t.charAt(i);
            if (c == '>' || c == '+' || c == '~') {
                pending = switch (c) {
                    case '>' -> Comb.CHILD;
                    case '+' -> Comb.ADJACENT;
                    default  -> Comb.GENERAL;
                };
                i++;
                continue;
            }
            if (sawSpace && !first) pending = Comb.DESCENDANT;
            int start = i, depth = 0;
            while (i < n) {                 // read one compound, keeping :nth-child(2n+1) intact
                char d = t.charAt(i);
                if (d == '(') depth++;
                else if (d == ')') depth--;
                else if (depth == 0 && (Character.isWhitespace(d) || d == '>' || d == '+' || d == '~')) break;
                i++;
            }
            Simple s = parseCompound(t.substring(start, i));
            if (s == null) return null;
            simples.add(s);
            if (!first) combs.add(pending);
            pending = Comb.DESCENDANT;
            first = false;
        }
        if (simples.isEmpty()) return null;

        int ids = 0, cls = 0, types = 0;
        for (Simple s : simples) {
            if (s.id != null) ids++;
            cls += s.classes.size();
            cls += s.attributes.size();
            if (s.nth != null) cls++;
            if (s.firstChild) cls++;
            if (s.lastChild) cls++;
            if (s.root) cls++;
            if (s.tag != null && !s.tag.equals("*")) types++;
        }
        int spec = ids * 100 + cls * 10 + types;
        return new Selector(simples.toArray(new Simple[0]), combs.toArray(new Comb[0]), spec);
    }

    public int specificity() { return specificity; }

    /** True if {@code element} matches this selector (subject = rightmost compound). */
    public boolean matches(HtmlNode element) {
        if (element == null || !element.isElement()) return false;
        return matchFrom(simples.length - 1, element);
    }

    private boolean matchFrom(int j, HtmlNode node) {
        if (node == null || !node.isElement()) return false;
        if (!matchSimple(simples[j], node)) return false;
        if (j == 0) return true;
        switch (combs[j]) {
            case CHILD -> { return matchFrom(j - 1, node.parent()); }
            case DESCENDANT -> {
                for (HtmlNode a = node.parent(); a != null; a = a.parent()) {
                    if (matchFrom(j - 1, a)) return true;
                }
                return false;
            }
            case ADJACENT -> { return matchFrom(j - 1, previousElement(node)); }
            case GENERAL -> {
                for (HtmlNode s = previousElement(node); s != null; s = previousElement(s)) {
                    if (matchFrom(j - 1, s)) return true;
                }
                return false;
            }
            default -> { return false; }
        }
    }

    private static boolean matchSimple(Simple s, HtmlNode el) {
        if (s.pseudoElement) return false;
        if (s.tag != null && !s.tag.equals("*") && !s.tag.equals(el.tagName())) return false;
        if (s.id != null && !s.id.equals(el.attr("id"))) return false;
        if (!s.classes.isEmpty()) {
            String classAttr = el.attr("class");
            if (classAttr == null) return false;
            List<String> have = List.of(classAttr.trim().toLowerCase(Locale.ROOT).split("\\s+"));
            for (String c : s.classes) if (!have.contains(c)) return false;
        }
        for (Attr a : s.attributes) {
            if (!attrMatches(a, el.attr(a.name()))) return false;
        }
        if (s.root && !isRoot(el)) return false;
        if (s.firstChild && elementIndex(el) != 1) return false;
        if (s.lastChild && !isLastElement(el)) return false;
        if (s.nth != null && !nthMatches(s.nth, elementIndex(el))) return false;
        return true;
    }

    private static boolean isRoot(HtmlNode el) {
        return el.parent() == null || "html".equals(el.tagName());
    }

    /** 1-based index of {@code el} among its element siblings. */
    private static int elementIndex(HtmlNode el) {
        HtmlNode p = el.parent();
        if (p == null) return 1;
        int idx = 0;
        for (HtmlNode c : p.children()) {
            if (c.isElement()) {
                idx++;
                if (c == el) return idx;
            }
        }
        return idx;
    }

    private static boolean isLastElement(HtmlNode el) {
        HtmlNode p = el.parent();
        if (p == null) return true;
        HtmlNode last = null;
        for (HtmlNode c : p.children()) if (c.isElement()) last = c;
        return last == el;
    }

    private static HtmlNode previousElement(HtmlNode el) {
        HtmlNode p = el.parent();
        if (p == null) return null;
        HtmlNode prev = null;
        for (HtmlNode c : p.children()) {
            if (c == el) return prev;
            if (c.isElement()) prev = c;
        }
        return null;
    }

    private static boolean nthMatches(int[] nth, int index) {
        int a = nth[0], b = nth[1];
        if (a == 0) return index == b;
        int diff = index - b;
        return diff % a == 0 && diff / a >= 0;
    }

    private static Simple parseCompound(String token) {
        Simple s = new Simple();
        int i = 0, n = token.length();
        if (i < n && token.charAt(i) != '.' && token.charAt(i) != '#' && token.charAt(i) != ':' && token.charAt(i) != '[') {
            int start = i;
            while (i < n && token.charAt(i) != '.' && token.charAt(i) != '#' && token.charAt(i) != ':' && token.charAt(i) != '[') i++;
            String tag = token.substring(start, i).toLowerCase(Locale.ROOT);
            if (!tag.isEmpty()) s.tag = tag;
        }
        while (i < n) {
            char c = token.charAt(i++);
            if (c == ':') {
                boolean pseudoElement = (i < n && token.charAt(i) == ':');
                if (pseudoElement) i++;
                int start = i, depth = 0;
                while (i < n) {
                    char d = token.charAt(i);
                    if (d == '(') depth++;
                    else if (d == ')') { depth--; i++; continue; }
                    else if (depth == 0 && (d == '.' || d == '#' || d == ':' || d == '[')) break;
                    i++;
                }
                String name = token.substring(start, i).toLowerCase(Locale.ROOT);
                if (pseudoElement) { s.pseudoElement = true; continue; }
                applyPseudoClass(s, name);
            } else if (c == '[') {
                int start = i;
                while (i < n && token.charAt(i) != ']') i++;
                String body = token.substring(start, i);
                if (i < n) i++;                       // consume ']'
                parseAttribute(s, body);
            } else {
                int start = i;
                while (i < n && token.charAt(i) != '.' && token.charAt(i) != '#' && token.charAt(i) != ':' && token.charAt(i) != '[') i++;
                String name = token.substring(start, i).toLowerCase(Locale.ROOT);
                if (c == '.') s.classes.add(name);
                else if (c == '#') s.id = name;
            }
        }
        return s;
    }

    /** Parses one {@code [name]}, {@code [name=value]} or {@code [name~=value]} attribute clause. */
    private static void parseAttribute(Simple s, String body) {
        String b = body.trim();
        if (b.isEmpty()) return;
        for (String op : new String[]{ "~=", "|=", "^=", "$=", "*=", "=" }) {
            int p = b.indexOf(op);
            if (p > 0) {
                String name = b.substring(0, p).trim().toLowerCase(Locale.ROOT);
                s.attributes.add(new Attr(name, op, unquote(b.substring(p + op.length()).trim())));
                return;
            }
        }
        s.attributes.add(new Attr(b.toLowerCase(Locale.ROOT), "", null));   // [attr] existence test
    }

    private static String unquote(String v) {
        if (v.length() >= 2) {
            char q = v.charAt(0);
            if ((q == '"' || q == '\'') && v.charAt(v.length() - 1) == q) return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static boolean attrMatches(Attr a, String actual) {
        String want = a.value();
        String lo = actual == null ? null : actual.toLowerCase(Locale.ROOT);
        String wl = want == null ? null : want.toLowerCase(Locale.ROOT);
        switch (a.op()) {
            case "":   return actual != null;                                  // [attr]
            case "=":  return actual != null && actual.equalsIgnoreCase(want);
            case "~=": if (lo == null || wl == null || wl.isEmpty()) return false;
                       for (String t : lo.trim().split("\\s+")) if (t.equals(wl)) return true;
                       return false;
            case "|=": return lo != null && wl != null && (lo.equals(wl) || lo.startsWith(wl + "-"));
            case "^=": return lo != null && wl != null && !wl.isEmpty() && lo.startsWith(wl);
            case "$=": return lo != null && wl != null && !wl.isEmpty() && lo.endsWith(wl);
            case "*=": return lo != null && wl != null && !wl.isEmpty() && lo.contains(wl);
            default:   return false;
        }
    }

    private static void applyPseudoClass(Simple s, String name) {
        if (name.equals("root")) { s.root = true; return; }
        if (name.equals("first-child")) { s.firstChild = true; return; }
        if (name.equals("last-child")) { s.lastChild = true; return; }
        if (name.startsWith("nth-child(") && name.endsWith(")")) {
            s.nth = parseNth(name.substring("nth-child(".length(), name.length() - 1).trim());
        }
        // unknown pseudo-classes (:hover, :focus, ...) are ignored (no effect on print)
    }

    /** Parses an An+B expression: {@code even}, {@code odd}, an integer, or {@code an+b}. */
    private static int[] parseNth(String arg) {
        arg = arg.replace(" ", "");
        if (arg.equals("even")) return new int[]{2, 0};
        if (arg.equals("odd")) return new int[]{2, 1};
        int nPos = arg.indexOf('n');
        if (nPos < 0) {
            try { return new int[]{0, Integer.parseInt(arg)}; }
            catch (NumberFormatException e) { return new int[]{0, -1}; }   // never matches
        }
        String aPart = arg.substring(0, nPos);
        String bPart = arg.substring(nPos + 1);
        int a = aPart.isEmpty() || aPart.equals("+") ? 1 : (aPart.equals("-") ? -1 : parseIntSafe(aPart, 1));
        int b = bPart.isEmpty() ? 0 : parseIntSafe(bPart, 0);
        return new int[]{a, b};
    }

    private static int parseIntSafe(String v, int def) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }
}
