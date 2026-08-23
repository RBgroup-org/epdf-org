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

import com.epdfengine.rb.org.testkit.Assert;

/** SPI registry: no-op defaults, builder overrides, and ServiceLoader discovery. */
public final class ProvidersTest {

    public static void main(String[] args) {
        Providers none = Providers.none();
        Assert.isTrue(none.fonts().fontBytes("Arial", false, false) == null, "default font provider is NONE");
        Assert.isTrue(none.ocr().recognize(new byte[0], 1, 1).isEmpty(), "default OCR is NONE");
        Assert.isTrue(!none.crypto().isAvailable(), "default crypto is NONE (crypto-free core)");
        Assert.isTrue(none.images().decode(new byte[]{0}) == null, "default image decoder is NONE");

        FontProvider fp = (family, bold, italic) -> new byte[]{1, 2, 3};
        Providers custom = Providers.builder().fonts(fp).telemetry((m, v) -> { }).build();
        Assert.equals(3L, custom.fonts().fontBytes("X", false, false).length, "custom provider used");

        Providers discovered = Providers.discover();   // no services on the test path → all defaults
        Assert.isTrue(discovered.images().decode(new byte[]{0}) == null, "discover() falls back to NONE");
        System.out.println("PASS — SPI providers (builder + discover + no-op defaults)");
    }
}
