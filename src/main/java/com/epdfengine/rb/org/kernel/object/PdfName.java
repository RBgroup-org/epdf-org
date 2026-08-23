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
package com.epdfengine.rb.org.kernel.object;

import java.util.Objects;

/**
 * A PDF name object (ISO 32000-1 §7.3.5), e.g. {@code /Type}. Stored without the
 * leading slash. Immutable; usable as a dictionary key.
 *
 * <p>Common names are exposed as {@code static final} constants (flyweights).
 * Arbitrary names are created via {@link #of(String)}. No global mutable intern
 * pool is used, honouring the engine's no-shared-mutable-state rule.</p>
 */
public final class PdfName implements PdfObject {

    // Frequently used names (immutable constants).
    public static final PdfName TYPE        = new PdfName("Type");
    public static final PdfName SUBTYPE     = new PdfName("Subtype");
    public static final PdfName CATALOG     = new PdfName("Catalog");
    public static final PdfName PAGES       = new PdfName("Pages");
    public static final PdfName PAGE        = new PdfName("Page");
    public static final PdfName KIDS        = new PdfName("Kids");
    public static final PdfName COUNT       = new PdfName("Count");
    public static final PdfName PARENT      = new PdfName("Parent");
    public static final PdfName MEDIA_BOX   = new PdfName("MediaBox");
    public static final PdfName RESOURCES   = new PdfName("Resources");
    public static final PdfName CONTENTS    = new PdfName("Contents");
    public static final PdfName LENGTH      = new PdfName("Length");
    public static final PdfName FILTER      = new PdfName("Filter");
    public static final PdfName FLATE_DECODE = new PdfName("FlateDecode");
    public static final PdfName ROOT        = new PdfName("Root");
    public static final PdfName SIZE        = new PdfName("Size");
    public static final PdfName INFO        = new PdfName("Info");
    public static final PdfName ID          = new PdfName("ID");
    public static final PdfName FONT        = new PdfName("Font");
    public static final PdfName XOBJECT     = new PdfName("XObject");
    public static final PdfName PROC_SET    = new PdfName("ProcSet");
    public static final PdfName XREF        = new PdfName("XRef");
    public static final PdfName OBJ_STM     = new PdfName("ObjStm");
    public static final PdfName W           = new PdfName("W");
    public static final PdfName INDEX       = new PdfName("Index");
    public static final PdfName PREV        = new PdfName("Prev");
    public static final PdfName N           = new PdfName("N");
    public static final PdfName FIRST       = new PdfName("First");

    private final String value;

    /** @param value the name text WITHOUT the leading slash */
    public PdfName(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static PdfName of(String value) { return new PdfName(value); }

    /** The name text without the leading slash. */
    public String value() { return value; }

    @Override public PdfType type() { return PdfType.NAME; }

    @Override public boolean equals(Object o) {
        return (o instanceof PdfName other) && value.equals(other.value);
    }

    @Override public int hashCode() { return value.hashCode(); }

    @Override public String toString() { return "/" + value; }
}
