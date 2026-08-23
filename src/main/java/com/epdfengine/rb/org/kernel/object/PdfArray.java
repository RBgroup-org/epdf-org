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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** A PDF array (ISO 32000-1 §7.3.6): an ordered sequence of objects. */
public final class PdfArray implements PdfObject, Iterable<PdfObject> {

    private final List<PdfObject> items;

    public PdfArray() { this.items = new ArrayList<>(); }

    public PdfArray(int initialCapacity) { this.items = new ArrayList<>(initialCapacity); }

    /** Appends an element; {@code null} is stored as the PDF null object. */
    public PdfArray add(PdfObject o) {
        items.add(o == null ? PdfNull.INSTANCE : o);
        return this;
    }

    public PdfArray addNumber(double v) { return add(PdfNumber.of(v)); }
    public PdfArray addNumber(long v)   { return add(PdfNumber.of(v)); }

    public PdfObject get(int index) { return items.get(index); }

    public int size() { return items.size(); }

    public boolean isEmpty() { return items.isEmpty(); }

    /** Read-only view of the elements. */
    public List<PdfObject> items() { return Collections.unmodifiableList(items); }

    @Override public Iterator<PdfObject> iterator() { return items.iterator(); }

    @Override public PdfType type() { return PdfType.ARRAY; }
}
