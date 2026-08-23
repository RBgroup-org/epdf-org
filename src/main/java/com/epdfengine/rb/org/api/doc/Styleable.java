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
package com.epdfengine.rb.org.api.doc;

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.common.Length;
import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.css.TextAlign;

/**
 * Fluent style base shared by the Mode B content builders. Every setter mutates the
 * builder's {@link ComputedStyle} — the very object the CSS cascade also produces —
 * and returns the concrete builder type for chaining. Mirrors the CSS properties a
 * report author reaches for: box model, colours, borders, radius and text.
 *
 * @param <T> the concrete builder type (self-type, for fluent chaining)
 */
public abstract class Styleable<T extends Styleable<T>> implements Content {

    /** The style this builder emits; visible to subclasses that need finer control. */
    protected final ComputedStyle style = new ComputedStyle();

    @SuppressWarnings("unchecked")
    protected final T self() { return (T) this; }

    /** The underlying computed style (for advanced tweaks not exposed fluently). */
    public ComputedStyle style() { return style; }

    // --- sizing ---
    public T width(Length v)          { style.width(v); return self(); }
    public T width(double pt)         { style.width(Length.pt(pt)); return self(); }
    public T widthPercent(double pct) { style.width(Length.percent(pct)); return self(); }
    public T height(double pt)        { style.height(Length.pt(pt)); return self(); }
    public T maxWidth(double pt)      { style.maxWidth(Length.pt(pt)); return self(); }

    // --- spacing ---
    public T padding(double all)      { style.padding(Edges.of(all)); return self(); }
    public T padding(Edges e)         { style.padding(e); return self(); }
    public T margin(double all)       { style.margin(Edges.of(all)); return self(); }
    public T margin(Edges e)          { style.margin(e); return self(); }

    // --- paint ---
    public T background(int rgb)              { style.backgroundRgb(rgb); return self(); }
    public T border(double widthPt, int rgb)  { style.border(Edges.of(widthPt)); style.borderColorRgb(rgb); return self(); }
    public T borderRadius(double r)           { style.borderRadiusPt(r); return self(); }

    // --- text ---
    public T color(int rgb)           { style.colorRgb(rgb); return self(); }
    public T fontSize(double pt)      { style.fontSizePt(pt); return self(); }
    public T font(String family)      { style.fontFamily(family); return self(); }
    public T bold()                   { style.bold(true); return self(); }
    public T italic()                 { style.italic(true); return self(); }
    public T align(TextAlign a)       { style.textAlign(a); return self(); }
    public T lineHeight(double factor){ style.lineHeightFactor(factor); return self(); }

    // --- accessibility / links ---
    public T role(String structRole)  { style.setStructRole(structRole); return self(); }
    public T id(String anchorId)      { style.setAnchorId(anchorId); return self(); }
}
