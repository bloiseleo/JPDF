package org.faceless.pdf2.viewer2;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import java.net.*;
import javax.swing.plaf.*;

/**
 * Load UI defaults from properties files. This is a hugely cut down and modified
 * version of the same class from the <a href="https://www.formdev.com/flatlaf">FlatLAF</a>
 * FlatLAF, and uses the same syntax as described at
 * <a href="https://www.formdev.com/flatlaf/properties-files/">https://www.formdev.com/flatlaf/properties-files/</a>.
 */
class UIDefaultsLoader {

    private enum ValueType { UNKNOWN, STRING, BOOLEAN, CHARACTER, INTEGER, FLOAT, ICON, INSETS, DIMENSION, COLOR, SCALEDINTEGER, SCALEDFLOAT, SCALEDINSETS, SCALEDDIMENSION, NULL }

    public static void install(String name) {
        ResourceBundle bundle = ResourceBundle.getBundle(name);
        Map<String,String> variables = new HashMap<String,String>();
        for (String key : bundle.keySet()) {
            if (key.charAt(0) == '%') {
                variables.put(key.substring(1), bundle.getString(key));
            }
        }
        for (String key : bundle.keySet()) {
            if (key.charAt(0) != '%') {
                Object o = resolve(key, bundle.getString(key), null, variables);
                if (o != null) {
//                    System.out.println("PUT "+key+" = "+o);
                    UIManager.put(key, o);
                }
            }
        }
    }

    private static Object resolve(String key, String value, ValueType[] resultValueType, Map<String,String> variables) {
        if (resultValueType == null) {
            resultValueType = new ValueType[1];
        }
        value = value.trim();
        if (value.startsWith("$")) {
            // eg TableHeader.separatorColor = darken($TableHeader.background,10%)
            final String subkey = value.substring(1);
            return new UIDefaults.LazyValue() {
                public Object createValue(UIDefaults table) {
                    return UIManager.get(subkey);
                }
            };
        } else if (value.startsWith("@")) {
            // eg @background = #f2f2f2
            //    Table.background = @background
            value = variables.get(value.substring(1));
            if (value == null) {
                return null;
            }
        }

        // null, false, true
        if (value.equals("null")) {
            resultValueType[0] = ValueType.NULL; return null;
        } else if (value.equals("true")) {
            resultValueType[0] = ValueType.BOOLEAN; return true;
        } else if (value.equals("false")) {
            resultValueType[0] = ValueType.BOOLEAN; return false;
        }

        ValueType valueType = ValueType.UNKNOWN;

        // check whether value type is specified in the value
        if (value.startsWith("#")) {
            valueType = ValueType.COLOR;
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            valueType = ValueType.STRING;
            value = value.substring(1, value.length() - 1);
        } else if (value.startsWith("{")) {
            int end = value.indexOf("}");
            if (end != -1) {
                try {
                    String typeStr = value.substring(1, end);
                    valueType = ValueType.valueOf(typeStr.toUpperCase(Locale.ENGLISH));
                    value = value.substring(end + 1);
                } catch (IllegalArgumentException ex) { }
            }
        }

        // determine value type from key
        if (valueType == ValueType.UNKNOWN && key != null) {
            if (key.endsWith("UI")) {
                valueType = ValueType.STRING;
            } else if (key.endsWith("Color") || (key.endsWith("ground") && (key.endsWith(".background") || key.endsWith("Background") || key.endsWith(".foreground") || key.endsWith("Foreground")))) {
                valueType = ValueType.COLOR;
            } else if (key.endsWith(".icon") || key.endsWith("Icon")) {
                valueType = ValueType.ICON;
            } else if (key.endsWith(".margin") || key.endsWith(".padding") || key.endsWith("Margins") || key.endsWith("Insets")) {
                valueType = ValueType.INSETS;
            } else if (key.endsWith("Size")) {
                valueType = ValueType.DIMENSION;
            } else if (key.endsWith("Width") || key.endsWith("Height")) {
                valueType = ValueType.INTEGER;
            } else if (key.endsWith("Char")) {
                valueType = ValueType.CHARACTER;
            }
        }

        resultValueType[0] = valueType;

        // parse value
        switch (valueType) {
            case STRING:        return value;
            case CHARACTER:     return parseCharacter(value);
            case INTEGER:       return parseInteger(value, true);
            case FLOAT:         return parseFloat(value, true);
            case ICON:          return parseIcon(value);
            case INSETS:        return parseInsets(value);
            case DIMENSION:     return parseDimension(value);
            case COLOR:         return parseColorOrFunction(value, variables, true);
            case SCALEDINTEGER: return parseInteger(value, true);
            case SCALEDFLOAT:   return parseFloat(value, true);
            case SCALEDINSETS:  return parseInsets(value);
            case SCALEDDIMENSION:return parseDimension(value);
            case UNKNOWN:
            default:
                // colors
                Object color = parseColorOrFunction(value, variables, false);
                if (color != null) {
                    resultValueType[0] = ValueType.COLOR;
                    return color;
                }

                // integer
                Integer integer = parseInteger(value, false);
                if (integer != null) {
                    resultValueType[0] = ValueType.INTEGER;
                    return integer;
                }

                // float
                Float f = parseFloat(value, false);
                if (f != null) {
                    resultValueType[0] = ValueType.FLOAT;
                    return f;
                }

                // string
                resultValueType[0] = ValueType.STRING;
                return value;
        }
    }

    private static Insets parseInsets(String value) {
        List<String> numbers = split(value, ',');
        try {
            return new InsetsUIResource(
                Integer.parseInt(numbers.get(0)),
                Integer.parseInt(numbers.get(1)),
                Integer.parseInt(numbers.get(2)),
                Integer.parseInt(numbers.get(3)));
        } catch(NumberFormatException ex) {
            throw new IllegalArgumentException("invalid insets '" + value + "'");
        }
    }

    private static Dimension parseDimension(String value) {
        List<String> numbers = split(value, ',');
        try {
            return new DimensionUIResource(
                Integer.parseInt(numbers.get(0)),
                Integer.parseInt(numbers.get(1)));
        } catch(NumberFormatException ex) {
            throw new IllegalArgumentException("invalid size '" + value + "'");
        }
    }

    private static Object parseColorOrFunction(String value, Map<String,String> variables, boolean reportError) {
        if (value.endsWith(")")) {
            return parseColorFunctions(value, variables, reportError);
        }
        return parseColor(value, reportError);
    }

    static ColorUIResource parseColor(String value) {
        return parseColor(value, false);
    }

    private static ColorUIResource parseColor(String value, boolean reportError) {
        try {
            int rgba = parseColorRGBA(value);
            return ((rgba & 0xff000000) == 0xff000000) ? new ColorUIResource(rgba) : new ColorUIResource(new Color(rgba, true));
        } catch (IllegalArgumentException ex) {
            if (reportError) {
                throw new IllegalArgumentException("invalid color '" + value + "'");
            }
        }
        return null;
    }

    /**
     * Parses a hex color in  {@code #RGB}, {@code #RGBA}, {@code #RRGGBB} or {@code #RRGGBBAA}
     * format and returns it as {@code rgba} integer suitable for {@link java.awt.Color},
     * which includes alpha component in bits 24-31.
     *
     * @throws IllegalArgumentException
     */
    private static int parseColorRGBA(String value) {
        int len = value.length();
        if ((len != 4 && len != 5 && len != 7 && len != 9) || value.charAt(0) != '#') {
            throw new IllegalArgumentException();
        }

        // parse hex
        int n = 0;
        for (int i = 1; i < len; i++) {
            char ch = value.charAt(i);
            int digit;
            if (ch >= '0' && ch <= '9') {
                digit = ch - '0';
            } else if (ch >= 'a' && ch <= 'f') {
                digit = ch - 'a' + 10;
            } else if (ch >= 'A' && ch <= 'F') {
                digit = ch - 'A' + 10;
            } else {
                throw new IllegalArgumentException();
            }
            n = (n << 4) | digit;
        }

        if (len <= 5) {
            // double nibbles
            int n1 = n & 0xf000;
            int n2 = n & 0xf00;
            int n3 = n & 0xf0;
            int n4 = n & 0xf;
            n = (n1 << 16) | (n1 << 12) | (n2 << 12) | (n2 << 8) | (n3 << 8) | (n3 << 4) | (n4 << 4) | n4;
        }

        return (len == 4 || len == 7)
            ? (0xff000000 | n) // set alpha to 255
            : (((n >> 8) & 0xffffff) | ((n & 0xff) << 24)); // move alpha from lowest to highest byte
    }

    private static Object parseColorFunctions(String value, Map<String,String> variables, boolean reportError) {
        int paramsStart = value.indexOf('(');
        if (paramsStart < 0) {
            if (reportError) {
                throw new IllegalArgumentException("missing opening parenthesis in function '" + value + "'");
            }
            return null;
        }

        String function = value.substring(0, paramsStart).trim();
        List<String> params = splitFunctionParams(value.substring(paramsStart + 1, value.length() - 1), ',');
        if (params.isEmpty()) {
            throw new IllegalArgumentException("missing parameters in function '" + value + "'");
        }

        if (function.equals("rgb")) return parseColorRgbOrRgba(false, params, variables, reportError);
        if (function.equals("rgba")) return parseColorRgbOrRgba(true, params, variables, reportError);
        if (function.equals("hsl")) return parseColorHslOrHsla(false, params);
        if (function.equals("hsla")) return parseColorHslOrHsla(true, params);
        if (function.equals("lighten")) return parseColorHSLIncreaseDecrease(2, true, params, variables, reportError);
        if (function.equals("darken")) return parseColorHSLIncreaseDecrease(2, false, params, variables, reportError);
        if (function.equals("saturate")) return parseColorHSLIncreaseDecrease(1, true, params, variables, reportError);
        if (function.equals("desaturate")) return parseColorHSLIncreaseDecrease(1, false, params, variables, reportError);
        if (function.equals("fadein")) return parseColorHSLIncreaseDecrease(3, true, params, variables, reportError);
        if (function.equals("fadeout")) return parseColorHSLIncreaseDecrease(3, false, params, variables, reportError);
        if (function.equals("fade")) return parseColorFade(params, variables, reportError);
        if (function.equals("spin")) return parseColorSpin(params, variables, reportError);
        throw new IllegalArgumentException("unknown color function '" + value + "'");
    }

    /**
     * Syntax: rgb(red,green,blue) or rgba(red,green,blue,alpha)
     *   - red:   an integer 0-255 or a percentage 0-100%
     *   - green: an integer 0-255 or a percentage 0-100%
     *   - blue:  an integer 0-255 or a percentage 0-100%
     *   - alpha: an integer 0-255 or a percentage 0-100%
     */
    private static ColorUIResource parseColorRgbOrRgba(boolean hasAlpha, List<String> params, Map<String,String> variables, boolean reportError) {
        if (hasAlpha && params.size() == 2) {
            // syntax rgba(color,alpha), which allows adding alpha to any color
            // NOTE: this syntax is deprecated
            //       use fade(color,alpha) instead
            String colorStr = params.get(0);
            int alpha = parseInteger(params.get(1), 0, 255, true);

            ColorUIResource color = (ColorUIResource) parseColorOrFunction(colorStr, variables, reportError);
            return new ColorUIResource(new Color(((alpha & 0xff) << 24) | (color.getRGB() & 0xffffff), true));
        }

        int red = parseInteger(params.get(0), 0, 255, true);
        int green = parseInteger(params.get(1), 0, 255, true);
        int blue = parseInteger(params.get(2), 0, 255, true);
        int alpha = hasAlpha ? parseInteger(params.get(3), 0, 255, true) : 255;

        return hasAlpha ? new ColorUIResource(new Color(red, green, blue, alpha)) : new ColorUIResource(red, green, blue);
    }

    /**
     * Syntax: hsl(hue,saturation,lightness) or hsla(hue,saturation,lightness,alpha)
     *   - hue: an integer 0-360 representing degrees
     *   - saturation: a percentage 0-100%
     *   - lightness: a percentage 0-100%
     *   - alpha: a percentage 0-100%
     */
    private static ColorUIResource parseColorHslOrHsla(boolean hasAlpha, List<String> params) {
        int hue = parseInteger(params.get(0), 0, 360, false);
        int saturation = parsePercentage(params.get(1));
        int lightness = parsePercentage(params.get(2));
        int alpha = hasAlpha ? parsePercentage(params.get(3)) : 100;
        return hsl2rgb(hue, saturation, lightness, alpha);
    }

    // input: 0..360, 0..100, 0.100, 0..100
    private static ColorUIResource hsl2rgb(float h, float s, float l, float alpha) {
        if (s < 0 || s > 100) {
            throw new IllegalArgumentException("Color parameter outside of expected range - Saturation");
        }
        if (l < 0 || l > 100) {
            throw new IllegalArgumentException("Color parameter outside of expected range - Luminance");
        }
        if (alpha < 0 || alpha > 100) {
            throw new IllegalArgumentException("Color parameter outside of expected range - Alpha");
        }
        h = Math.max(0, Math.min(1, h / 360));
        s /= 100;
        l /= 100;
        float q = l < 0.5 ? l * (1 + s) : (l + s) - (s * l);
        float p = 2 * l - q;
        float r = Math.max(0, hue2rgb(p, q, h + (1.0f / 3.0f)));
        float g = Math.max(0, hue2rgb(p, q, h));
        float b = Math.max(0, hue2rgb(p, q, h - (1.0f / 3.0f)));
        r = Math.min(r, 1.0f);
        g = Math.min(g, 1.0f);
        b = Math.min(b, 1.0f);
        return new ColorUIResource(new Color(r, g, b, alpha / 100));
    }

    private static float hue2rgb(float p, float q, float h) {
        if (6 * h < 1) {
            return p + ((q - p) * 6 * h);
        }
        if (2 * h < 1 ) {
            return  q;
	}
        if (3 * h < 2) {
            return p + ( (q - p) * 6 * ((2.0f / 3.0f) - h) );
        }
        return p;
    }

    /**
     * Syntax: lighten(color,amount[,options]) or darken(color,amount[,options]) or
     *         saturate(color,amount[,options]) or desaturate(color,amount[,options]) or
     *         fadein(color,amount[,options]) or fadeout(color,amount[,options])
     *   - color: a color (e.g. #f00) or a color function
     *   - amount: percentage 0-100%
     *   - options: [relative] [autoInverse] [noAutoInverse] [lazy] [derived]
     */
    private static Object parseColorHSLIncreaseDecrease(final int hslIndex, final boolean increase, List<String> params, final Map<String,String> variables, boolean reportError) {
        String colorStr = params.get(0);
        final int amount = parsePercentage(params.get(1));
        boolean relative = false;
        boolean autoInverse = false;
        boolean lazy = false;
        boolean derived = false;

        if (params.size() > 2) {
            String options = params.get(2);
            relative = options.contains("relative");
            autoInverse = options.contains("autoInverse");
            lazy = options.contains("lazy");
            derived = options.contains("derived");

            // use autoInverse by default for derived colors, except if noAutoInverse is set
            if (derived && !options.contains("noAutoInverse")) {
                autoInverse = true;
            }
        }
        final boolean frelative = relative;
        final boolean fautoInverse = autoInverse;

        final Object o = resolve(null, colorStr, new ValueType[] { ValueType.COLOR }, variables);
        if (o instanceof UIDefaults.LazyValue || lazy) {
            return new UIDefaults.LazyValue() {
                public Object createValue(UIDefaults table) {
                    ColorUIResource color = o instanceof ColorUIResource ? (ColorUIResource)o : (ColorUIResource)((UIDefaults.LazyValue)o).createValue(table);
                    return hslIncreaseDecrease(hslIndex, increase, amount, frelative, fautoInverse, color);
                }
            };
        } else {
            ColorUIResource color = (ColorUIResource)o;
            return hslIncreaseDecrease(hslIndex, increase, amount, frelative, fautoInverse, color);
        }
    }

    /**
     * Syntax: fade(color,amount[,options])
     *   - color: a color (e.g. #f00) or a color function
     *   - amount: percentage 0-100%
     *   - options: [derived]
     */
    private static Object parseColorFade(List<String> params, Map<String,String> variables, boolean reportError) {
        String colorStr = params.get(0);
        final int amount = parsePercentage(params.get(1));
        boolean derived = false;
        if (params.size() > 2) {
            String options = params.get(2);
            derived = options.contains("derived");
        }

        final Object o = resolve(null, colorStr, new ValueType[] { ValueType.COLOR }, variables);
        if (o instanceof UIDefaults.LazyValue) {
            return new UIDefaults.LazyValue() {
                public Object createValue(UIDefaults table) {
                    ColorUIResource color = o instanceof ColorUIResource ? (ColorUIResource)o : (ColorUIResource)((UIDefaults.LazyValue)o).createValue(table);
                    float a = color.getAlpha() / 255f;  // ??
                    return new ColorUIResource(new Color(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, amount / 100f));
                }
            };
        } else {
            ColorUIResource color = (ColorUIResource)o;
            float a = color.getAlpha() / 255f;  // ??
            return new ColorUIResource(new Color(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, amount / 100f));
        }
    }

    /**
     * Syntax: spin(color,angle[,options])
     *   - color: a color (e.g. #f00) or a color function
     *   - angle: number of degrees to rotate
     *   - options: [derived]
     */
    private static Object parseColorSpin(List<String> params, Map<String,String> variables, boolean reportError) {
        String colorStr = params.get(0);
        final int amount = parseInteger(params.get(1), true);
        boolean derived = false;
        if (params.size() > 2) {
            String options = params.get(2);
            derived = options.contains("derived");
        }
        final Object o = resolve(null, colorStr, new ValueType[] { ValueType.COLOR }, variables);
        if (o instanceof UIDefaults.LazyValue) {
            return new UIDefaults.LazyValue() {
                public Object createValue(UIDefaults table) {
                    ColorUIResource color = o instanceof ColorUIResource ? (ColorUIResource)o : (ColorUIResource)((UIDefaults.LazyValue)o).createValue(table);
                    return hslIncreaseDecrease(0, true, amount, false, false, color);
                }
            };
        } else {
            ColorUIResource color = (ColorUIResource)o;
            return hslIncreaseDecrease(0, true, amount, false, false, color);
        }
    }

    private static int parsePercentage(String value) {
        if (!value.endsWith("%")) {
            throw new NumberFormatException("invalid percentage '" + value + "'");
        }

        int val;
        try {
            val = Integer.parseInt(value.substring(0, value.length() - 1));
        } catch(NumberFormatException ex) {
            throw new NumberFormatException("invalid percentage '" + value + "'");
        }

        if (val < 0 || val > 100) {
            throw new IllegalArgumentException("percentage out of range (0-100%) '" + value + "'");
        }
        return val;
    }

    private static Character parseCharacter(String value) {
        if (value.length() != 1) {
            throw new IllegalArgumentException("invalid character '" + value + "'");
        }
        return value.charAt(0);
    }

    private static Integer parseInteger(String value, int min, int max, boolean allowPercentage) {
        if (allowPercentage && value.endsWith("%")) {
            int percent = parsePercentage(value);
            return (max * percent) / 100;
        }

        Integer integer = parseInteger(value, true);
        if (integer.intValue() < min || integer.intValue() > max) {
            throw new NumberFormatException("integer '" + value + "' out of range (" + min + '-' + max + ')');
        }
        return integer;
    }

    private static Integer parseInteger(String value, boolean reportError) {
        try {
            return Integer.parseInt(value);
        } catch(NumberFormatException ex) {
            if (reportError) {
                throw new NumberFormatException("invalid integer '" + value + "'");
            }
        }
        return null;
    }

    private static Float parseFloat(String value, boolean reportError) {
        try {
            return Float.parseFloat(value);
        } catch(NumberFormatException ex) {
            if (reportError) {
                throw new NumberFormatException("invalid float '" + value + "'");
            }
        }
        return null;
    }

    /**
     * Split string and trim parts.
     */
    private static List<String> split(String str, char delim) {
        ArrayList<String> strs = new ArrayList<String>();
        int delimIndex = str.indexOf(delim);
        int index = 0;
        while (delimIndex >= 0) {
            strs.add(str.substring(index, delimIndex).trim());
            index = delimIndex + 1;
            delimIndex = str.indexOf(delim, index);
        }
        strs.add(str.substring(index).trim());
        return strs;
    }

    /**
     * Splits function parameters and allows using functions as parameters.
     * In other words: Delimiters surrounded by '(' and ')' are ignored.
     */
    private static List<String> splitFunctionParams(String str, char delim) {
        ArrayList<String> strs = new ArrayList<String>();
        int nestLevel = 0;
        int start = 0;
        int strlen = str.length();
        for(int i = 0; i < strlen; i++) {
            char ch = str.charAt(i);
            if (ch == '(') {
                nestLevel++;
            } else if (ch == ')') {
                nestLevel--;
            } else if (nestLevel == 0 && ch == delim) {
                strs.add(str.substring(start, i).trim());
                start = i + 1;
            }
        }
        strs.add(str.substring(start).trim());
        return strs;
    }

    private static float clamp(float value) {
        return (value < 0) ? 0 : ((value > 100) ? 100 : value);
    }

    // input values all 0..1. Output in range 0..360, 0..100, 0..100, 0..100
    private static float[] rgb2hsla(float r, float g, float b, float a) {
        float cmin = Math.max(Math.max(r, g), b);
        float cmax = Math.max(Math.max(r, g), b);
        float delta = cmax - cmin;
        float h = 0;
        float s = 0;
        float l = 0;
        if (delta == 0) {
            h = 0;
        } else if (cmax == r) {
            h = ((g - b) / delta) % 6;
        } else if (cmax == g) {
            h = (b - r) / delta + 2;
        } else {
            h = (r - g) / delta + 4;
        }
        h *= 60;
        if (h < 0) {
            h += 360;
        }
        l = (cmax + cmin) / 2;
        s = delta == 0 ? 0 : delta / (1 - Math.abs(2 * l - 1));
        return new float[] { h, s * 100 , l * 100 , a * 100 };
    }

    private static ColorUIResource hslIncreaseDecrease(int hslIndex, boolean increase, float amount, boolean relative, boolean autoInverse, Color c) {
        float[] hsla = rgb2hsla(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);
        float amount2 = increase ? amount : -amount;
        if (hslIndex == 0) { // hue is range 0-360
            hsla[0] = (hsla[0] + amount2) % 360;
        } else {
            amount2 = autoInverse && (increase ? hsla[hslIndex] > 65 : hsla[hslIndex] < 35) ? -amount2 : amount2;
            hsla[hslIndex] = clamp(relative ? (hsla[hslIndex] * ((100 + amount2) / 100)) : (hsla[hslIndex] + amount2));
        }
        return hsl2rgb(hsla[0], hsla[1], hsla[2], hsla[3]);
    }

    private static Object parseIcon(final String key) {
        return new UIDefaults.LazyValue() {
            public Object createValue(UIDefaults table) {
                // FQDN expected by Flat.
                String s = key;
                do {
                    URL url = getClass().getResource("/" + s);
                    if (url != null) {
                        return new ImageIcon(url);
                    }
                    int ix = s.indexOf(".");
                    if (ix < 0) {
                        // Would fail - we fall back to local query
                        url = getClass().getResource(key);
                        if (url != null) {
                            return new ImageIcon(url);
                        }
                        return null;
                    }
                    s = s.substring(0, ix) + "/" + s.substring(ix + 1);
                } while (true);
            }
        };
    }

}
