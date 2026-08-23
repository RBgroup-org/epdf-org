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
 * Orchestration for the markup path (Mode A). {@code HtmlRenderer} is the Mode A
 * entry point (parse → cascade → box build → layout → render); {@code DomToBoxes}
 * turns the resolved DOM into the box tree; {@code FontMetricsMeasurer} is a
 * {@code TextMeasurer} backed by {@code io.font.FontRegistry} (kept here so the
 * layout engine never depends on {@code io.font}); {@code AutoFitter} scales a
 * design to the page; and {@code ResourceLoader} is the resource-fetch port with a
 * concurrent prefetching loader ({@code ResourcePrefetcher} /
 * {@code PrefetchedResourceLoader}). Per-job runtime orchestration lives in
 * {@code runtime}. Holds no static mutable state.
 */
package com.epdfengine.rb.org.core;
