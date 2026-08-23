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
package com.epdfengine.rb.org.multimedia;

/**
 * Builds document JavaScript that animates a PDF by cycling the visibility of
 * Optional Content Groups (layers) on a timer. Each layer is one animation
 * frame; the timer makes exactly one frame visible at a time, producing a
 * flip-book animation.
 *
 * <p><b>Viewer support:</b> motion requires a PDF viewer that runs document
 * JavaScript (Adobe Acrobat / Reader). Viewers without a JS engine (Chrome,
 * Edge, Firefox, Preview) show only the default-visible frame — so authors
 * should make frame&nbsp;0 a sensible static poster.</p>
 */
public final class PdfAnimation {

    private PdfAnimation() {}

    /**
     * Returns an OpenAction script that cycles every Optional Content Group whose
     * name starts with {@code framePrefix} (sorted by name) every
     * {@code intervalMs} milliseconds. Frame layers should be named e.g.
     * {@code frame00, frame01, ...} so lexical order matches playback order.
     */
    public static String frameCycleScript(String framePrefix, int intervalMs) {
        String p = framePrefix == null ? "frame" : framePrefix;
        return ""
            + "var _ocgs = this.getOCGs();\n"
            + "var _frames = [];\n"
            + "if (_ocgs) { for (var i = 0; i < _ocgs.length; i++) {\n"
            + "  if (_ocgs[i].name && _ocgs[i].name.indexOf('" + p + "') == 0) _frames.push(_ocgs[i]);\n"
            + "} }\n"
            + "_frames.sort(function(a, b){ return a.name < b.name ? -1 : (a.name > b.name ? 1 : 0); });\n"
            + "var _idx = 0;\n"
            + "function _epdfAnimStep() {\n"
            + "  for (var i = 0; i < _frames.length; i++) _frames[i].state = (i == _idx);\n"
            + "  _idx = (_idx + 1) % _frames.length;\n"
            + "}\n"
            + "if (_frames.length > 1) { _epdfAnimStep(); this._epdfAnimTimer = app.setInterval('_epdfAnimStep()', "
            + Math.max(50, intervalMs) + "); }\n";
    }

    /** A frame layer name padded so lexical order matches playback order (frame00, frame01, ...). */
    public static String frameName(int index) {
        return String.format("frame%02d", index);
    }

    /**
     * Returns an OpenAction script for HTML {@code @keyframes} animations. All OCGs
     * named {@code epdfanim.<elem>.f<k>} are grouped by their frame index {@code k};
     * every tick shows the frame-{@code idx} layer of each element and hides the rest,
     * so multiple animated elements stay in sync. Runs only in Adobe Acrobat/Reader.
     */
    public static String bgFrameCycleScript(int intervalMs) {
        return ""
            + "var _o = this.getOCGs();\n"
            + "var _g = {}; var _max = 0;\n"
            + "if (_o) { for (var i = 0; i < _o.length; i++) {\n"
            + "  var nm = _o[i].name;\n"
            + "  if (nm && nm.indexOf('epdfanim.') == 0) {\n"
            + "    var p = nm.lastIndexOf('.f');\n"
            + "    var f = parseInt(nm.substring(p + 2));\n"
            + "    if (!isNaN(f)) { if (!_g[f]) _g[f] = []; _g[f].push(_o[i]); if (f > _max) _max = f; }\n"
            + "  }\n"
            + "} }\n"
            + "var _idx = 0; var _N = _max + 1;\n"
            + "function _epdfAnimStep() {\n"
            + "  for (var f = 0; f < _N; f++) { var a = _g[f] || []; for (var j = 0; j < a.length; j++) a[j].state = (f == _idx); }\n"
            + "  _idx = (_idx + 1) % _N;\n"
            + "}\n"
            + "if (_N > 1) { _epdfAnimStep(); this._epdfAnimTimer = app.setInterval('_epdfAnimStep()', "
            + Math.max(50, intervalMs) + "); }\n";
    }
}
