# 13 — Roadmap & Build Order

Build bottom-up: the kernel first (everything depends on it), then resources, then
producers, then API. Each milestone is independently testable.

```mermaid
flowchart LR
    M0[M0 Foundation] --> M1[M1 Kernel]
    M1 --> M2[M2 Resources]
    M2 --> M3[M3 Render core]
    M3 --> M4[M4 CSS/HTML fidelity]
    M4 --> M5[M5 Intl text]
    M5 --> M6[M6 Manipulation]
    M6 --> M7[M7 Compliance]
    M7 --> M8[M8 Features]
    M8 --> M9[M9 Scale hardening]
```

## M0 — Foundation
- `common` (units, geometry, buffers, hashing), `spi` ports, `security` limits +
  SSRF-safe loader, in-house test harness, architecture tests (no statics/layers).

## M1 — Kernel (highest leverage)
- `kernel.object` sealed model; `kernel.write` streaming writer with classic +
  compressed xref/objstm; `kernel.read` lazy reader with all xref forms + recovery;
  filters with bomb guards. **Deliverable:** create + round-trip PDFs.

## M2 — Resources
- `io.source`, `io.image` (PNG/JPEG/GIF/BMP + SMask), `io.color`, `io.font`
  (TrueType + CFF/OTF + TTC, subsetting, embedding). Font/image content-hash caches
  in `runtime.cache`.

## M3 — Render core
- `layout.box` + `render.content`/`render.paint`; block + inline first, then the
  Java `api.doc` model. **Deliverable:** programmatic PDF (Mode B) end-to-end.

## M4 — CSS/HTML fidelity
- `html` tree builder; `css.parse/select/cascade/value`; box builder; then
  flex → grid → table → floats → position → pagination. **Deliverable:** Mode A
  HTML→PDF matching the browser on report/invoice templates.

## M5 — International text
- `text.bidi`, `text.script`, `text.shape` (Arabic/Indic/Thai), `text.font`
  fallback, line breaking. **Deliverable:** correct Arabic/CJK/Thai output.

## M6 — Manipulation
- Merge/split/stamp/n-up on the reader; `forms` create/fill/flatten; `redact`;
  `debug` inspector.

## M7 — Compliance
- `tagged` + `pdfua` (full role map, reading order, Alt validation); `pdfa`
  (A-1/2/3/4, real sRGB ICC, validator).

## M8 — Features
- `barcode` (1D+2D), `svg` vector, `optimize` (objstm/xref/dedup/recompress/
  linearize), `xfa` flatten, `ocr` pipeline + SPI.

## M9 — Scale hardening
- `runtime.event` scheduler, pools/arenas, backpressure, deadlines, metrics;
  fuzz corpus expansion; capacity/load tests to the 1M-request target.

## Definition of done (per milestone)
- Unit + golden-file tests (render → re-read via kernel → assert).
- Fuzz pass for any new parser.
- Architecture tests green (no statics, layering intact).
- Docs in `docs/` updated with any structural change + diagram.

## Immediate next actions
1. Approve package tree ([11-package-layout.md](11-package-layout.md)) and Java
   baseline (**21**, latest LTS) — confirmed.
2. Scaffold `package-info.java` for every package (done in this phase).
3. Start **M1 kernel** object model + streaming writer.
