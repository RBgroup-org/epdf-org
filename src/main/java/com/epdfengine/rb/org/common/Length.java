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
package com.epdfengine.rb.org.common;

/**
 * A CSS length value with its unit, resolvable to PostScript points (1pt = 1/72in).
 * Relative units (em/rem/%) require a resolution context; absolute units do not.
 */
public final class Length {

    public enum Unit { PT, PX, MM, CM, IN, EM, REM, PERCENT, AUTO }

    public static final Length AUTO = new Length(0, Unit.AUTO);
    public static final Length ZERO = new Length(0, Unit.PT);

    private final double value;
    private final Unit unit;

    private Length(double value, Unit unit) {
        this.value = value;
        this.unit = unit;
    }

    public static Length of(double value, Unit unit) { return new Length(value, unit); }
    public static Length pt(double v)      { return new Length(v, Unit.PT); }
    public static Length px(double v)      { return new Length(v, Unit.PX); }
    public static Length mm(double v)      { return new Length(v, Unit.MM); }
    public static Length cm(double v)      { return new Length(v, Unit.CM); }
    public static Length inches(double v)  { return new Length(v, Unit.IN); }
    public static Length em(double v)      { return new Length(v, Unit.EM); }
    public static Length rem(double v)     { return new Length(v, Unit.REM); }
    public static Length percent(double v) { return new Length(v, Unit.PERCENT); }

    public double value()     { return value; }
    public Unit   unit()      { return unit; }
    public boolean isAuto()   { return unit == Unit.AUTO; }
    public boolean isPercent(){ return unit == Unit.PERCENT; }

    /** Absolute conversion; throws for em/rem/%/auto which need a context. */
    public double toPt() {
        return switch (unit) {
            case PT -> value;
            case PX -> value * 0.75;          // 1px = 1/96in = 0.75pt
            case IN -> value * 72.0;
            case CM -> value * 72.0 / 2.54;
            case MM -> value * 72.0 / 25.4;
            default -> throw new IllegalStateException("Relative length needs context: " + unit);
        };
    }

    /**
     * Full resolution to points.
     *
     * @param emPt          current font size in pt (for em)
     * @param remPt         root font size in pt (for rem)
     * @param percentBasisPt basis for percentage values in pt
     * @return resolved points, or {@code NaN} for {@link Unit#AUTO}
     */
    public double resolve(double emPt, double remPt, double percentBasisPt) {
        return switch (unit) {
            case PT -> value;
            case PX -> value * 0.75;
            case IN -> value * 72.0;
            case CM -> value * 72.0 / 2.54;
            case MM -> value * 72.0 / 25.4;
            case EM -> value * emPt;
            case REM -> value * remPt;
            case PERCENT -> value / 100.0 * percentBasisPt;
            case AUTO -> Double.NaN;
        };
    }

    @Override public String toString() {
        return isAuto() ? "auto" : value + unit.name().toLowerCase();
    }
}
