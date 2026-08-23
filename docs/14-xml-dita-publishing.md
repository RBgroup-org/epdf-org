# 14 — XML / DITA Publishing to PDF

This document is the completeness checklist for the engine's **XML → PDF** and **DITA → PDF**
publishing pipeline. It is scoped against the feature surface of established XML-to-PDF toolchains
(the DITA Open Toolkit PDF plugin, and iText's `pdfHTML` / legacy *XML Worker*) used **purely as a
capability checklist** — no third-party or copyleft code is copied. Everything here is clean-room
(`com.epdfengine.rb.org.xml…`).

The XML mapping is kept deliberately **separate** from the HTML/CSS layout engine: XML/DITA is
transformed to styled HTML (`DitaTransformer` / `XmlToHtml`) and then rendered by the same Mode-A
HTML/CSS → PDF pipeline. This keeps one layout/paint code path and lets any CSS feature the engine
already supports (grid, flex, tables, `@page`, tagged PDF, fonts) apply to DITA output for free.

Legend:  ✅ implemented + verified  ·  🟡 partial  ·  ⬜ not yet / deferred

## 1. XML foundation

| Capability | Status | Notes |
|---|---|---|
| Namespace-safe XML parser | ✅ | `XmlParser` — well-formed tree, `prefix()`/`localName()` split. |
| XXE / entity-expansion hardening | ✅ | No external DTD/entity resolution; decompression/size guards. |
| Generic element → HTML rule mapper | ✅ | `XmlToHtml.Resolver` → per-element `Rule` (wrap / void / labeled / transparent / drop). |
| In-place tree mutation API | ✅ | `XmlNode` add/insert/remove/clear children, `deepCopy()`, parent tracking. |
| Arbitrary XML + user stylesheet | ✅ | Any XML vocabulary maps via a caller `Resolver` + CSS. |

## 2. DITA topic transform

| Capability | Status | Notes |
|---|---|---|
| Topic types (concept/task/reference/glossary/troubleshooting/topic) | ✅ | `DitaTransformer.ruleFor`. |
| Titles, shortdesc, abstract, sections, examples | ✅ | Context-aware `<title>` → `h1`/`h2`. |
| Task structure (prereq/context/steps/step/cmd/info/substeps/choices/result/postreq) | ✅ | Generated labels (*Before you begin*, *Result*, *What to do next*). |
| Reference / troubleshooting (properties, refsyn, condition/cause/remedy) | ✅ | Labelled blocks. |
| Lists (ul/ol/sl/dl) | ✅ | `dl` → 2-column table. |
| Notes with type (tip/warning/caution/important/attention/danger) | ✅ | Coloured callouts. |
| Tables (CALS `table/tgroup/thead/tbody/row/entry`) + simpletable | ✅ | Mapped to HTML tables (real column/row layout). |
| Inline domain (b/i/u/sup/sub/term/keyword/uicontrol/menucascade + code phrases) | ✅ | Full software/UI domain. |
| Images (`<image>` + `<alt>`) | ✅ | `href`→`src`, alt fill-in; alt also feeds Figure tag. |
| Cross references (`<xref>`, `<link>`) with generated labels | ✅ | Empty-text xref → readable label. |
| **Content references (`@conref`)** | ✅ | Same-file resolution (id index; deep-copy target; nested conref; cycle guard). Cross-file → keeps fallback content. |
| **Footnotes (`<fn>`)** | ✅ | Numbered superscript callout + collected *Notes* list per topic. |
| **Related links (`related-links`/`linklist`/`linkpool`/`linktext`)** | ✅ | *Related information* section; internal links become GoTo. |
| **Conditional processing (DITAVAL / profiling)** | ✅ | `DitaVal` filter on `audience/platform/product/otherprops/props/rev/deliveryTarget`; DITA "all values excluded" rule. |
| `conkeyref` / `keyref` / `<keyword keyref>` | ⬜ | Needs map-level key space (see §3). |
| Cross-file `@conref` / `@conaction` push | ⬜ | Needs a resolver-backed content store across topics. |
| Index terms (`<indexterm>`) → back-of-book index | ⬜ | Requires index collection + generated index topic. |
| `<data>` / `<foreign>` / MathML / equation domain | ⬜ | Foreign vocabularies not mapped. |

## 3. DITA map / bookmap

| Capability | Status | Notes |
|---|---|---|
| Map walk (topicref/topichead/topicgroup/chapter) | ✅ | `DitaMap` depth-first, level tracking. |
| `navtitle` / resolved topic titles | ✅ | Navtitle wins, else topic `<title>`. |
| Hierarchical numbering (1, 1.1, 2 …) | ✅ | `number()` by level. |
| Generated Table of Contents | ✅ | Clickable TOC rows → `#tN` GoTo links. |
| **PDF bookmarks / outline tree** | ✅ | Heading structRoles → nested `/Outlines` (Dest `/FitH`). |
| **Page break per topic** | ✅ | `page-break-before: always` on `.map-topic`. |
| **Cross-topic `<xref>` rewriting** | ✅ | `file.dita#id` → in-document `#id`/`#tN` anchor. |
| `keydef` / map key space + `keyref` resolution | ⬜ | Collect `keys=` → `href`; resolve `conkeyref`/`keyref`/variable text. |
| `reltable` relationship-table link generation | 🟡 | Walked transparently; not yet emitted as related-links. |
| Chapter/front/back matter (bookmap) + running headers | 🟡 | `@page` margin boxes exist in the engine; not wired to chapter titles. |
| Chunking / `@chunk` | ⬜ | All topics combined into one flow today. |

## 4. Rendering & PDF features (inherited from the HTML/CSS engine)

| Capability | Status | Notes |
|---|---|---|
| Full CSS layout (block/inline/table/flex/grid/multi-col) | ✅ | Shared Mode-A engine. |
| `@page` size / landscape / margins / auto-fit | ✅ | Selectable page geometry. |
| Internal `#id` links + external URI links | ✅ | GoTo + URI annotations. |
| Tagged PDF (PDF/UA) + PDF/A-2b conformance | ✅ | StructTree, ParentTree, XMP, output intent. |
| Embedded + subset fonts, per-codepoint fallback | ✅ | TrueType subsetting. |
| Images (JPEG/PNG/GIF/BMP/WebP), SVG (vector+raster) | ✅ | Shared resource loader. |
| Running headers/footers with chapter/page counters | 🟡 | `counter(page)`/margin boxes exist; DITA chapter-title header not auto-fed. |
| Back-of-book index page | ⬜ | Pairs with §2 `<indexterm>`. |

## 5. Verified this iteration

Rendered `guide.ditamap` (concept + task + reference) via the playground and inspected with PDFBox:

- **Bookmarks**: nested `/Outlines` tree with correct topic & section titles → correct pages.
- **Page breaks**: each topic starts on a fresh page.
- **Cross-topic xref + related-links**: `dita-task.dita` / `dita-reference.dita` references resolve to
  internal GoTo links; only the genuinely external ISO link stays a URI link
  (`links goto=9 uri=1`).
- **conref**: a `<note id="lib-note">` reused via `conref="#…/lib-note"` renders its copy in a later section.
- **footnotes**: `<fn>` → superscript "1" callout in the body + a *Notes* list at topic end.
- **DITAVAL filtering**: `DitaValCheck` — `exclude("audience","admin")` + `exclude("platform","linux")`
  drops admin-only and linux-only paragraphs, keeps user, multi-value (`linux windows`) and
  unconditioned content. `RESULT PASS`.

## 6. Recommended next increments (priority order)

1. **Map key space + `keyref`/`conkeyref`** — collect `<keydef keys=…>`/`topicref@keys` → href/text,
   resolve key-based links, variable text, and cross-file conref. (Highest reuse value.)
2. **Cross-file `@conref`** — resolver-backed content store so conref can pull from other topics/library files.
3. **`<indexterm>` → back-of-book index** — collect terms with page numbers, emit a generated index topic.
4. **Relationship tables (`reltable`)** — emit derived related-links between topics.
5. **Running headers/footers fed by chapter titles** — wire the engine's `@page` margin boxes to the
   current chapter/topic title + page counters.
6. **Chunking + bookmap front/back matter** — cover/TOC/index/glossary section ordering.
