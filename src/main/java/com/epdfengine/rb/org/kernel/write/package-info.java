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
/**
 * Serialization of the object model to PDF bytes.
 *
 * <p>{@code ObjectSerializer} writes each {@code PdfObject} in ISO 32000 syntax
 * (name/string escaping, exponent-free numbers, indirect-object envelopes, and
 * streams with an auto-computed {@code /Length}). {@code PdfWriter} streams the file
 * header, indirect objects, xref and trailer directly to the output — either a
 * classic xref table, or a compressed layout ({@code writeCompressed}) using object
 * streams ({@code /ObjStm}) and a cross-reference stream ({@code /XRef}).</p>
 */
package com.epdfengine.rb.org.kernel.write;
