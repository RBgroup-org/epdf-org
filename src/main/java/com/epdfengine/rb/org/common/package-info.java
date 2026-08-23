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
 * Shared foundation: units and geometry ({@code Length}, {@code Point}, {@code Size},
 * {@code Rect}, {@code Edges}) and stream (de)compression — {@code Deflate} (pooled
 * JDK FlateDecode) and {@code Inflate} (bomb-guarded inflate with output-size and
 * expansion-ratio caps). Depends on nothing else.
 */
package com.epdfengine.rb.org.common;
