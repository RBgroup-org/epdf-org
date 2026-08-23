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

import com.epdfengine.rb.org.css.Display;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.TextBox;

/**
 * An inline run of text with its own style — the Mode B building block for mixed
 * formatting inside a {@link Paragraph} (e.g. a bold word amid normal text). On its
 * own it maps to an inline {@link TextBox}.
 */
public final class Text extends Styleable<Text> {

    private final String value;

    private Text(String value) { this.value = (value != null) ? value : ""; }

    /** An inline run carrying {@code value}. */
    public static Text of(String value) { return new Text(value); }

    /** A convenience for a bold run. */
    public static Text bold(String value) { return of(value).bold(); }

    /** A convenience for a coloured run. */
    public static Text colored(String value, int rgb) { return of(value).color(rgb); }

    /** A run that is also an internal/external link ({@code href}). */
    public Text link(String href) { style.setLinkHref(href); return this; }

    String value() { return value; }

    @Override public Box toBox() {
        style.display(Display.INLINE);
        return new TextBox(value, style);
    }
}
