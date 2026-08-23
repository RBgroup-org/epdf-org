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
 * Redaction. {@link com.epdfengine.rb.org.redact.Redactor} performs <b>visual</b>
 * redaction: it paints opaque bars over regions via an incremental update. It masks
 * content but does <b>not</b> remove the underlying text/images from the file, so it
 * must not be relied on to strip extractable sensitive data. Secure content-removal
 * redaction (rewriting content streams to delete covered data) is a heavier,
 * separate operation and is not part of this build.
 */
package com.epdfengine.rb.org.redact;
