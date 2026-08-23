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
package com.epdfengine.rb.org.spi;

import com.epdfengine.rb.org.ocr.OcrProvider;

import java.util.Objects;
import java.util.ServiceLoader;

/**
 * An immutable, per-engine bundle of pluggable providers (fonts, image decoders, OCR, telemetry,
 * crypto). Build one with {@link #builder()} or {@link #discover()} (which loads implementations
 * via the JDK {@link ServiceLoader}). Being an instance — not a global singleton — keeps the
 * engine free of shared mutable static state and lets different tenants use different providers.
 */
public final class Providers {

    private final FontProvider fonts;
    private final ImageDecoderProvider images;
    private final OcrProvider ocr;
    private final TelemetrySink telemetry;
    private final CryptoProvider crypto;

    private Providers(Builder b) {
        this.fonts = b.fonts;
        this.images = b.images;
        this.ocr = b.ocr;
        this.telemetry = b.telemetry;
        this.crypto = b.crypto;
    }

    public FontProvider fonts()          { return fonts; }
    public ImageDecoderProvider images() { return images; }
    public OcrProvider ocr()             { return ocr; }
    public TelemetrySink telemetry()     { return telemetry; }
    public CryptoProvider crypto()       { return crypto; }

    /** All-default providers (no-ops). */
    public static Providers none() { return builder().build(); }

    /** Discovers providers via {@link ServiceLoader}; falls back to the no-op default for each. */
    public static Providers discover() {
        return builder()
                .fonts(first(FontProvider.class, FontProvider.NONE))
                .images(first(ImageDecoderProvider.class, ImageDecoderProvider.NONE))
                .ocr(first(OcrProvider.class, OcrProvider.NONE))
                .telemetry(first(TelemetrySink.class, TelemetrySink.NONE))
                .crypto(first(CryptoProvider.class, CryptoProvider.NONE))
                .build();
    }

    private static <T> T first(Class<T> type, T fallback) {
        return ServiceLoader.load(type).findFirst().orElse(fallback);
    }

    public static Builder builder() { return new Builder(); }

    /** Fluent builder; unset providers default to their no-op {@code NONE}. */
    public static final class Builder {
        private FontProvider fonts = FontProvider.NONE;
        private ImageDecoderProvider images = ImageDecoderProvider.NONE;
        private OcrProvider ocr = OcrProvider.NONE;
        private TelemetrySink telemetry = TelemetrySink.NONE;
        private CryptoProvider crypto = CryptoProvider.NONE;

        public Builder fonts(FontProvider p)          { this.fonts = req(p); return this; }
        public Builder images(ImageDecoderProvider p) { this.images = req(p); return this; }
        public Builder ocr(OcrProvider p)             { this.ocr = req(p); return this; }
        public Builder telemetry(TelemetrySink p)     { this.telemetry = req(p); return this; }
        public Builder crypto(CryptoProvider p)       { this.crypto = req(p); return this; }

        public Providers build() { return new Providers(this); }

        private static <T> T req(T p) { return Objects.requireNonNull(p, "provider"); }
    }
}
