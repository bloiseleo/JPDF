// $Id: LineEndingSelector.java 19740 2014-07-22 13:39:16Z mike $

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

class LineEndingSelector extends JComboBox<String> implements ItemListener, ListCellRenderer<String> {

    private Preferences preferences;
    private String prefkey;

    static class LineEndingDataModel implements ComboBoxModel<String> {

        final String[] contents = new String[] {
            "None", "Square", "Circle", "Diamond", "OpenArrow", "ClosedArrow",
            "Butt", "ROpenArrow", "RClosedArrow", "Slash"
        };
        String selected = "None";

        public int getSize() {
            return contents.length;
        }

        public String getElementAt(int index) {
            return contents[index];
        }

        public void addListDataListener(ListDataListener l) {
        }

        public void removeListDataListener(ListDataListener l) {
        }

        public void setSelectedItem(Object item) {
            selected = item instanceof String ? (String)item : null;
        }

        public String getSelectedItem() {
            return selected;
        }

    }

    final boolean last;

    LineEndingSelector(String ending, boolean last) {
        super(new LineEndingDataModel());
        this.last = last;
        setEditable(false);
        addItemListener(this);
        setRenderer(this);
        ((LineEndingDataModel) getModel()).selected = ending;
    }

    void setPreferences(Preferences preferences, String prefkey) {
        this.preferences = preferences;
        this.prefkey = prefkey;
    }

    String getLineEnding() {
        return (String)getSelectedItem();
    }

    public void itemStateChanged(ItemEvent e) {
        String end = getLineEnding();
        if (preferences!=null && prefkey!=null) {
            preferences.put(prefkey, end.toString());
        }
    }

    public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
        if ("None".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        Line2D line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                        g2.draw(line);
                    }
                }
            };
        } else if ("Square".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        Line2D line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                        g2.draw(line);
                        float xoffset = last ? (float) bounds.width - 9 : 2;
                        Rectangle2D box = new Rectangle2D.Float(xoffset, yoffset - 3, 7, 7);
                        g2.fill(box);
                    }
                }
            };
        } else if ("Circle".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        Line2D line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                        g2.draw(line);
                        float xoffset = last ? (float) bounds.width - 9 : 2;
                        Ellipse2D circle = new Ellipse2D.Float(xoffset, yoffset - 3, 7, 7);
                        g2.fill(circle);
                    }
                }
            };
        } else if ("Diamond".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        Line2D line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                        g2.draw(line);
                        float xoffset = last ? (float) bounds.width - 10 : 2;
                        GeneralPath path = new GeneralPath();
                        path.moveTo(xoffset, yoffset);
                        path.lineTo(xoffset + 4, yoffset - 3);
                        path.lineTo(xoffset + 8, yoffset);
                        path.lineTo(xoffset + 4, yoffset + 3);
                        path.closePath();
                        g2.fill(path);
                    }
                }
            };
        } else if ("OpenArrow".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        Line2D line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                        g2.draw(line);
                        if (last) {
                            float xoffset = (float) bounds.width;
                            line = new Line2D.Float(xoffset - 10, yoffset - 3, xoffset - 2, yoffset);
                            g2.draw(line);
                            line = new Line2D.Float(xoffset - 10, yoffset + 3, xoffset - 2, yoffset);
                            g2.draw(line);
                        } else {
                            line = new Line2D.Float(2, yoffset, 8, yoffset - 3);
                            g2.draw(line);
                            line = new Line2D.Float(2, yoffset, 8, yoffset + 3);
                            g2.draw(line);
                        }
                    }
                }
            };
        } else if ("ClosedArrow".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        Line2D line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                        g2.draw(line);
                        GeneralPath path = new GeneralPath();
                        if (last) {
                            float xoffset = (float) bounds.width;
                            path.moveTo(xoffset - 10, yoffset - 3);
                            path.lineTo(xoffset - 2, yoffset);
                            path.lineTo(xoffset - 10, yoffset + 3);
                            path.closePath();
                        } else {
                            path.moveTo(8, yoffset - 3);
                            path.lineTo(2, yoffset);
                            path.lineTo(8, yoffset + 3);
                            path.closePath();
                        }
                        g2.fill(path);
                    }
                }
            };
        } else if ("Butt".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        Line2D line = new Line2D.Float(2, yoffset, (float) bounds.width - 2, yoffset);
                        g2.draw(line);
                        float xoffset = last ? (float) bounds.width - 2 : 2;
                        line = new Line2D.Float(xoffset, yoffset - 4, xoffset, yoffset + 4);
                        g2.draw(line);
                    }
                }
            };
        } else if ("ROpenArrow".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        float xoffset = (float) bounds.width;
                        Line2D line;
                        if (last) {
                            line = new Line2D.Float(2, yoffset, xoffset - 10, yoffset);
                            g2.draw(line);
                            line = new Line2D.Float(xoffset - 2, yoffset - 3, bounds.width - 10, yoffset);
                            g2.draw(line);
                            line = new Line2D.Float(xoffset - 2, yoffset + 3, xoffset - 10, yoffset);
                            g2.draw(line);
                        } else {
                            line = new Line2D.Float(8, yoffset, xoffset - 2, yoffset);
                            g2.draw(line);
                            line = new Line2D.Float(2, yoffset - 3, 8, yoffset);
                            g2.draw(line);
                            line = new Line2D.Float(2, yoffset + 3, 8, yoffset);
                            g2.draw(line);
                        }
                    }
                }
            };
        } else if ("RClosedArrow".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        float xoffset = (float) bounds.width;
                        GeneralPath path = new GeneralPath();
                        Line2D line;
                        if (last) {
                            line = new Line2D.Float(2, yoffset, xoffset - 10, yoffset);
                            path.moveTo(xoffset - 2, yoffset - 3);
                            path.lineTo(xoffset - 10, yoffset);
                            path.lineTo(xoffset - 2, yoffset + 3);
                            path.closePath();
                        } else {
                            line = new Line2D.Float(8, yoffset, xoffset - 2, yoffset);
                            path.moveTo(2, yoffset - 3);
                            path.lineTo(8, yoffset);
                            path.lineTo(2, yoffset + 3);
                            path.closePath();
                        }
                        g2.draw(line);
                        g2.fill(path);
                    }
                }
            };
        } else if ("Slash".equals(value)) {
            return new JComponent() {
                public Dimension getPreferredSize() {
                    return new Dimension(40, 15);
                }
                public Dimension getMinimumSize() {
                    return getPreferredSize();
                }
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (g instanceof Graphics2D) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(getForeground());
                        Rectangle bounds = getBounds();
                        float yoffset = (float) bounds.height / 2;
                        float xoffset = (float) bounds.width;
                        Line2D line;
                        if (last) {
                            line = new Line2D.Float(2, yoffset, xoffset - 4, yoffset);
                            g2.draw(line);
                            line = new Line2D.Float(xoffset - 2, yoffset - 4, xoffset - 6, yoffset + 4);
                            g2.draw(line);
                        } else {
                            line = new Line2D.Float(4, yoffset, xoffset - 2, yoffset);
                            g2.draw(line);
                            line = new Line2D.Float(6, yoffset - 4, 2, yoffset + 4);
                            g2.draw(line);
                        }
                    }
                }
            };
        } else {
            return new JLabel(value.toString());
        }
    }
}

