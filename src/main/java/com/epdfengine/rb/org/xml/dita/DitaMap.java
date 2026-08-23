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
package com.epdfengine.rb.org.xml.dita;

import com.epdfengine.rb.org.xml.XmlNode;
import com.epdfengine.rb.org.xml.XmlParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a DITA map ({@code .ditamap}) into a single publication: it walks the
 * {@code topicref} hierarchy, resolves each referenced topic through a caller-supplied
 * {@link TopicResolver}, transforms every topic to HTML, and prepends a generated,
 * numbered table of contents — a clean-room analogue of a DITA-OT PDF map transform.
 */
public final class DitaMap {

    private DitaMap() {}

    /** Resolves a topic {@code href} (relative to the map) to its XML source, or {@code null}. */
    public interface TopicResolver {
        String read(String href);
    }

    /** Builds the combined HTML document for {@code ditamapXml} using the default stylesheet. */
    public static String mapToHtml(String ditamapXml, TopicResolver resolver) {
        return mapToHtml(ditamapXml, resolver, DitaTransformer.defaultCss() + MAP_CSS);
    }

    /** Builds the combined HTML document with a caller-supplied stylesheet. */
    public static String mapToHtml(String ditamapXml, TopicResolver resolver, String css) {
        XmlNode map = XmlParser.parse(ditamapXml);
        String mapTitle = (map == null) ? "" : map.attr("title");
        if (mapTitle.isBlank() && map != null) mapTitle = navtitle(map);
        if (mapTitle.isBlank() && map != null) mapTitle = DitaTransformer.titleOf(map);

        List<Entry> entries = new ArrayList<>();
        java.util.Map<String, String[]> keySpace = new java.util.LinkedHashMap<>();
        if (map != null) walk(map, 0, resolver, entries, keySpace);
        number(entries);

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html><html><head><style>").append(css).append("</style></head><body>");
        sb.append("<article class=\"dita ditamap\">");
        if (!mapTitle.isBlank()) sb.append("<h1 class=\"map-title\">").append(escape(mapTitle)).append("</h1>");

        // Table of contents.
        sb.append("<nav class=\"toc\"><div class=\"toc-title\">Contents</div>");
        for (Entry e : entries) {
            int lvl = Math.min(e.level, 3);
            sb.append("<div class=\"toc-row lvl").append(lvl).append("\">")
              .append("<span class=\"toc-num\">").append(e.number).append("</span>\u00A0\u00A0")
              .append("<a class=\"toc-label\" href=\"#").append(e.id).append("\">")
              .append(escape(e.title)).append("</a></div>");
        }
        sb.append("</nav>");

        // Topics in map order. Page-break policy (avoids orphaned chapter heads and blank pages):
        //  - the first block flows straight after the table of contents;
        //  - every later chapter head starts a fresh page;
        //  - a topic that follows another topic starts a fresh page, but the first topic of a
        //    chapter stays with its heading.
        Entry prev = null;
        boolean firstBlock = true;
        for (Entry e : entries) {
            boolean isHead = e.node == null;
            boolean brk = !firstBlock && (isHead || (prev != null && prev.node != null));
            String pb = brk ? " pb" : "";
            if (isHead) {
                sb.append("<h1 class=\"map-head").append(pb).append("\" id=\"").append(e.id).append("\">")
                  .append("<span class=\"map-num\">").append(e.number).append("</span>\u00A0\u00A0")
                  .append(escape(e.title)).append("</h1>");
            } else {
                boolean firstInChapter = prev != null && prev.node == null;
                String cls = firstInChapter ? "map-topic map-topic-first" : "map-topic";
                sb.append("<section class=\"").append(cls).append(pb).append("\" id=\"").append(e.id).append("\">")
                  .append(DitaTransformer.topicFragment(e.node, null, keySpace)).append("</section>");
            }
            prev = e;
            firstBlock = false;
        }
        sb.append("</article></body></html>");
        // Cross-topic xref rewrite: a link to another topic file becomes an in-document #anchor.
        java.util.Map<String, String> fileToAnchor = new java.util.HashMap<>();
        for (Entry e : entries) if (e.file != null) fileToAnchor.put(e.file, e.id);
        return rewriteXrefs(sb.toString(), fileToAnchor);
    }

    private static final java.util.regex.Pattern HREF = java.util.regex.Pattern.compile("href=\"([^\"]*)\"");

    /** Rewrites {@code href="file.dita#frag"} to {@code #frag} and {@code href="file.dita"} to the topic anchor. */
    private static String rewriteXrefs(String html, java.util.Map<String, String> fileToAnchor) {
        if (fileToAnchor.isEmpty()) return html;
        java.util.regex.Matcher m = HREF.matcher(html);
        StringBuilder out = new StringBuilder(html.length());
        while (m.find()) {
            String href = m.group(1), repl = href;
            if (!href.isEmpty() && !href.startsWith("#") && !href.startsWith("http") && !href.startsWith("mailto:")) {
                int hash = href.indexOf('#');
                String file = base(hash >= 0 ? href.substring(0, hash) : href);
                String frag = hash >= 0 ? href.substring(hash + 1) : "";
                if (fileToAnchor.containsKey(file)) repl = "#" + (frag.isEmpty() ? fileToAnchor.get(file) : frag);
            }
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement("href=\"" + repl + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String base(String path) {
        return path == null ? "" : path.replaceFirst("^.*[\\\\/]", "");
    }

    /** Depth-first walk of topicref/topichead/topicgroup, collecting a flat, levelled entry list. */
    private static void walk(XmlNode node, int level, TopicResolver res, List<Entry> out,
                             java.util.Map<String, String[]> keys) {
        for (XmlNode c : node.children()) {
            if (!c.isElement()) continue;
            String ln = c.localName();
            switch (ln) {
                case "topicref": case "mapref": case "chapter": case "topichead": case "keydef": {
                    String href = c.attr("href");
                    String nav = navtitle(c);
                    String keysAttr = c.attr("keys");
                    if (!keysAttr.isBlank()) {                       // collect the map key space
                        String txt = keyText(c);
                        for (String k : keysAttr.trim().split("\\s+")) keys.putIfAbsent(k, new String[]{href, txt});
                    }
                    Entry e = null;
                    boolean linksTopic = !href.isBlank() && !ln.equals("topichead") && !ln.equals("keydef");
                    if (linksTopic) {
                        String xml = res == null ? null : res.read(href);
                        if (xml != null) {
                            XmlNode topic = XmlParser.parse(xml);
                            String title = !nav.isBlank() ? nav : DitaTransformer.titleOf(topic);
                            e = new Entry(level, title, topic);      // defer transform until keys are known
                        }
                    }
                    if (e == null && !nav.isBlank() && !ln.equals("keydef")) e = new Entry(level, nav, null);
                    if (e != null) {
                        e.id = "t" + (out.size() + 1);
                        if (linksTopic) e.file = base(href);
                        out.add(e);
                    }
                    walk(c, (e != null) ? level + 1 : level, res, out, keys);
                    break;
                }
                case "topicgroup": case "reltable":     // structural containers: transparent
                    walk(c, level, res, out, keys);
                    break;
                default:
                    walk(c, level, res, out, keys);
            }
        }
    }

    /** Text a {@code keyref} substitutes: {@code topicmeta/keywords/keyword}, else navtitle/linktext. */
    private static String keyText(XmlNode e) {
        XmlNode meta = e.child("topicmeta");
        if (meta != null) {
            XmlNode kws = meta.child("keywords");
            if (kws != null) { XmlNode kw = kws.child("keyword"); if (kw != null) return kw.textContent().trim(); }
            XmlNode lt = meta.child("linktext");
            if (lt != null) return lt.textContent().trim();
        }
        return navtitle(e);
    }

    /** Hierarchical numbering: 1, 1.1, 1.2, 2, … based on each entry's level. */
    private static void number(List<Entry> entries) {
        int[] cnt = new int[16];
        for (Entry e : entries) {
            int L = Math.min(e.level, 15);
            cnt[L]++;
            for (int d = L + 1; d < cnt.length; d++) cnt[d] = 0;
            StringBuilder num = new StringBuilder();
            for (int d = 0; d <= L; d++) { if (d > 0) num.append('.'); num.append(cnt[d]); }
            e.number = num.toString();
        }
    }

    /** DITA navtitle: the {@code @navtitle} attribute or a {@code <topicmeta><navtitle>}. */
    private static String navtitle(XmlNode e) {
        if (e.hasAttr("navtitle") && !e.attr("navtitle").isBlank()) return e.attr("navtitle").trim();
        XmlNode meta = e.child("topicmeta");
        if (meta != null) {
            XmlNode nt = meta.child("navtitle");
            if (nt != null) return nt.textContent().trim();
        }
        return "";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class Entry {
        final int level;
        final String title;
        final XmlNode node;    // null for a topichead (heading only); the topic root otherwise
        String number = "";
        String id;
        String file;    // source topic file basename (for cross-topic xref rewriting)
        Entry(int level, String title, XmlNode node) { this.level = level; this.title = title; this.node = node; }
    }

    private static final String MAP_CSS = """
        .ditamap { }
        .map-title { font-size: 26pt; font-weight: bold; margin: 0 0 6pt; color: #0f172a; }
        .toc { margin: 4pt 0 20pt; padding: 12pt 16pt; background: #f8fafc; border: 0.6pt solid #e5e7eb; border-radius: 10px; }
        .toc-title { font-size: 12pt; font-weight: bold; color: #0f172a; margin: 0 0 8pt; }
        .toc-row { font-size: 10.5pt; color: #334155; margin: 0 0 4pt; }
        .toc-row.lvl1 { padding-left: 16pt; color: #475569; font-size: 10pt; }
        .toc-row.lvl2 { padding-left: 32pt; color: #64748b; font-size: 9.5pt; }
        .toc-row.lvl3 { padding-left: 48pt; color: #64748b; font-size: 9.5pt; }
        .toc-num { color: #4f46e5; font-weight: bold; margin-right: 6pt; }
        a.toc-label { color: #334155; text-decoration: none; }
        .map-topic { border-top: 0.6pt solid #e5e7eb; margin-top: 14pt; padding-top: 8pt; }
        .map-topic-first { border-top: 0; margin-top: 10pt; padding-top: 0; }
        .pb { page-break-before: always; }
        .map-topic .topic { }
        .map-topic h1.title { font-size: 18pt; }
        .map-head { font-size: 16pt; color: #1e293b; margin: 18pt 0 6pt; }
        .map-num { color: #4f46e5; margin-right: 6pt; }
        """;
}
