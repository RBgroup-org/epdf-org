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
package com.epdfengine.rb.org.pdfa;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;

/** Supplies the sRGB ICC profile bytes for the PDF/A output intent (via {@code java.desktop}). */
final class SrgbProfile {

    private SrgbProfile() {}

    static byte[] iccBytes() {
        ICC_ColorSpace cs = (ICC_ColorSpace) ColorSpace.getInstance(ColorSpace.CS_sRGB);
        return cs.getProfile().getData();
    }
}
