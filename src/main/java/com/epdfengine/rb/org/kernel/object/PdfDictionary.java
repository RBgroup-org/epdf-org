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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A PDF dictionary (ISO 32000-1 §7.3.7): name-to-object associations. Insertion
 * order is preserved for deterministic, diff-friendly output.
 */
public final class PdfDictionary implements PdfObject {

    private final Map<PdfName, PdfObject> map = new LinkedHashMap<>();

    public PdfDictionary() {}

    /** Stores {@code value} under {@code key}; a null value removes the key. */
    public PdfDictionary put(PdfName key, PdfObject value) {
        Objects.requireNonNull(key, "key");
        if (value == null) { map.remove(key); } else { map.put(key, value); }
        return this;
    }

    public PdfDictionary putName(PdfName key, String name) { return put(key, PdfName.of(name)); }
    public PdfDictionary putNumber(PdfName key, long v)    { return put(key, PdfNumber.of(v)); }
    public PdfDictionary putNumber(PdfName key, double v)  { return put(key, PdfNumber.of(v)); }

    public PdfObject get(PdfName key) { return map.get(key); }

    public boolean has(PdfName key) { return map.containsKey(key); }

    public PdfObject remove(PdfName key) { return map.remove(key); }

    public Set<PdfName> keys() { return map.keySet(); }

    public int size() { return map.size(); }

    public boolean isEmpty() { return map.isEmpty(); }

    /** Iterates entries in insertion order. */
    public Iterable<Map.Entry<PdfName, PdfObject>> entries() { return map.entrySet(); }

    @Override public PdfType type() { return PdfType.DICTIONARY; }
}
