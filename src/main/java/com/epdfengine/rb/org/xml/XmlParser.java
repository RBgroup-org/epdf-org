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
package com.epdfengine.rb.org.xml;

/**
 * A small, tolerant, XXE-proof XML parser producing an {@link XmlNode} tree.
 *
 * <p>Security by design: the {@code <!DOCTYPE>} declaration (including any internal
 * subset) is skipped without processing — no external DTD/entity is ever fetched and
 * no custom entities are expanded. Only the five predefined entities and numeric
 * character references are decoded. A nesting-depth cap guards against billion-laughs
 * style structural blowups.</p>
 */
public final class XmlParser {

    private static final int MAX_DEPTH = 400;

    private final String s;
    private final int len;
    private int i;

    private XmlParser(String src) { this.s = (src == null) ? "" : src; this.len = s.length(); }

    /** Parses {@code xml} and returns the root element, or {@code null} if none is found. */
    public static XmlNode parse(String xml) {
        return new XmlParser(stripBom(xml)).document();
    }

    private static String stripBom(String x) {
        return (x != null && !x.isEmpty() && x.charAt(0) == '\uFEFF') ? x.substring(1) : x;
    }

    private XmlNode document() {
        skipMisc();
        if (i >= len || s.charAt(i) != '<') return null;
        return element(0);
    }

    /** Skips whitespace, the XML declaration, comments, PIs and the DOCTYPE (unprocessed). */
    private void skipMisc() {
        while (i < len) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c != '<') return;
            if (starts("<!--")) { int e = s.indexOf("-->", i + 4); i = (e < 0) ? len : e + 3; continue; }
            if (starts("<?"))   { int e = s.indexOf("?>", i + 2);  i = (e < 0) ? len : e + 2; continue; }
            if (starts("<!DOCTYPE")) { skipDoctype(); continue; }
            return;   // an element start tag
        }
    }

    private void skipDoctype() {
        i += "<!DOCTYPE".length();
        int depth = 0;
        while (i < len) {
            char c = s.charAt(i++);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == '>' && depth <= 0) return;
        }
    }

    /** Parses one element starting at the current '<'. */
    private XmlNode element(int depth) {
        i++;                                   // consume '<'
        String name = readName();
        XmlNode el = XmlNode.element(name);
        // attributes
        while (i < len) {
            skipWs();
            char c = peek();
            if (c == '/' || c == '>' || c == 0) break;
            String an = readName();
            if (an.isEmpty()) { i++; continue; }
            String av = "";
            skipWs();
            if (peek() == '=') {
                i++; skipWs();
                char q = peek();
                if (q == '"' || q == '\'') {
                    i++;
                    int start = i;
                    while (i < len && s.charAt(i) != q) i++;
                    av = decodeEntities(s.substring(start, Math.min(i, len)));
                    if (i < len) i++;          // consume closing quote
                }
            }
            el.setAttr(an, av);
        }
        if (peek() == '/') {                   // self-closing
            i++;
            if (peek() == '>') i++;
            return el;
        }
        if (peek() == '>') i++;
        parseContent(el, depth);
        return el;
    }

    /** Parses element content (text, CDATA, child elements) until the matching end tag. */
    private void parseContent(XmlNode el, int depth) {
        StringBuilder text = new StringBuilder();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '<') {
                if (starts("<![CDATA[")) {
                    int e = s.indexOf("]]>", i + 9);
                    text.append(s, i + 9, (e < 0) ? len : e);
                    i = (e < 0) ? len : e + 3;
                    continue;
                }
                if (starts("<!--")) { int e = s.indexOf("-->", i + 4); i = (e < 0) ? len : e + 3; continue; }
                if (starts("<?"))   { int e = s.indexOf("?>", i + 2);  i = (e < 0) ? len : e + 2; continue; }
                flushText(el, text);
                if (starts("</")) {             // end tag
                    i += 2;
                    readName();
                    while (i < len && s.charAt(i) != '>') i++;
                    if (i < len) i++;
                    return;
                }
                if (depth < MAX_DEPTH) el.addChild(element(depth + 1));
                else { skipElement(); }
            } else {
                text.append(c);
                i++;
            }
        }
        flushText(el, text);
    }

    private void flushText(XmlNode el, StringBuilder text) {
        if (text.length() == 0) return;
        el.addChild(XmlNode.text(decodeEntities(text.toString())));
        text.setLength(0);
    }

    /** Skips a full element (used only past the depth cap) without building nodes. */
    private void skipElement() {
        int guard = 0;
        while (i < len && guard++ < len) {
            if (s.charAt(i) == '<' && starts("</")) { while (i < len && s.charAt(i) != '>') i++; if (i < len) i++; return; }
            i++;
        }
    }

    private String readName() {
        skipWs();
        int start = i;
        while (i < len) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '>' || c == '/' || c == '=' || c == '<') break;
            i++;
        }
        return s.substring(start, i);
    }

    private void skipWs() { while (i < len && Character.isWhitespace(s.charAt(i))) i++; }
    private char peek()   { return i < len ? s.charAt(i) : 0; }
    private boolean starts(String t) { return s.regionMatches(i, t, 0, t.length()); }

    /** Decodes the five predefined entities and numeric character references only. */
    private static String decodeEntities(String in) {
        if (in.indexOf('&') < 0) return in;
        StringBuilder out = new StringBuilder(in.length());
        int j = 0, n = in.length();
        while (j < n) {
            char c = in.charAt(j);
            if (c == '&') {
                int semi = in.indexOf(';', j + 1);
                if (semi > j && semi - j <= 12) {
                    String ent = in.substring(j + 1, semi);
                    String rep = entity(ent);
                    if (rep != null) { out.append(rep); j = semi + 1; continue; }
                }
            }
            out.append(c);
            j++;
        }
        return out.toString();
    }

    private static String entity(String ent) {
        switch (ent) {
            case "amp":  return "&";
            case "lt":   return "<";
            case "gt":   return ">";
            case "quot": return "\"";
            case "apos": return "'";
            default:
                if (!ent.isEmpty() && ent.charAt(0) == '#') {
                    try {
                        int cp = ent.length() > 1 && (ent.charAt(1) == 'x' || ent.charAt(1) == 'X')
                                ? Integer.parseInt(ent.substring(2), 16)
                                : Integer.parseInt(ent.substring(1));
                        if (cp > 0 && cp <= 0x10FFFF) return new String(Character.toChars(cp));
                    } catch (NumberFormatException ignored) { /* leave as-is */ }
                }
                return null;   // unknown/custom entity: not expanded (XXE-safe)
        }
    }
}
