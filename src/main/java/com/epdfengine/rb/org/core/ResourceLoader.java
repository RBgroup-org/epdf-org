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
package com.epdfengine.rb.org.core;

/**
 * Resolves an HTML resource reference (e.g. an {@code <img src>}) to its raw
 * bytes. Supplied by the caller so the engine stays free of any I/O or network
 * policy: a host application decides whether {@code src} maps to a file, a
 * classpath entry, an HTTP fetch (with its own SSRF controls), or a lookup table.
 * Return {@code null} (or throw) when the reference cannot be resolved; the
 * engine then skips that element gracefully.
 */
@FunctionalInterface
public interface ResourceLoader {
    byte[] load(String reference);
}
