// $Id: PropertyParser.java 21769 2015-07-28 16:55:22Z mike $

package org.faceless.pdf2.viewer2.util;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.feature.*;
import java.awt.*;

/**
 * A Utility class for parsing particular Strings into particular types of object.
 * Used primarily with preferences and user options.
 */
public class PropertyParser {

    private PropertyParser() {
    }

    /**
     * Convert the specified string into a {@link Paint}
     * @param text the string
     * @param def the default value
     */
    public static Paint getPaint(String text, Paint def) {
        if (text!=null) {
            try {
                int colorint = -1;
                if (text.startsWith("0x")) {
                    colorint = (int)Long.parseLong(text.substring(2), 16);
                    def = new Color(colorint, colorint > 0xFFFFFF || colorint < 0);
                } else if (text.startsWith("#")) {
                    colorint = Integer.parseInt(text.substring(1), 16);
                    def = new Color(colorint, colorint > 0xFFFFFF || colorint < 0);
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return def;
    }

    /**
     * Convert the specified string into a {@link Color}
     * @param text the string
     * @param def the default value
     */
    public static Color getColor(String text, Color def) {
        return (Color)getPaint(text, def);
    }

    /**
     * Convert the specified string into a highlight (eg
     * {@link TextSelection#TYPE_BLOCK TextSelection.TYPE_BLOCK})
     * @param text the string
     * @param def the default value
     */
    public static int getHighlightType(String text, int def) {
        if (text != null) {
            if (text.equalsIgnoreCase("block")) {
                def = TextSelection.TYPE_BLOCK;
            } else if (text.equalsIgnoreCase("underline")) {
                def = TextSelection.TYPE_UNDERLINE;
            } else if (text.equalsIgnoreCase("doubleunderline")) {
                def = TextSelection.TYPE_DOUBLEUNDERLINE;
            } else if (text.equalsIgnoreCase("outline")) {
                def = TextSelection.TYPE_OUTLINE;
            } else if (text.equalsIgnoreCase("strikeout")) {
                def = TextSelection.TYPE_STRIKEOUT;
            } else if (text.equalsIgnoreCase("doublestrikeout")) {
                def = TextSelection.TYPE_DOUBLESTRIKEOUT;
            }
        }
        return def;
    }

    /**
     * Convert the specified string into a {@link Stroke}
     * @param text the string
     * @param def the default value
     */
    public static Stroke getStroke(String text, Stroke def) {
        return def;
    }

    /**
     * Convert the specified string into a margin;
     * @param text the string
     * @param def the default value
     */
    public static float getMargin(String text, float def) {
        if (text != null) {
            try {
                def = Float.parseFloat(text);
            } catch (NumberFormatException e) { }
        }
        return def;
    }
}
