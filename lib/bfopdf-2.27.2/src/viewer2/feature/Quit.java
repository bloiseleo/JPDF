// $Id: Quit.java 41473 2021-10-27 11:11:07Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;

/**
 * Create a menu item that will quit the application - ie. it calls <code>System.exit(0)</code>.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">Quit</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class Quit extends ViewerWidget {

    private final Action action;

    public Quit() {
        super("Quit");
        setDocumentRequired(false);
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                Quit.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
    }

    public void initialize(PDFViewer viewer) {
        // On Windows this menu item is called "Exit" and has Alt-F4 as a shortcut. Everywhere
        // else it's called "Quit"
        if (Util.isLAFWindows()) {
            setMenu("File\tExit(999)");
        } else {
            setMenu("File\tQuit(999)", 'q');
        }
        super.initialize(viewer);
        JMenuItem menu = (JMenuItem)viewer.getNamedComponent("MenuQuit");
        if (menu != null) {
            if (Util.isLAFWindows()) {
                menu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
            }
        }
    }

    public void action(ViewerEvent event) {
        // While we could just call System.exit() here, it's a bit more polite to
        // instead trigger a close event on the Frame. This will ensure that "quit"
        // has the same effect as clicking the close icon on the frame, and that the
        // user is prompted re. closing dirty frames. (change made in 2.23.4)
        //
        JFrame window = (JFrame)SwingUtilities.getAncestorOfClass(JFrame.class, event.getViewer());
        window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
    }

}
