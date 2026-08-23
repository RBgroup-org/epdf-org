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
package com.epdfengine.rb.org.runtime;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Consumes a finished {@link PipelineTrace} in debug mode. The engine never writes logs inside its
 * own jar; a host supplies a sink — typically {@link #toFile(Path)} pointing at an application log
 * path — so per-PDF diagnostics (which stage failed, and why) land in an external file.
 */
@FunctionalInterface
public interface TraceSink {

    void accept(PipelineTrace trace);

    /** Appends each trace to {@code path} (thread-safe across concurrent jobs). Created if absent. */
    static TraceSink toFile(Path path) {
        Object lock = new Object();
        OpenOption[] opts = { StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND };
        return trace -> {
            synchronized (lock) {
                try {
                    Files.writeString(path, trace.toString(), StandardCharsets.UTF_8, opts);
                } catch (IOException e) {
                    throw new UncheckedIOException("trace write failed: " + path, e);
                }
            }
        };
    }

    /** Prints each trace to {@code out} (thread-safe). */
    static TraceSink toStream(PrintStream out) {
        return trace -> out.print(trace.toString());
    }

    /** Prints each trace to {@code System.err}. */
    static TraceSink toConsole() {
        return toStream(System.err);
    }
}
