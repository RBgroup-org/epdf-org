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
package com.epdfengine.rb.org.kernel;

import com.epdfengine.rb.org.testkit.Assert;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/** Verifies content-hash image dedup: identical source bytes embed once, saving objects and size. */
public final class ImageDedupTest {

    public static void main(String[] args) throws Exception {
        dedupSameContent();
        distinctContent();
        sizeWin();
        System.out.println("PASS — image content-hash dedup");
    }

    private static byte[] noise(int bytes, long seed) {
        byte[] b = new byte[bytes];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static void dedupSameContent() {
        PdfDocument pdf = PdfDocument.create();
        byte[] img = noise(200 * 200 * 3, 1);
        String n1 = pdf.addRgbImage(img, 200, 200);
        String n2 = pdf.addRgbImage(img.clone(), 200, 200);   // identical content, different array
        String n3 = pdf.addRgbImage(img, 200, 200);           // identical content, same array
        Assert.equals(n1, n2, "identical content → same resource (different array)");
        Assert.equals(n1, n3, "identical content → same resource (same array)");
        Assert.equals(1L, pdf.distinctImageCount(), "one distinct image embedded");
        Assert.equals(2L, pdf.imageDedupHits(), "two embeds satisfied from cache");
    }

    private static void distinctContent() {
        PdfDocument pdf = PdfDocument.create();
        byte[] a = noise(64 * 64 * 3, 2);
        byte[] b = a.clone(); b[0] ^= 0xFF;                   // one byte different
        String na = pdf.addRgbImage(a, 64, 64);
        String nb = pdf.addRgbImage(b, 64, 64);
        Assert.isTrue(!na.equals(nb), "different content → different resource");
        Assert.equals(2L, pdf.distinctImageCount(), "two distinct images");
        // Same bytes but a different image kind (JPEG vs RGB) must not collide.
        String nj = pdf.addJpegImage(a, 64, 64, 3);
        Assert.isTrue(!nj.equals(na), "same bytes, different kind → not deduped");
    }

    private static void sizeWin() throws Exception {
        // Ten identical incompressible images embed as ONE object → output ≈ one image, not ten.
        PdfDocument pdf = PdfDocument.create();
        byte[] img = noise(300 * 300 * 3, 3);   // ~270 KB, deflate-resistant
        for (int i = 0; i < 10; i++) pdf.addRgbImage(img, 300, 300);
        Assert.equals(1L, pdf.distinctImageCount(), "10 identical embeds → 1 object");
        Assert.equals(9L, pdf.imageDedupHits(), "9 dedup hits");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.writeTo(out, true);
        int size = out.size();
        System.out.println("  10 identical 270KB images → PDF " + size + " bytes (one copy)");
        Assert.isTrue(size < 400_000, "output holds a single image copy, not ten (" + size + " bytes)");
    }
}
