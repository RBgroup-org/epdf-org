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
 * Security enforcement: SSRF-safe resource loading ({@code SafeResourceLoader} +
 * {@code NetworkGuard}: host allowlist, resolved-IP pinning, no blind redirects,
 * blocked private/link-local/metadata ranges), working with the caps behind
 * {@code runtime.ResourceLimits} (XXE/entity-expansion and decompression-bomb
 * guards). See docs/08-security.md.
 */
package com.epdfengine.rb.org.security;
