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
package com.epdfengine.rb.org.pdfa;

/** Document metadata used to fill the Info dictionary and the XMP packet. */
public final class DocumentMetadata {

    private String title = "";
    private String author = "";
    private String subject = "";
    private String language = "en-US";
    private final String producer = "ePDF Engine (epdf-org)";

    public DocumentMetadata title(String v)    { this.title = safe(v); return this; }
    public DocumentMetadata author(String v)   { this.author = safe(v); return this; }
    public DocumentMetadata subject(String v)  { this.subject = safe(v); return this; }
    public DocumentMetadata language(String v) { this.language = safe(v); return this; }

    public String title()    { return title; }
    public String author()   { return author; }
    public String subject()  { return subject; }
    public String language() { return language; }
    public String producer() { return producer; }

    private static String safe(String v) { return v == null ? "" : v; }
}
