# 11 — Package Layout

One Maven module, one jar. Root package `com.epdfengine.rb.org`. Only `api` (and a
few value types it exposes) is public; everything else is internal.

## 11.1 Tree

```
com.epdfengine.rb.org
├── api                      L5  public facade, requests, results, options, Java layout model
│   ├── (Epdf, EpdfEngine, RenderRequest, RenderResult, PdfToolkit)
│   ├── doc                  programmatic layout: Document, Div, Paragraph, Table, Cell, Barcode...
│   └── config               EngineConfig, RenderLimits, PageSpec, PdfAConformance
├── core                     L4  engine impl, pipeline, RenderContext, stage orchestration
├── runtime                  L4  scalability plumbing
│   ├── event                RenderJob, scheduler, backpressure
│   ├── pool                 buffer/object pools, arenas
│   ├── cache                BoundedCache, TieredCache, font/image/style caches
│   └── metrics              counters, timers, gauges, CacheStats
├── html                    L3  HTML5 tokenizer + tree builder → DOM
├── xml                     L3  namespace-aware, XXE-safe XML parser
├── css                     L3  CSS engine
│   ├── parse                tokenizer + parser
│   ├── select               selector matching + specificity
│   ├── cascade              origin/importance/inheritance → computed styles
│   └── value                Length, Color, Gradient, Shadow, Transform...
├── svg                     L3  SVG DOM → vector PDF ops
├── layout                  L3  box tree + formatting contexts + pagination
│   ├── box                  Box hierarchy
│   ├── block                BFC, margin collapse
│   ├── inline               IFC, line breaking, baseline
│   ├── flex                 flexbox
│   ├── grid                 grid
│   ├── table                table algorithm
│   ├── floats               floats + clear
│   ├── position             relative/absolute/fixed, z-index
│   └── page                 fragmentation, @page, running headers/footers, counters
├── render                  L3  layout → PDF content
│   ├── paint                box/text/image/border/gradient/shadow painters
│   └── content              content-stream builder, graphics state
├── forms                   L3  AcroForm create/fill/flatten
├── xfa                     L3  XFA flatten to static
├── barcode                 L3  1D + 2D encoders → vector
├── tagged                  L3  structure tree, marked content, MCID
├── pdfua                   L3  role map, reading order, Alt validation (on tagged)
├── pdfa                    L3  conformance, output intent, ICC, validator
├── redact                  L3  content-stream redaction (pdfSweep-style)
├── optimize                L3  obj/xref streams, image recompress, dedup, linearize
├── ocr                     L3  imaging pipeline + text-layer writer (recognizer via SPI)
├── debug                   L3  RUPS-like object-graph inspector/exporter
├── text                    L2  international text
│   ├── bidi                 UAX #9
│   ├── shape                OpenType GSUB/GPOS shapers (Arabic/Indic/Thai/...)
│   ├── script               script detection + segmentation
│   └── font                 font selection + fallback
├── io                      L2  low-level resources
│   ├── source               RandomAccessSource (array/file/mmap)
│   ├── font                 TrueType/CFF/OTF/TTC parse + subset + embed
│   ├── image                PNG/JPEG/GIF/BMP decode + recompress + SMask
│   └── color                color spaces, ICC
├── kernel                  L1  PDF object model + reader + writer
│   ├── object               PdfObject hierarchy (sealed)
│   ├── read                 tokenizer, xref (classic/stream/objstm), lazy loader, recovery
│   └── write                serializer, xref strategy, incremental update
├── spi                     L0  ports: CryptoProvider, OcrProvider, FontProvider, ResourceLoader
├── security                L0  SSRF/XXE/bomb guards, RenderLimits enforcement
└── common                  L0  units, geometry (Rect/Edges/Point), buffers, hashing, math
```

## 11.2 Visibility rules

- **Public**: `api` and the value types it returns/accepts (`api.config`, selected
  `common` geometry). Marked stable.
- **Internal**: everything else. Enforced by Javadoc `@apiNote internal`, package
  naming, and (Java 17+ optional) a `module-info.java` that `exports` only `api`.
- **No cyclic dependencies**; dependencies point downward per
  [01-architecture.md](01-architecture.md).

## 11.3 Why single-module (vs the old multi-jar layout)

| Old (many jars) | New (one jar) |
|---|---|
| `engine-html.jar`, `engine-css.jar`, `engine-layout.jar`, ... wired by consumers | one `epdf-org.jar` |
| Version-skew risk between modules | impossible — single version |
| Public types leak across module boundaries | internal packages truly internal |
| Harder to enforce layering | enforced in one compilation unit |

Internal **layering is preserved by package structure and architecture tests**, so
we keep the modular discipline without the packaging overhead.
