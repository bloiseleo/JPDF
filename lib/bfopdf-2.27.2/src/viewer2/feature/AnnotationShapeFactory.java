// $Id: AnnotationShapeFactory.java 39802 2021-04-21 15:32:05Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.util.ColorChoicePanel;
import javax.swing.*;
import java.awt.geom.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.*;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Abstract superclass for factories creating annotations that handle {@link AnnotationShape} objects.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.7
 */
abstract class AnnotationShapeFactory extends AnnotationComponentFactory {

    /**
     * Return a new AnnotationShapeFactory
     */
    protected AnnotationShapeFactory(String name) {
        super(name);
    }

    public boolean matches(PDFAnnotation annot) {
        if (annot instanceof AnnotationShape) {
            return matchesShape((AnnotationShape) annot);
        }
        return false;
    }

    protected abstract boolean matchesShape(AnnotationShape annot);

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation a) {
        final AnnotationShape annot = (AnnotationShape)a;
        final JComponent comp = super.createComponent(pagepanel, a);
        makeComponentInteractive(comp, annot, !annot.isReadOnly(), true, true, pagepanel);
        return comp;
    }

    public JComponent createEditComponent(PDFAnnotation annot, boolean readonly, boolean create) {
        AnnotationShape annotShape = (AnnotationShape) annot;
        return createEditor(annotShape);
    }

    protected abstract JComponent createEditor(AnnotationShape annot);

    protected void copyAnnotationState(PDFAnnotation sourceannot, PDFAnnotation targetannot) {
        final AnnotationShape source = (AnnotationShape)sourceannot;
        final AnnotationShape target = (AnnotationShape)targetannot;
        super.copyAnnotationState(source, target);

        // perform translation to/from rect origin
        // this is necessary because the editor can only handle annotations
        // with an origin of 0,0
        // Still necessary? Shape doesn't seem to be used...
        boolean sourceIsOrigin = (target.getPage() == null);
        Shape sourceshape = sourceIsOrigin ? source.getShape() : target.getShape();
        Rectangle2D bounds = sourceshape.getBounds2D();
        Shape targetshape = null;
        float x = sourceIsOrigin ? (float)-bounds.getMinX() : (float)bounds.getMinX();
        float y = sourceIsOrigin ? (float)-bounds.getMinY() : (float)bounds.getMinY();
        targetshape = AffineTransform.getTranslateInstance(x, y).createTransformedShape(sourceshape);
        if (sourceIsOrigin) {
            target.setShape(targetshape);
        }
        target.setFirstLineEnding(source.getFirstLineEnding());
        target.setLastLineEnding(source.getLastLineEnding());
        target.setStyle(source.getStyle());
    }

    Color getLineColor(PDFStyle style) {
        Paint paint = style.getLineColor();
        return (paint instanceof Color) ? (Color) paint : Color.black;
    }

    Color getFillColor(PDFStyle style) {
        Paint paint = style.getFillColor();
        return (paint instanceof Color) ? (Color) paint : null;
    }

    static class BorderStyleEditor extends JPanel {

        private final AnnotationShape annot;
        private final JComboBox<Float> lineWeightings;
        private final JComboBox<String> borderStyles;
        private final boolean includeclouds;
        private Preferences preferences;
        private String prefkeyweight, prefkeydash;

        BorderStyleEditor(final AnnotationShape annot, boolean includeclouds) {
            this.annot = annot;
            this.includeclouds = includeclouds;
            PDFStyle currentStyle = annot.getStyle();
            setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(0, 0, 0, 5);
            c.gridx = 0;
            c.gridy = 0;
            c.weightx = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            lineWeightings = new JComboBox<Float>(new DefaultComboBoxModel<Float>(new Float[] {
                Float.valueOf(1),
                Float.valueOf(2),
                Float.valueOf(4),
                Float.valueOf(8)
            }));
            lineWeightings.setEditable(false);
            lineWeightings.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        Float val = (Float)lineWeightings.getSelectedItem();
                        if (val != null) {
                            PDFStyle style = annot.getStyle();
                            style.setLineWeighting(val.floatValue());
                            annot.setStyle(style);
                            if (preferences!=null && prefkeyweight!=null) {
                                preferences.putFloat(prefkeyweight, val.floatValue());
                            }
                        }
                    }
                }
            });
            lineWeightings.setRenderer(new ListCellRenderer<Float>() {
                public Component getListCellRendererComponent(JList<? extends Float> list, Float value, int index, final boolean isSelected, boolean cellHasFocus) {
                    if (value != null) {
                        final float lineWidth = value.floatValue();
                        return new JComponent() {
                            public Dimension getPreferredSize() {
                                return new Dimension(40, 15);
                            }
                            public Dimension getMinimumSize() {
                                return getPreferredSize();
                            }
                            public void paintComponent(Graphics g) {
                                super.paintComponent(g);
                                if (isSelected) {
                                    g.setColor(UIManager.getColor("List.selectionBackground"));
                                    g.fillRect(0, 0, getWidth(), getHeight());
                                }
                                if (g instanceof Graphics2D) {
                                    Graphics2D g2 = (Graphics2D) g;
                                    g2.setColor(getForeground());
                                    Rectangle bounds = getBounds();
                                    float yoffset = (float) bounds.height / 2;
                                    Line2D line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                                    g2.setStroke(new BasicStroke(lineWidth));
                                    g2.draw(line);
                                }
                            }
                        };
                    }
                    return new JLabel();
                }
            });
            lineWeightings.setSelectedItem(Float.valueOf(currentStyle.getLineWeighting()));
            add(lineWeightings, c);

            c.gridx = 1;
            c.insets = new Insets(0, 0, 0, 0);
            if (includeclouds) {
                borderStyles = new JComboBox<String>(new DefaultComboBoxModel<String>(new String[] {
                    "solid", "dash22", "dash33", "dash44", "dash4323", "dash43G3", "dash8444", "cloud1", "cloud2"
                }));
            } else {
                borderStyles = new JComboBox<String>(new DefaultComboBoxModel<String>(new String[] {
                    "solid", "dash22", "dash33", "dash44", "dash4323", "dash43G3", "dash8444"
                }));
            }
            borderStyles.setEditable(false);
            borderStyles.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        String val = (String)borderStyles.getSelectedItem();
                        if (val != null) {
                            PDFStyle style = annot.getStyle();
                            style.setLineDash(getDashArray(val), 0);
                            if (style.getLineDashPattern() == null) {
                                if (val.equals("cloud1")) {
                                    style.setFormStyle(PDFStyle.FORMSTYLE_CLOUDY1);
                                } else if (val.equals("cloud2")) {
                                    style.setFormStyle(PDFStyle.FORMSTYLE_CLOUDY2);
                                } else {
                                    style.setFormStyle(PDFStyle.FORMSTYLE_SOLID);
                                }
                            }
                            annot.setStyle(style);
                            if (preferences!=null && prefkeydash!=null) {
                                preferences.put(prefkeydash, val);
                            }
                        }
                    }
                }
            });
            borderStyles.setRenderer(new ListCellRenderer<String>() {
                public Component getListCellRendererComponent(JList<? extends String> list, final String value, int index, final boolean isSelected, boolean cellHasFocus) {
                    return new JComponent() {
                        public Dimension getPreferredSize() {
                            return new Dimension(40, 15);
                        }
                        public Dimension getMinimumSize() {
                            return getPreferredSize();
                        }
                        public void paintComponent(Graphics g) {
                            super.paintComponent(g);
                            if (isSelected) {
                                g.setColor(UIManager.getColor("List.selectionBackground"));
                                g.fillRect(0, 0, getWidth(), getHeight());
                            }
                            if (g instanceof Graphics2D) {
                                Graphics2D g2 = (Graphics2D) g;
                                g2.setColor(getForeground());
                                Rectangle bounds = getBounds();
                                float yoffset = (float) bounds.height / 2;
                                float[] dash = getDashArray(value);
                                if (dash != null) {
                                    g2.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1, dash, 0));
                                }
                                Shape line;
                                if (value.startsWith("cloud")) {
                                   line = new GeneralPath();
                                   int rad = "cloud2".equals(value) ? 8 : 4;
                                   for (int i=2;i<bounds.width-2-rad-rad;i+=rad*2) {
                                       ((GeneralPath)line).append(new Arc2D.Float(i, yoffset-rad*3f/2, rad*2, rad*2, 180, 180, Arc2D.OPEN), false);
                                   }
                                } else {
                                    line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                                }
                                g2.draw(line);
                            }
                        }
                    };
                }
            });
            borderStyles.setSelectedItem(getDashName(currentStyle));
            add(borderStyles, c);
        }

        public void setPreferences(Preferences preferences, String prefkeyweight, String prefkeydash) {
            this.preferences = preferences;
            this.prefkeyweight = prefkeyweight;
            this.prefkeydash = prefkeydash;
        }

        void addItemListener(ItemListener l) {
            lineWeightings.addItemListener(l);
            borderStyles.addItemListener(l);
        }

        static String getDashName(PDFStyle style) {
            float[] dasharray = style.getLineDashPattern();
            if (dasharray!=null) {
                float d0 = dasharray.length > 0 ? dasharray[0] : Float.NaN;
                float d1 = dasharray.length > 1 ? dasharray[1] : Float.NaN;
                float d2 = dasharray.length > 2 ? dasharray[2] : Float.NaN;
                float d3 = dasharray.length > 3 ? dasharray[3] : Float.NaN;
                if (d0==2 && (d1==2 || d1!=d1)) {
                    return "dash22";
                } else if (d0==3 && (d1==3 || d1!=d1)) {
                    return "dash33";
                } else if (d0==4 && (d1==4 || d1!=d1)) {
                    return "dash44";
                } else if (d0==4 && d1==3 && d2==2 && d3==3) {
                    return "dash4323";
                } else if (d0==4 && d1==3 && d2==16 && d3==3) {
                    return "dash43G3";
                } else if (d0==8 && d1==4 && d2==4 && d3==4) {
                    return "dash8444";
                } else {
                    return "solid";
                }
            } else if (style.getFormStyle()==PDFStyle.FORMSTYLE_CLOUDY1) {
                return "cloud1";
            } else if (style.getFormStyle()==PDFStyle.FORMSTYLE_CLOUDY2) {
                return "cloud2";
            } else {
                return "solid";
            }
        }

        static float[] getDashArray(String name) {
            if ("dash22".equals(name)) {
                return new float[] { 2, 2 };
            } else if ("dash33".equals(name)) {
                return new float[] { 3, 3 };
            } else if ("dash44".equals(name)) {
                return new float[] { 4, 4 };
            } else if ("dash4323".equals(name)) {
                return new float[] { 4, 3, 2, 3 };
            } else if ("dash43G3".equals(name)) {
                return new float[] { 4, 3, 16, 3 };
            } else if ("dash8444".equals(name)) {
                return new float[] { 8, 4, 4, 4 };
            } else {
                return null;
            }
        }

    }

}
