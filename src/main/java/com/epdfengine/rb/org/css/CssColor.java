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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses CSS colour values to a packed {@code 0xRRGGBB} integer, or {@code null}
 * when the value is {@code none}/{@code transparent}/{@code inherit} or cannot be
 * parsed. Supports the full CSS named-colour table, {@code #rgb}, {@code #rrggbb},
 * {@code #rrggbbaa} (alpha ignored), and {@code rgb(...)}/{@code rgba(...)}.
 */
public final class CssColor {

    private CssColor() {}

    private static final Map<String, Integer> NAMED = new HashMap<>();
    static {
        // CSS named colours (extended set).
        String[][] c = {
            {"black","000000"},{"silver","C0C0C0"},{"gray","808080"},{"grey","808080"},
            {"white","FFFFFF"},{"maroon","800000"},{"red","FF0000"},{"purple","800080"},
            {"fuchsia","FF00FF"},{"magenta","FF00FF"},{"green","008000"},{"lime","00FF00"},
            {"olive","808000"},{"yellow","FFFF00"},{"navy","000080"},{"blue","0000FF"},
            {"teal","008080"},{"aqua","00FFFF"},{"cyan","00FFFF"},{"orange","FFA500"},
            {"aliceblue","F0F8FF"},{"antiquewhite","FAEBD7"},{"aquamarine","7FFFD4"},
            {"azure","F0FFFF"},{"beige","F5F5DC"},{"bisque","FFE4C4"},{"blanchedalmond","FFEBCD"},
            {"blueviolet","8A2BE2"},{"brown","A52A2A"},{"burlywood","DEB887"},{"cadetblue","5F9EA0"},
            {"chartreuse","7FFF00"},{"chocolate","D2691E"},{"coral","FF7F50"},
            {"cornflowerblue","6495ED"},{"cornsilk","FFF8DC"},{"crimson","DC143C"},
            {"darkblue","00008B"},{"darkcyan","008B8B"},{"darkgoldenrod","B8860B"},
            {"darkgray","A9A9A9"},{"darkgrey","A9A9A9"},{"darkgreen","006400"},
            {"darkkhaki","BDB76B"},{"darkmagenta","8B008B"},{"darkolivegreen","556B2F"},
            {"darkorange","FF8C00"},{"darkorchid","9932CC"},{"darkred","8B0000"},
            {"darksalmon","E9967A"},{"darkseagreen","8FBC8F"},{"darkslateblue","483D8B"},
            {"darkslategray","2F4F4F"},{"darkslategrey","2F4F4F"},{"darkturquoise","00CED1"},
            {"darkviolet","9400D3"},{"deeppink","FF1493"},{"deepskyblue","00BFFF"},
            {"dimgray","696969"},{"dimgrey","696969"},{"dodgerblue","1E90FF"},
            {"firebrick","B22222"},{"floralwhite","FFFAF0"},{"forestgreen","228B22"},
            {"gainsboro","DCDCDC"},{"ghostwhite","F8F8FF"},{"gold","FFD700"},
            {"goldenrod","DAA520"},{"greenyellow","ADFF2F"},{"honeydew","F0FFF0"},
            {"hotpink","FF69B4"},{"indianred","CD5C5C"},{"indigo","4B0082"},{"ivory","FFFFF0"},
            {"khaki","F0E68C"},{"lavender","E6E6FA"},{"lavenderblush","FFF0F5"},
            {"lawngreen","7CFC00"},{"lemonchiffon","FFFACD"},{"lightblue","ADD8E6"},
            {"lightcoral","F08080"},{"lightcyan","E0FFFF"},{"lightgoldenrodyellow","FAFAD2"},
            {"lightgray","D3D3D3"},{"lightgrey","D3D3D3"},{"lightgreen","90EE90"},
            {"lightpink","FFB6C1"},{"lightsalmon","FFA07A"},{"lightseagreen","20B2AA"},
            {"lightskyblue","87CEFA"},{"lightslategray","778899"},{"lightslategrey","778899"},
            {"lightsteelblue","B0C4DE"},{"lightyellow","FFFFE0"},{"limegreen","32CD32"},
            {"linen","FAF0E6"},{"mediumaquamarine","66CDAA"},{"mediumblue","0000CD"},
            {"mediumorchid","BA55D3"},{"mediumpurple","9370DB"},{"mediumseagreen","3CB371"},
            {"mediumslateblue","7B68EE"},{"mediumspringgreen","00FA9A"},
            {"mediumturquoise","48D1CC"},{"mediumvioletred","C71585"},{"midnightblue","191970"},
            {"mintcream","F5FFFA"},{"mistyrose","FFE4E1"},{"moccasin","FFE4B5"},
            {"navajowhite","FFDEAD"},{"oldlace","FDF5E6"},{"olivedrab","6B8E23"},
            {"orangered","FF4500"},{"orchid","DA70D6"},{"palegoldenrod","EEE8AA"},
            {"palegreen","98FB98"},{"paleturquoise","AFEEEE"},{"palevioletred","DB7093"},
            {"papayawhip","FFEFD5"},{"peachpuff","FFDAB9"},{"peru","CD853F"},{"pink","FFC0CB"},
            {"plum","DDA0DD"},{"powderblue","B0E0E6"},{"rosybrown","BC8F8F"},
            {"royalblue","4169E1"},{"saddlebrown","8B4513"},{"salmon","FA8072"},
            {"sandybrown","F4A460"},{"seagreen","2E8B57"},{"seashell","FFF5EE"},
            {"sienna","A0522D"},{"skyblue","87CEEB"},{"slateblue","6A5ACD"},
            {"slategray","708090"},{"slategrey","708090"},{"snow","FFFAFA"},
            {"springgreen","00FF7F"},{"steelblue","4682B4"},{"tan","D2B48C"},
            {"thistle","D8BFD8"},{"tomato","FF6347"},{"turquoise","40E0D0"},{"violet","EE82EE"},
            {"wheat","F5DEB3"},{"whitesmoke","F5F5F5"},{"yellowgreen","9ACD32"},
            {"rebeccapurple","663399"}
        };
        for (String[] e : c) NAMED.put(e[0], Integer.parseInt(e[1], 16));
    }

    /** @return packed 0xRRGGBB, or {@code null} for none/transparent/unparseable. */
    public static Integer parse(String value) {
        if (value == null) return null;
        String s = value.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("none") || s.equals("transparent") || s.equals("inherit")
                || s.equals("currentcolor")) {
            return null;
        }
        if (s.startsWith("#")) return parseHex(s.substring(1));
        if (s.startsWith("rgb")) return parseRgb(s);
        return NAMED.get(s);
    }

    private static Integer parseHex(String h) {
        try {
            if (h.length() == 3) {
                int r = Integer.parseInt(h.substring(0, 1), 16);
                int g = Integer.parseInt(h.substring(1, 2), 16);
                int b = Integer.parseInt(h.substring(2, 3), 16);
                return (r * 17 << 16) | (g * 17 << 8) | (b * 17);
            }
            if (h.length() == 6) return Integer.parseInt(h, 16);
            if (h.length() == 8) return Integer.parseInt(h.substring(0, 6), 16); // #rrggbbaa, drop alpha
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private static Integer parseRgb(String s) {
        int open = s.indexOf('(');
        int close = s.indexOf(')');
        if (open < 0 || close < 0 || close < open) return null;
        String[] parts = s.substring(open + 1, close).replace("%", "").split(",");
        if (parts.length < 3) return null;
        try {
            int r = clamp((int) Math.round(Double.parseDouble(parts[0].trim())));
            int g = clamp((int) Math.round(Double.parseDouble(parts[1].trim())));
            int b = clamp((int) Math.round(Double.parseDouble(parts[2].trim())));
            return (r << 16) | (g << 8) | b;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** @return packed 0xAARRGGBB (alpha in the high byte, 0xFF when opaque), or {@code null}. */
    public static Integer parseArgb(String value) {
        if (value == null) return null;
        String s = value.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("none") || s.equals("inherit") || s.equals("currentcolor")) return null;
        if (s.equals("transparent")) return 0x00000000;
        if (s.startsWith("#")) return parseHexArgb(s.substring(1));
        if (s.startsWith("rgb")) return parseRgbArgb(s);
        Integer named = NAMED.get(s);
        return named == null ? null : (0xFF000000 | named);
    }

    /** The alpha (0..1) of a packed 0xAARRGGBB value. */
    public static double alphaOf(int argb) { return ((argb >>> 24) & 0xFF) / 255.0; }

    private static Integer parseHexArgb(String h) {
        try {
            if (h.length() == 3) { Integer rgb = parseHex(h); return rgb == null ? null : (0xFF000000 | rgb); }
            if (h.length() == 4) {
                int r = Integer.parseInt(h.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(h.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(h.substring(2, 3), 16) * 17;
                int a = Integer.parseInt(h.substring(3, 4), 16) * 17;
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
            if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
            if (h.length() == 8) {
                int rr = Integer.parseInt(h.substring(0, 2), 16);
                int gg = Integer.parseInt(h.substring(2, 4), 16);
                int bb = Integer.parseInt(h.substring(4, 6), 16);
                int aa = Integer.parseInt(h.substring(6, 8), 16);
                return (aa << 24) | (rr << 16) | (gg << 8) | bb;
            }
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private static Integer parseRgbArgb(String s) {
        int open = s.indexOf('('), close = s.indexOf(')');
        if (open < 0 || close < 0 || close < open) return null;
        String inner = s.substring(open + 1, close).replace("%", "").trim();
        String[] parts = inner.split("[\\s,/]+");
        if (parts.length < 3) return null;
        try {
            int r = clamp((int) Math.round(Double.parseDouble(parts[0].trim())));
            int g = clamp((int) Math.round(Double.parseDouble(parts[1].trim())));
            int b = clamp((int) Math.round(Double.parseDouble(parts[2].trim())));
            int a = 255;
            if (parts.length >= 4) {
                double av = Double.parseDouble(parts[3].trim());
                a = clamp((int) Math.round(av <= 1.0 ? av * 255 : av));
            }
            return (a << 24) | (r << 16) | (g << 8) | b;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
