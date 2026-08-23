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
package com.epdfengine.rb.org.forms;

import com.epdfengine.rb.org.kernel.PdfDocument;
import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfBoolean;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.object.PdfString;

import java.nio.charset.StandardCharsets;

/**
 * Builds an AcroForm (interactive form) on a {@link PdfDocument}, following
 * ISO 32000-1 §12.7. Supports text fields, checkboxes, radio groups, choice
 * fields (list/combo) and push buttons. Each terminal field is merged with its
 * widget annotation and carries a generated {@code /AP} appearance stream so the
 * field renders identically in every viewer (no {@code /NeedAppearances} needed).
 *
 * <p>Signing/signature fields are intentionally out of scope for epdf-org.</p>
 */
public final class AcroForm {

    // Field flags (/Ff), ISO 32000 tables 226-228 (1-based bit positions).
    private static final int FF_READ_ONLY   = 1;
    private static final int FF_REQUIRED     = 1 << 1;
    private static final int FF_MULTILINE    = 1 << 12;   // text
    private static final int FF_PASSWORD     = 1 << 13;   // text
    private static final int FF_NO_TOGGLE_OFF = 1 << 14;  // radio: exactly one on
    private static final int FF_RADIO        = 1 << 15;   // button
    private static final int FF_PUSHBUTTON   = 1 << 16;   // button
    private static final int FF_COMBO        = 1 << 17;   // choice
    private static final int FF_EDIT         = 1 << 18;   // choice (editable combo)
    private static final int FF_MULTI_SELECT = 1 << 21;   // choice (list box multi-select)
    private static final int FF_COMB         = 1 << 24;   // text (comb of equal cells; needs MaxLen)
    private static final int ANNOT_PRINT     = 1 << 2;    // /F Print
    private static final int PLACEHOLDER_RGB = 0x9CA3AF;  // grey placeholder text

    private final PdfDocument doc;
    private final PdfArray fields = new PdfArray();
    private int helvFont = -1;

    private AcroForm(PdfDocument doc) { this.doc = doc; }

    public static AcroForm on(PdfDocument doc) { return new AcroForm(doc); }

    // ---- Text field --------------------------------------------------------

    public AcroForm textField(int pageObj, String name, double x, double y, double w, double h,
                              String value, double fontSize) {
        return textField(pageObj, name, x, y, w, h, value, fontSize, false, false, 0xD9D9D9, 0xFFFFFF, 0x000000);
    }

    /** Text field with multiline/password flags and CSS-derived colours. */
    public AcroForm textField(int pageObj, String name, double x, double y, double w, double h,
                              String value, double fontSize, boolean multiline, boolean password,
                              int borderRgb, int bgRgb, int textRgb) {
        return textField(pageObj, name, x, y, w, h, value, fontSize, multiline, password,
                borderRgb, bgRgb, textRgb, 1.0, 0.0);
    }

    /** Text field with multiline/password flags, CSS colours, border width and corner radius. */
    public AcroForm textField(int pageObj, String name, double x, double y, double w, double h,
                              String value, double fontSize, boolean multiline, boolean password,
                              int borderRgb, int bgRgb, int textRgb, double borderWidthPt, double radiusPt) {
        return textField(pageObj, name, x, y, w, h, value, fontSize, multiline, password,
                borderRgb, bgRgb, textRgb, borderWidthPt, radiusPt, null, -1, false, false);
    }

    /** Full text field: colours, border, radius, placeholder, maxlength, read-only and required. */
    public AcroForm textField(int pageObj, String name, double x, double y, double w, double h,
                              String value, double fontSize, boolean multiline, boolean password,
                              int borderRgb, int bgRgb, int textRgb, double borderWidthPt, double radiusPt,
                              String placeholder, int maxLen, boolean readOnly, boolean required) {
        TextOpts o = new TextOpts();
        o.fontSize = fontSize; o.multiline = multiline; o.password = password;
        o.borderRgb = borderRgb; o.bgRgb = bgRgb; o.textRgb = textRgb;
        o.borderWidth = borderWidthPt; o.radius = radiusPt;
        o.placeholder = placeholder; o.maxLen = maxLen; o.readOnly = readOnly; o.required = required;
        return textField(pageObj, name, x, y, w, h, value, o);
    }

    /** Options for an advanced text field (comb cells, min/max/step and pattern validation). */
    public static final class TextOpts {
        public double fontSize = 11, borderWidth = 1, radius = 0;
        public boolean multiline, password, readOnly, required, comb;
        public int borderRgb = 0xD9D9D9, bgRgb = 0xFFFFFF, textRgb = 0x000000, maxLen = -1;
        public String placeholder, pattern;
        public Double min, max, step;
    }

    /** The complete text-field builder — all styling and modern HTML5 behaviours. */
    public AcroForm textField(int pageObj, String name, double x, double y, double w, double h,
                              String value, TextOpts o) {
        String v = value == null ? "" : value;
        boolean comb = o.comb && o.maxLen > 0 && !o.multiline && !o.password;
        boolean showPlaceholder = v.isEmpty() && o.placeholder != null && !o.placeholder.isEmpty() && !comb;
        String shown = showPlaceholder ? o.placeholder : v;
        int shownRgb = showPlaceholder ? PLACEHOLDER_RGB : o.textRgb;
        double ty = o.multiline ? Math.max(2, h - o.fontSize - 3) : Math.max(2, (h - o.fontSize) / 2 + o.fontSize * 0.24);
        double pad = Math.max(3, o.borderWidth + 2);
        StringBuilder content = new StringBuilder(fieldBox(w, h, rgb(o.borderRgb), rgb(o.bgRgb), o.borderWidth, o.radius));
        if (comb) {
            content.append(combContent(w, h, o, v, ty));
        } else {
            content.append("/Tx BMC\nq ").append(n(pad)).append(" ").append(n(1)).append(" ")
                   .append(n(w - 2 * pad)).append(" ").append(n(h - 2)).append(" re W n\n")
                   .append("BT /Helv ").append(n(o.fontSize)).append(" Tf ").append(rgb(shownRgb)).append(" rg ")
                   .append(n(pad)).append(" ").append(n(ty)).append(" Td (").append(esc(shown)).append(") Tj ET\nQ\nEMC\n");
        }
        int ap = appearance(w, h, content.toString());

        int flags = (o.multiline ? FF_MULTILINE : 0) | (o.password ? FF_PASSWORD : 0)
                | (o.readOnly ? FF_READ_ONLY : 0) | (o.required ? FF_REQUIRED : 0) | (comb ? FF_COMB : 0);
        PdfDictionary f = widget(pageObj, x, y, w, h, ap)
                .put(PdfName.of("FT"), PdfName.of("Tx"))
                .put(PdfName.of("T"), PdfString.ofText(name))
                .put(PdfName.of("V"), PdfString.ofText(v))
                .put(PdfName.of("DA"), PdfString.ofAscii("/Helv " + n(o.fontSize) + " Tf " + rgb(o.textRgb) + " rg"));
        if (flags != 0) f.put(PdfName.of("Ff"), PdfNumber.of(flags));
        if (o.maxLen > 0) f.put(PdfName.of("MaxLen"), PdfNumber.of(o.maxLen));
        if (o.placeholder != null && !o.placeholder.isBlank()) f.put(PdfName.of("TU"), PdfString.ofText(o.placeholder));
        String js = buildValidateJs(o.min, o.max, o.step, o.pattern);
        if (js != null) {
            PdfDictionary vAct = new PdfDictionary()
                    .put(PdfName.of("S"), PdfName.of("JavaScript"))
                    .put(PdfName.of("JS"), PdfString.ofText(js));
            f.put(PdfName.of("AA"), new PdfDictionary().put(PdfName.of("V"), vAct));
        }
        addTerminal(pageObj, f);
        return this;
    }

    /** Draws a comb text field: {@code MaxLen} equal cells with separators and per-cell characters. */
    private String combContent(double w, double h, TextOpts o, String v, double ty) {
        int cells = o.maxLen;
        double cellW = w / cells;
        StringBuilder sb = new StringBuilder();
        sb.append(rgb(o.borderRgb)).append(" RG 0.7 w\n");
        for (int i = 1; i < cells; i++) {
            double cx = i * cellW;
            sb.append(n(cx)).append(" 2 m ").append(n(cx)).append(" ").append(n(h - 2)).append(" l S\n");
        }
        sb.append("BT /Helv ").append(n(o.fontSize)).append(" Tf ").append(rgb(o.textRgb)).append(" rg\n");
        double charW = o.fontSize * 0.5;
        for (int i = 0; i < Math.min(v.length(), cells); i++) {
            double cx = i * cellW + (cellW - charW) / 2;
            sb.append("1 0 0 1 ").append(n(cx)).append(" ").append(n(ty)).append(" Tm (")
              .append(esc(String.valueOf(v.charAt(i)))).append(") Tj\n");
        }
        sb.append("ET\n");
        return sb.toString();
    }

    /** Builds an Acrobat validation script for min/max (AFRange), step and regex pattern; null if none. */
    private static String buildValidateJs(Double min, Double max, Double step, String pattern) {
        StringBuilder js = new StringBuilder();
        if (min != null || max != null) {
            boolean bLo = min != null, bHi = max != null;
            js.append("AFRange_Validate(").append(bLo).append(",").append(bLo ? jnum(min) : "0")
              .append(",").append(bHi).append(",").append(bHi ? jnum(max) : "0").append(");\n");
        }
        if (step != null && step > 0) {
            double base = min != null ? min : 0;
            js.append("if(event.value){var _n=parseFloat(event.value);var _k=(_n-").append(jnum(base)).append(")/")
              .append(jnum(step)).append(";if(!isNaN(_n)&&Math.abs(_k-Math.round(_k))>1e-9){app.alert('Enter a value in steps of ")
              .append(jnum(step)).append("');event.rc=false;}}\n");
        }
        if (pattern != null && !pattern.isBlank()) {
            String re = pattern.replace("\\", "\\\\").replace("/", "\\/");
            js.append("if(event.value&&!(/^(").append(re).append(")$/.test(event.value))){app.alert('Invalid format');event.rc=false;}\n");
        }
        return js.length() == 0 ? null : js.toString();
    }

    private static String jnum(double d) {
        return d == Math.rint(d) && !Double.isInfinite(d) ? Long.toString((long) d) : Double.toString(d);
    }

    // ---- Checkbox ----------------------------------------------------------

    public AcroForm checkBox(int pageObj, String name, double x, double y, double size, boolean checked) {
        return checkBox(pageObj, name, x, y, size, checked, -1, 0x666666);
    }

    /** Checkbox with an optional CSS {@code accent-color} ({@code accentRgb < 0} = classic style). */
    public AcroForm checkBox(int pageObj, String name, double x, double y, double size, boolean checked,
                             int accentRgb, int borderRgb) {
        double s = size;
        double r = Math.min(3, s * 0.2);
        String off = fieldBox(s, s, rgb(borderRgb), "1 1 1", 1, r);
        String tick = n(s * 0.22) + " " + n(s * 0.52) + " m " + n(s * 0.42) + " " + n(s * 0.30)
                + " l " + n(s * 0.78) + " " + n(s * 0.72) + " l S\n";
        String on = accentRgb >= 0
                ? fieldBox(s, s, rgb(accentRgb), rgb(accentRgb), 1, r) + "1 1 1 RG 1.7 w " + tick   // accent fill + white tick
                : off + "0 0 0 RG 1.7 w " + tick;                                                    // classic dark tick
        int onAp = appearance(s, s, on), offAp = appearance(s, s, off);

        PdfDictionary apN = new PdfDictionary()
                .put(PdfName.of("Yes"), doc.ref(onAp))
                .put(PdfName.of("Off"), doc.ref(offAp));
        PdfDictionary f = widgetAp(pageObj, x, y, s, s, apN)
                .put(PdfName.of("FT"), PdfName.of("Btn"))
                .put(PdfName.of("T"), PdfString.ofText(name))
                .put(PdfName.of("V"), PdfName.of(checked ? "Yes" : "Off"))
                .put(PdfName.of("AS"), PdfName.of(checked ? "Yes" : "Off"));
        addTerminal(pageObj, f);
        return this;
    }

    // ---- Radio group -------------------------------------------------------

    /** Adds a radio group; each option gets a widget at the matching rect. rects[i] = {x,y,size}. */
    public AcroForm radioGroup(int pageObj, String name, String[] options, double[][] rects, int selectedIndex) {
        return radioGroup(pageObj, name, options, rects, selectedIndex, -1);
    }

    /** Radio group with an optional CSS {@code accent-color} ({@code accentRgb < 0} = classic dark). */
    public AcroForm radioGroup(int pageObj, String name, String[] options, double[][] rects, int selectedIndex,
                               int accentRgb) {
        if (options.length != rects.length) throw new IllegalArgumentException("options/rects length mismatch");
        String ring = accentRgb >= 0 ? rgb(accentRgb) : "0.4 0.4 0.4";
        String dot  = accentRgb >= 0 ? rgb(accentRgb) : "0 0 0";
        PdfDictionary parent = new PdfDictionary()
                .put(PdfName.of("FT"), PdfName.of("Btn"))
                .put(PdfName.of("T"), PdfString.ofText(name))
                .put(PdfName.of("Ff"), PdfNumber.of(FF_RADIO | FF_NO_TOGGLE_OFF))
                .put(PdfName.of("V"), PdfName.of(selectedIndex >= 0 ? safeName(options[selectedIndex]) : "Off"));
        int parentNum = doc.addObject(parent);
        PdfArray kids = new PdfArray();
        for (int i = 0; i < options.length; i++) {
            double x = rects[i][0], y = rects[i][1], s = rects[i][2];
            String opt = safeName(options[i]);
            boolean sel = i == selectedIndex;
            String off = circle(s / 2, s / 2, s / 2 - 0.6, false, ring);
            String on = off + circle(s / 2, s / 2, s * 0.24, true, dot);
            PdfDictionary apN = new PdfDictionary()
                    .put(PdfName.of(opt), doc.ref(appearance(s, s, on)))
                    .put(PdfName.of("Off"), doc.ref(appearance(s, s, off)));
            PdfDictionary kid = widgetAp(pageObj, x, y, s, s, apN)
                    .put(PdfName.of("Parent"), doc.ref(parentNum))
                    .put(PdfName.of("AS"), PdfName.of(sel ? opt : "Off"));
            int kidNum = doc.addAnnotation(pageObj, kid);
            kids.add(doc.ref(kidNum));
        }
        parent.put(PdfName.of("Kids"), kids);
        fields.add(doc.ref(parentNum));
        return this;
    }

    // ---- Choice (list / combo) --------------------------------------------

    public AcroForm choice(int pageObj, String name, double x, double y, double w, double h,
                           String[] options, String value, boolean combo) {
        return choice(pageObj, name, x, y, w, h, options, value, combo, 0xD9D9D9, 0xFFFFFF, 0x000000);
    }

    public AcroForm choice(int pageObj, String name, double x, double y, double w, double h,
                           String[] options, String value, boolean combo,
                           int borderRgb, int bgRgb, int textRgb) {
        return choice(pageObj, name, x, y, w, h, options, value, combo, borderRgb, bgRgb, textRgb, 1.0, 0.0);
    }

    public AcroForm choice(int pageObj, String name, double x, double y, double w, double h,
                           String[] options, String value, boolean combo,
                           int borderRgb, int bgRgb, int textRgb, double borderWidthPt, double radiusPt) {
        return choice(pageObj, name, x, y, w, h, options, value, combo,
                borderRgb, bgRgb, textRgb, borderWidthPt, radiusPt, null, false, false, false, false);
    }

    /** Full choice field: colours, border, radius, placeholder, read-only, required, multi-select, editable. */
    public AcroForm choice(int pageObj, String name, double x, double y, double w, double h,
                           String[] options, String value, boolean combo,
                           int borderRgb, int bgRgb, int textRgb, double borderWidthPt, double radiusPt,
                           String placeholder, boolean readOnly, boolean required, boolean multiSelect, boolean editable) {
        PdfArray opt = new PdfArray();
        for (String o : options) opt.add(PdfString.ofText(o));
        double fontSize = Math.min(11, h - 4);
        double ty = Math.max(2, (h - fontSize) / 2 + fontSize * 0.24);
        double pad = Math.max(3, borderWidthPt + 2);
        String v = value == null ? "" : value;
        boolean showPlaceholder = v.isEmpty() && placeholder != null && !placeholder.isEmpty();
        String shown = showPlaceholder ? placeholder : v;
        int shownRgb = showPlaceholder ? PLACEHOLDER_RGB : textRgb;
        String content = fieldBox(w, h, rgb(borderRgb), rgb(bgRgb), borderWidthPt, radiusPt)
                + (combo ? dropArrow(w, h) : "")
                + "/Tx BMC\nq " + n(pad) + " 1 " + n(w - 2 * pad) + " " + n(h - 2) + " re W n\n"
                + "BT /Helv " + n(fontSize) + " Tf " + rgb(shownRgb) + " rg " + n(pad) + " " + n(ty) + " Td (" + esc(shown) + ") Tj ET\nQ\nEMC\n";
        int ap = appearance(w, h, content);

        int flags = (combo ? FF_COMBO : 0) | (readOnly ? FF_READ_ONLY : 0)
                | (required ? FF_REQUIRED : 0) | (multiSelect ? FF_MULTI_SELECT : 0)
                | (editable && combo ? FF_EDIT : 0);
        PdfDictionary f = widget(pageObj, x, y, w, h, ap)
                .put(PdfName.of("FT"), PdfName.of("Ch"))
                .put(PdfName.of("T"), PdfString.ofText(name))
                .put(PdfName.of("V"), PdfString.ofText(v))
                .put(PdfName.of("Opt"), opt)
                .put(PdfName.of("Ff"), PdfNumber.of(flags))
                .put(PdfName.of("DA"), PdfString.ofAscii("/Helv " + n(fontSize) + " Tf " + rgb(textRgb) + " rg"));
        if (placeholder != null && !placeholder.isBlank()) f.put(PdfName.of("TU"), PdfString.ofText(placeholder));
        addTerminal(pageObj, f);
        return this;
    }

    // ---- Push button -------------------------------------------------------

    public AcroForm pushButton(int pageObj, String name, double x, double y, double w, double h,
                               String caption) {
        return pushButton(pageObj, name, x, y, w, h, caption, 0xE6E6E6, 0x595959, 0x000000);
    }

    public AcroForm pushButton(int pageObj, String name, double x, double y, double w, double h,
                               String caption, int bgRgb, int borderRgb, int textRgb) {
        return pushButton(pageObj, name, x, y, w, h, caption, bgRgb, borderRgb, textRgb, 1.0, 0.0);
    }

    public AcroForm pushButton(int pageObj, String name, double x, double y, double w, double h,
                               String caption, int bgRgb, int borderRgb, int textRgb,
                               double borderWidthPt, double radiusPt) {
        double fontSize = Math.min(12, h - 6);
        double tw = caption.length() * fontSize * 0.5;
        double tx = Math.max(3, (w - tw) / 2), ty = Math.max(3, (h - fontSize) / 2 + fontSize * 0.24);
        String content = fieldBox(w, h, rgb(borderRgb), rgb(bgRgb), borderWidthPt, radiusPt)
                + "BT /Helv " + n(fontSize) + " Tf " + rgb(textRgb) + " rg " + n(tx) + " " + n(ty) + " Td (" + esc(caption) + ") Tj ET\n";
        int ap = appearance(w, h, content);

        PdfDictionary mk = new PdfDictionary().put(PdfName.of("CA"), PdfString.ofText(caption));
        PdfDictionary f = widget(pageObj, x, y, w, h, ap)
                .put(PdfName.of("FT"), PdfName.of("Btn"))
                .put(PdfName.of("T"), PdfString.ofText(name))
                .put(PdfName.of("Ff"), PdfNumber.of(FF_PUSHBUTTON))
                .put(PdfName.of("MK"), mk);
        addTerminal(pageObj, f);
        return this;
    }

    // ---- Finalize ----------------------------------------------------------

    /** Writes the {@code /AcroForm} dictionary into the document catalog. */
    public void write() {
        PdfDictionary dr = new PdfDictionary().put(PdfName.of("Font"),
                new PdfDictionary().put(PdfName.of("Helv"), doc.ref(helv())));
        PdfDictionary acro = new PdfDictionary()
                .put(PdfName.of("Fields"), fields)
                .put(PdfName.of("DR"), dr)
                .put(PdfName.of("DA"), PdfString.ofAscii("/Helv 0 Tf 0 g"))
                .put(PdfName.of("NeedAppearances"), PdfBoolean.FALSE);
        doc.setAcroForm(doc.addObject(acro));
    }

    // ---- helpers -----------------------------------------------------------

    private void addTerminal(int pageObj, PdfDictionary field) {
        int num = doc.addAnnotation(pageObj, field);   // widget goes on the page /Annots
        fields.add(doc.ref(num));                      // and the (merged) field on /Fields
    }

    /** Widget with a single-stream {@code /AP /N}. */
    private PdfDictionary widget(int pageObj, double x, double y, double w, double h, int apStream) {
        return widgetAp(pageObj, x, y, w, h, doc.ref(apStream));
    }

    /** Widget whose {@code /AP /N} is the given object (a stream ref or a state dictionary). */
    private PdfDictionary widgetAp(int pageObj, double x, double y, double w, double h, Object apN) {
        PdfArray rect = new PdfArray().addNumber(x).addNumber(y).addNumber(x + w).addNumber(y + h);
        PdfDictionary ap = new PdfDictionary();
        if (apN instanceof PdfDictionary d) ap.put(PdfName.of("N"), d);
        else ap.put(PdfName.of("N"), (com.epdfengine.rb.org.kernel.object.PdfObject) apN);
        return new PdfDictionary()
                .put(PdfName.SUBTYPE, PdfName.of("Widget"))
                .put(PdfName.of("Rect"), rect)
                .put(PdfName.of("P"), doc.ref(pageObj))
                .put(PdfName.of("F"), PdfNumber.of(ANNOT_PRINT))
                .put(PdfName.of("AP"), ap);
    }

    private int appearance(double w, double h, String content) {
        PdfDictionary fonts = new PdfDictionary().put(PdfName.of("Helv"), doc.ref(helv()));
        PdfDictionary res = new PdfDictionary().put(PdfName.of("Font"), fonts);
        PdfArray bbox = new PdfArray().addNumber(0L).addNumber(0L).addNumber(w).addNumber(h);
        PdfDictionary d = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("XObject"))
                .put(PdfName.SUBTYPE, PdfName.of("Form"))
                .put(PdfName.of("FormType"), PdfNumber.of(1))
                .put(PdfName.of("BBox"), bbox)
                .put(PdfName.of("Resources"), res);
        return doc.addObject(new PdfStream(d, content.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private int helv() {
        if (helvFont < 0) {
            helvFont = doc.addObject(new PdfDictionary()
                    .put(PdfName.TYPE, PdfName.FONT)
                    .put(PdfName.SUBTYPE, PdfName.of("Type1"))
                    .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                    .put(PdfName.of("Encoding"), PdfName.of("WinAnsiEncoding")));
        }
        return helvFont;
    }

    private static String borderAndBg(double w, double h, String borderRgb, String bgRgb) {
        return bgRgb + " rg 0 0 " + n(w) + " " + n(h) + " re f\n"
                + borderRgb + " RG 1 w 0.5 0.5 " + n(w - 1) + " " + n(h - 1) + " re S\n";
    }

    /**
     * Fills and (optionally) strokes a field box. When {@code radius} &gt; 0 the box
     * is drawn as a rounded rectangle; a zero {@code borderWidth} paints no border
     * (a flat, filled control as in modern "material" form styling).
     */
    private static String fieldBox(double w, double h, String borderRgb, String bgRgb,
                                   double borderWidth, double radius) {
        double fillR = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        StringBuilder sb = new StringBuilder();
        sb.append(bgRgb).append(" rg\n");
        appendRoundRectPath(sb, 0, 0, w, h, fillR);
        sb.append("f\n");
        if (borderWidth > 0) {
            double inset = borderWidth / 2;
            double strokeR = Math.max(0, Math.min(radius - inset, Math.min(w, h) / 2 - inset));
            sb.append(borderRgb).append(" RG ").append(n(borderWidth)).append(" w\n");
            appendRoundRectPath(sb, inset, inset, w - borderWidth, h - borderWidth, strokeR);
            sb.append("S\n");
        }
        return sb.toString();
    }

    /** Appends a (rounded) rectangle path; a plain {@code re} when {@code r <= 0}. */
    private static void appendRoundRectPath(StringBuilder sb, double x, double y, double w, double h, double r) {
        if (r <= 0) { sb.append(n(x)).append(' ').append(n(y)).append(' ').append(n(w)).append(' ').append(n(h)).append(" re\n"); return; }
        double k = K, x2 = x + w, y2 = y + h;
        sb.append(n(x + r)).append(' ').append(n(y)).append(" m\n");
        sb.append(n(x2 - r)).append(' ').append(n(y)).append(" l\n");
        sb.append(n(x2 - r + r * k)).append(' ').append(n(y)).append(' ').append(n(x2)).append(' ').append(n(y + r - r * k)).append(' ').append(n(x2)).append(' ').append(n(y + r)).append(" c\n");
        sb.append(n(x2)).append(' ').append(n(y2 - r)).append(" l\n");
        sb.append(n(x2)).append(' ').append(n(y2 - r + r * k)).append(' ').append(n(x2 - r + r * k)).append(' ').append(n(y2)).append(' ').append(n(x2 - r)).append(' ').append(n(y2)).append(" c\n");
        sb.append(n(x + r)).append(' ').append(n(y2)).append(" l\n");
        sb.append(n(x + r - r * k)).append(' ').append(n(y2)).append(' ').append(n(x)).append(' ').append(n(y2 - r + r * k)).append(' ').append(n(x)).append(' ').append(n(y2 - r)).append(" c\n");
        sb.append(n(x)).append(' ').append(n(y + r)).append(" l\n");
        sb.append(n(x)).append(' ').append(n(y + r - r * k)).append(' ').append(n(x + r - r * k)).append(' ').append(n(y)).append(' ').append(n(x + r)).append(' ').append(n(y)).append(" c\n");
        sb.append("h\n");
    }

    private static String dropArrow(double w, double h) {
        double cx = w - 10, cy = h / 2;
        return "0.3 0.3 0.3 rg " + n(cx - 3) + " " + n(cy + 2) + " m " + n(cx + 3) + " " + n(cy + 2)
                + " l " + n(cx) + " " + n(cy - 3) + " l f\n";
    }

    private static final double K = 0.5522847498307936;

    private static String circle(double cx, double cy, double r, boolean fill, String rgb) {
        String op = fill ? (rgb + " rg") : (rgb + " RG 1 w");
        return op + "\n"
                + n(cx + r) + " " + n(cy) + " m\n"
                + n(cx + r) + " " + n(cy + r * K) + " " + n(cx + r * K) + " " + n(cy + r) + " " + n(cx) + " " + n(cy + r) + " c\n"
                + n(cx - r * K) + " " + n(cy + r) + " " + n(cx - r) + " " + n(cy + r * K) + " " + n(cx - r) + " " + n(cy) + " c\n"
                + n(cx - r) + " " + n(cy - r * K) + " " + n(cx - r * K) + " " + n(cy - r) + " " + n(cx) + " " + n(cy - r) + " c\n"
                + n(cx + r * K) + " " + n(cy - r) + " " + n(cx + r) + " " + n(cy - r * K) + " " + n(cx + r) + " " + n(cy) + " c\n"
                + (fill ? "f\n" : "S\n");
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == ')' || c == '\\') b.append('\\');
            b.append(c > 0xFF ? '?' : c);        // WinAnsi appearance text; exotic glyphs fall back
        }
        return b.toString();
    }

    /** A PDF name token for a radio option (no spaces / delimiters). */
    private static String safeName(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return b.length() == 0 ? "Opt" : b.toString();
    }

    private static String n(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return java.math.BigDecimal.valueOf(v).setScale(3, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /** An RGB int (0xRRGGBB) as a normalised "r g b" content-stream colour. */
    private static String rgb(int c) {
        return n(((c >> 16) & 0xFF) / 255.0) + " " + n(((c >> 8) & 0xFF) / 255.0) + " " + n((c & 0xFF) / 255.0);
    }
}
