// $Id: FullScreenMode.java 21231 2015-04-10 16:06:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Create a menu item that will display the Document in "Full Screen" mode.
 * Pressing escape will exit form this mode. This feature may also be run
 * as the result of a named action or the documents initial view setting
 * (see {@link PDF#setOption PDF.setOption("view.fullscreen")}).
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FullScreen</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.2
 */
public class FullScreenMode extends ViewerWidget {

    private Container parent;
    private int index;

    public FullScreenMode() {
        super("FullScreen");
        setMenu("View\tDisplay\tFullScreenMode", 'l');
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
    }

    public void action(final ViewerEvent event) {
        setFullScreen(parent==null, event.getDocumentPanel().getViewport());
    }

    /**
     * Set the specified Viewport to be the fullscreen viewport or not
     * @param fullscreen whether to display the viewport as fullscreen
     * @param viewport the DocumentViewport
     */
    public void setFullScreen(final boolean fullscreen, final DocumentViewport viewport) {
        boolean oldfullscreen = parent!=null;
        GraphicsDevice device = viewport.getGraphicsConfiguration().getDevice();
        if (oldfullscreen==fullscreen || !device.isFullScreenSupported()) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    setFullScreen(fullscreen, viewport);
                }
            });
            return;
        }

        if (fullscreen) {
            parent = viewport.getParent();
            for (int i=0;i<parent.getComponentCount();i++) {
                if (parent.getComponent(i)==viewport) {
                    index = i;
                    break;
                }
            }
            JFrame fullscreenframe = new JFrame(device.getDefaultConfiguration());
            fullscreenframe.setUndecorated(true);
            fullscreenframe.setResizable(false);
            fullscreenframe.setContentPane(viewport);
            device.setFullScreenWindow(fullscreenframe);
            fullscreenframe.validate();
            InputMap inputmap = viewport.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            ActionMap actionmap = viewport.getActionMap();
            inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitFullScreen");
            actionmap.put("exitFullScreen", new AbstractAction() {
                public void actionPerformed(ActionEvent event) {
                    setFullScreen(false, viewport);
                }
            });
            viewport.setFocusable(true);
            viewport.requestFocusInWindow();
            PDFPage page = viewport.getPage();
            if (page != null) {
                viewport.getDocumentPanel().runAction(PDFAction.goToFit(page));
            }
        } else {
            JFrame frame = (JFrame)device.getFullScreenWindow();
            frame.setVisible(false);
            InputMap inputmap = viewport.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            inputmap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
            viewport.getActionMap().remove("exitFullScreen");

            if (index == 0) {
                parent.add(viewport);
            } else {
                parent.add(viewport, index);
            }
            device.setFullScreenWindow(null);
            // Ugly solution to odd problem - on returning from fullscreen the
            // window is invalid. If we set it to valid it briefly appears then
            // gets invalidated again. Don't know why, but it happens on OS X 1.6u7
            // and Windows/Sun 1.6u10. So give it 1/2 a second and revalidate again.
            new Thread() {
                public void run() {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {}
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            viewport.revalidate();
                            viewport.repaint();
                        }
                    });
                }
            }.start();
            parent = null;
        }
    }
}
