// $Id: RichTextTransferHandler.java 22446 2016-01-13 14:11:12Z mike $

package org.faceless.pdf2.viewer2.util;

import java.io.*;
import java.awt.*;
import java.awt.datatransfer.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.rtf.*;
import javax.swing.text.html.*;
import java.awt.font.*;
import java.util.*;
import java.util.List;
import java.text.*;
import org.faceless.util.AttributedStringBuilder;
import org.faceless.pdf2.PageExtractor;
import org.faceless.pdf2.viewer2.DocumentPanel;
import org.faceless.pdf2.viewer2.PDFViewer;
import org.faceless.pdf2.viewer2.feature.TextTool;
import org.faceless.pdf2.viewer2.feature.TextSelection;
import org.faceless.pdf2.viewer2.feature.TextSelection.RangeList;
import org.faceless.pdf2.viewer2.feature.TextSelection.Range;

/**
 * <p>
 * A {@link TransferHandler} that can copy/paste RichText. The default TransferHandler
 * on a JEditorPane can't copy RichText, and pasting it will insert it at the
 * end of the document rather than at the caret (because RTFEditorKit ignores
 * the "pos" attribute in read).
 * </p><p>
 * This isn't a perfect solution - in particular the following faults exist:
 * </p>
 * <ul>
 * <li>pasting into the middle of a document always appends a line-feed - but
 * it is an improvement.</li>
 * <li>pasting RTF DataFlavor into HTMLDocument puts weird spaces in place, and
 * deleting those spaces isn't possible as it deletes the text instead.</li>
 * </ul>
 * <p>
 * Tested on Windows and OS X for copying/pasting between plain, RTF and HTML
 * JEditorPanes with or without the handler, and also testing paste in from and
 * copy out to external application.
 * </p><p>
 * Also works with TextTool to allow copying of styled text.
 * </p>
 *
 */
public class RichTextTransferHandler extends TransferHandler {

    private static final DataFlavor PLAIN, RTF, HTML;
    static {
        DataFlavor rtf = null, html = null;
        try {
            rtf = new DataFlavor("text/rtf;class=java.io.InputStream");
            html = new DataFlavor("text/html;class=java.lang.String");
        } catch (Exception e) {
            throw new Error(e); // Can't happen
        }
        PLAIN = DataFlavor.stringFlavor;
        RTF = rtf;
        HTML = html;
    }

    private static TextTool getTextTool(JComponent c) {
        PDFViewer viewer = (PDFViewer)SwingUtilities.getAncestorOfClass(PDFViewer.class, c);
        return viewer == null ? null : viewer.getFeature(TextTool.class);
    }

    @Override public boolean canImport(JComponent c, DataFlavor[] f) {
        if (c instanceof JTextComponent && f != null) {
            for (int i=0;i<f.length;i++) {
                if (f[i] == PLAIN || f[i] == RTF || f[i] == HTML) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override public boolean importData(JComponent c, Transferable tran) {
        // Note - import only aplies to JTextComponents, we don't have to
        // worry about importing text into a PDF

        if (c instanceof JTextComponent) {
            try {
                JTextComponent tc = (JTextComponent)c;
                if (tc.getDocument() instanceof HTMLDocument && tran.isDataFlavorSupported(HTML)) {
                    int pos = tc.getSelectionStart();
                    int end = tc.getSelectionEnd();
                    if (end > pos) {
                        tc.getDocument().remove(pos, end-pos);
                    }

                    HTMLEditorKit kit = (HTMLEditorKit)tc.getUI().getEditorKit(tc);
                    Reader r = new StringReader((String)tran.getTransferData(HTML));
                    kit.read(r, tc.getDocument(), pos);
                    return true;
                } else if (tc.getDocument() instanceof StyledDocument && tran.isDataFlavorSupported(RTF)) {
                    int pos = tc.getSelectionStart();
                    int end = tc.getSelectionEnd();
                    if (end > pos) {
                        tc.getDocument().remove(pos, end-pos);
                    }

                    RTFEditorKit kit = new RTFEditorKit();
                    StyledDocument tdoc = (StyledDocument)kit.createDefaultDocument();
                    InputStream in = (InputStream)tran.getTransferData(RTF);
                    kit.read(in, tdoc, 0);
                    in.close();
                    StyledDocument doc = (StyledDocument)tc.getDocument();
                    for (int i=0;i<tdoc.getLength();i++) {
                        doc.insertString(pos+i, tdoc.getText(i, 1), tdoc.getCharacterElement(i).getAttributes());
                    }
                    return true;
                } else if (tran.isDataFlavorSupported(PLAIN)) {
                    tc.replaceSelection((String)tran.getTransferData(PLAIN));
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override protected Transferable createTransferable(JComponent c) {
        if (c instanceof JTextComponent) {
            JTextComponent tc = (JTextComponent)c;
            return new RichTransferable(tc.getDocument(), tc.getSelectionStart(), tc.getSelectionEnd());
        } else {
            TextTool texttool = getTextTool(c);
            if (texttool != null) {
                DocumentPanel panel = (DocumentPanel)SwingUtilities.getAncestorOfClass(DocumentPanel.class, c);
                if (panel != null) {
                    TextSelection.RangeList list = texttool.getRangeList();
                    return new RichTransferable(list.getStyledText());
                }
            }
        }
        return null;
    }

    @Override public int getSourceActions(JComponent c) {
        if (c instanceof JTextComponent) {
            JTextComponent tc = (JTextComponent)c;
            return tc.getSelectionStart() == tc.getSelectionEnd() ? NONE : COPY;
        } else {
            TextTool texttool = getTextTool(c);
            if (texttool != null) {
                return texttool.getRangeList().isValid() ? COPY : NONE;
            }
        }
        return NONE;
    }

    //---------------------------------------------------------------------------------

    /**
     * Transferable class that can transfer Rich-Text as plain, RTF or HTML.
     * Takes it's input from either
     *   a) A List of AttributedStrings, as supplied from TextSelection ranges
     *   b) A javax.swing.text.Document, as taken from a JTextComponent
     */
    public static class RichTransferable implements Transferable {

        private final AttributedString string;
        private final Document doc;
        private final int start, len;

        public RichTransferable(AttributedString string) {
            this.string = string;
            this.doc = null;
            this.start = this.len = 0;
        }

        public RichTransferable(Document doc, int start, int end) {
            this.string = null;
            this.doc = doc;
            this.start = start;
            this.len = end-start;
        }

        @Override public DataFlavor[] getTransferDataFlavors() {
            List<DataFlavor> df = new ArrayList<DataFlavor>();
            df.add(PLAIN);
            if (string != null || doc instanceof HTMLDocument) {
                df.add(HTML);
            }
            if (string != null || doc instanceof StyledDocument) {
                df.add(RTF);
            }
            return df.toArray(new DataFlavor[df.size()]);
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return PLAIN.equals(flavor) ||
                (RTF.equals(flavor) && (string != null || doc instanceof StyledDocument)) ||
                (HTML.equals(flavor) && (string != null || doc instanceof HTMLDocument));
        }

        @Override public Object getTransferData(DataFlavor flavor) throws IOException {
            try {
                if (PLAIN.equals(flavor)) {
                    if (doc != null) {
                        return doc.getText(start, len);
                    } else {
                        return new AttributedStringBuilder(string).toString().trim();
                    }
                } else if (HTML.equals(flavor)) {
                    StringWriter buf = new StringWriter();
                    if (doc != null) {
                        HTMLEditorKit kit = new HTMLEditorKit();
                        kit.write(buf, doc, start, len);
                    } else {
                        toHTML(string, buf);
                    }
                    if (flavor.isRepresentationClassInputStream()) {
                        String charset = flavor.getParameter("charset");
                        if (charset == null) {
                            charset = "ISO-8859-1";
                        }
                        return new ByteArrayInputStream(buf.toString().getBytes(charset));
                    } else {
                        return buf.toString();
                    }
                } else if (RTF.equals(flavor)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    if (doc != null) {
                        RTFEditorKit kit = new RTFEditorKit();
                        kit.write(out, doc, start, len);
                    } else {
                        OutputStreamWriter wout = new OutputStreamWriter(out, "ISO-8859-1");
                        toRTF(string, wout);
                        wout.close();
                    }
                    return new ByteArrayInputStream(out.toByteArray());
                } else {
                    throw new IllegalArgumentException("Unsupported Flavor: "+flavor.getMimeType());
                }
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        }

    }

    //---------------------------------------------------------------------------------
    // Util methods to convert AttributedString to HTML / RTF
    //---------------------------------------------------------------------------------

    /**
     * Convert an AttributedString to an HTML String.
     */
    private static void toHTML(AttributedString s, Appendable buf) throws IOException {
        AttributedCharacterIterator i = s.getIterator();
        char c = i.first();
        while (c != AttributedCharacterIterator.DONE) {
            if (c == '\n') {
                buf.append("<br />\n");
                c = i.next();
            } else {
                int start = i.getIndex();
                int end = i.getRunLimit();
                buf.append("<span style='");
                String fontname = (String) i.getAttribute(TextAttribute.FAMILY);
                Number fontsize = (Number) i.getAttribute(TextAttribute.SIZE);
                boolean fontbold = TextAttribute.WEIGHT_BOLD.equals(i.getAttribute(TextAttribute.WEIGHT));
                boolean fontitalic = TextAttribute.POSTURE_OBLIQUE.equals(i.getAttribute(TextAttribute.POSTURE));
                buf.append("font:");
                if (fontsize != null) {
                    buf.append(Integer.toString(fontsize.intValue()));
                    buf.append("pt \"");
                }
                buf.append(fontname);
                buf.append("\";");
                Paint fg = (Paint) i.getAttribute(TextAttribute.FOREGROUND);
                if (fg instanceof Color) {
                    Color color = (Color) fg;
                    buf.append("color:#");
                    buf.append(hexpad(color.getRed(), 2));
                    buf.append(hexpad(color.getGreen(), 2));
                    buf.append(hexpad(color.getBlue(), 2));
                    buf.append(";");
                }
                buf.append("'>");
                if (fontbold) {
                    buf.append("<b>");
                }
                if (fontitalic) {
                    buf.append("<i>");
                }
                do {
                    if (c == '<') {
                        buf.append("&lt;");
                    } else if (c == '>') {
                        buf.append("&gt;");
                    } else if (c == '&') {
                        buf.append("&amp;");
                    } else if (c >= 127 || c < 32) {
                        String hex = Integer.toHexString((int) c);
                        buf.append("&#x");
                        buf.append(hex);
                        buf.append(";");
                    } else {
                        buf.append(c);
                    }
                    c = i.next();
                } while (i.getIndex() < end);
                if (fontitalic) {
                    buf.append("</i>");
                }
                if (fontbold) {
                    buf.append("</b>");
                }
                buf.append("</span>");
            }
        }
    }

    private static boolean toBoolean(Object o) {
        return o == null ? false : o instanceof Boolean && ((Boolean)o).booleanValue();
    }

    private static final String hexpad(int number, int width) {
        String hex = Integer.toHexString(number);
        StringBuilder buf = new StringBuilder();
        int l = hex.length();
        while (l < width) {
            buf.append('0');
            l++;
        }
        buf.append(hex);
        return buf.toString();
    }

    // -- Convert a list of AttributedStrings to RTF with paragraph breaks

    /**
     * Convert a list of AttributedString to an RTF String.
     */
    private static final void toRTF(AttributedString s, Appendable out) throws IOException {
        // Build resource tables
        List<String> fonts = new ArrayList<String>();
        List<Color> colors = new ArrayList<Color>();
        StringBuilder fontout = new StringBuilder();
        StringBuilder colorout = new StringBuilder();

        AttributedCharacterIterator i = s.getIterator();
        char c = i.first();
        while (c != AttributedCharacterIterator.DONE) {
            String fontname = (String) i.getAttribute(TextAttribute.FAMILY);
            Paint paint = (Paint) i.getAttribute(TextAttribute.FOREGROUND);
            if (fontname != null && !fonts.contains(fontname)) {
                fontout.append("\\f");
                fontout.append(Integer.toString(fonts.size()));
                fontout.append("\\fnil ");
                fontout.append(fontname);
                fontout.append(";");
                fonts.add(fontname);
            }
            if (paint instanceof Color && !colors.contains(paint)) {
                Color color = (Color)paint;
                colors.add(color);
                colorout.append("\\red");
                colorout.append(Integer.toString(color.getRed()));
                colorout.append("\\green");
                colorout.append(Integer.toString(color.getGreen()));
                colorout.append("\\blue");
                colorout.append(Integer.toString(color.getBlue()));
                colorout.append(";");
            }
            c = i.setIndex(i.getRunLimit());
        }

        out.append("{\\rtf1\\ansi");
        out.append("{\\fonttbl");
        out.append(fontout);
        out.append("}");
        out.append("{\\colortbl");
        out.append(colorout);
        out.append("}");

        // Text
        i = s.getIterator();
        c = i.first();
        while (c != AttributedCharacterIterator.DONE) {
            if (c == '\n') {
                out.append("\\par");
                c = i.next();
            } else {
                out.append("{");
                String fontname = (String) i.getAttribute(TextAttribute.FAMILY);
                int fontindex = fonts.indexOf(fontname);
                if (fontindex >= 0) {
                    out.append("\\f");
                    out.append(Integer.toString(fontindex));
                }
                boolean fontbold = TextAttribute.WEIGHT_BOLD.equals(i.getAttribute(TextAttribute.WEIGHT));
                boolean fontitalic = TextAttribute.POSTURE_OBLIQUE.equals(i.getAttribute(TextAttribute.POSTURE));
                if (fontbold) {
                    out.append("\\b");
                }
                if (fontitalic) {
                    out.append("\\i");
                }
                Number fontsize = (Number) i.getAttribute(TextAttribute.SIZE);
                if (fontsize != null) {
                    out.append("\\fs");
                    out.append(Integer.toString((int) Math.ceil(fontsize.doubleValue() * 2.0)));
                }
                Paint fg = (Paint) i.getAttribute(TextAttribute.FOREGROUND);
                int colorindex = colors.indexOf(fg);
                if (colorindex >= 0) {
                    out.append("\\cf");
                    out.append(Integer.toString(colorindex));
                }
                out.append(" ");

                int end = i.getRunLimit();
                do {
                    if (c == '\\') {
                        out.append("\\\\");
                    } else if (c == '{') {
                        out.append("\\{");
                    } else if (c == '}') {
                        out.append("\\}");
                    } else if (c >= 127 || c < 32) {
                        out.append("\\u");
                        out.append(Short.toString((short)c));     // Signed 16-bit decimal, not hex!
                        out.append('?');
                    } else {
                        out.append(c);
                    }
                    c = i.next();
                } while (i.getIndex() < end);
                out.append("}");
            }
        }
        out.append("}");
    }

}
