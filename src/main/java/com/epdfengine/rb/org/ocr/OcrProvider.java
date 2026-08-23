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
package com.epdfengine.rb.org.ocr;

import java.util.List;

/**
 * Recognises text in a raster image. epdf-org bundles no OCR engine (that pulls heavy native
 * dependencies); a host plugs one in (Tesseract, a cloud OCR) behind this SPI. The default
 * {@link #NONE} returns no words.
 */
@FunctionalInterface
public interface OcrProvider {
    List<OcrWord> recognize(byte[] imageBytes, int width, int height);

    OcrProvider NONE = (imageBytes, width, height) -> List.of();
}
