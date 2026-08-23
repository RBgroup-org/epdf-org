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
 * PDF kernel: the object model, reader, and writer. Foundation layer — every
 * producer depends on it.
 *
 * <ul>
 *   <li>{@code object} — the sealed {@code PdfObject} model.</li>
 *   <li>{@code read} — tokenizer, xref (classic + {@code /XRef} streams +
 *       {@code /ObjStm} object streams), {@code /Prev} incremental chains, lazy
 *       loading, bounded recovery, and an in-place {@code PdfEditor}.</li>
 *   <li>{@code write} — streaming serializer with a classic xref table and a
 *       compressed writer (object streams + cross-reference streams).</li>
 * </ul>
 *
 * <p>Top-level: {@code PdfDocument} (object table, catalog/pages tree,
 * {@code addPage} with FlateDecode content, standard/embedded-font and image
 * {@code /XObject} resources), {@code PdfMerger} (merge / extract pages, plain or
 * compressed output) and {@code StandardFont} (the base-14 fonts).</p>
 */
package com.epdfengine.rb.org.kernel;
