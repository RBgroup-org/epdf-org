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
 * DITA support: transforms DITA topics <em>and maps</em> into styled HTML that
 * reuses the engine's HTML/CSS layout — a clean-room analogue of a DITA-OT PDF
 * transform. {@code DitaTransformer} renders concept/task/reference topics;
 * {@code DitaMap} assembles a {@code .ditamap} (resolved topicref hierarchy +
 * generated TOC) and resolves {@code keyref}/keys; {@code DitaVal} carries
 * conditional-processing filters. The DITA vocabulary is mapped separately from
 * layout so it can evolve independently.
 */
package com.epdfengine.rb.org.xml.dita;
