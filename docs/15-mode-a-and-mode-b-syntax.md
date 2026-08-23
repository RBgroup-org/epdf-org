# 15 — Mode A and Mode B: complete syntax reference

There is **one engine** and **one box tree**. You can drive it two ways:

| | **Mode A — markup** | **Mode B — programmatic** |
|---|---|---|
| Where layout lives | HTML/CSS you author | Java function calls |
| Page size / margins | CSS `@page { size; margin }` | `Document.of(PageSpec…).margins(…)` |
| Who decides layout | the document | your code |
| Entry class | `HtmlRenderer` / `RenderRequest.of(html)` | `Document` / `RenderRequest.of(document)` |
| Package to import | (author markup) | `com.epdfengine.rb.org.api.doc.*` |

Both converge on the **same box tree**, so layout, fonts, images, SVG, barcodes,
optimization and the PDF writer are **identical** — only the front end differs.

> These are not literal flags in the code — they are two ways to call the engine.
> You can even mix them (render Mode A HTML, then post-process with the toolkit).

---

## 1. Running the engine

Both modes submit a `RenderRequest` to the scalable `Epdf` engine facade (bounded
worker pool, backpressure, async, streaming, metrics). Create **one** engine and
reuse it; it is immutable and thread-safe.

```java
import com.epdfengine.rb.org.runtime.*;

try (Epdf engine = Epdf.create()) {                 // reuse across requests
    // Mode A
    RenderResult a = engine.render(RenderRequest.of(html).build());
    // Mode B
    RenderResult b = engine.render(RenderRequest.of(document).build());

    byte[] pdf = b.bytes();                          // buffered result
    System.out.println(b.byteCount() + " bytes in " + b.elapsedMillis() + "ms");
}
```

Other engine calls (identical for both modes):

```java
engine.submit(request);                              // CompletableFuture<RenderResult> (async)
engine.renderTo(request, outputStream);              // stream straight to a stream (no byte[])
engine.submitTo(request, outputStream);              // async streamed
engine.stats();                                      // active / queued / completed / failed
engine.runtimeStats().heapUsedRatio();               // load-shedding signal
```

`RenderRequest.Builder` options (both modes): `.fonts(FontRegistry)`,
`.conformance(Conformance)`, `.metadata(DocumentMetadata)`, `.limits(ResourceLimits)`.
Mode A also has `.params(LayoutParams)` and `.resources(ResourceLoader)`.

---

## 2. Page setup (size + margins)

**Mode A** — in CSS; the backend passes nothing:

```css
@page { size: A4; margin: 40pt; }          /* or: size: 210mm 297mm; margin: 36pt 48pt; */
@page { size: A4 landscape; }
```

**Mode B** — in Java:

```java
Document doc = Document.of(PageSpec.A4).margins(40);          // uniform
Document doc = Document.of(PageSpec.A4).margins(Edges.of(36, 48, 36, 48));
Document doc = Document.of(PageSpec.LETTER.landscape());
Document doc = Document.of(PageSpec.mm(210, 297));            // custom mm
Document doc = Document.of(PageSpec.of(600, 900));            // custom pt
```

`PageSpec`: `A4 A3 A5 LETTER LEGAL TABLOID`, `of(wPt,hPt)`, `mm(w,h)`, `.landscape()`, `.portrait()`.

---

## 3. Headings and paragraphs

| Feature | Mode A | Mode B |
|---|---|---|
| Heading | `<h1>Title</h1>` | `Paragraph.heading(1, "Title")` |
| Paragraph | `<p>Body</p>` | `Paragraph.of("Body")` |
| Font size | `font-size: 20pt` | `.fontSize(20)` |
| Bold / italic | `font-weight:bold` / `font-style:italic` | `.bold()` / `.italic()` |
| Colour | `color: #334155` | `.color(0x334155)` |
| Font family | `font-family: "Open Sans"` | `.font("Open Sans")` |
| Alignment | `text-align: right` | `.align(TextAlign.RIGHT)` |
| Line height | `line-height: 1.5` | `.lineHeight(1.5)` |

```java
doc.add(Paragraph.heading(1, "Invoice #42"))
   .add(Paragraph.of("Thank you for your business.").color(0x334155).align(TextAlign.START));
```

**Mixed inline formatting** (bold/coloured word inside a line, links):

```html
<p>Total is <strong>$42</strong> in <span style="color:#dc2626">red</span>
   — <a href="https://x.org">pay now</a>.</p>
```
```java
Paragraph.empty()
    .add(Text.of("Total is ")).add(Text.bold("$42"))
    .add(Text.of(" in ")).add(Text.colored("red", 0xDC2626))
    .add(Text.of(" — ")).add(Text.of("pay now").link("https://x.org"));
```

---

## 4. Containers, flexbox and grid

| Layout | Mode A | Mode B |
|---|---|---|
| Block container | `<div>…</div>` | `Div.create()` |
| Flex row | `display:flex; flex-direction:row` | `Div.row()` |
| Flex column | `display:flex; flex-direction:column` | `Div.column()` |
| Grid | `display:grid; grid-template-columns:1fr 1fr 1fr` | `Div.grid("1fr 1fr 1fr")` |
| Gap | `gap: 10px` | `.gap(10)` |
| Grow | `flex: 1 1 auto` | `.grow(1)` |
| Column span | `grid-column: span 2` | `.columnSpan(2)` |

```java
Div.grid("1fr 1fr 1fr").gap(10)
   .add(card("Revenue", "$128k"))
   .add(card("Orders",  "1,204"))
   .add(card("Refunds", "12"));
```

Shared style setters on every builder (`Div`, `Paragraph`, `Cell`, `Table`, …):
`.padding(pt|Edges)`, `.margin(pt|Edges)`, `.background(0xRRGGBB)`,
`.border(widthPt, 0xRRGGBB)`, `.borderRadius(pt)`, `.width(pt)`, `.widthPercent(%)`,
`.height(pt)`, `.maxWidth(pt)`.

---

## 5. Tables

**Mode A**

```html
<table>
  <thead><tr><th>Item</th><th>Qty</th><th>Amount</th></tr></thead>
  <tbody>
    <tr><td>Widget</td><td>2</td><td>$9.99</td></tr>
    <tr><td colspan="2">Total</td><td class="right">$9.99</td></tr>
  </tbody>
</table>
```

**Mode B**

```java
Table.columns("55%", "15%", "30%")          // per-column widths (% or pt)
    .header("Item", "Qty", "Amount")         // bold shaded header row
    .row("Widget", "2", "$9.99")             // data row
    .add(Row.create()                        // custom row: spans + alignment
        .add(Cell.of("Total").bold().colSpan(2))
        .add(Cell.of("$9.99").bold().align(TextAlign.RIGHT)));
```

`Cell`: `Cell.of(text)`, `Cell.empty().add(content)`, `.colSpan(n)`, `.rowSpan(n)`,
plus all shared style setters.

---

## 6. Lists

| | Mode A | Mode B |
|---|---|---|
| Bulleted | `<ul><li>…</li></ul>` | `ListBlock.bulleted().item("…")` |
| Numbered | `<ol><li>…</li></ol>` | `ListBlock.ordered().item("…")` |

```java
ListBlock.ordered().item("First").item("Second").item("Third");
ListBlock.bulleted().item(Paragraph.of("Rich item").bold());   // items can be any Content
```

---

## 7. Images, barcodes and SVG

| Asset | Mode A | Mode B |
|---|---|---|
| Raster image | `<img src="logo.png" width="120">` | `Image.of(bytes).width(120)` / `Image.fromFile(path)` |
| Alt text | `<img … alt="Logo">` | `Image.of(bytes).alt("Logo")` |
| QR / barcode | `<img src="barcode:qr?data=…">` (playground) | `Barcode.qr("…").width(90)` |
| SVG | inline `<svg>…</svg>` | `SvgGraphic.of("<svg>…</svg>")` |

```java
Barcode.qr("https://example.org/pay/INV-42").width(90).height(90);
Barcode.code128("INV-42-2026").width(200).height(60);   // also code39, ean13, dataMatrix, of(type,data)
SvgGraphic.of("<svg viewBox='0 0 120 40'><rect width='120' height='40' rx='8' fill='#2563eb'/></svg>")
          .width(120).height(40);
```

In Mode A, raster/QR/SVG `<img src>` are resolved through the `ResourceLoader` you
pass on the request; the playground maps `barcode:<type>?data=<value>` to a generated
PNG. In Mode B the engine encodes the barcode and decodes the image for you.

---

## 8. Colours, backgrounds and borders

| | Mode A | Mode B |
|---|---|---|
| Text colour | `color: #334155` | `.color(0x334155)` |
| Background | `background: #0f172a` | `.background(0x0F172A)` |
| Page background | `body { background: #f8fafc }` | `Document.…​.background(0xF8FAFC)` |
| Border | `border: 1pt solid #e2e8f0` | `.border(1, 0xE2E8F0)` |
| Rounded | `border-radius: 8px` | `.borderRadius(8)` |
| Gradient | `background: linear-gradient(90deg,#0f172a,#1e293b)` | *(Mode A only today)* |

Gradients, per-side border colours, box-shadow, backdrop-blur and CSS animations are
Mode A features (rich CSS). Mode B covers solid fills, borders and radius; drop to the
box-tree `ComputedStyle` via `builder.style()` for advanced properties if needed.

---

## 9. Fonts

Both modes embed and subset TrueType fonts through a `FontRegistry` (required for
PDF/A; otherwise the base-14 fonts + approximate metrics are used).

```java
FontRegistry fonts = new FontRegistry();
fonts.register("Open Sans", false, false, Files.readAllBytes(Path.of("OpenSans-Regular.ttf")));
fonts.register("Open Sans", true,  false, Files.readAllBytes(Path.of("OpenSans-Bold.ttf")));

// Mode A
engine.render(RenderRequest.of(html).fonts(fonts).build());
// Mode B
Document.of(PageSpec.A4).fonts(fonts) /* … */;
```

Reference a family with `font-family: "Open Sans"` (Mode A) or `.font("Open Sans")` (Mode B).

---

## 10. Accessible / archival PDF (PDF/A-2b + PDF/UA-1)

```java
import com.epdfengine.rb.org.pdfa.*;

DocumentMetadata meta = new DocumentMetadata()
        .title("Invoice 42").author("ACME").subject("Invoice").language("en-US");

// Mode A (richest tagging — structure comes from the HTML)
HtmlRenderer.ConformantResult r =
    new HtmlRenderer().renderConformant(html, LayoutParams.a4(36), fonts, loader,
                                        Conformance.PDF_A_2B_UA_1, meta);
boolean ok = r.isConformant();

// Mode B
Document.of(PageSpec.A4).fonts(fonts)
        .conformance(Conformance.PDF_A_2B_UA_1).metadata(meta);
```

> Tagging depth: Mode A derives the full structure tree from the HTML (headings,
> lists, tables, figures, links) and is the recommended path for **PDF/UA**. Mode B
> tags headings via `Paragraph.heading(n, …)`; embed fonts + metadata for **PDF/A**.

---

## 11. Enabling debugging

Debugging is **off by default**. Turn it on explicitly with the `Debug` helper — the
per-PDF pipeline trace (which stage ran, how long, where it failed) is written to a
destination **you choose** (log file or console), never inside the jar. Works the same
for both modes.

```java
import com.epdfengine.rb.org.debug.Debug;
import java.nio.file.Path;

// 1) trace to a log file
try (Epdf engine = Epdf.create(Debug.enabled(Path.of("logs/epdf-debug.log")))) {
    engine.render(RenderRequest.of(document).build());   // each job appends a trace
}

// 2) trace to the console (System.err)
Epdf engine = Epdf.create(Debug.enabledToConsole());

// 3) enable on an existing config
EngineConfig cfg = Debug.enableOn(EngineConfig.defaults(), Path.of("logs/epdf.log"));
```

A trace records each stage (`layout`, `serialize`, …) with timing, and `fail(e)` if a
stage throws — so you see exactly where a slow or broken render spent its time.

---

## 12. Manipulating existing PDFs (toolkit, both modes)

```java
byte[] merged    = PdfMerger.merge(a, b);                         // combine (or new PdfMerger().add(a).add(b).build())
byte[] page1     = PdfMerger.extractPages(pdf, 0);                // subset pages
byte[] compact   = PdfOptimizer.optimize(pdf);                    // obj/xref streams, dedup
byte[] fastWeb   = Linearizer.linearize(pdf);                     // fast web view
byte[] filled    = FormFiller.fill(pdf, Map.of("name", "Ada"));   // fill AcroForm
byte[] flat      = FormFiller.flatten(filled);                    // bake fields into content
byte[] redacted  = Redactor.apply(pdf, List.of(new Redactor.Box(0, 72, 700, 180, 16)));
```

---

## 13. Feature-coverage map (what to test where)

The two flagship playground templates exercise the new pipeline end-to-end; the older
templates cover specialised features.

| Feature | Mode A demo (`showcase-a`) | Mode B demo (`modeb-showcase`) | Also in |
|---|:---:|:---:|---|
| Page size / margins from source | ✓ (`@page`) | ✓ (`Document.of…margins`) | all |
| Gradient / dark header | ✓ | ✓ (solid) | `invoice`, `dashboard` |
| SVG logo (vector) | ✓ | ✓ | `report` |
| Grid stat cards | ✓ | ✓ | `neubento`, `dashboard` |
| Zebra / spanning table | ✓ | ✓ | `tablebig`, `compare` |
| Bulleted list | ✓ | ✓ | `docs` |
| QR + Code 128 barcode | ✓ | ✓ | `export-invoice` |
| Internal `#anchor` link | ✓ | — | `docs` |
| Mixed inline (bold/colour/link) | ✓ | ✓ | `testimonial` |
| Embedded fonts (subset) | ✓ | ✓ | `accessible` |
| PDF/A-2b + PDF/UA-1 | via `renderConformant` | via `.conformance(…)` | `accessible` |
| AcroForm fields | — | — | `form`, `modern-form`, `form-features` |
| Optimize / linearize / redact | toolkit | toolkit | PDF Tools panel |
| Async / streaming / metrics | ✓ | ✓ | engine facade |
| Debug tracing | ✓ | ✓ | `Debug.enabled(…)` |

Open the playground at **http://localhost:8080/** and pick **★ Mode A — Markup** or
**★ Mode B — Programmatic** to compare the same invoice built both ways.
