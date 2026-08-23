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
 * Host-controlled debug tracing. Debugging is <b>off by default</b> and can be
 * enabled <b>only</b> through the Java API by the host application — a document
 * (CSS/HTML/XML) has <b>no</b> hook to turn it on, so untrusted input cannot switch
 * on tracing or write files.
 *
 * <p>{@link com.epdfengine.rb.org.debug.Debug} is a one-call helper that produces an
 * {@link com.epdfengine.rb.org.runtime.EngineConfig} with the per-render
 * <b>pipeline trace</b> enabled — which stage ran (layout, serialize, …), how long
 * it took, and where it failed. The trace is written to a destination the caller
 * chooses (a log file, the console, or any stream) via the runtime
 * {@link com.epdfengine.rb.org.runtime.TraceSink}; nothing is written inside the
 * jar. See {@code Debug.enabled(Path)}, {@code Debug.enabledToConsole()} and
 * {@code Debug.enableOn(config, path)}. No GUI, no external dependency.</p>
 *
 * @see com.epdfengine.rb.org.runtime.PipelineTrace
 * @see com.epdfengine.rb.org.runtime.TraceSink
 */
package com.epdfengine.rb.org.debug;
