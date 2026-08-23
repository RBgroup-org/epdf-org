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
package com.epdfengine.rb.org.layout;

import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.layout.box.FormFieldBox;

/**
 * A positioned interactive form control (top-down coordinates). The render stage
 * converts it to an AcroForm field, styling border/background/text from the
 * carried {@link ComputedStyle}.
 */
public final class PositionedFormField implements Drawable {

    private final int page;
    private final double x, yTop, w, h;
    private final FormFieldBox.Kind kind;
    private final String name, value;
    private final String[] options;
    private final boolean checked;
    private final ComputedStyle style;

    // modern HTML5 attributes
    private String placeholder = null;
    private int maxLength = -1;
    private boolean readOnly = false;
    private boolean required = false;
    private boolean multiSelect = false;
    // validation / advanced
    private Double min = null, max = null, step = null;
    private String pattern = null;
    private boolean comb = false;
    private boolean editable = false;

    public PositionedFormField(int page, double x, double yTop, double w, double h,
                               FormFieldBox.Kind kind, String name, String value,
                               String[] options, boolean checked, ComputedStyle style) {
        this.page = page; this.x = x; this.yTop = yTop; this.w = w; this.h = h;
        this.kind = kind; this.name = name; this.value = value;
        this.options = options; this.checked = checked; this.style = style;
    }

    /** Carries the modern HTML5 attributes forward to the render stage. */
    public PositionedFormField modern(String placeholder, int maxLength, boolean readOnly, boolean required, boolean multiSelect) {
        this.placeholder = placeholder; this.maxLength = maxLength;
        this.readOnly = readOnly; this.required = required; this.multiSelect = multiSelect;
        return this;
    }

    /** Carries validation/advanced behaviour forward (min/max/step, pattern, comb, editable). */
    public PositionedFormField advanced(Double min, Double max, Double step, String pattern, boolean comb, boolean editable) {
        this.min = min; this.max = max; this.step = step;
        this.pattern = pattern; this.comb = comb; this.editable = editable;
        return this;
    }

    @Override public int pageIndex() { return page; }
    public double x()      { return x; }
    public double yTop()   { return yTop; }
    public double width()  { return w; }
    public double height() { return h; }
    public FormFieldBox.Kind kind() { return kind; }
    public String fieldName() { return name; }
    public String value()  { return value; }
    public String[] options() { return options; }
    public boolean checked() { return checked; }
    public ComputedStyle style() { return style; }
    public String placeholder() { return placeholder; }
    public int maxLength()      { return maxLength; }
    public boolean readOnly()   { return readOnly; }
    public boolean required()   { return required; }
    public boolean multiSelect() { return multiSelect; }
    public Double min()         { return min; }
    public Double max()         { return max; }
    public Double step()        { return step; }
    public String pattern()     { return pattern; }
    public boolean comb()       { return comb; }
    public boolean editable()   { return editable; }
}
