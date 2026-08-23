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
 * Clean-room barcode encoders. 1D: {@code Code128}, {@code Code39}, {@code EAN13}.
 * 2D: {@code QrCode}, {@code DataMatrix}. {@code Barcodes.encode(type, data)} is the
 * facade returning a {@code BarcodeMatrix}; {@code BarcodeImages} rasterises a matrix
 * to PNG for embedding and {@code BarcodeRenderer} draws it. No external dependency.
 */
package com.epdfengine.rb.org.barcode;
