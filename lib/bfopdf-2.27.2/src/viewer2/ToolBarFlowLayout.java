// $Id: ToolBarFlowLayout.java 24902 2017-02-28 13:41:05Z mike $

package org.faceless.pdf2.viewer2;

import java.awt.*;

/**
 * Custom version of FlowLayout that wraps components onto the next line
 * when necessary.
 */
class ToolBarFlowLayout extends FlowLayout {

    public ToolBarFlowLayout() {
        super(FlowLayout.LEFT, 0, 0);
    }

    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int w = Integer.MAX_VALUE;
            int h = 0;
            boolean anyVisible = false;
            int n = target.getComponentCount();

            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (c.isVisible()) {
                    anyVisible = true;
                    Dimension ps = c.getPreferredSize();
                    w = Math.min(w, ps.width);
                    h = Math.max(h, ps.height);
                }
            }
            return anyVisible ? new Dimension(w, h) : new Dimension(0, 0);
        }
    }

    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int hgap = getHgap();
            int vgap = getVgap();
            int w = target.getWidth();
            if (w == 0) {
                w = Integer.MAX_VALUE;
            }
            Insets insets = target.getInsets();
            if (insets == null) {
                insets = new Insets(0, 0, 0, 0);
            }

            int maxwidth = w - (insets.left + insets.right + hgap * 2);
            int n = target.getComponentCount();
            int x = 0, y = 0;
            int rw = 0, rh = 0;
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (c.isVisible()) {
                    Dimension ps = c.getPreferredSize();
                    rh = Math.max(rh, ps.height);
                }
            }
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (c.isVisible()) {
                    Dimension ps = c.getPreferredSize();
                    x += ps.width;
                    if (x > maxwidth) {
                        x = ps.width;
                        if (y > 0) {
                            y += vgap;
                        }
                        y += rh;
                    }
                    x += hgap;
                    rw = Math.max(rw, x);
                }
            }
            return new Dimension(rw + insets.left + insets.right, y + rh + insets.top + insets.bottom);
        }
    }

    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            int hgap = getHgap();
            int vgap = getVgap();
            int w = target.getWidth();
            if (w == 0) {
                return;
            }
            Insets insets = target.getInsets();
            if (insets == null) {
                insets = new Insets(0, 0, 0, 0);
            }
            int n = target.getComponentCount();
            // row height = max component height
            int rh = 0;
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (c.isVisible()) {
                    Dimension ps = c.getPreferredSize();
                    rh = Math.max(rh, ps.height);
                }
            }
            int x = 0, y = 0;
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (c.isVisible()) {
                    Dimension ps = c.getPreferredSize();
                    c.setBounds(x + insets.left, y + insets.top, ps.width, rh);
                    x += ps.width;
                    if (x > w) {
                        x = 0;
                        if (y > 0) {
                            y += vgap;
                        }
                        y += rh;
                        c.setBounds(x + insets.left, y + insets.top, ps.width, rh);
                        x += ps.width;
                    }
                    x += hgap;
                }
            }
        }
    }

}

