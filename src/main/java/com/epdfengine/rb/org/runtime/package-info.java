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
 * Runtime/scalability layer. {@link com.epdfengine.rb.org.runtime.Epdf} is the engine
 * facade: each render is one job ({@link com.epdfengine.rb.org.runtime.RenderRequest} +
 * {@link com.epdfengine.rb.org.runtime.RenderContext}) dispatched onto a bounded CPU
 * worker pool with backpressure ({@link com.epdfengine.rb.org.runtime.BackpressureException}),
 * a virtual-thread pool for I/O, per-job deadlines/cancellation and
 * {@link com.epdfengine.rb.org.runtime.ResourceLimits}. No per-render mutable state.
 * Object/buffer pools, byte-bounded caches and richer metrics build on this. See
 * docs/07-scalability-runtime.md.
 */
package com.epdfengine.rb.org.runtime;
