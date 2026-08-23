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
 * CSS engine: tokenizer/parser, selector matching + specificity, the cascade to
 * {@code ComputedStyle}, and typed values. {@code CssParser} parses inline
 * {@code style} and {@code <style>} sheets including {@code @page},
 * {@code @keyframes}, {@code :root} custom properties + {@code var()};
 * {@code StyleResolver} applies UA defaults + author rules + inline styles;
 * {@code Selector} handles type/class/id/compound/multi-class selectors,
 * descendant/child/adjacent/general combinators, and structural pseudo-classes
 * with correct specificity. {@code ComputedStyle} is the resolved box style
 * (display, box model, flexbox, grid, font, colours, gradients, per-side/rounded
 * borders, shadow, backdrop-blur, animation). Typed values: {@code CssColor}
 * (named/#hex/rgb/rgba), {@code Gradient} (linear/radial/conic), {@code Shadow},
 * {@code AnimFrame}, {@code PageRule}, {@code PageSize}, and the layout enums. See
 * docs/03-pipeline-html-css-svg.md.
 */
package com.epdfengine.rb.org.css;
