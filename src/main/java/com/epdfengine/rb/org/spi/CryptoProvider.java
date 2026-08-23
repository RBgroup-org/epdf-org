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
package com.epdfengine.rb.org.spi;

/**
 * Encryption/signing hook. epdf-org ships <b>no</b> cryptography by policy; a companion module
 * (e.g. {@code epdf-global} backed by a vetted crypto library) supplies a real implementation.
 * The default {@link #NONE} reports unavailable so the core stays crypto-free.
 */
public interface CryptoProvider {
    boolean isAvailable();
    String name();

    CryptoProvider NONE = new CryptoProvider() {
        @Override public boolean isAvailable() { return false; }
        @Override public String name() { return "none"; }
    };
}
