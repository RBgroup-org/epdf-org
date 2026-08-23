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

import com.epdfengine.rb.org.testkit.Assert;

import java.util.List;

/** Verifies the OCR invisible-text overlay content fragment. */
public final class OcrOverlayTest {

    public static void main(String[] args) {
        List<OcrWord> words = List.of(
                new OcrWord("Hello", 10, 20, 100, 30),
                new OcrWord("World", 120, 20, 100, 30));
        // 200x100 px image drawn 1:1 at page origin (0,0) → 200x100 pt.
        String frag = SearchableText.overlay(words, 200, 100, 0, 0, 200, 100, "F1");

        Assert.contains(frag, "3 Tr", "invisible render mode");
        Assert.contains(frag, "(Hello)", "first word present");
        Assert.contains(frag, "(World)", "second word present");
        Assert.contains(frag, "Tj", "text shown");
        Assert.contains(frag, "1 0 0 1 10 50 Tm", "Hello positioned (x=10, y=100-(20+30)=50)");
        Assert.contains(frag, "/F1 30 Tf", "font size fits box height (30)");
        Assert.isTrue(SearchableText.overlay(List.of(), 200, 100, 0, 0, 200, 100, "F1").isEmpty(),
                "no words → empty fragment");
        System.out.println("PASS — OCR searchable overlay (invisible text layer)");
    }
}
