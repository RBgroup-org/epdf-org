# 15 — Mode A and Mode B: complete syntax reference

**Everything in this document reflects what `epdf-org` actually implements — not the
full HTML/CSS/SVG/DITA specs.** Anything not listed here is either ignored
gracefully or unsupported (see §11). There is **one engine** and **one box tree**;
you drive it two ways:

| | **Mode A — markup** | **Mode B — programmatic** |
|---|---|---|
| Where layout lives | HTML / CSS / XML / SVG you author | Java function calls |
| Page size / margins | CSS `@page { size; margin }` | `Document.of(PageSpec…).margins(…)` |
| Entry point | `RenderRequest.of(html)` / `HtmlRenderer` | `RenderRequest.of(document)` |
| Package to import | (author markup) | `com.epdfengine.rb.org.api.doc.*` |

Both converge on the same box tree, so fonts, layout, images, SVG, barcodes,
optimization and the writer are identical — only the front end differs.

**Contents:** [1 Running the engine](#1-running-the-engine) · [2 Mode A markup](#2-mode-a--markup)
· [3 XML](#3-xml) · [4 DITA topics](#4-dita-topics) · [5 DITA maps](#5-dita-maps)
· [6 Mode B Java API](#6-mode-b--java-api) · [7 PDF features](#7-pdf-features)
· [8 Rendering & viewers](#8-rendering--viewer-compatibility) · [9 Concurrency](#9-concurrency--scalability)
· [10 Security](#10-security-related-syntax) · [11 Unsupported / partial](#11-unsupported--partial-syntax)
· [12 Matrix](#12-complete-syntax-matrix)

---

## 1. Running the engine

```java
import com.epdfengine.rb.org.runtime.*;
import com.epdfengine.rb.org.api.doc.*;

try (Epdf engine = Epdf.create()) {                 // immutable, thread-safe, reuse it
    byte[] a = engine.render(RenderRequest.of(html).build()).bytes();       // Mode A
    byte[] b = engine.render(RenderRequest.of(document).build()).bytes();   // Mode B
}
```

`RenderRequest.Builder` (both modes): `.fonts(FontRegistry)`,
`.conformance(Conformance)`, `.metadata(DocumentMetadata)`, `.limits(ResourceLimits)`.
Mode A also: `.params(LayoutParams)`, `.resources(ResourceLoader)`.
Engine calls: `render`, `submit` (async `CompletableFuture`), `renderTo(out)`,
`submitTo(out)`, `stats()`, `metrics()`, `runtimeStats()`. See §9.

---

## 2. Mode A — markup

### 2.1 HTML parsing

Error-tolerant tag-soup parser (`HtmlParser` → `HtmlNode`). **No script execution,
no external entities.** `<script>`/`<style>` are raw-text; `<style>` is collected as
CSS. Comments and doctype are handled.

**Void elements** (no closing tag): `area base br col embed hr img input link meta
param source track wbr`.

### 2.2 Supported HTML elements

Any element renders as a box; these have **built-in (UA) behaviour**:

| Group | Elements | Default |
|---|---|---|
| Headings | `h1 h2 h3 h4 h5 h6` | block, bold, sized by level |
| Text block | `p pre blockquote div section header footer main article aside nav figure figcaption` | block |
| Inline | `a span b i em strong small code label sub sup abbr cite q mark u s big tt` | inline |
| Emphasis | `b strong`→bold, `i em`→italic, `u`→underline, `s`→line-through, `small`→smaller | — |
| Lists | `ul ol li` (and `dl dt dd`) | list items with markers |
| Table | `table thead tbody tfoot tr td th` | table / row-group / row / cell; `th` bold+centered |
| Rule / break | `hr`, `br` | horizontal rule / line break |
| Replaced | `img`, inline `svg` | image / vector |
| Forms | `input textarea select option button datalist` | AcroForm fields (§2.20) |

Unlisted elements still render as a **block** (or inline if in the inline set above).

### 2.3 Attributes

`class`, `id` (also the target for internal `#id` links), `style` (inline CSS),
`src`, `alt`, `width`, `height` (on `<img>`), `href` (on `<a>`), `colspan`, `rowspan`
(on cells), `start` (on `<ol>`), plus form attributes in §2.20.

### 2.4 Character entities

Named: `&amp; &lt; &gt; &quot; &apos; &nbsp; &copy; &reg; &trade; &mdash; &ndash;
&hellip; &bull; &middot; &deg; &euro; &pound; &cent; &laquo; &raquo; &ldquo; &rdquo;
&lsquo; &rsquo; &times; &divide;` — plus **numeric** (`&#169;`, `&#x2022;`).

### 2.5 CSS — supported properties

Applied by the cascade to `ComputedStyle`. **Only these are honoured:**

- **Box model:** `display`, `box-sizing`, `width`, `height`, `min-width`,
  `max-width`, `margin`(`-top/-right/-bottom/-left`), `padding`(same), `border`
  (+ `-top/-right/-bottom/-left`, `-color`, `-width`, `-style`, per-side `-color`),
  `border-radius`, `border-collapse`, `border-spacing`, `box-shadow`.
- **Flexbox:** `flex`, `flex-direction`, `flex-wrap`, `flex-grow`, `flex-shrink`,
  `flex-basis`, `justify-content`, `align-items`, `align-self`, `gap`, `column-gap`,
  `row-gap`.
- **Grid:** `grid`, `grid-template-columns`, `grid-template-rows`,
  `grid-template-areas`, `grid-auto-rows`, `grid-auto-flow`, `grid-column`,
  `grid-row`, `grid-area`.
- **Multi-column:** `column-count`, `columns`.
- **Typography:** `color`, `font`, `font-family`, `font-weight`, `font-style`,
  `line-height`, `letter-spacing`, `text-align`, `text-decoration`, `vertical-align`,
  `white-space`.
- **Lists:** `list-style`, `list-style-type`.
- **Background/effects:** `background`, `background-color`, `background-image`,
  `accent-color`, `backdrop-filter`, `animation`, `animation-name`,
  `animation-duration`.
- **Paging:** `page-break-before`.

> Not honoured: `position`, `float`, `top/right/bottom/left`, `z-index`, `transform`
> (except via `@keyframes`), `opacity` (use `rgba`), `overflow`, `text-transform`,
> `visibility`, `cursor`. See §11.

**Values:**
- `display`: `block · inline · inline-block · inline-flex · inline-grid · flex ·
  grid · table · table-row · table-row-group · table-cell · list-item · none`.
- `text-align`: `left right center justify start end`.
- `border-style`: `solid dashed dotted none`.
- `list-style-type`: `disc circle square decimal decimal-leading-zero lower-alpha
  upper-alpha lower-latin upper-latin lower-roman upper-roman none`.
- `font-weight`: `bold`/`normal` (numeric ≥600 → bold). `font-style`: `italic`/`normal`.
- **Units:** `pt px mm cm in em rem %` and `auto` (px→pt at 96 dpi).
- **Colours:** `#rgb`, `#rrggbb`, `#rrggbbaa`, `rgb()`, `rgba()`, and CSS named colours.

**Selectors** (`Selector`): type, `.class`, `#id`, `[attr]`/`[attr=val]`, compound &
**multi-class** (`.card.dark`), combinators descendant (space), child `>`, adjacent
`+`, general `~`, and pseudo-classes `:root :first-child :last-child :nth-child(...)`.
Specificity and source order are respected; inline `style` wins.

### 2.6 `@page`

```css
@page { size: A4; margin: 40pt; }
@page { size: A4 landscape; }             /* or portrait */
@page { size: 210mm 297mm; margin: 36pt 48pt; }
```

`size` accepts named `a3 a4 a5 b4 b5 letter legal tabloid ledger`, an explicit
`<w> <h>` with units, and/or `landscape`/`portrait`. When present, `@page` **wins**
over the request's `LayoutParams`.

### 2.7 Layout modes

**Block flow** — default vertical stacking with margins/padding/border.

**Inline flow** — text wraps on spaces; inline-block/inline elements flow on the line.

**Flexbox**

```css
.row { display: flex; flex-direction: row; gap: 12px; justify-content: space-between; align-items: center; }
.grow { flex: 1 1 auto; }
```
`justify-content`: `start center end space-between space-around space-evenly`.
`align-items`: `stretch center start end baseline`. `flex-direction: column` supported.

**Grid**

```css
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.wide  { grid-column: span 2; }
.panel { grid-template-areas: "hd hd" "nav main"; grid-auto-rows: 40pt; }
```
Supports track lists (`1fr`, `<len>`, `repeat()`), `grid-template-areas`, `span`,
`grid-auto-flow: dense|column`, multi-row spans, stretch alignment.

**Multi-column**

```css
.article { column-count: 3; column-gap: 18px; }   /* or: columns: 3; */
```

**Tables** — see §2.18.

### 2.8 Typography

```css
body { font-family: "Open Sans"; font-size: 11pt; line-height: 1.5; color: #334155;
       letter-spacing: .2pt; text-align: justify; }
.small { font-size: 9pt; } .bold { font-weight: bold; } .it { font-style: italic; }
.u { text-decoration: underline; }   /* underline · line-through · none */
```
Font families resolve against the registered `FontRegistry` (§2 fonts). With no
fonts, the base-14 (Helvetica family) with approximate metrics is used.

### 2.9 Colours

```css
.a { color: #334155; } .b { color: rgb(51,65,85); } .c { color: rgba(0,0,0,.6); }
.d { color: slateblue; } .e { background: #eff3f8; } .f { background: #10203aff; }
```

### 2.10 Borders

```css
.box { border: 1pt solid #e2e8f0; border-radius: 8px; }
.accent { border: .4pt solid #e5e7eb; border-left: 2.5pt solid #f59e0b; }   /* per-side */
.rounded-top { border-radius: 12px 12px 0 0; }                              /* 4-value */
.dashed { border: 1pt dashed #94a3b8; }
```
Per-side colours (`border-top-color` …) and per-corner radius are honoured; a table
uses `border-collapse: collapse` / `border-spacing`.

### 2.11 Gradients

```css
.hero { background: linear-gradient(135deg, #0f172a, #1e293b); }
.orb  { background: radial-gradient(circle at 30% 30%, #22d3ee, #0ea5e9); }
.badge{ background: conic-gradient(from 0deg, #6366f1, #22d3ee, #6366f1); }
```
Linear (angle or `to <dir>`), radial and conic gradients with multiple colour stops.

### 2.12 Shadows

```css
.card { box-shadow: 0 6px 18px rgba(2,6,23,.12); }
```

### 2.13 Blur / backdrop

```css
.glass { background: rgba(255,255,255,.15); backdrop-filter: blur(8px); }
```
A frosted veil (+ top highlight) is rendered, clipped to the (optionally rounded)
surface.

### 2.14 Modern UI designs

Glassmorphism, neumorphism, bento grids, aurora backgrounds, liquid-glass pills are
achievable by **combining** the above: `rgba` fills + `backdrop-filter: blur()` +
`border-radius` + gradients + `box-shadow` + grid/flex. (See the playground
templates `glass`, `neubento`, `frost`, `glyphs`, `dashboard`.)

### 2.15 Images

```html
<img src="logo.png" width="120" height="40" alt="Logo"/>
<img src="data:image/png;base64,iVBOR..."/>
```
Formats: **PNG, JPEG, GIF, BMP, WebP** (VP8 lossy + VP8L lossless). `src` resolves
via the request's `ResourceLoader` (or `data:`). Alpha becomes a PDF soft mask.
`alt` supplies the PDF/UA `/Alt`.

### 2.16 SVG (inline vector)

```html
<svg viewBox="0 0 120 40">
  <rect x="0" y="0" width="120" height="40" rx="8" fill="#2563eb"/>
  <circle cx="20" cy="20" r="10" fill="#f59e0b"/>
  <g transform="translate(40,8) rotate(15)"><path d="M0 0 L20 0 L10 18 Z" fill="#fff"/></g>
</svg>
```
**Elements:** `rect circle ellipse line polyline polygon path g`. **Attributes:**
`fill stroke stroke-width` + inline `style`, `viewBox`. **Transforms:** `translate
scale rotate matrix`. **Path** commands `M L H V C S Q T Z` (absolute + relative;
arcs `A` degrade to a line). Drawn as native PDF vectors. *Not supported:* `<text>`,
gradients, patterns, clip paths, filters (§11).

### 2.17 Lists

```html
<ul><li>Bulleted</li></ul>
<ol start="3"><li>Third</li></ol>
<ul style="list-style-type: square"><li>Square</li></ul>
```
Markers: `disc circle square` (vector) and `decimal decimal-leading-zero
lower/upper-alpha lower/upper-latin lower/upper-roman none` (text). `<ol start>` honoured.

### 2.18 Tables

```html
<table style="border-collapse: collapse; width: 100%">
  <thead><tr><th>Item</th><th>Qty</th><th>Amount</th></tr></thead>
  <tbody>
    <tr><td>Widget</td><td>2</td><td class="right">$9.99</td></tr>
    <tr><td colspan="2">Total</td><td class="right">$19.98</td></tr>
  </tbody>
</table>
```
`colspan`/`rowspan`, column widths (`width` on cells), zebra via `:nth-child(even)`,
`<thead>` **repeats on every page** across a page break, and cell heights equalize
per row with vertical-centering.

### 2.19 Links

```html
<a href="https://example.org">external link</a>
<a href="#notes">jump to notes</a>          <!-- internal -->
<h2 id="notes">Notes</h2>                    <!-- anchor target via id -->
```
External `http(s)` links become URI link annotations; `#id` become GoTo links to the
element carrying that `id`.

### 2.20 Forms → AcroForm

```html
<input type="text" name="fullName" placeholder="Name" maxlength="40" required/>
<input type="password" name="pw"/>
<input type="checkbox" name="subscribe" checked/>
<input type="radio" name="plan" value="Pro"/>
<input type="email" name="email" required/>          <!-- email/tel/url/number/search/date -> text field -->
<input type="number" name="qty" min="1" max="9" step="1"/>
<input name="city" list="cities"/> <datalist id="cities"><option>Pune</option></datalist>
<textarea name="notes" maxlength="200"></textarea>
<select name="country" multiple><option value="IN" selected>India</option></select>
<button name="submit">Submit</button>
```
**Types:** text, password, checkbox, radio, submit/button, and email/tel/url/number/
search/date (styled text). `type="hidden"` is skipped. **Attributes:** `name value
placeholder maxlength readonly disabled required min max step pattern checked
selected multiple`, `list` (→ editable combo), `data-comb` (comb field). CSS styles
the field (border, radius, `accent-color`). Fill/flatten server-side with
`FormFiller` (§7).

### 2.21 Barcodes (Mode A)

The core engine renders barcodes as **images via the `ResourceLoader`**. The
playground maps `barcode:<type>?data=<value>`:

```html
<img src="barcode:qr?data=https://example.org/inv/42" width="90" height="90"/>
<img src="barcode:code128?data=INV-42-2026" width="200" height="60"/>
```
Symbologies (also in Mode B): `qr`, `code128`, `code39`, `ean13`, `upca`,
`datamatrix` (with aliases `qrcode`, `128`, `39`, `ean-13`, `upc-a`, `dm`/`ecc200`).

### 2.22 Keyframes / animation

```css
@keyframes slidein { from { transform: translateX(-40px); opacity: 0 } to { transform: none; opacity: 1 } }
.item { animation-name: slidein; animation-duration: 600ms; }   /* or: animation: slidein 600ms; */
```
Frames are baked into PDF optional-content and play in JavaScript-capable viewers
(Adobe Acrobat/Reader); other viewers show the final frame.

---

## 3. XML

The engine ships an **XXE-proof** XML parser (`XmlParser` → `XmlNode`: no external
entities, no DTD fetch, entity-expansion/nesting caps). The concrete, fully-supported
XML *vocabulary* is **DITA** (§4–5); generic XML can be bridged into the HTML/CSS
pipeline via `xml.XmlToHtml`, but arbitrary XML→PDF beyond DITA is application-specific.
For custom XML, transform it to HTML (Mode A) or build a `Document` (Mode B).

---

## 4. DITA topics

`DitaTransformer.topicToHtml(xml)` maps a DITA topic to styled HTML, then the normal
pipeline renders it. **Topic types:** `concept`, `task`, `reference`,
`troubleshooting`, `glossentry` (and generic `topic`).

**Supported elements (subset):**

- Structure: `title titlealts shortdesc abstract body conbody taskbody refbody
  section sectiondiv example prolog metadata`.
- Task: `prereq context steps steps-unordered steps-informal step substeps substep
  cmd info stepxmp stepresult result postreq choices choice`.
- Reference: `refsyn properties`, CALS `table`/`tgroup`/`colspec`/`thead`/`tbody`/
  `row`/`entry` and `simpletable`/`sthead`/`strow`/`stentry`.
- Blocks: `p note fig image codeblock lines pre dl dlentry dt dd ul ol li sl sli`.
- Inline: `b i u sub sup em strong ph term keyword cmdname apiname parmname varname
  filepath userinput systemoutput codeph uicontrol menucascade wintitle synph
  option xref link`.
- Troubleshooting: `troublebody condition cause remedy responsibleParty`.
- Glossary: `glossterm glossdef glossSurfaceForm glossBody`.
- Links/footnotes: `related-links linklist linkpool linktext fn`.

### 4.5 keyref

```xml
<keyword keyref="product"/>            <!-- variable text from a key -->
<xref keyref="install-topic">Install</xref>   <!-- key-based link -->
```
Keys are resolved from `<keydef keys="..">` in the map; variable text and key-based
links are substituted at transform time.

### 4.6 Generated TOC — see §5.

---

## 5. DITA maps

`DitaMap.mapToHtml(ditamap, hrefLoader)` assembles a whole publication.

```xml
<map>
  <topicmeta><navtitle>User Guide</navtitle></topicmeta>
  <topichead navtitle="Getting Started">
    <topicref href="intro.dita"/>
    <topicref href="install.dita"><topicref href="install-linux.dita"/></topicref>
  </topichead>
  <chapter href="reference.dita"/>
  <keydef keys="product" href="prod.dita"><topicmeta><keywords><keyword>ACME</keyword></keywords></topicmeta></keydef>
  <reltable>…</reltable>
</map>
```
**Map elements:** `topicref`, `chapter`, `mapref`, `topichead`, `topicgroup`,
`keydef`, `reltable`; `navtitle`/`topicmeta`/`linktext`; `keys` for keyref. The
`topicref` hierarchy is resolved, every referenced topic is transformed and
concatenated in order, and a **numbered table of contents** is generated. Per-entry
page-break policy keeps chapter heads with their first topic.

---

## 6. Mode B — Java API

Package `com.epdfengine.rb.org.api.doc`. Every builder extends `Styleable` (shared
setters) and produces a `Box`.

### 6.1 Document

```java
Document doc = Document.of(PageSpec.A4)      // or a4()
        .margins(40)                          // or margins(Edges.of(t,r,b,l))
        .background(0xF8FAFC)                 // full-page fill
        .fonts(fontRegistry)                  // embed fonts (needed for PDF/A)
        .conformance(Conformance.PDF_A_2B_UA_1)
        .metadata(new DocumentMetadata().title("Invoice").language("en-US"))
        .add(content).add(more);
```

### 6.2 PageSpec

`PageSpec.A4 A3 A5 LETTER LEGAL TABLOID`; `PageSpec.of(wPt,hPt)`; `PageSpec.mm(w,h)`;
`.landscape()`, `.portrait()`.

### 6.3 Paragraph & Text

```java
Paragraph.of("Body").fontSize(11).color(0x334155).align(TextAlign.START).lineHeight(1.5);
Paragraph.heading(1, "Invoice #42");         // H1..H6, bold, tagged for accessibility
Paragraph.empty()
    .add(Text.of("Total is ")).add(Text.bold("$42"))
    .add(Text.colored("red", 0xDC2626)).add(Text.of("pay").link("https://x.org"));
```
`Text.of/bold/colored`, `.link(href)`. Paragraph text setters: `fontSize bold italic
color font`; block setters: `align lineHeight background padding margin border
borderRadius grow id role`.

### 6.4 Div (containers, flex, grid)

```java
Div.create();                                  // block stack
Div.row().gap(12);                             // flex row
Div.column();                                  // flex column
Div.grid("1fr 1fr 1fr").gap(10);               // grid
child.grow(1);  child.columnSpan(2);
```

### 6.5 Table / Row / Cell

```java
Table.columns("55%", "15%", "30%")
     .header("Item", "Qty", "Amount")
     .row("Widget", "2", "$9.99")
     .add(Row.create()
         .add(Cell.of("Total").bold().colSpan(2))
         .add(Cell.of("$9.99").bold().align(TextAlign.RIGHT)));
Cell.of(text); Cell.empty().add(content); .colSpan(n); .rowSpan(n);
```

### 6.6 Lists

```java
ListBlock.bulleted().item("A").item(Paragraph.of("rich").bold());
ListBlock.ordered().item("First").item("Second");
```

### 6.7 Images

```java
Image.of(pngBytes).width(120).alt("Logo");
Image.fromFile(Path.of("logo.png")).width(120);
```

### 6.8 Barcodes

```java
Barcode.qr("https://x.org/42").width(90).height(90);
Barcode.code128("INV-42").width(200).height(60);
Barcode.code39("AB123"); Barcode.ean13("4006381333931"); Barcode.dataMatrix("x");
Barcode.of("upca", "036000291452");            // by name
```

### 6.9 SVG

```java
SvgGraphic.of("<svg viewBox='0 0 120 40'><rect width='120' height='40' rx='8' fill='#2563eb'/></svg>")
          .width(120).height(40);
```

### 6.10 Shared style setters (`Styleable`)

`width(pt) widthPercent(%) height(pt) maxWidth(pt) padding(pt|Edges) margin(pt|Edges)
background(0xRRGGBB) border(widthPt,0xRRGGBB) borderRadius(pt) color(0xRRGGBB)
fontSize(pt) font(family) bold() italic() align(TextAlign) lineHeight(f) role(str)
id(anchor)` — and `.style()` to reach the raw `ComputedStyle` for anything not
exposed fluently (e.g. gradients/shadow).

> Mode B does solid fills, borders, radius directly; **gradients, box-shadow,
> backdrop-blur and animation are Mode A features** (set them via `.style()` if
> needed). Mode B tags **headings** for PDF/UA; Mode A produces the fuller structure
> tree.

---

## 7. PDF features

```java
// Conformance (Mode A richest tagging; Mode B tags headings)
Conformance.PDF_A_2B;  Conformance.PDF_UA_1;  Conformance.PDF_A_2B_UA_1;
HtmlRenderer.ConformantResult r = new HtmlRenderer()
    .renderConformant(html, LayoutParams.a4(36), fonts, loader, Conformance.PDF_A_2B_UA_1, meta);
boolean ok = r.isConformant();

// Toolkit (static)
byte[] merged  = PdfMerger.merge(a, b);
byte[] page1   = PdfMerger.extractPages(pdf, 0);
byte[] compact = PdfOptimizer.optimize(pdf);                 // obj/xref streams + dedup
PdfOptimizer.Result rep = PdfOptimizer.optimizeWithReport(pdf);  // + before/after sizes
byte[] fastWeb = Linearizer.linearize(pdf);                  // fast web view
byte[] filled  = FormFiller.fill(pdf, Map.of("name","Ada"));
byte[] flat    = FormFiller.flatten(filled);
byte[] redact  = Redactor.apply(pdf, List.of(new Redactor.Box(0, 72, 700, 180, 16)));  // VISUAL only
```

---

## 8. Rendering & viewer compatibility

- **Output:** standard PDF; tagged when a conformance target is set. Base-14 fonts or
  embedded/subset TrueType (Type0/CIDFontType2, Identity-H + ToUnicode → searchable).
- **Adobe Acrobat/Reader:** highest fidelity; required for **animation** (§2.22) and
  JavaScript-driven features; validates PDF/A & PDF/UA best.
- **Browser PDF viewers (Chrome/Edge/Firefox):** render static content fine; do
  **not** run PDF JavaScript, so animations show only the final frame.
- **Viewer-dependent:** animation, some form appearances. Static layout, links,
  forms (fields), barcodes, images and SVG are viewer-independent.

---

## 9. Concurrency & scalability

```java
EngineConfig cfg = EngineConfig.defaults()
        .withCpuThreads(Runtime.getRuntime().availableProcessors())   // 9.2 worker pool
        .withQueueCapacity(10_000)                                    // 9.5 queue
        .withDefaultLimits(ResourceLimits.defaults());                // 9.6 limits
try (Epdf engine = Epdf.create(cfg)) {                                // 9.1 lifecycle (AutoCloseable)
    CompletableFuture<RenderResult> f = engine.submit(req);           // async
    engine.renderTo(req, outputStream);                               // 9.7 streaming
}
```
- **9.2 Worker pool** — bounded CPU pool ≈ cores runs layout/render.
- **9.3 Virtual-thread I/O** — `engine.ioExecutor()` / `engine.prefetch(html, loader)`
  fetch remote assets concurrently without pinning OS threads.
- **9.4 Backpressure** — a full queue throws `BackpressureException` (catch → HTTP 429).
- **9.6 Cancellation** — per-job wall-clock deadline (`ResourceLimits`) cancels runaways.
- **9.7 Streaming** — `renderTo`/`submitTo` write straight to your stream.
- **9.8 Caching** — byte-bounded font/image caches (content-hash dedup); pooled `Deflater`.
- Observability: `engine.stats()`, `engine.metrics().percentileMillis(95)`,
  `engine.runtimeStats().heapUsedRatio()`.

---

## 10. Security-related syntax

```java
EngineConfig.defaults()
    .withDefaultLimits(ResourceLimits.defaults())    // max input bytes/pages/nodes/wall-clock
    .withTraceSink(...);                             // debug is host-only (see 16-debugging.md)
RenderRequest.of(html).resources(sslSafeLoader).limits(strictLimits).build();
```
- **Resource limits** (`ResourceLimits`) cap input size, pages, DOM nodes, wall-clock.
- **`ResourceLoader`** is the SSRF boundary — the host decides what URLs resolve.
- **Debug** is enabled only via `EngineConfig`/`Debug` — **never** from CSS/HTML.
- **Redaction** is visual-only (not content removal). **No crypto** (SPI hook only).

---

## 11. Unsupported / partial syntax

| Area | Status |
|---|---|
| CSS `position` / `float` / `top/left/right/bottom` / `z-index` | **not supported** |
| CSS `transform` (outside `@keyframes`), `opacity`, `overflow`, `text-transform`, `visibility` | **not supported** (use `rgba` for alpha) |
| CSS `@media`, `@font-face` (Mode A) | **not supported** — register fonts via `FontRegistry` in code |
| SVG `<text>`, gradients, patterns, clip paths, filters; path `A` arc | **not supported** (arc → line) |
| Mode B gradients / box-shadow / backdrop-blur / animation | **partial** — via `.style()` only |
| Mode B PDF/UA tagging | **partial** — headings only (Mode A is full) |
| Redaction | **visual only** (no content removal) |
| Encryption / digital signatures | **out of scope** (SPI hook only) |
| Bidi / complex-script shaping (Arabic/Indic/Thai) | **not implemented** (Latin/CJK + hyphenation only) |
| Generic (non-DITA) XML → PDF | **bridge only** — transform to HTML/Mode B |
| Fonts | **TrueType only** (no CFF/OTF outlines); none bundled |

---

## 12. Complete syntax matrix

| Capability | Mode A (markup) | Mode B (Java) |
|---|---|---|
| Page size / margins | `@page { size; margin }` | `Document.of(PageSpec).margins()` |
| Heading / paragraph | `<h1>…</h1>` / `<p>` | `Paragraph.heading(n,…)` / `Paragraph.of()` |
| Bold / italic / colour / size | `font-weight/style;color;font-size` | `.bold().italic().color().fontSize()` |
| Mixed inline / link | `<strong>`,`<span>`,`<a href>` | `Text.bold/colored/link` |
| Container / flex / grid | `display:flex/grid` | `Div.row()/column()/grid()` |
| Table (spans, widths) | `<table>…colspan` | `Table.columns()…Cell.colSpan()` |
| List (bulleted / numbered) | `<ul>/<ol><li>` | `ListBlock.bulleted()/ordered()` |
| Image (PNG/JPG/GIF/BMP/WebP) | `<img src>` | `Image.of()/fromFile()` |
| SVG (vector subset) | inline `<svg>` | `SvgGraphic.of()` |
| Barcode (QR/128/39/EAN13/UPCA/DM) | `barcode:` via loader | `Barcode.qr()/code128()/…` |
| Gradients / shadow / blur / animation | ✅ CSS | ⚠️ via `.style()` |
| Forms → AcroForm | `<input>/<select>/…` | (toolkit `AcroForm`) |
| Fonts (embed + subset) | `font-family` + `.fonts()` | `.font()` + `Document.fonts()` |
| PDF/A-2b + PDF/UA-1 | `renderConformant(...)` | `.conformance(...)` |
| XML / DITA (topics + maps + keyref) | ✅ | — |
| Merge / optimize / linearize / redact | toolkit | toolkit |
| Async / streaming / backpressure / metrics | ✅ | ✅ |
| Debug tracing (host-only) | `Debug.enabled(...)` | `Debug.enabled(...)` |
