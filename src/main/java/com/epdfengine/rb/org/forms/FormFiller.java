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

import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfBoolean;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfIndirectReference;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.object.PdfString;
import com.epdfengine.rb.org.kernel.read.PdfEditor;
import com.epdfengine.rb.org.kernel.read.PdfReader;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Fills and flattens AcroForm fields in an <b>existing</b> PDF via incremental
 * update ({@link PdfEditor}). Text/choice fields get a fresh {@code /V} and a
 * regenerated {@code /AP} appearance; button fields (checkbox/radio) get {@code /V}
 * and {@code /AS} switched to the matching on-state. Flattening bakes each widget's
 * appearance into the page content and removes the interactive form.
 */
public final class FormFiller {

    private static final PdfName FT = PdfName.of("FT");
    private static final PdfName T  = PdfName.of("T");
    private static final PdfName V  = PdfName.of("V");
    private static final PdfName AS = PdfName.of("AS");
    private static final PdfName AP = PdfName.of("AP");
    private static final PdfName N  = PdfName.of("N");
    private static final PdfName DA = PdfName.of("DA");
    private static final PdfName DR = PdfName.of("DR");
    private static final PdfName RECT = PdfName.of("Rect");
    private static final PdfName KIDS = PdfName.KIDS;
    private static final PdfName ACRO = PdfName.of("AcroForm");
    private static final PdfName FIELDS = PdfName.of("Fields");

    private FormFiller() {}

    /** Sets the given field values (by fully-qualified {@code /T} name) and regenerates appearances. */
    public static byte[] fill(byte[] pdf, Map<String, String> values) {
        PdfEditor ed = new PdfEditor(pdf);
        PdfReader r = ed.reader();
        PdfDictionary cat = r.catalog();
        if (cat == null) return pdf;
        PdfObject acroObj = r.resolve(cat.get(ACRO));
        if (!(acroObj instanceof PdfDictionary acro)) return pdf;

        PdfObject drFontHelv = drFont(r, acro);
        PdfObject fieldsObj = r.resolve(acro.get(FIELDS));
        if (fieldsObj instanceof PdfArray fields)
            for (int i = 0; i < fields.size(); i++)
                fillField(ed, r, fields.get(i), values, acro, drFontHelv);
        return ed.save();
    }

    private static void fillField(PdfEditor ed, PdfReader r, PdfObject ref, Map<String, String> values,
                                  PdfDictionary acro, PdfObject drFontHelv) {
        if (!(ref instanceof PdfIndirectReference fref)) return;
        int num = fref.objectNumber();
        PdfObject fo = r.get(num);
        if (!(fo instanceof PdfDictionary fd)) return;
        String name = str(fd.get(T));
        String ft = nm(r.resolve(fd.get(FT)));
        PdfObject kids = r.resolve(fd.get(KIDS));

        if (name != null && values.containsKey(name)) {
            String val = values.get(name);
            if ("Tx".equals(ft) || "Ch".equals(ft)) {
                fd.put(V, PdfString.ofText(val));
                double[] rect = rect(r, fd);
                if (rect != null) {
                    double size = daSize(fd, acro, rect[3] - rect[1]);
                    int apRef = ed.addObject(textAppearance(rect[2] - rect[0], rect[3] - rect[1], val, size, drFontHelv));
                    fd.put(AP, new PdfDictionary().put(N, PdfIndirectReference.of(apRef)));
                }
                ed.setObject(num, fd);
                return;
            }
            if ("Btn".equals(ft)) {
                if (kids instanceof PdfArray ka) {                 // radio group: match value to a kid's on-state
                    String selected = "Off";
                    for (int k = 0; k < ka.size(); k++) {
                        String kidOn = kidOnState(r, ka.get(k));
                        if (kidOn != null && kidOn.equalsIgnoreCase(val)) { selected = kidOn; break; }
                    }
                    fd.put(V, PdfName.of(selected));
                    for (int k = 0; k < ka.size(); k++) setKidState(ed, r, ka.get(k), selected);
                } else {                                            // checkbox
                    String on = onState(r, fd);
                    boolean checked = truthy(val, on);
                    fd.put(V, PdfName.of(checked ? on : "Off"));
                    fd.put(AS, PdfName.of(checked ? on : "Off"));
                }
                ed.setObject(num, fd);
                return;
            }
        }
        if (kids instanceof PdfArray ka)                            // recurse into non-terminal fields
            for (int k = 0; k < ka.size(); k++) fillField(ed, r, ka.get(k), values, acro, drFontHelv);
    }

    private static String kidOnState(PdfReader r, PdfObject kidRef) {
        PdfObject ko = r.resolve(kidRef);
        return ko instanceof PdfDictionary kd ? onState(r, kd) : null;
    }

    private static void setKidState(PdfEditor ed, PdfReader r, PdfObject kidRef, String selectedOn) {
        if (!(kidRef instanceof PdfIndirectReference kr)) return;
        PdfObject ko = r.get(kr.objectNumber());
        if (!(ko instanceof PdfDictionary kd)) return;
        String kidOn = onState(r, kd);
        kd.put(AS, PdfName.of(kidOn.equals(selectedOn) ? kidOn : "Off"));
        ed.setObject(kr.objectNumber(), kd);
    }

    // ---- flatten -----------------------------------------------------------

    /** Bakes every widget appearance into page content and removes the interactive form. */
    public static byte[] flatten(byte[] pdf) {
        PdfEditor ed = new PdfEditor(pdf);
        PdfReader r = ed.reader();
        PdfDictionary cat = r.catalog();
        if (cat == null) return pdf;

        for (PdfDictionary page : r.pages()) {
            PdfObject annotsObj = r.resolve(page.get(PdfName.of("Annots")));
            if (!(annotsObj instanceof PdfArray annots)) continue;
            StringBuilder content = new StringBuilder();
            PdfDictionary xobjects = new PdfDictionary();
            int seq = 0;
            PdfArray keep = new PdfArray();
            for (int i = 0; i < annots.size(); i++) {
                PdfObject aRef = annots.get(i);
                PdfObject ao = r.resolve(aRef);
                if (!(ao instanceof PdfDictionary aw) || !"Widget".equals(nm(r.resolve(aw.get(PdfName.SUBTYPE))))) {
                    keep.add(aRef);
                    continue;
                }
                PdfObject apN = apForState(r, aw);        // resolved appearance for the current /AS state
                PdfObject apRef = apRefForState(r, aw);   // ref (or stream) to reference from resources
                double[] rect = rect(r, aw);
                if (apN instanceof PdfStream form && rect != null && apRef != null) {
                    String xn = "Fm" + (seq++);
                    xobjects.put(PdfName.of(xn), apRef);
                    double[] bb = bbox(r, form);
                    double sx = bb[2] - bb[0] == 0 ? 1 : (rect[2] - rect[0]) / (bb[2] - bb[0]);
                    double sy = bb[3] - bb[1] == 0 ? 1 : (rect[3] - rect[1]) / (bb[3] - bb[1]);
                    content.append("q ").append(n(sx)).append(" 0 0 ").append(n(sy)).append(' ')
                           .append(n(rect[0] - bb[0] * sx)).append(' ').append(n(rect[1] - bb[1] * sy))
                           .append(" cm /").append(xn).append(" Do Q\n");
                }
            }
            if (seq == 0) continue;
            // merge XObjects into page resources
            PdfDictionary res = r.resolve(page.get(PdfName.RESOURCES)) instanceof PdfDictionary d ? d : new PdfDictionary();
            PdfDictionary xo = r.resolve(res.get(PdfName.XOBJECT)) instanceof PdfDictionary d ? d : new PdfDictionary();
            for (Map.Entry<PdfName, PdfObject> e : xobjects.entries()) xo.put(e.getKey(), e.getValue());
            res.put(PdfName.XOBJECT, xo);
            page.put(PdfName.RESOURCES, res);
            // append the drawing content stream
            int csNum = ed.addObject(new PdfStream(new PdfDictionary(),
                    ("\n" + content).getBytes(StandardCharsets.ISO_8859_1)));
            PdfArray contents = new PdfArray();
            PdfObject existing = page.get(PdfName.CONTENTS);
            if (existing instanceof PdfArray ea) for (int i = 0; i < ea.size(); i++) contents.add(ea.get(i));
            else if (existing != null) contents.add(existing);
            contents.add(PdfIndirectReference.of(csNum));
            page.put(PdfName.CONTENTS, contents);
            page.put(PdfName.of("Annots"), keep);
            ed.setObject(pageNum(r, page), page);
        }
        // drop the interactive form from the catalog
        if (cat.remove(ACRO) != null) ed.setObject(catNum(r), cat);
        return ed.save();
    }

    // ---- appearance building -----------------------------------------------

    private static PdfStream textAppearance(double w, double h, String value, double size, PdfObject font) {
        double ty = Math.max(2, (h - size) / 2 + size * 0.24);
        String c = "1 1 1 rg 0 0 " + n(w) + " " + n(h) + " re f\n"
                + "0.85 0.85 0.85 RG 1 w 0.5 0.5 " + n(w - 1) + " " + n(h - 1) + " re S\n"
                + "/Tx BMC\nq 1 1 " + n(w - 2) + " " + n(h - 2) + " re W n\n"
                + "BT /Helv " + n(size) + " Tf 0 g 3 " + n(ty) + " Td (" + esc(value) + ") Tj ET\nQ\nEMC\n";
        PdfDictionary fonts = new PdfDictionary();
        if (font != null) fonts.put(PdfName.of("Helv"), font);
        PdfDictionary res = new PdfDictionary().put(PdfName.FONT, fonts);
        PdfArray bbox = new PdfArray().addNumber(0L).addNumber(0L).addNumber(w).addNumber(h);
        PdfDictionary d = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("XObject"))
                .put(PdfName.SUBTYPE, PdfName.of("Form"))
                .put(PdfName.of("FormType"), PdfNumber.of(1))
                .put(PdfName.of("BBox"), bbox)
                .put(PdfName.RESOURCES, res);
        return new PdfStream(d, c.getBytes(StandardCharsets.ISO_8859_1));
    }

    // ---- helpers -----------------------------------------------------------

    private static PdfObject drFont(PdfReader r, PdfDictionary acro) {
        PdfObject dr = r.resolve(acro.get(DR));
        if (dr instanceof PdfDictionary drd) {
            PdfObject f = r.resolve(drd.get(PdfName.FONT));
            if (f instanceof PdfDictionary fd) {
                PdfObject helv = fd.get(PdfName.of("Helv"));
                if (helv != null) return helv;
                for (PdfObject v : fdValues(fd)) return v;   // any font
            }
        }
        return null;
    }

    private static Iterable<PdfObject> fdValues(PdfDictionary d) {
        java.util.List<PdfObject> l = new java.util.ArrayList<>();
        for (Map.Entry<PdfName, PdfObject> e : d.entries()) l.add(e.getValue());
        return l;
    }

    private static PdfObject apForState(PdfReader r, PdfDictionary widget) {
        PdfObject ap = r.resolve(widget.get(AP));
        if (!(ap instanceof PdfDictionary apd)) return null;
        PdfObject n = r.resolve(apd.get(N));
        if (n instanceof PdfStream) return n;
        if (n instanceof PdfDictionary states) {
            String as = nm(r.resolve(widget.get(AS)));
            return r.resolve(states.get(PdfName.of(as == null ? "Off" : as)));
        }
        return null;
    }

    private static PdfObject apRefForState(PdfReader r, PdfDictionary widget) {
        PdfObject ap = r.resolve(widget.get(AP));
        if (!(ap instanceof PdfDictionary apd)) return null;
        PdfObject n = apd.get(N);
        if (n instanceof PdfIndirectReference) return n;
        if (r.resolve(n) instanceof PdfDictionary states) {
            String as = nm(r.resolve(widget.get(AS)));
            return states.get(PdfName.of(as == null ? "Off" : as));
        }
        return n;
    }

    private static double[] bbox(PdfReader r, PdfStream form) {
        PdfObject b = r.resolve(form.dict().get(PdfName.of("BBox")));
        if (b instanceof PdfArray a && a.size() == 4)
            return new double[]{ num(r, a.get(0)), num(r, a.get(1)), num(r, a.get(2)), num(r, a.get(3)) };
        return new double[]{ 0, 0, 1, 1 };
    }

    private static double[] rect(PdfReader r, PdfDictionary d) {
        PdfObject o = r.resolve(d.get(RECT));
        if (!(o instanceof PdfArray a) || a.size() != 4) return null;
        double x0 = num(r, a.get(0)), y0 = num(r, a.get(1)), x1 = num(r, a.get(2)), y1 = num(r, a.get(3));
        return new double[]{ Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1) };
    }

    private static double daSize(PdfDictionary fd, PdfDictionary acro, double h) {
        String da = str(fd.get(DA));
        if (da == null) da = str(acro.get(DA));
        if (da != null) {
            String[] tok = da.trim().split("\\s+");
            for (int i = 0; i < tok.length - 1; i++)
                if ("Tf".equals(tok[i + 1]) && i >= 1) {
                    try { double s = Double.parseDouble(tok[i]); if (s > 0) return s; } catch (Exception ignore) {}
                }
        }
        return Math.min(11, Math.max(6, h - 4));
    }

    private static String onState(PdfReader r, PdfDictionary widget) {
        PdfObject ap = r.resolve(widget.get(AP));
        if (ap instanceof PdfDictionary apd && r.resolve(apd.get(N)) instanceof PdfDictionary states)
            for (PdfName k : states.keys()) if (!"Off".equals(k.value())) return k.value();
        return "Yes";
    }

    private static boolean truthy(String v, String on) {
        if (v == null) return false;
        return v.equalsIgnoreCase(on) || v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("on")
                || v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("checked");
    }

    private static int pageNum(PdfReader r, PdfDictionary page) { return refNumFor(r, page); }
    private static int catNum(PdfReader r) {
        PdfObject root = r.rootRef();
        return root instanceof PdfIndirectReference ir ? ir.objectNumber() : -1;
    }

    /** Finds the object number whose loaded object is {@code target} (identity). */
    private static int refNumFor(PdfReader r, PdfObject target) {
        for (int i = 1; i <= r.size(); i++) if (r.get(i) == target) return i;
        return -1;
    }

    private static double num(PdfReader r, PdfObject o) {
        o = r.resolve(o);
        return o instanceof PdfNumber n ? n.doubleValue() : 0;
    }

    private static String str(PdfObject o) {
        if (!(o instanceof PdfString s)) return null;
        byte[] b = s.bytes();
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF)
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    private static String nm(PdfObject o) { return o instanceof PdfName n ? n.value() : null; }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == ')' || c == '\\') b.append('\\');
            b.append(c > 0xFF ? '?' : c);
        }
        return b.toString();
    }

    private static String n(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return java.math.BigDecimal.valueOf(v).setScale(3, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
