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
package com.epdfengine.rb.org.layout.box;

import com.epdfengine.rb.org.css.ComputedStyle;

/**
 * A replaced (leaf) box for an interactive form control — {@code <input>},
 * {@code <textarea>}, {@code <select>} or {@code <button>}. The render stage turns
 * it into a real AcroForm field positioned and styled from CSS.
 */
public final class FormFieldBox extends Box {

    public enum Kind { TEXT, PASSWORD, TEXTAREA, CHECKBOX, RADIO, CHOICE, BUTTON }

    private final Kind kind;
    private final String name;
    private final String value;
    private final String[] options;   // <select> options
    private final boolean checked;

    // modern HTML5 attributes
    private String placeholder = null;
    private int maxLength = -1;
    private boolean readOnly = false;
    private boolean required = false;
    private boolean multiSelect = false;   // <select multiple>
    // validation / advanced
    private Double min = null, max = null, step = null;
    private String pattern = null;
    private boolean comb = false;
    private boolean editable = false;      // <input list=...> -> editable combo

    public FormFieldBox(Kind kind, String name, String value, String[] options, boolean checked, ComputedStyle style) {
        super(style);
        this.kind = kind;
        this.name = name;
        this.value = value == null ? "" : value;
        this.options = options == null ? new String[0] : options;
        this.checked = checked;
    }

    /** Sets the modern HTML5 attributes (placeholder, maxlength, readonly, required, multiple). */
    public FormFieldBox modern(String placeholder, int maxLength, boolean readOnly, boolean required, boolean multiSelect) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.readOnly = readOnly;
        this.required = required;
        this.multiSelect = multiSelect;
        return this;
    }

    /** Sets validation/advanced behaviour (min/max/step, pattern, comb, editable combo). */
    public FormFieldBox advanced(Double min, Double max, Double step, String pattern, boolean comb, boolean editable) {
        this.min = min; this.max = max; this.step = step;
        this.pattern = pattern; this.comb = comb; this.editable = editable;
        return this;
    }

    public Kind kind()        { return kind; }
    public String fieldName() { return name; }
    public String value()     { return value; }
    public String[] options() { return options; }
    public boolean checked()  { return checked; }
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

    @Override public Box add(Box child) {
        throw new UnsupportedOperationException("FormFieldBox is a replaced leaf");
    }

    @Override public boolean isBlockLevel()  { return true; }
    @Override public boolean isInlineLevel() { return false; }
}
