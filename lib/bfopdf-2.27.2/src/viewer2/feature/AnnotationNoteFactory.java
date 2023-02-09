// $Id: AnnotationNoteFactory.java 26352 2017-10-31 18:27:42Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.swing.event.*;
import org.faceless.pdf2.viewer2.util.ColorChoicePanel;
import java.awt.event.*;
import java.awt.*;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * <p>
 * Create annotations that handle {@link AnnotationNote} objects
 * </p>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationNote</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class AnnotationNoteFactory extends AnnotationComponentFactory {

    private static final String[] types = new String[] { "Note", "Comment", "Help", "Insert", "Key", "NewParagraph", "Paragraph", "Check", "Cross", "RightArrow", "RightPointer", "Star", "UpArrow", "UpLeftArrow", "Circle" };
    /**
     * Create a new AnnotationNoteFactory
     * @since 2.11
     */
    public AnnotationNoteFactory() {
        super("AnnotationNote");
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof AnnotationNote;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation a) {
        final AnnotationNote annot = (AnnotationNote)a;
        final JComponent comp = super.createComponent(pagepanel, a);
        String content = annot.getContents();
        if (content != null && content.length() > 80) {
            content = content.substring(0, 80) + "...";
        }
        comp.setToolTipText(content);
        makeComponentInteractive(comp, annot, true, true, false, pagepanel);
        return comp;
    }

    public String getAnnotationType() {
        return UIManager.getString("PDFViewer.annot.Note");
    }

    public PDFAnnotation createNewAnnotation(float x1, float y1, float x2, float y2) {
        AnnotationNote ret = new AnnotationNote();
        ret.setRectangle(x1, y1, x2, y2);
        return ret;
    }

    protected void copyAnnotationState(PDFAnnotation source, PDFAnnotation target) {
        super.copyAnnotationState(source, target);

        AnnotationNote sourceNote = (AnnotationNote) source;
        AnnotationNote targetNote = (AnnotationNote) target;

        targetNote.setOpen(sourceNote.isOpen());
        targetNote.setType(sourceNote.getType(), sourceNote.getColor());
        String status = sourceNote.getStatus();
        if (status != null) {
            targetNote.setStatus(status);
        }
    }

    public JComponent createEditComponent(PDFAnnotation gannot, final boolean readonly, final boolean create) {
        final AnnotationNote annot = (AnnotationNote)gannot;
        final Preferences preferences = getPreferences();
        if (create) {
            if (preferences!=null) {
                Color c = annot.getColor();
                c = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(annot.getOpacity()/100f*255));
                c = ColorChoicePanel.loadColor(preferences, "feature.AnnotationNoteFactory.addDefaultColor", c);
                String typestring = preferences.get("feature.AnnotationNoteFactory.addDefaultType", "Note");
                annot.setType(typestring, new Color(c.getRed(), c.getGreen(), c.getBlue()));
                annot.setOpacity(Math.round(c.getAlpha()/255f*100));
            }
        }

        final JPanel panel = new JPanel(new GridBagLayout());

        addStockDetailsToEditComponent(annot, panel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.gridwidth = gbc.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;

        final JTextArea textarea = new JTextArea(5, 20);
        textarea.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent e) { }
            public void focusLost(FocusEvent e) {
                annot.setContents(textarea.getText());
            }
        });

        Util.setAutoFocusComponent(textarea);
        textarea.setEditable(!readonly);
        textarea.setLineWrap(true);
        textarea.setText(annot.getContents());
        panel.add(new JScrollPane(textarea), gbc);

        if (!readonly) {
            gbc.weighty = 0;
            gbc.fill = gbc.NONE;

            final JPanel appearancepanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            panel.add(appearancepanel, gbc);
            final ButtonGroup appearancegroup = new ButtonGroup();
            ActionListener selectionlistener = new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    for (int i=0;i<appearancepanel.getComponentCount();i++) {
                        if (((AbstractButton)appearancepanel.getComponent(i)).isSelected()) {
                            annot.setType(types[i], annot.getColor());
                            if (preferences!=null) {
                                preferences.put("feature.AnnotationNoteFactory.addDefaultType", types[i]);
                            }
                            break;
                        }
                    }
                }
            };
            for (int i=0;i<types.length;i++) {
                String type = types[i].replaceAll(" ","");
                java.net.URL url = PDFViewer.class.getResource("resources/annots/note_"+type+".png");
                if (url!=null) {
                    JRadioButton button = new JRadioButton();
                    button.setSelectedIcon(new ImageIcon(url));
                    Image image = ((ImageIcon)button.getSelectedIcon()).getImage();
                    image = GrayFilter.createDisabledImage(image);
                    button.setIcon(new ImageIcon(image));
                    button.setSelected(types[i].equals(annot.getType()));
                    button.addActionListener(selectionlistener);
                    button.setToolTipText(types[i]);
                    button.setMargin(new Insets(0, 0, 0, 0));
                    appearancepanel.add(button);
                    appearancegroup.add(button);
                }
            }
            JPanel space = new JPanel();
            space.setPreferredSize(new Dimension(20, 20));
            appearancepanel.add(space);
            Color c = annot.getColor();
            c = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(annot.getOpacity()/100f*255));
            JComponent colorbox = ColorChoicePanel.createColorChoiceButton(c, new ColorChoicePanel.ColorChoiceListener() {
                public void colorChosen(Color c) {
                    annot.setOpacity((int)(c.getAlpha()/255f*100));
                    annot.setType(annot.getType(), new Color(c.getRed(), c.getGreen(), c.getBlue()));
                    if (preferences!=null) {
                        ColorChoicePanel.saveColor(preferences, "feature.AnnotationNoteFactory.addDefaultColor", c);
                    }
                }
            }, ColorChoicePanel.TYPE_RECTANGLE, true, false, UIManager.getString("PDFViewer.annot.setColor"));
            appearancepanel.add(colorbox);
        }
        return panel;
    }

}
