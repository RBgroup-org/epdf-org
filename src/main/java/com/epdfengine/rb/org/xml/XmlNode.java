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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in a parsed XML tree — either an ELEMENT (name, attributes, children) or a
 * TEXT node. Deliberately parallel to the HTML {@code HtmlNode} so XML content can be
 * mapped onto the same layout pipeline. Namespace prefixes are preserved on the raw
 * {@link #name()}; use {@link #localName()} / {@link #prefix()} to split them.
 */
public final class XmlNode {

    public enum Type { ELEMENT, TEXT }

    private final Type type;
    private final String name;      // ELEMENT: raw qualified name (may carry a prefix)
    private final String text;      // TEXT: the character data
    private final Map<String, String> attrs = new LinkedHashMap<>();
    private final List<XmlNode> children = new ArrayList<>();
    private XmlNode parent;

    private XmlNode(Type type, String name, String text) {
        this.type = type; this.name = name; this.text = text;
    }

    public static XmlNode element(String name) { return new XmlNode(Type.ELEMENT, name, null); }
    public static XmlNode text(String text)    { return new XmlNode(Type.TEXT, null, text); }

    public Type type()          { return type; }
    public boolean isElement()  { return type == Type.ELEMENT; }
    public boolean isText()     { return type == Type.TEXT; }
    public String name()        { return name; }
    public String text()        { return text; }
    public Map<String, String> attributes() { return attrs; }
    public List<XmlNode> children()          { return children; }
    public XmlNode parent()     { return parent; }

    /** The element name without any namespace prefix (e.g. {@code dita:concept} -> {@code concept}). */
    public String localName() {
        if (name == null) return null;
        int i = name.indexOf(':');
        return i >= 0 ? name.substring(i + 1) : name;
    }

    /** The namespace prefix, or {@code ""} if none. */
    public String prefix() {
        if (name == null) return "";
        int i = name.indexOf(':');
        return i >= 0 ? name.substring(0, i) : "";
    }

    public void addChild(XmlNode c) { if (c != null) { c.parent = this; children.add(c); } }
    public void addChild(int index, XmlNode c) { if (c != null) { c.parent = this; children.add(index, c); } }
    public boolean removeChild(XmlNode c) { boolean r = children.remove(c); if (r) c.parent = null; return r; }
    public int indexOf(XmlNode c) { return children.indexOf(c); }
    public void clearChildren() { for (XmlNode c : children) c.parent = null; children.clear(); }

    /** A deep copy of this node (and its subtree), detached from any parent. */
    public XmlNode deepCopy() {
        XmlNode copy = isElement() ? new XmlNode(Type.ELEMENT, name, null) : new XmlNode(Type.TEXT, null, text);
        copy.attrs.putAll(attrs);
        for (XmlNode c : children) copy.addChild(c.deepCopy());
        return copy;
    }

    public void setAttr(String k, String v) { attrs.put(k, v); }
    public boolean hasAttr(String k) { return attrs.containsKey(k); }
    public String attr(String k) { return attrs.getOrDefault(k, ""); }

    /** The concatenated text of this node and all descendants. */
    public String textContent() {
        StringBuilder b = new StringBuilder();
        collect(this, b);
        return b.toString();
    }

    private static void collect(XmlNode n, StringBuilder b) {
        if (n.isText()) b.append(n.text);
        else for (XmlNode c : n.children) collect(c, b);
    }

    /** First direct child element with the given local name, or {@code null}. */
    public XmlNode child(String localName) {
        for (XmlNode c : children) if (c.isElement() && c.localName().equals(localName)) return c;
        return null;
    }
}
