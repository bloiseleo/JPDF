// $Id: EditMenu.java 23409 2016-05-23 12:01:45Z chris $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import java.beans.*;
import java.awt.*;
import java.awt.event.*;

/**
 * This features adds an "Edit" menu to the application, which provides a familiar
 * interface for those components allowing cut, copy, paste and text selection in the
 * viewer - although those actions will still be available if this feature is not added to the
 * viewer.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">EditMenu</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.3
 */
public final class EditMenu extends ViewerFeature
{
    private JComponent focusOwner;
    private JMenuItem[] menus;
    private PropertyChangeListener pclistener;

    public EditMenu() {
        super("EditMenu");
        menus = new JMenuItem[4];
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (focusOwner != null) {
                    String action = (String)e.getActionCommand();
                    Action a = focusOwner.getActionMap().get(action);
                    if (a != null) {
                        a.actionPerformed(new ActionEvent(focusOwner, ActionEvent.ACTION_PERFORMED, null));
                    }
                }
            }
        };
        JMenu menu = viewer.getMenu("Edit");
        int count = menu.getMenuComponentCount();
        if (count > 0 && !(menu.getMenuComponent(count - 1) instanceof JPopupMenu.Separator)) {
            menu.addSeparator();
        }
        menus[0] = viewer.setMenu("Edit\tCut", 'x', true, listener);
        menus[1] = viewer.setMenu("Edit\tCopy", 'c', true, listener);
        menus[2] = viewer.setMenu("Edit\tPaste", 'v', true, listener);
        menus[3] = viewer.setMenu("Edit\tSelectAll", 'a', true, listener);

        menus[0].setActionCommand((String)TransferHandler.getCutAction().getValue(Action.NAME));
        menus[1].setActionCommand((String)TransferHandler.getCopyAction().getValue(Action.NAME));
        menus[2].setActionCommand((String)TransferHandler.getPasteAction().getValue(Action.NAME));
        menus[3].setActionCommand(DefaultEditorKit.selectAllAction);

        updateMenuEnabled(null, null);

        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addPropertyChangeListener("permanentFocusOwner", pclistener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                JComponent comp = e.getNewValue() instanceof JComponent ? (JComponent)e.getNewValue() : null;
                updateMenuEnabled(focusOwner, comp);
                focusOwner = comp;
            }
        });
    }

    public void teardown() {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.removePropertyChangeListener("permanentFocusOwner", pclistener);
    }

    private void updateMenuEnabled(JComponent oldfocus, JComponent newfocus) {
        for (int i=0;i<menus.length;i++) {
            String command = menus[i].getActionCommand();
            Action newaction = newfocus!=null ? newfocus.getActionMap().get(command) : null;
            menus[i].setEnabled(newaction!=null);
        }
    }
}
