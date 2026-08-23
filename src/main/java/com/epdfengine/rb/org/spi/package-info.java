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
 * Service Provider Interfaces (ports) — the seams through which capabilities that
 * epdf-org does not implement itself are supplied by adapters (the host or the
 * sibling epdf-global): {@code CryptoProvider} (encryption/signing — default NONE),
 * {@code FontProvider}, {@code ImageDecoderProvider} and {@code TelemetrySink}.
 * {@code Providers} is the instance-based registry that resolves them. (The OCR
 * port is {@code ocr.OcrProvider}; the resource-loading port is
 * {@code core.ResourceLoader}.)
 */
package com.epdfengine.rb.org.spi;
