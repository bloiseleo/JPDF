// $Id: AnnotationTextFactory.java 39856 2021-04-26 11:10:42Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.RichTextTransferHandler;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.text.html.*;
import javax.swing.text.*;
import java.awt.geom.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.text.DateFormat;
import org.faceless.pdf2.viewer2.util.ColorChoicePanel;
import java.util.prefs.Preferences;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;

/**
 * <p>
 * Create annotations that handle {@link AnnotationText} objects (without callouts).
 * </p>
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>customfont.<i>n</i></th><td>One or more of these options can be set, starting with <i>n</i>=0. Each is the URL of an OpenTypeFont to add to the dialog list (since 2.18.3).</td></tr>
 * <tr><th>defaultFont</th><td>if set, the name of the font to default to in dialogs. May be overriden by the prevous font selected by the user if those settings are stored (since 2.18.3).</td></tr>
 * <tr><th>defaultFontSize</th><td>if set, the font size of the font to default to in dialogs. May be override, as above (since 2.18.3).</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationText</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10
 */
public class AnnotationTextFactory extends AnnotationComponentFactory {

    /**
     * Create a new AnnotationTextFactory
     */
    public AnnotationTextFactory() {
        super("AnnotationText");
    }

    protected AnnotationTextFactory(String name) {
        super(name);
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof AnnotationText && ((AnnotationText) annot).getCallout() == null;
    }

    public String getAnnotationType() {
        return UIManager.getString("PDFViewer.annot.Text");
    }

    public PDFAnnotation createNewAnnotation(float x1, float y1, float x2, float y2) {
        AnnotationText annot = new AnnotationText();
        annot.setRectangle(x1, y1, x2, y2);
        PDFStyle style = annot.getStyle();
        PDFStyle backstyle = annot.getBackgroundStyle();
        PDFFont font = style.getFont();
        String fontname = font.getBaseName();
        String fontsize = (int)Math.round(style.getFontSize())+"pt";
        if (fontsize.equals("0pt")) {
            fontsize = "Auto";
        }
        String align = Integer.toString(style.getTextAlign()&15);
        if (align.equals("0")) align = Integer.toString(PDFStyle.TEXTALIGN_LEFT);
        Color fontcolor = (Color)(style.getFillColor()==null ? Color.black : style.getFillColor());
        Color linecolor = Color.black;
        Color backcolor = null;
        float weight = 1;
        float[] dash = null;

        String defaultFontName = null, defaultFontSize = null;   
        PDFViewer viewer = getViewer();
        if (viewer != null) { 
            PropertyManager pm = viewer.getPropertyManager();
            fontname = defaultFontName = pm.getProperty("feature.AnnotationTextFactory.defaultFont");
            fontsize = defaultFontSize = pm.getProperty("feature.AnnotationTextFactory.defaultFontSize");
        }

        Preferences preferences = getPreferences();
        if (preferences != null) {
            if (defaultFontName == null) {
                fontname = preferences.get("feature.AnnotationTextFactory.addDefaultFont", fontname);
            }
            if (defaultFontSize == null) {
                fontsize = preferences.get("feature.AnnotationTextFactory.addDefaultFontSize", fontsize);
            }
            align = preferences.get("feature.AnnotationTextFactory.addDefaultTextAlign", align);
            fontcolor = ColorChoicePanel.loadColor(preferences, "feature.AnnotationTextFactory.addDefaultTextColor", fontcolor);
            backcolor = ColorChoicePanel.loadColor(preferences, "feature.AnnotationTextFactory.addDefaultBackgroundColor", backcolor);
            linecolor = ColorChoicePanel.loadColor(preferences, "feature.AnnotationTextFactory.addDefaultBorderColor", linecolor);
            weight = preferences.getFloat("feature.AnnotationTextFactory.addDefaultLineWeight", 1);
            dash = AnnotationShapeFactory.BorderStyleEditor.getDashArray(preferences.get("feature.AnnotationTextFactory.addDefaultDashPattern", "solid"));
        }
        List<PDFFont> fonts = getFontList(font);
        for (int i=0;i<fonts.size();i++) {
            if (fonts.get(i).getBaseName().equals(fontname)) {
                font = fonts.get(i);
                break;
            }
        }
        try {
            style.setFont(font, fontsize.equals("Auto") ? 0 : Integer.parseInt(fontsize.substring(0, fontsize.length()-2)));
        } catch (Exception e) { }
        style.setTextAlign(Integer.parseInt(align));
        style.setFillColor(fontcolor);
        annot.setStyle(style);
        int opacity = backcolor == null ? 100 : Math.round(backcolor.getAlpha()/255f*100);
        annot.setOpacity(opacity);
        backstyle.setLineColor(linecolor);
        backstyle.setFillColor(backcolor);
        backstyle.setLineWeighting(weight);
        backstyle.setLineDash(dash, 0);
        annot.setBackgroundStyle(backstyle);
        return annot;
    }

    private String getProperty(PropertyManager pm, String key, String def) {
        String val = pm.getProperty("feature.AnnotationTextFactory." + key);
        return val == null ? def : val;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation a) {
        final AnnotationText annot = (AnnotationText)a;
        JComponent comp = super.createComponent(pagepanel, annot);
        comp.setToolTipText(annot.getSubject());
        makeComponentInteractive(comp, annot, !annot.isReadOnly(), true, true, pagepanel);
        return comp;
    }

    protected void copyAnnotationState(PDFAnnotation source, PDFAnnotation target) {
        super.copyAnnotationState(source, target);

        AnnotationText sourceText = (AnnotationText) source;
        AnnotationText targetText = (AnnotationText) target;

        targetText.setContents(sourceText.getContents());
        String rc = sourceText.getRichTextContents();
        if (rc != null) {
            targetText.setRichTextContents(rc);
        }
        targetText.setStyle(sourceText.getStyle());
        targetText.setBackgroundStyle(sourceText.getBackgroundStyle());
        float[] cr = sourceText.getContentRectangle();
        if (cr != null) {
            targetText.setContentRectangle(cr[0], cr[1], cr[2], cr[3]);
        }
    }

    private Vector<PDFFont> getFontList(PDFFont annotFont) {
        String fontname = annotFont.getBaseName();
        Vector<PDFFont> fonts = new Vector<PDFFont>();
        for (int i=0;i<12;i++) {
            fonts.add(new StandardFont(i));
        }
        try {
            for (int i=0;i<6;i++) {
                fonts.add(new StandardCJKFont(i, 0));
            }
        } catch (Throwable e) {
            // bfopdf-cmap.jar is missing - carry on regardless
        }
        // Custom fonts
        PDFViewer viewer = getViewer();
        if (viewer != null) {
            PropertyManager pm = viewer.getPropertyManager();
            int customFontIndex = 0;
            final String customFontKey = "feature.AnnotationTextFactory.customFont.";
            try {
                URL url = pm.getURLProperty(customFontKey + customFontIndex);
                while (url != null) {
                    InputStream in = url.openStream();
                    fonts.add(new OpenTypeFont(in, 2));
                    in.close();
                    customFontIndex++;
                    url = pm.getURLProperty(customFontKey + customFontIndex);
                }
            } catch (IOException e) {
                RuntimeException re = new RuntimeException("Can't read font");
                re.initCause(e);
                throw re;
            }
        }
        boolean foundfont = false;
        for (int i=0;!foundfont && i<fonts.size();i++) {
            foundfont = fonts.get(i).getBaseName().equals(fontname);
        }
        if (!foundfont) {
            fonts.add(annotFont);
        }
        return fonts;
    }

    public JComponent createEditComponent(PDFAnnotation gannot, final boolean readonly, boolean create) {
        final AnnotationText annot = (AnnotationText)gannot;
        final Preferences preferences = getPreferences();
        return this.new TextEditor(annot, preferences, readonly, false, false);
    }


    /**
     * TextEditor to handle editing text and rich-text content. Except rich-text doesn't work because
     * Swing's HTML support is abysmal - editor tool tends to produce invalid HTML, and pasting
     * HTML in from outside gives something that looks a bit like HTML, but isn't. At least on OS X.
     * So shelved rich-text for now.
     */
    class TextEditor extends JPanel {

        final AnnotationText annot;
        final Preferences preferences;

        TextEditor(final AnnotationText annot, final Preferences preferences, boolean readonly, final boolean richtext, boolean hascallout) {
            super(new GridBagLayout());
            this.annot = annot;
            this.preferences = preferences;

            PDFStyle style = annot.getStyle();
            PDFStyle backstyle = annot.getBackgroundStyle();
            Color backcolor = (Color)backstyle.getFillColor();
            backcolor = backcolor==null ? null : new Color(backcolor.getRed(), backcolor.getGreen(), backcolor.getBlue(), Math.round(annot.getOpacity()/100f*255));
            Color linecolor = (Color)backstyle.getLineColor();
            PDFFont font = style.getFont();
            int fontsize = (int)Math.round(annot.getStyle().getFontSize());

            Vector<PDFFont> fonts = getFontList(font);
            String fontname = font.getBaseName();
            String fontsizestring = fontsize == 0 ? "Auto" : fontsize+"pt";
            Color fontcolor = (Color)(style.getFillColor()==null ? Color.black : style.getFillColor());

            addStockDetailsToEditComponent(annot, this);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.weightx = 1;
            gbc.gridwidth = gbc.REMAINDER;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weighty = 1;

            final JEditorPane editorpane = new JEditorPane() {
                public boolean getScrollableTracksViewportWidth() {
                    return true;
                }
                public void removeNotify() {
                    super.removeNotify();
                    commitChanges(this, richtext);
                }
            };
            editorpane.setPreferredSize(new Dimension(300, 100));
            editorpane.setEditable(false);
            if (richtext) {
                editorpane.setEditorKit(new HTMLEditorKit());
                InputMap inputMap = editorpane.getInputMap();
                inputMap.put(KeyStroke.getKeyStroke('B', Util.getMenuShortcutKeyMask(editorpane)), HTMLEditorKit.BOLD_ACTION);
                inputMap.put(KeyStroke.getKeyStroke('I', Util.getMenuShortcutKeyMask(editorpane)), HTMLEditorKit.ITALIC_ACTION);
                inputMap.put(KeyStroke.getKeyStroke('U', Util.getMenuShortcutKeyMask(editorpane)), "bfo-underline");
                ActionMap actionMap = editorpane.getActionMap();
                actionMap.put(HTMLEditorKit.BOLD_ACTION, new StyledEditorKit.BoldAction());
                actionMap.put(HTMLEditorKit.ITALIC_ACTION, new StyledEditorKit.ItalicAction());
                actionMap.put("bfo-underline", new StyledEditorKit.UnderlineAction());
                editorpane.setTransferHandler(Util.createTransferHandler(getViewer()));

                String content = annot.getRichTextContents();
                if (content == null) {
                    content = annot.getContents();
                } else {
                    content = annot.getContents();
                    if (content != null) {
                        content = "<html>" + content.replaceAll("\r", "<br>") + "</html>";
                    }
                }
                if (content != null && content.length() > 0) {
                    editorpane.setText(content);
                }
            } else {
                editorpane.setDocument(new PlainDocument());
                String content = annot.getContents();
                if (content != null) {
                    content = content.replaceAll("\r", "");
                }
                editorpane.setText(content);
            }
            add(new JScrollPane(editorpane), gbc);

            if (!readonly) {
                // EditorPane
                editorpane.setEditable(true);
                Util.setAutoFocusComponent(editorpane);
                editorpane.addFocusListener(new FocusListener() {
                    public void focusGained(FocusEvent e) { }
                    public void focusLost(FocusEvent e) {
                        commitChanges(editorpane, richtext);
                    }
                });


                // AppearancePanel
                gbc.fill = GridBagConstraints.NONE;
                gbc.weighty = 0;
                final JPanel appearancepanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                add(appearancepanel, gbc);

                // Font Names
                final JComboBox<PDFFont> fontnames = new JComboBox<PDFFont>(fonts);
                fontnames.setEditable(false);
                for (int i=0;i<fonts.size();i++) {
                    if (fonts.get(i).getBaseName().equals(fontname)) {
                        fontnames.setSelectedIndex(i);
                        break;
                    }
                }
                fontnames.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        PDFFont font = (PDFFont)fontnames.getSelectedItem();
                        String name = font.getBaseName();
                        PDFStyle style = annot.getStyle();
                        style.setFont(font, style.getFontSize());
                        annot.setStyle(style);

                        if (richtext) {
                            new StyledEditorKit.FontFamilyAction("bfo-set-fontfamily", name).actionPerformed(new ActionEvent(editorpane, ActionEvent.ACTION_PERFORMED, name));
                        }
                        if (preferences != null) {
                            preferences.put("feature.AnnotationTextFactory.addDefaultFont", name);
                        }
                    }
                });
                fontnames.setRenderer(new DefaultListCellRenderer() {
                    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean focus) {
                        return super.getListCellRendererComponent(list, ((PDFFont)value).getBaseName(), index, isSelected, focus);
                    }
                });
                appearancepanel.add(fontnames);

                // Font Sizes
                Vector<String> fontsizes = new Vector<String>(Arrays.asList(new String[] { "Auto", "6pt", "8pt", "10pt", "11pt", "12pt", "14pt", "16pt", "18pt", "20pt", "24pt", "26pt", "28pt", "36pt", "48pt", "60pt", "72pt"}));
                if (!fontsizes.contains(fontsizestring)) {
                    fontsizes.add(fontsizestring);
                    Collections.sort(fontsizes);
                }
                final JComboBox<String> fontsizecombo = new JComboBox<String>(fontsizes);
                fontsizecombo.setSelectedItem(fontsizestring);
                fontsizecombo.setEditable(true);
                fontsizecombo.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        try {
                            PDFStyle style = annot.getStyle();
                            String ssize = (String)fontsizecombo.getSelectedItem();
                            if (ssize.equalsIgnoreCase("Auto")) {
                                ssize = "0";
                            } else if (ssize.endsWith("pt")) {
                                ssize = ssize.substring(0, ssize.length() - 2);
                            }
                            style.setFont(style.getFont(), Integer.parseInt(ssize));
                            annot.setStyle(style);

                            if (richtext) {
                                new StyledEditorKit.FontSizeAction("bfo-set-fontsize", Integer.parseInt(ssize)).actionPerformed(new ActionEvent(editorpane, ActionEvent.ACTION_PERFORMED, ssize));
                            }
                            if (preferences != null) {
                                preferences.put("feature.AnnotationTextFactory.addDefaultFontSize", ssize+"pt");
                            }
                        } catch (NumberFormatException e) {
                        }
                    }
                });
                appearancepanel.add(fontsizecombo);

                JPanel space = new JPanel();
                space.setPreferredSize(new Dimension(20, 20));
                appearancepanel.add(space);

                // Font Color
                appearancepanel.add(ColorChoicePanel.createColorChoiceButton(fontcolor, new ColorChoicePanel.ColorChoiceListener() {
                    public void colorChosen(Color c) {
                        PDFStyle style = annot.getStyle();
                        style.setFillColor(c);
                        annot.setStyle(style);

                        if (richtext) {
                            new StyledEditorKit.ForegroundAction("bfo-set-color", c).actionPerformed(new ActionEvent(editorpane, ActionEvent.ACTION_PERFORMED, Util.encodeColor(c)));
                        }
                        if (preferences!=null) {
                            ColorChoicePanel.saveColor(preferences, "feature.AnnotationTextFactory.addDefaultTextColor", c);
                        }
                    }
                }, ColorChoicePanel.TYPE_TEXT, false, false, UIManager.getString("PDFViewer.annot.setColor")));

                space = new JPanel();
                space.setPreferredSize(new Dimension(2, 20));
                appearancepanel.add(space);

                // Background Color
                appearancepanel.add(ColorChoicePanel.createColorChoiceButton(backcolor, new ColorChoicePanel.ColorChoiceListener() {
                    public void colorChosen(Color c) {
                        PDFStyle backstyle = annot.getBackgroundStyle();
                        if (c == null) {
                            annot.setOpacity(100);
                            backstyle.setFillColor(null);
                        } else {
                            annot.setOpacity(Math.round(c.getAlpha() / 255f * 100));
                            backstyle.setFillColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
                        }
                        annot.setBackgroundStyle(backstyle);

                        if (preferences != null) {
                            ColorChoicePanel.saveColor(preferences, "feature.AnnotationTextFactory.addDefaultBackgroundColor", c);
                        }
                    }
                }, ColorChoicePanel.TYPE_RECTANGLE, true, true, UIManager.getString("PDFViewer.annot.setColor")));

                space = new JPanel();
                space.setPreferredSize(new Dimension(2, 20));
                appearancepanel.add(space);

                // Border Color
                appearancepanel.add(ColorChoicePanel.createColorChoiceButton(linecolor, new ColorChoicePanel.ColorChoiceListener() {
                    public void colorChosen(Color c) {
                        PDFStyle backstyle = annot.getBackgroundStyle();
                        backstyle.setLineColor(c);
                        annot.setBackgroundStyle(backstyle);

                        if (preferences != null) {
                            ColorChoicePanel.saveColor(preferences, "feature.AnnotationTextFactory.addDefaultBorderColor", c);
                        }
                    }
                }, ColorChoicePanel.TYPE_BORDER, false, true, UIManager.getString("PDFViewer.annot.setColor")));
                space = new JPanel();
                space.setPreferredSize(new Dimension(5, 20));
                appearancepanel.add(space);

                // Text Alignment
                String[] icons = new String[] { "PDFViewer.Feature.AnnotationTextFactory.left.icon", "PDFViewer.Feature.AnnotationTextFactory.center.icon", "PDFViewer.Feature.AnnotationTextFactory.right.icon" };
                int[] aligns = new int[] { PDFStyle.TEXTALIGN_LEFT, PDFStyle.TEXTALIGN_CENTER, PDFStyle.TEXTALIGN_RIGHT };
                final ButtonGroup aligngroup = new ButtonGroup();
                for (int i=0;i<3;i++) {
                    JRadioButton alignbutton = new ToggleButton((ImageIcon)UIManager.getIcon(icons[i]));
                    if (aligns[i] == style.getTextAlign()) {
                        alignbutton.setSelected(true);
                    }
                    final int alignval = aligns[i];
                    alignbutton.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent event) {
                            PDFStyle style = annot.getStyle();
                            style.setTextAlign(alignval);
                            annot.setStyle(style);

                            if (richtext) {
                                int swingval = (new int[] { StyleConstants.ALIGN_LEFT, StyleConstants.ALIGN_CENTER, StyleConstants.ALIGN_RIGHT })[alignval];
                                new StyledEditorKit.AlignmentAction("bfo-set-alignment", swingval).actionPerformed(new ActionEvent(editorpane, ActionEvent.ACTION_PERFORMED, Integer.toString(swingval)));
                            }
                            if (preferences != null) {
                                preferences.put("feature.AnnotationTextFactory.addDefaultTextAlign", Integer.toString(alignval));
                            }
                        }
                    });
                    appearancepanel.add(alignbutton);
                    aligngroup.add(alignbutton);
                }

                // Callout stuff - line endings, callout with/without elbow
                if (hascallout) {
                    space = new JPanel();
                    space.setPreferredSize(new Dimension(20, 20));
                    appearancepanel.add(space);
                    String arrow = annot.getCalloutEnding();
                    boolean calloutelbow = annot.getCallout().length == 6;

                    LineEndingSelector endChoice = new LineEndingSelector(arrow, true) {
                        public void itemStateChanged(ItemEvent e) {
                            String calloutend = getLineEnding();
                            annot.setCalloutEnding(calloutend);

                            if (preferences != null) {
                                preferences.put("feature.AnnotationTextFactory.addDefaultCalloutArrow", calloutend);
                            }
                            super.itemStateChanged(e);
                        }
                    };
                    appearancepanel.add(endChoice);
                    endChoice.setSelectedItem(arrow);

                    final ButtonGroup elbowGroup = new ButtonGroup();
                    JRadioButton removeElbowButton = new ToggleButton((ImageIcon)UIManager.getIcon("PDFViewer.Feature.AnnotationTextFactory.line.icon"));
                    elbowGroup.add(removeElbowButton);
                    removeElbowButton.setSelected(!calloutelbow);
                    removeElbowButton.setAction(new AbstractAction() {
                        public void actionPerformed(ActionEvent event) {
                            float[] callout = annot.getCallout();
                            if (callout.length == 6) {
                                float[] dst = new float[4];
                                System.arraycopy(callout, 0, dst, 0, 2);
                                System.arraycopy(callout, 4, dst, 2, 2);
                                annot.setCallout(dst);
                            }
                            if (preferences != null) {
                                preferences.putBoolean("feature.AnnotationTextFactory.addDefaultCalloutElbow", false);
                            }
                        }
                    });
                    appearancepanel.add(removeElbowButton);

                    JRadioButton addElbowButton = new ToggleButton((ImageIcon)UIManager.getIcon("PDFViewer.Feature.AnnotationTextFactory.elbow.icon"));
                    elbowGroup.add(addElbowButton);
                    addElbowButton.setSelected(calloutelbow);
                    addElbowButton.setAction(new AbstractAction() {
                        public void actionPerformed(ActionEvent event) {
                            float[] callout = annot.getCallout();
                            if (callout.length == 4) {
                                float[] dst = new float[6];
                                System.arraycopy(callout, 0, dst, 0, 2);
                                System.arraycopy(callout, 2, dst, 4, 2);
                                dst[2] = callout[0] + ((callout[2] - callout[0]) / 2);
                                dst[3] = callout[1] + ((callout[3] - callout[1]) / 2);
                                annot.setCallout(dst);
                            }
                            if (preferences!=null) {
                                preferences.putBoolean("feature.AnnotationTextFactory.addDefaultCalloutElbow", true);
                            }
                        }
                    });
                    appearancepanel.add(addElbowButton);
                }
            }

        }

        private void commitChanges(final JEditorPane editorpane, final boolean rt) {
            if (rt) {
                try {
                    String richtext = toAnnotationHTML(editorpane.getText());
                    annot.setRichTextContents(richtext);
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            } else {
                annot.setContents(editorpane.getText());
            }
        }


        /**
         * Convert HTML from HTMLDocument to HTML as accepted by PDF spec. This sounds easy but
         * is actually near impossible due to garbage input from HTMLDocument.
         * Given up. Nice idea, leaving code here for posterity.
         */
        private String toAnnotationHTML(String in) {
            try {
                final StringBuilder out = new StringBuilder();
                SAXParserFactory.newInstance().newSAXParser().parse(new InputSource(new StringReader(in)), new DefaultHandler() {
                    private boolean dokids, inpara;
                    public void startDocument() {
                        out.append("<body xmlns=\"http://www.w3.org/1999/xtml\" xmlns:xfa=\"http://www.xfa.org/schema/xfa-data/1.0/\" xfa:spec=\"2.0.2\">");
                    }
                    public void startElement(String ns, String tag, String q, Attributes atts) {
                        if (q.equals("u")) {
                            q = "span";
                            AttributesImpl newatts = new AttributesImpl();
                            newatts.addAttribute(null, null, "style", "CDATA", "text-decoration:underline");
                            atts = newatts;
                        } else if (q.equals("font")) {
                            q = "span";
                            String color = atts.getValue("color");
                            String face = atts.getValue("face");
                            String style = "";
                            if (color != null) {
                               style += "color:"+color+";";
                            }
                            if (face != null) {
                               style += "font-family:\""+face+"\";";
                            }
                            AttributesImpl newatts = new AttributesImpl();
                            newatts.addAttribute(null, null, "style", "CDATA", style);
                            atts = newatts;
                        }
                        if (q.equals("body")) {
                            dokids = true;
                        } else if (dokids && (q.equals("p") || q.equals("b") || q.equals("i") || q.equals("span"))) {
                            if (q.equals("p")) {
                                inpara = true;
                            }
                            out.append("<"+q);
                            String s = atts.getValue("style");
                            if (s != null) {
                                s = s.replaceAll("\"", "\\\"");
                                out.append(" style=\""+s+"\"");
                            }
                            out.append(">");
                        }
                    }
                    public void endElement(String ns, String tag, String q) {
                        if (q.equals("font") || q.equals("u")) {
                            q = "span";
                        }
                        if (q.equals("body")) {
                            dokids = false;
                        } else if (dokids) {
                            out.append("</"+q+">");
                            if (q.equals("p")) {
                                inpara = false;
                            }
                        }
                    }
                    public void characters(char[] buf, int off, int len) {
                        if (dokids) {
                            boolean space = false;
                            for (int i=0;i<len;i++) {
                                char c = buf[off+i];
                                if (c == ' ') {
                                    if (!space) {
                                        out.append(c);
                                        space = true;
                                    }
                                } else if (c == '\n') {
                                    if (inpara) {
                                        out.append("<br/>");
                                    }
                                } else if (c != '\r') {
                                    out.append(c);
                                    space = false;
                                }
                            }
                        }
                    }
                    public void endDocument() {
                        out.append("</body>");
                    }
                });
                return out.toString();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException(e);
            } catch (SAXException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException)e.getCause();
                } else if (e.getCause() != null) {
                    throw new RuntimeException(e.getCause());
                } else {
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static class ToggleButton extends JRadioButton {
        public ToggleButton(ImageIcon icon) {
            super(icon);
        }
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isSelected()) {
                BorderFactory.createEtchedBorder().paintBorder(this, g, 0, 0, getWidth(), getHeight());
            }
        }
    }
}
