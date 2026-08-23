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
package com.epdfengine.rb.org.css;

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.common.Length;

/**
 * The resolved (computed) style of a single box: the property values the layout
 * and render stages consume. Both fronts populate it — the programmatic API
 * (Mode B) sets values directly, and the CSS cascade (Mode A) produces the same
 * object. Fluent setters return {@code this} for concise construction.
 *
 * <p>Box-model edges (margin/padding/border) are stored already resolved to
 * points; sizes (width/height) remain {@link Length} so percentages resolve
 * against the containing block at layout time.</p>
 */
public final class ComputedStyle {

    private Display display = Display.BLOCK;
    private boolean borderBoxSizing = false;      // false = content-box (CSS default)

    private Length width  = Length.AUTO;
    private Length height = Length.AUTO;
    private Length minWidth = Length.AUTO;
    private Length maxWidth = Length.AUTO;

    private Edges margin  = Edges.ZERO;
    private Edges padding = Edges.ZERO;
    private Edges border  = Edges.ZERO;           // border widths in pt

    private double fontSizePt = 12.0;
    private double lineHeightFactor = 1.2;        // multiple of font size
    private TextAlign textAlign = TextAlign.START;
    private WhiteSpace whiteSpace = WhiteSpace.NORMAL;
    private ListStyleType listStyleType = ListStyleType.DISC;
    private VerticalAlign verticalAlign = VerticalAlign.BASELINE;
    private boolean borderCollapse = false;       // table border model
    private double borderSpacingPt = 0;           // separate-borders cell spacing

    // Flex container properties.
    private FlexDirection flexDirection = FlexDirection.ROW;
    private boolean flexWrap = false;
    private JustifyContent justifyContent = JustifyContent.START;
    private AlignItems alignItems = AlignItems.STRETCH;
    private double columnGapPt = 0;
    private double rowGapPt = 0;
    private int columnCount = 1;                   // CSS multi-column: >1 flows block children into N columns
    // Flex item properties.
    private double flexGrow = 0;
    private double flexShrink = 1;
    private Length flexBasis = Length.AUTO;
    // Grid properties.
    private String gridTemplateColumns = null;    // raw track list (parsed at layout)
    private String gridTemplateRows = null;       // raw row track list
    private String gridAutoRows = null;           // implicit row size
    private String gridTemplateAreas = null;      // raw named-area rows
    private int gridColumnSpan = 1;               // number of columns this item spans
    private int gridRowSpan = 1;                  // number of rows this item spans
    private String gridArea = null;               // named area placement
    private boolean gridAutoFlowDense = false;    // grid-auto-flow: dense (backfill holes)
    private boolean gridAutoFlowColumn = false;   // grid-auto-flow: column (fill columns first)
    private AlignItems alignSelf = null;          // null = auto (use container align-items)

    private int colorRgb = 0x000000;              // text color
    private Integer backgroundRgb = null;         // null = transparent
    private double backgroundAlpha = 1.0;         // background opacity (rgba / #rrggbbaa)
    private double backdropBlurPt = 0;            // backdrop-filter: blur(); >0 = frosted glass
    private Integer borderColorRgb = null;        // null = use text color
    private Integer accentColorRgb = null;        // form control accent (checkbox/radio); null = default
    private String animationName = null;          // CSS animation-name -> @keyframes
    private double animationDurationMs = 0;       // CSS animation-duration
    private AnimFrame[] animFrames = null;        // sampled per-frame colour/opacity/transform
    private double animMs = 0;                     // total cycle duration for the frames
    private Integer borderColorTopRgb = null;     // per-side border colors (null = use borderColorRgb)
    private Integer borderColorRightRgb = null;
    private Integer borderColorBottomRgb = null;
    private Integer borderColorLeftRgb = null;
    private double borderRadiusTlPt = 0;          // per-corner radii (TL, TR, BR, BL)
    private double borderRadiusTrPt = 0;
    private double borderRadiusBrPt = 0;
    private double borderRadiusBlPt = 0;
    private BorderStyle borderStyle = BorderStyle.SOLID;
    private TextDecoration textDecoration = TextDecoration.NONE;  // inheritable
    private double letterSpacingPt = 0;           // inheritable
    private Shadow boxShadow = null;
    private Gradient backgroundGradient = null;   // non-null overrides backgroundRgb

    private String fontFamily = null;             // null = engine default (first registered)
    private boolean bold = false;
    private boolean italic = false;
    private String structRole = null;             // PDF/UA structure role (H1..H6, P, Figure); null = untagged
    private int structGroupId = -1;               // groups runs of one source block into one struct element
    private String anchorId = null;               // element id -> destination for #fragment links (not inherited)
    private boolean pageBreakBefore = false;      // force a new page before this box (paged mode only; not inherited)
    private String structAlt = null;              // alternate text for a Figure (image)
    private String linkHref = null;               // <a href> target (inheritable to child text runs)

    public ComputedStyle() {}

    // --- getters ---
    public Display display()            { return display; }
    public boolean isBorderBoxSizing()  { return borderBoxSizing; }
    public Length width()               { return width; }
    public Length height()              { return height; }
    public Length minWidth()            { return minWidth; }
    public Length maxWidth()            { return maxWidth; }
    public Edges margin()               { return margin; }
    public Edges padding()              { return padding; }
    public Edges border()               { return border; }
    public double fontSizePt()          { return fontSizePt; }
    public double lineHeightFactor()    { return lineHeightFactor; }
    public double lineHeightPt()        { return fontSizePt * lineHeightFactor; }
    public TextAlign textAlign()        { return textAlign; }
    public WhiteSpace whiteSpace()      { return whiteSpace; }
    public ListStyleType listStyleType(){ return listStyleType; }
    public VerticalAlign verticalAlign(){ return verticalAlign; }
    public boolean borderCollapse()     { return borderCollapse; }
    public double borderSpacingPt()     { return borderSpacingPt; }
    public FlexDirection flexDirection(){ return flexDirection; }
    public boolean flexWrap()           { return flexWrap; }
    public JustifyContent justifyContent() { return justifyContent; }
    public AlignItems alignItems()      { return alignItems; }
    public double columnGapPt()         { return columnGapPt; }
    public double rowGapPt()            { return rowGapPt; }
    public int columnCount()            { return columnCount; }
    public double flexGrow()            { return flexGrow; }
    public double flexShrink()          { return flexShrink; }
    public Length flexBasis()           { return flexBasis; }
    public String gridTemplateColumns() { return gridTemplateColumns; }
    public String gridTemplateRows()    { return gridTemplateRows; }
    public String gridAutoRows()        { return gridAutoRows; }
    public String gridTemplateAreas()   { return gridTemplateAreas; }
    public int gridColumnSpan()         { return gridColumnSpan; }
    public int gridRowSpan()            { return gridRowSpan; }
    public String gridArea()            { return gridArea; }
    public boolean gridAutoFlowDense()  { return gridAutoFlowDense; }
    public boolean gridAutoFlowColumn() { return gridAutoFlowColumn; }
    public AlignItems alignSelf()       { return alignSelf; }
    public int colorRgb()               { return colorRgb; }
    public Integer backgroundRgb()      { return backgroundRgb; }
    public double backgroundAlpha()     { return backgroundAlpha; }
    public double backdropBlurPt()      { return backdropBlurPt; }
    public Integer borderColorRgb()     { return borderColorRgb; }
    public Integer accentColorRgb()     { return accentColorRgb; }
    public String animationName()       { return animationName; }
    public double animationDurationMs() { return animationDurationMs; }
    public AnimFrame[] animFrames()     { return animFrames; }
    public double animMs()              { return animMs; }
    /** Effective border color for a side: per-side, else the shared border color, else the text color. */
    public int borderTopColor()    { return borderColorTopRgb != null ? borderColorTopRgb : (borderColorRgb != null ? borderColorRgb : colorRgb); }
    public int borderRightColor()  { return borderColorRightRgb != null ? borderColorRightRgb : (borderColorRgb != null ? borderColorRgb : colorRgb); }
    public int borderBottomColor() { return borderColorBottomRgb != null ? borderColorBottomRgb : (borderColorRgb != null ? borderColorRgb : colorRgb); }
    public int borderLeftColor()   { return borderColorLeftRgb != null ? borderColorLeftRgb : (borderColorRgb != null ? borderColorRgb : colorRgb); }
    public boolean hasPerSideBorderColors() { return borderColorTopRgb != null || borderColorRightRgb != null || borderColorBottomRgb != null || borderColorLeftRgb != null; }
    public double borderRadiusPt()      { return Math.max(Math.max(borderRadiusTlPt, borderRadiusTrPt), Math.max(borderRadiusBrPt, borderRadiusBlPt)); }
    public double borderRadiusTlPt()    { return borderRadiusTlPt; }
    public double borderRadiusTrPt()    { return borderRadiusTrPt; }
    public double borderRadiusBrPt()    { return borderRadiusBrPt; }
    public double borderRadiusBlPt()    { return borderRadiusBlPt; }
    public boolean hasBorderRadius()    { return borderRadiusTlPt > 0 || borderRadiusTrPt > 0 || borderRadiusBrPt > 0 || borderRadiusBlPt > 0; }
    public BorderStyle borderStyle()    { return borderStyle; }
    public TextDecoration textDecoration() { return textDecoration; }
    public double letterSpacingPt()     { return letterSpacingPt; }
    public String structRole()          { return structRole; }
    public int structGroupId()          { return structGroupId; }
    public String anchorId()            { return anchorId; }
    public boolean pageBreakBefore()    { return pageBreakBefore; }
    public String structAlt()           { return structAlt; }
    public String linkHref()            { return linkHref; }
    public ComputedStyle setStructRole(String r)   { this.structRole = r; return this; }
    public ComputedStyle setStructGroupId(int id)  { this.structGroupId = id; return this; }
    public ComputedStyle setAnchorId(String id)    { this.anchorId = id; return this; }
    public ComputedStyle pageBreakBefore(boolean v){ this.pageBreakBefore = v; return this; }
    public ComputedStyle setStructAlt(String a)    { this.structAlt = a; return this; }
    public ComputedStyle setLinkHref(String h)     { this.linkHref = h; return this; }
    public Shadow boxShadow()           { return boxShadow; }
    public Gradient backgroundGradient(){ return backgroundGradient; }
    public String fontFamily()          { return fontFamily; }
    public boolean bold()               { return bold; }
    public boolean italic()             { return italic; }

    // --- fluent setters ---
    public ComputedStyle display(Display v)        { this.display = v; return this; }
    public ComputedStyle borderBoxSizing(boolean v){ this.borderBoxSizing = v; return this; }
    public ComputedStyle width(Length v)           { this.width = v; return this; }
    public ComputedStyle height(Length v)          { this.height = v; return this; }
    public ComputedStyle minWidth(Length v)        { this.minWidth = v; return this; }
    public ComputedStyle maxWidth(Length v)        { this.maxWidth = v; return this; }
    public ComputedStyle margin(Edges v)           { this.margin = v; return this; }
    public ComputedStyle padding(Edges v)          { this.padding = v; return this; }
    public ComputedStyle border(Edges v)           { this.border = v; return this; }
    public ComputedStyle fontSizePt(double v)      { this.fontSizePt = v; return this; }
    public ComputedStyle lineHeightFactor(double v){ this.lineHeightFactor = v; return this; }
    public ComputedStyle textAlign(TextAlign v)    { this.textAlign = v; return this; }
    public ComputedStyle whiteSpace(WhiteSpace v)  { this.whiteSpace = v; return this; }
    public ComputedStyle listStyleType(ListStyleType v) { this.listStyleType = v; return this; }
    public ComputedStyle verticalAlign(VerticalAlign v) { this.verticalAlign = v; return this; }
    public ComputedStyle borderCollapse(boolean v) { this.borderCollapse = v; return this; }
    public ComputedStyle borderSpacingPt(double v) { this.borderSpacingPt = v; return this; }
    public ComputedStyle flexDirection(FlexDirection v) { this.flexDirection = v; return this; }
    public ComputedStyle flexWrap(boolean v)       { this.flexWrap = v; return this; }
    public ComputedStyle justifyContent(JustifyContent v) { this.justifyContent = v; return this; }
    public ComputedStyle alignItems(AlignItems v)  { this.alignItems = v; return this; }
    public ComputedStyle columnGapPt(double v)     { this.columnGapPt = v; return this; }
    public ComputedStyle rowGapPt(double v)        { this.rowGapPt = v; return this; }
    public ComputedStyle columnCount(int v)        { this.columnCount = Math.max(1, v); return this; }
    public ComputedStyle flexGrow(double v)        { this.flexGrow = v; return this; }
    public ComputedStyle flexShrink(double v)      { this.flexShrink = v; return this; }
    public ComputedStyle flexBasis(Length v)       { this.flexBasis = v; return this; }
    public ComputedStyle gridTemplateColumns(String v) { this.gridTemplateColumns = v; return this; }
    public ComputedStyle gridTemplateRows(String v) { this.gridTemplateRows = v; return this; }
    public ComputedStyle gridAutoRows(String v)    { this.gridAutoRows = v; return this; }
    public ComputedStyle gridTemplateAreas(String v) { this.gridTemplateAreas = v; return this; }
    public ComputedStyle gridColumnSpan(int v)     { this.gridColumnSpan = v; return this; }
    public ComputedStyle gridRowSpan(int v)        { this.gridRowSpan = v; return this; }
    public ComputedStyle gridArea(String v)        { this.gridArea = v; return this; }
    public ComputedStyle gridAutoFlowDense(boolean v) { this.gridAutoFlowDense = v; return this; }
    public ComputedStyle gridAutoFlowColumn(boolean v) { this.gridAutoFlowColumn = v; return this; }
    public ComputedStyle alignSelf(AlignItems v)   { this.alignSelf = v; return this; }
    public ComputedStyle colorRgb(int v)           { this.colorRgb = v; return this; }
    public ComputedStyle backgroundRgb(Integer v)  { this.backgroundRgb = v; return this; }
    public ComputedStyle backgroundAlpha(double v) { this.backgroundAlpha = v; return this; }
    public ComputedStyle backdropBlurPt(double v)  { this.backdropBlurPt = v; return this; }
    public ComputedStyle borderColorRgb(Integer v) { this.borderColorRgb = v; return this; }
    public ComputedStyle accentColorRgb(Integer v) { this.accentColorRgb = v; return this; }
    public ComputedStyle animationName(String v)   { this.animationName = v; return this; }
    public ComputedStyle animationDurationMs(double v) { this.animationDurationMs = v; return this; }
    public ComputedStyle animFrames(AnimFrame[] frames, double ms) { this.animFrames = frames; this.animMs = ms; return this; }
    public ComputedStyle borderColorTop(Integer v)    { this.borderColorTopRgb = v; return this; }
    public ComputedStyle borderColorRight(Integer v)  { this.borderColorRightRgb = v; return this; }
    public ComputedStyle borderColorBottom(Integer v) { this.borderColorBottomRgb = v; return this; }
    public ComputedStyle borderColorLeft(Integer v)   { this.borderColorLeftRgb = v; return this; }
    public ComputedStyle borderRadiusPt(double v)  { this.borderRadiusTlPt = this.borderRadiusTrPt = this.borderRadiusBrPt = this.borderRadiusBlPt = v; return this; }
    public ComputedStyle borderRadii(double tl, double tr, double br, double bl) { this.borderRadiusTlPt = tl; this.borderRadiusTrPt = tr; this.borderRadiusBrPt = br; this.borderRadiusBlPt = bl; return this; }
    public ComputedStyle borderStyle(BorderStyle v) { this.borderStyle = (v != null) ? v : BorderStyle.SOLID; return this; }
    public ComputedStyle textDecoration(TextDecoration v) { this.textDecoration = (v != null) ? v : TextDecoration.NONE; return this; }
    public ComputedStyle letterSpacingPt(double v) { this.letterSpacingPt = v; return this; }
    public ComputedStyle boxShadow(Shadow v)       { this.boxShadow = v; return this; }
    public ComputedStyle backgroundGradient(Gradient v) { this.backgroundGradient = v; return this; }
    public ComputedStyle fontFamily(String v)      { this.fontFamily = v; return this; }
    public ComputedStyle bold(boolean v)           { this.bold = v; return this; }
    public ComputedStyle italic(boolean v)         { this.italic = v; return this; }

    /**
     * A copy carrying only the CSS-inheritable properties (font, color, text
     * alignment, line height) applied onto fresh defaults. Non-inheritable box
     * properties (display, size, margins, padding, border, background) reset.
     */
    public ComputedStyle inheritedCopy() {
        ComputedStyle c = new ComputedStyle();
        c.fontSizePt = this.fontSizePt;
        c.lineHeightFactor = this.lineHeightFactor;
        c.textAlign = this.textAlign;
        c.whiteSpace = this.whiteSpace;
        c.listStyleType = this.listStyleType;
        c.borderCollapse = this.borderCollapse;
        c.borderSpacingPt = this.borderSpacingPt;
        c.textDecoration = this.textDecoration;
        c.letterSpacingPt = this.letterSpacingPt;
        c.colorRgb = this.colorRgb;
        c.fontFamily = this.fontFamily;
        c.bold = this.bold;
        c.italic = this.italic;
        c.structRole = this.structRole;           // inline runs stay in their block's tag group
        c.structGroupId = this.structGroupId;
        c.linkHref = this.linkHref;                // <a> descendants inherit the link target
        return c;
    }
}
