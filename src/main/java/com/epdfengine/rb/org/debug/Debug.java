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
package com.epdfengine.rb.org.debug;

import com.epdfengine.rb.org.runtime.EngineConfig;
import com.epdfengine.rb.org.runtime.TraceSink;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * One-call debug enablement. Debugging is <b>off by default</b>; a host turns it on explicitly,
 * and the per-PDF pipeline trace (which stage ran, how long, and where it failed) is written to an
 * external destination the caller chooses — a log file or the console, never inside the jar.
 *
 * <pre>{@code
 * // enable debug tracing to an application log file
 * Epdf engine = Epdf.create(Debug.enabled(Path.of("logs/epdf-debug.log")));
 * }</pre>
 */
public final class Debug {

    private Debug() {}

    /** An {@link EngineConfig} with debug tracing enabled to {@code logFile}. */
    public static EngineConfig enabled(Path logFile) {
        return EngineConfig.defaults().withDebugLog(logFile);
    }

    /** An {@link EngineConfig} with debug tracing enabled to the console ({@code System.err}). */
    public static EngineConfig enabledToConsole() {
        return EngineConfig.defaults().withDebugConsole();
    }

    /** Enables debug tracing on an existing config, writing traces to {@code logFile}. */
    public static EngineConfig enableOn(EngineConfig config, Path logFile) {
        return config.withDebugLog(logFile);
    }

    public static TraceSink fileSink(Path logFile)      { return TraceSink.toFile(logFile); }
    public static TraceSink consoleSink()               { return TraceSink.toConsole(); }
    public static TraceSink streamSink(PrintStream out) { return TraceSink.toStream(out); }
}
