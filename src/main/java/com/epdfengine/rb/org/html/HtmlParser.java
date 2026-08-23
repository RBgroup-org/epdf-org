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
package com.epdfengine.rb.org.html;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A forgiving, tag-soup HTML parser producing an {@link HtmlNode} tree. It is not a
 * full HTML5 tree-construction implementation, but it is robust for report/invoice
 * templates: it handles void elements, raw-text elements ({@code script}/
 * {@code style}), comments, doctype, quoted/unquoted/boolean attributes, and a
 * useful set of character entities.
 *
 * <p>Security: no script is executed and no external entities are resolved.
 * Parsing is a single linear pass, so input size is the only cost driver.</p>
 */
public final class HtmlParser {

    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr");

    private static final Set<String> RAW_TEXT_ELEMENTS = Set.of("script", "style");

    public HtmlNode parse(String html) {
        if (html == null) html = "";
        HtmlNode document = HtmlNode.element("#document");
        Deque<HtmlNode> stack = new ArrayDeque<>();
        stack.push(document);

        StringBuilder text = new StringBuilder();
        int i = 0;
        int n = html.length();

        while (i < n) {
            char c = html.charAt(i);
            if (c != '<' || i + 1 >= n) {
                text.append(c);
                i++;
                continue;
            }
            char next = html.charAt(i + 1);

            if (next == '!') {                              // comment or doctype/declaration
                if (html.startsWith("<!--", i)) {
                    int end = html.indexOf("-->", i + 4);
                    i = (end < 0) ? n : end + 3;
                } else {
                    int end = html.indexOf('>', i);
                    i = (end < 0) ? n : end + 1;
                }
                continue;
            }

            if (next == '/') {                              // end tag
                int end = html.indexOf('>', i);
                if (end < 0) break;
                String tag = tagNameOf(html.substring(i + 2, end));
                flushText(stack.peek(), text);
                popUntil(stack, tag);
                i = end + 1;
                continue;
            }

            if (!Character.isLetter(next)) {                // stray '<'
                text.append(c);
                i++;
                continue;
            }

            int end = findTagEnd(html, i);                  // start tag
            if (end < 0) {
                text.append(c);
                i++;
                continue;
            }
            String raw = html.substring(i + 1, end);
            boolean selfClose = raw.endsWith("/");
            if (selfClose) raw = raw.substring(0, raw.length() - 1);
            ParsedTag pt = parseTag(raw);
            if (pt.name.isEmpty()) { i = end + 1; continue; }

            flushText(stack.peek(), text);
            HtmlNode el = HtmlNode.element(pt.name);
            el.attributes().putAll(pt.attrs);
            stack.peek().addChild(el);

            if (!selfClose && !VOID_ELEMENTS.contains(pt.name)) {
                stack.push(el);
                if (RAW_TEXT_ELEMENTS.contains(pt.name)) {
                    String close = "</" + pt.name;
                    int rawEnd = indexOfIgnoreCase(html, close, end + 1);
                    if (rawEnd < 0) rawEnd = n;
                    String body = html.substring(end + 1, rawEnd);
                    if (!body.isEmpty()) el.addChild(HtmlNode.text(body)); // no entity decode
                    i = rawEnd;                              // end tag handled by the loop
                    continue;
                }
            }
            i = end + 1;
        }
        flushText(stack.peek(), text);
        return document;
    }

    // --- helpers ---

    private static String tagNameOf(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int sp = 0;
        while (sp < s.length() && !Character.isWhitespace(s.charAt(sp))) sp++;
        return s.substring(0, sp);
    }

    private static int findTagEnd(String s, int from) {
        boolean quoted = false;
        char q = 0;
        for (int j = from + 1; j < s.length(); j++) {
            char ch = s.charAt(j);
            if (quoted) {
                if (ch == q) quoted = false;
            } else if (ch == '"' || ch == '\'') {
                quoted = true;
                q = ch;
            } else if (ch == '>') {
                return j;
            }
        }
        return -1;
    }

    private static void flushText(HtmlNode parent, StringBuilder buf) {
        if (buf.length() == 0 || parent == null) return;
        parent.addChild(HtmlNode.text(decodeEntities(buf.toString())));
        buf.setLength(0);
    }

    private static void popUntil(Deque<HtmlNode> stack, String tag) {
        boolean present = false;
        for (HtmlNode node : stack) {
            if (node.isElement() && tag.equals(node.tagName())) { present = true; break; }
        }
        if (!present) return;                               // stray end tag: ignore
        while (!stack.isEmpty()) {
            HtmlNode top = stack.pop();
            if (tag.equals(top.tagName())) return;
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        int hl = haystack.length();
        int nl = needle.length();
        outer:
        for (int i = from; i <= hl - nl; i++) {
            for (int j = 0; j < nl; j++) {
                if (Character.toLowerCase(haystack.charAt(i + j)) != Character.toLowerCase(needle.charAt(j))) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private record ParsedTag(String name, Map<String, String> attrs) {}

    private static ParsedTag parseTag(String raw) {
        int i = 0;
        int n = raw.length();
        while (i < n && !Character.isWhitespace(raw.charAt(i))) i++;
        String name = raw.substring(0, i).toLowerCase(Locale.ROOT);

        Map<String, String> attrs = new LinkedHashMap<>();
        while (i < n) {
            while (i < n && Character.isWhitespace(raw.charAt(i))) i++;
            if (i >= n) break;
            int keyStart = i;
            while (i < n && raw.charAt(i) != '=' && !Character.isWhitespace(raw.charAt(i))) i++;
            String key = raw.substring(keyStart, i).toLowerCase(Locale.ROOT);
            String value = "";
            if (i < n && raw.charAt(i) == '=') {
                i++;
                if (i < n && (raw.charAt(i) == '"' || raw.charAt(i) == '\'')) {
                    char quote = raw.charAt(i++);
                    int vs = i;
                    while (i < n && raw.charAt(i) != quote) i++;
                    value = raw.substring(vs, i);
                    if (i < n) i++;
                } else {
                    int vs = i;
                    while (i < n && !Character.isWhitespace(raw.charAt(i))) i++;
                    value = raw.substring(vs, i);
                }
            }
            if (!key.isEmpty()) attrs.put(key, decodeEntities(value));
        }
        return new ParsedTag(name, attrs);
    }

    private static final Map<String, Integer> NAMED_ENTITIES = buildNamedEntities();

    private static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c != '&') { out.append(c); i++; continue; }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 12) { out.append(c); i++; continue; }
            String body = s.substring(i + 1, semi);
            int cp = -1;
            if (body.startsWith("#x") || body.startsWith("#X")) {
                cp = parseInt(body.substring(2), 16);
            } else if (body.startsWith("#")) {
                cp = parseInt(body.substring(1), 10);
            } else {
                Integer named = NAMED_ENTITIES.get(body);
                if (named != null) cp = named;
            }
            if (cp >= 0 && cp <= 0x10FFFF) {
                out.appendCodePoint(cp);
                i = semi + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int parseInt(String s, int radix) {
        try { return Integer.parseInt(s, radix); } catch (NumberFormatException e) { return -1; }
    }

    private static Map<String, Integer> buildNamedEntities() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("amp", 38); m.put("lt", 60); m.put("gt", 62);
        m.put("quot", 34); m.put("apos", 39); m.put("nbsp", 160);
        m.put("copy", 169); m.put("reg", 174); m.put("trade", 8482);
        m.put("mdash", 8212); m.put("ndash", 8211); m.put("hellip", 8230);
        m.put("bull", 8226); m.put("middot", 183); m.put("deg", 176);
        m.put("euro", 8364); m.put("pound", 163); m.put("cent", 162);
        m.put("laquo", 171); m.put("raquo", 187);
        m.put("ldquo", 8220); m.put("rdquo", 8221);
        m.put("lsquo", 8216); m.put("rsquo", 8217);
        m.put("times", 215); m.put("divide", 247);
        return m;
    }
}
