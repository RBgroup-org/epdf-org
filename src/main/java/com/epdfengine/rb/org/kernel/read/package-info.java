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
 * PDF reading and in-place editing (the input side of the kernel).
 *
 * <ul>
 *   <li>{@code Lexer} — a bounded PDF tokenizer.</li>
 *   <li>{@code PdfReader} — parses a PDF into the {@code kernel.object} model:
 *       classic xref tables, cross-reference streams and object streams
 *       ({@code /ObjStm}), {@code /Prev} incremental chains, lazy object loading and
 *       bounded xref recovery for malformed files. Exposes the catalog, pages,
 *       Info, id, and decoded stream bytes.</li>
 *   <li>{@code PdfEditor} — rewrites selected objects and serializes an updated
 *       file, the basis for merge/split/optimize/redact/form operations.</li>
 * </ul>
 *
 * <p>All parsing validates bounds at the boundary; the reader cannot loop or
 * exhaust memory on hostile input (see {@code docs/08-security.md}).</p>
 */
package com.epdfengine.rb.org.kernel.read;
