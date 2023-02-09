package org.faceless.pdf2.viewer2.util;

import java.awt.*;
import java.awt.geom.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.prefs.Preferences;

/**
 * JPanel (and associated dialog) that can be used to select a color
 */
public class ColorChoicePanel extends JPanel {

    /**
     * Value for {@link createColorChoiceButton} to create a "rectangle" button
     */
    public static final int TYPE_RECTANGLE = 0;

    /**
     * Value for {@link createColorChoiceButton} to create a "text" button
     */
    public static final int TYPE_TEXT = 2;

    /**
     * Value for {@link createColorChoiceButton} to create a "border" button
     */
    public static final int TYPE_BORDER = 3;

    /**
     * Value for {@link createColorChoiceButton} to create a "border" button
     */
    public static final int TYPE_ARROW = 4;

    /**
     * Color constant representing the "no color" choice. May be returned from
     * {@link #getColor} if "none" is set to true in the constructor.
     */
    public static final Color NONE = new Color(0, 1, 2, 3);

    private Color color;
    private JSlider rslider, gslider, bslider, aslider;
    private JPanel newcolor, swatchpanel;
    private boolean isadjusting;
    private static final TexturePaint tile;

    private static final int[] swatches = {
        0x000000, 0xaa4000, 0x404000, 0x004000, 0x004055, 0x000080, 0x4040aa, 0x404040,
        0x800000, 0xff5500, 0x808000, 0x00aa00, 0x008080, 0x0000ff, 0x5555aa, 0x808080,
        0xff0000, 0xffaa00, 0xaabf00, 0x40aa55, 0x40bfbf, 0x4055ff, 0x800080, 0xaaaaaa,
        0xff00ff, 0xffbf00, 0xffff00, 0x00ff00, 0x00ffff, 0x00bfff, 0xaa4055, 0xbfbfbf,
        0xffaabf, 0xffbfaa, 0xffffaa, 0xbfffbf, 0xbfffff, 0xaabfff, 0xbfaaff, 0xffffff
    };

    static {
        BufferedImage tileimage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tileg = (Graphics2D)tileimage.createGraphics();
        tileg.setColor(Color.white);
        tileg.fillRect(0, 0, tileimage.getWidth(), tileimage.getHeight());
        tileg.setColor(new Color(0xAAAAAA, false));
        tileg.fillRect(0, 0, 5, 5);
        tileg.fillRect(5, 5, 5, 5);
        tile = new TexturePaint(tileimage, new Rectangle2D.Float(0, 0, 10, 10));
    }

    /**
     * Create a new ColorChoicePanel
     * @param color the initial color
     * @param alpha whether to allow Alpha values
     * @param none whether to allow {@link #NONE} as a valid choice
     */
    public ColorChoicePanel(Color color, boolean alpha, boolean none) {
        super(new GridBagLayout());
        if (color==null) {
            if (none) {
                color = NONE;
            } else {
                throw new NullPointerException("Color is null");
            }
        }
        swatchpanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 1;
        for (int i=0;i<swatches.length;i++) {
            final Color c = none && i==0 ? NONE : none && i==1 ? Color.black : new Color(swatches[i], false);
            JButton button = new JButton() {
                public void paintComponent(Graphics g) {
                    paintSwatch(this, g, c, 2);
                    boolean selected = c.equals(getColor());
                    if (selected) {
                        g.setColor(Color.red);
                        g.drawRect(0, 0, getWidth()-1, getHeight()-1);
                    }
                }
            };
            button.setPreferredSize(UIManager.getDimension("PDFViewer.ViewerWidget.iconSize"));
            button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setColor(c);
                    repaint();
                }
            });
            gbc.gridwidth = i%8==7 ? GridBagConstraints.REMAINDER : 1;
            swatchpanel.add(button, gbc);
        }

        ChangeListener listener = new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (!isadjusting) {
                    int r = rslider.getValue();
                    int g = gslider.getValue();
                    int b = bslider.getValue();
                    int a = aslider==null ? 255 : aslider.getValue();
                    setColor(new Color(r, g, b, a));
                }
            }
        };
        JPanel sliders = new JPanel(new GridBagLayout());
        GridBagConstraints gbc1 = new GridBagConstraints();
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc1.fill = gbc2.HORIZONTAL;
        gbc1.gridwidth = GridBagConstraints.REMAINDER;
        rslider = createSlider(Color.red, 100, listener);
        gslider = createSlider(Color.green, 100, listener);
        bslider = createSlider(Color.blue, 100, listener);
        sliders.add(rslider, gbc1);
        sliders.add(gslider, gbc1);
        sliders.add(bslider, gbc1);

        JPanel grid = new JPanel(new GridLayout(1, 2));
        grid.add(swatchpanel);
        grid.add(sliders);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = gbc.BOTH;
        add(grid, gbc);

        if (alpha) {
            aslider = createSlider(tile, 100, listener);
            add(aslider, gbc);
        }
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        gbc.fill = gbc.BOTH;
        gbc.insets = new Insets(4, 4, 4, 4);
        final Color orig = color==NONE ? NONE : new Color(color.getRGB(), true);
        JButton origcolor = new JButton() {
            public void paintComponent(Graphics g) {
                paintSwatch(this, g, orig, 0);
            }
        };
        origcolor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setColor(orig);
                repaint();
            }
        });
        origcolor.setPreferredSize(new Dimension(20, 20));

        newcolor = new JPanel() {
            public void paintComponent(Graphics g) {
                paintSwatch(this, g, getColor(), 0);
            }
        };
        newcolor.setPreferredSize(new Dimension(20, 20));
        add(origcolor, gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        add(newcolor, gbc);
        setColor(color);
    }

    private void paintSwatch(Component comp, Graphics g, Color color, int margin) {
        if (color==NONE) {
            Shape clip = ((Graphics2D)g).getClip();
            ((Graphics2D)g).setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
            g.setColor(Color.white);
            g.clipRect(margin, margin, comp.getWidth()-margin*2, comp.getHeight()-margin*2);
            g.fillRect(margin, margin, comp.getWidth()-margin*2, comp.getHeight()-margin*2);
            g.setColor(Color.red);
            ((Graphics2D)g).setStroke(new BasicStroke(2));
            g.drawLine(margin, comp.getHeight()-margin-1, comp.getWidth()-margin-1, margin);
            ((Graphics2D)g).setStroke(new BasicStroke(1));
            ((Graphics2D)g).setClip(clip);
        } else {
            if (color.getAlpha()!=255) {
                ((Graphics2D)g).setPaint(tile);
                g.fillRect(margin, margin, comp.getWidth()-margin*2, comp.getHeight()-margin*2);
            }
            g.setColor(color);
            g.fillRect(margin, margin, comp.getWidth()-margin*2, comp.getHeight()-margin*2);
        }
        g.setColor(Color.black);
        g.drawRect(margin, margin, comp.getWidth()-margin*2-1, comp.getHeight()-margin*2-1);
    }

    private JSlider createSlider(final Paint knob, int w, ChangeListener listener) {
        JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, 255, 0);
        Dimension pref = slider.getPreferredSize();
        pref = new Dimension(100, (int)pref.getHeight());
        slider.setPreferredSize(pref);
        slider.addChangeListener(listener);
        slider.setUI(new javax.swing.plaf.basic.BasicSliderUI(slider) {
            protected Dimension getThumbSize() {
                return new Dimension(14, 14);
            }
            public void paintThumb(Graphics g) {
                if (slider.isEnabled()) {
                    Rectangle knobBounds = thumbRect;
                    int w = knobBounds.width;
                    int h = knobBounds.height;
                    g.translate(knobBounds.x, knobBounds.y);
                    g.setColor(new Color(0,true));
                    g.fillRect(0, 0, w, h);
                    ((Graphics2D)g).setPaint(knob);
                    g.fillOval(0, 0, w, h);
                    g.setColor(Color.black);
                    if (knob==tile) {
                        g.fillArc(0, 0, w, h, -90, 180);
                    }
                    g.drawOval(0, 0, w-1, h-1);
                }
            }
        });
        return slider;
    }

    /**
     * Get the currently selected color
     */
    public synchronized Color getColor() {
        return color;
    }

    /**
     * Set the currently selected color
     */
    public synchronized void setColor(Color c) {
        this.color = c;
        isadjusting = true;
        if (color==NONE) {
            rslider.setEnabled(false);
            gslider.setEnabled(false);
            bslider.setEnabled(false);
            if (aslider!=null) {
                aslider.setEnabled(false);
            }
        } else {
            rslider.setEnabled(true);
            gslider.setEnabled(true);
            bslider.setEnabled(true);
            rslider.setValue(color.getRed());
            gslider.setValue(color.getGreen());
            bslider.setValue(color.getBlue());
            if (aslider!=null) {
                aslider.setEnabled(true);
                aslider.setValue(color.getAlpha());
            }
        }
        newcolor.repaint();
        for (int i=0;i<swatchpanel.getComponentCount();i++) {
            swatchpanel.getComponent(i).repaint();
        }
        isadjusting = false;
    }

    /*
    public static Color showColorChoiceDialog(Component root, Color color, boolean alpha, boolean none) {
        ColorChoicePanel chooser = new ColorChoicePanel(color, alpha, none);
        DialogPanel dialog = new DialogPanel();
        dialog.addComponent(chooser);
        if (dialog.showDialog(root, UIManager.getString("PDFViewer.annot.SetColor"))) {
            return chooser.getColor();
        } else {
            return null;
        }
    }
    */

    /**
     * Create a "choose color" button for the specified annotation
     */
    public static JComponent createColorChoiceButton(Color initial, final ColorChoiceListener listener, final int type, final boolean alpha, final boolean none, final String tooltip) {
        final Color[] color = new Color[] { initial };

        final JButton button = new JButton() {
            public void paintComponent(Graphics gg) {
                Graphics2D g = (Graphics2D)gg;
                gg.setColor(new Color(0, true));
                gg.fillRect(0, 0, getWidth(), getHeight());
                float border;
                if (UIManager.getLookAndFeel()!=null && UIManager.getLookAndFeel().getClass().getName().equals("com.sun.java.swing.plaf.windows.WindowsLookAndFeel")) {
                    border = 3;
                } else {
                    border = 1;
                }
                Color bordercolor = new Color(0x80000000, true);

                g.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
                Shape path;
                if (type==TYPE_TEXT) {
                    path = new Font("Serif", Font.BOLD, 0).deriveFont((getHeight()-(border*2))/0.7f).createGlyphVector(new FontRenderContext(new AffineTransform(), true, true), "A").getOutline();
                    if (!(path instanceof GeneralPath)) {
                        path = new GeneralPath(path);
                    }
                    Rectangle2D bounds = path.getBounds2D();
                    ((GeneralPath)path).transform(AffineTransform.getTranslateInstance((getWidth() - bounds.getWidth())/2, getHeight()-(getHeight()-bounds.getHeight())/2));
                } else if (type==TYPE_BORDER) {
                    path = new GeneralPath(new Rectangle(0, 0, getWidth()-1, getHeight()-1));
                    ((GeneralPath)path).append(new Rectangle(5, 5, getWidth()-11, getHeight()-11), false);
                    ((GeneralPath)path).setWindingRule(GeneralPath.WIND_EVEN_ODD);
                } else if (type==TYPE_ARROW) {
                    GeneralPath gpath = new GeneralPath();
                    gpath.moveTo(0.1f, 0.7f);
                    gpath.lineTo(0.3f, 0.5f);
                    gpath.lineTo(0.2f, 0.4f);
                    gpath.lineTo(0.9f, 0.1f);
                    gpath.lineTo(0.6f, 0.8f);
                    gpath.lineTo(0.5f, 0.7f);
                    gpath.lineTo(0.3f, 0.9f);
                    gpath.transform(new AffineTransform(getWidth(), 0, 0, getHeight(), 0, 0));
                    path = gpath;
                } else {
                    path = new Rectangle(0, 0, getWidth()-1, getHeight()-1);
                }
                g.setPaint(tile);
                g.fill(path);
                g.setPaint(color[0]);
                g.fill(path);
                g.setColor(bordercolor);
                g.draw(path);
            }
        };
        button.setOpaque(false);
        button.setPreferredSize(new Dimension(20, 20));
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                Component src = evt.getSource() instanceof Component ? (Component)evt.getSource() : null;

                ColorChoicePanel chooser = new ColorChoicePanel(color[0], alpha, none);
                DialogPanel dialog = new DialogPanel();
                dialog.addComponent(chooser);
                if (dialog.showDialog(button, UIManager.getString("PDFViewer.annot.SetColor"))) {
                    color[0] = chooser.getColor();
                    button.repaint();
                    listener.colorChosen(color[0]);
                }
            }
        });
        if (tooltip!=null) {
            button.setToolTipText(tooltip);
        }
        return button;
    }

    /**
     * Passed in to the {@link #createColorChoiceButton}, this is a callback when the
     * color is chosen
     */
    public static interface ColorChoiceListener {
        public void colorChosen(Color color);
    }

    /**
     * Load a color with the specified key from the specified {@link Preferences}
     */
    public static Color loadColor(Preferences prefs, String key, Color defaultcolor) {
        String val = prefs.get(key, defaultcolor==null ? "null" : Integer.toHexString(defaultcolor.getRGB()));
        return val.equals("null") ? null : new Color((int)Long.parseLong(val, 16), true);
    }

    /**
     * Save a color with the specified key to the specified {@link Preferences}
     */
    public static void saveColor(Preferences prefs, String key, Color color) {
        prefs.put(key, color==null ? "null" : Integer.toHexString(color.getRGB()));
    }

    /*
    public static void main(String[] args) throws Exception {
        JFrame panel = new JFrame();
        panel.setContentPane(createColorChoiceButton(Color.red, new ColorChoiceListener() {
            public void colorChosen(Color c) {
                System.out.println("COLOR="+c);
            }
        }, TYPE_RECTANGLE, true, true, "Choose a Color"));
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension windowSize = panel.getSize();
        panel.setLocation(Math.max(0, (screenSize.width - windowSize.width) / 2), Math.max(0, (screenSize.height - windowSize.height) / 2));
        panel.setVisible(true);
    }
    */
}
