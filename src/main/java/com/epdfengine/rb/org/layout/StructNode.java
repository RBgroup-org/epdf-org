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

/**
 * One node of the logical-structure tree, produced while building the box tree and
 * consumed by the tagged-PDF writer. {@code parentId} points at the enclosing node
 * ({@code -1} = a top-level child of the document). {@code role} is a standard PDF
 * structure type (Document, Div, H1–H6, P, L, LI, LBody, Table, TR, TD, TH, Figure,
 * Caption, BlockQuote, Link). {@code alt} is alternate text (figures/links) and
 * {@code label} is a list marker used for an {@code Lbl} element.
 */
public final class StructNode {

    private final int id;
    private final String role;
    private final int parentId;
    private final String alt;
    private final String label;

    public StructNode(int id, String role, int parentId, String alt, String label) {
        this.id = id;
        this.role = role;
        this.parentId = parentId;
        this.alt = alt;
        this.label = label;
    }

    public int id()        { return id; }
    public String role()   { return role; }
    public int parentId()  { return parentId; }
    public String alt()    { return alt; }
    public String label()  { return label; }
}
