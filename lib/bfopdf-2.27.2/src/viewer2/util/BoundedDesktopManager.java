// $Id: BoundedDesktopManager.java 32357 2019-05-22 17:02:19Z mike $

package org.faceless.pdf2.viewer2.util;

import javax.swing.*;
import java.awt.*;

/**
 * DesktopManager which ensures JInternalFrames cannot be dragged out of view.
 * Derived from
 * <a href="http://stackoverflow.com/questions/8136944/preventing-jinternalframe-from-being-moved-out-of-a-jdesktoppane">http://stackoverflow.com/questions/8136944/preventing-jinternalframe-from-being-moved-out-of-a-jdesktoppane</a>
 */
public class BoundedDesktopManager extends DefaultDesktopManager {

    @Override
    public void beginDraggingFrame(JComponent f) {
        // Don't do anything. Needed to prevent the DefaultDesktopManager setting the dragMode
    }

    @Override
    public void beginResizingFrame(JComponent f, int direction) {
        // Don't do anything. Needed to prevent the DefaultDesktopManager setting the dragMode
    }

    @Override
    public void setBoundsForFrame(JComponent f, int newX, int newY, int newWidth, int newHeight) {
        if (f instanceof JInternalFrame) {
            JDesktopPane pane = ((JInternalFrame)f).getDesktopPane();
            newX = Math.max(newX, HMARGIN - newWidth);
            newY = Math.max(newY, 0);
            newX = Math.min(newX, pane.getWidth() - HMARGIN);
            newY = Math.min(newY, pane.getWidth() - VMARGIN);
            f.setBounds(newX, newY, newWidth, newHeight);
            if (f.getWidth() != newWidth || f.getHeight() != newHeight) {
                f.validate();
            }
        }
    }

    private static final int HMARGIN = 50, VMARGIN = 20;        // Arbitrary but these give plenty of latitude

}
