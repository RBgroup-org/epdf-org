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
 * The PDF object model (ISO 32000-1 §7.3) as a {@code sealed} hierarchy rooted at
 * {@link com.epdfengine.rb.org.kernel.object.PdfObject}.
 *
 * <p><b>Implemented:</b> {@code PdfType} (enum) and the nine object kinds —
 * {@code PdfNull} (singleton), {@code PdfBoolean} (shared TRUE/FALSE),
 * {@code PdfNumber} (integer/real aware), {@code PdfName} (interned common names),
 * {@code PdfString} (literal/hex, ASCII + UTF-16BE text), {@code PdfArray},
 * {@code PdfDictionary} (insertion-ordered), {@code PdfStream} (dictionary + raw
 * bytes; filters applied by the writer), and {@code PdfIndirectReference}
 * ({@code N G R}, as a record). Objects are pure data — serialization lives in
 * {@code kernel.write}.</p>
 */
package com.epdfengine.rb.org.kernel.object;
