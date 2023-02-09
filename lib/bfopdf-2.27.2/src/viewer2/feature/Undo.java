// $Id: Undo.java 34043 2019-10-23 01:10:31Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import javax.swing.*;
import javax.swing.undo.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.event.*;

/**
 * This features adds an "Undo" and "Redo" entry to the Edit menu, which interfaces
 * with the {@link DocumentPanel#fireUndoableEditEvent} method to provide undo/redo
 * across the Document.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">Undo</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.19
 */
public final class Undo extends ViewerFeature implements ActionListener, UndoableEditListener, DocumentPanelListener {

    /**
     * An UndableEdit which can be passed into {@link DocumentPanel#fireUndoableEditEvent}
     * to clear the list. This should be done when the list needs to be cleared, due to an
     * action on the Document that permanently changes the state of the PDF.
     */
    public static final UndoableEdit DISCARD = new AbstractUndoableEdit() { };

    private PDFViewer viewer;
    private Map<DocumentPanel,UndoManager> undomanagers;
    private JMenuItem undomenu, redomenu;

    public Undo() {
        super("Undo");
        undomanagers = new WeakHashMap<DocumentPanel,UndoManager>();
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == undomenu) {
            undo(viewer.getActiveDocumentPanel());
        } else if (e.getSource() == redomenu) {
            redo(viewer.getActiveDocumentPanel());
        }
    }

    /**
     * Perform an undo action on the specified panel
     */
    public void undo(DocumentPanel panel) {
        UndoManager manager = undomanagers.get(panel);
        if (manager.canUndo()) {
            manager.undo();
            update(panel);
        }
    }

    /**
     * Perform a redo action on the specified panel
     */
    public void redo(DocumentPanel panel) {
        UndoManager manager = undomanagers.get(panel);
        if (manager.canRedo()) {
            manager.redo();
            update(panel);
        }
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        this.viewer = viewer;
        undomenu = viewer.setMenu("Edit\tUndo", 'z', true, this);
        redomenu = viewer.setMenu("Edit\tRedo", 'y', true, this);
        viewer.addDocumentPanelListener(this);
        update(viewer.getActiveDocumentPanel());
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        if (type == "closing") {
            event.getDocumentPanel().removeUndoableEditListener(this);
            undomanagers.remove(event.getDocumentPanel());
            update(event.getDocumentPanel());
        } else if (type == "loaded") {
            event.getDocumentPanel().addUndoableEditListener(this);
            UndoManager undomanager;
            undomanagers.put(event.getDocumentPanel(), undomanager = new UndoManager());
            undomanager.setLimit(10);
            update(event.getDocumentPanel());
        } else if (type == "deactivated" || type == "activated") {
            update(event.getDocumentPanel());
        }
    }

    public void undoableEditHappened(UndoableEditEvent event) {
        DocumentPanel panel = (DocumentPanel)event.getSource();
        UndoManager manager = panel == null ? null : (UndoManager)undomanagers.get(panel);
        if (manager != null) {
            if (event.getEdit() == DISCARD) {
                manager.discardAllEdits();
            } else {
                manager.undoableEditHappened(event);
            }
            update(panel);
        }
    }

    private void update(DocumentPanel panel) {
        UndoManager manager = panel == null ? null : (UndoManager)undomanagers.get(panel);
        if (manager == null || !manager.canUndo()) {
            undomenu.setText(UIManager.getString("AbstractUndoableEdit.undoText"));
            undomenu.setEnabled(false);
        } else {
            undomenu.setText(manager.getUndoPresentationName());
            undomenu.setEnabled(true);
        }

        if (manager == null || !manager.canRedo()) {
            redomenu.setText(UIManager.getString("AbstractUndoableEdit.redoText"));
            redomenu.setEnabled(false);
        } else {
            redomenu.setText(manager.getRedoPresentationName());
            redomenu.setEnabled(true);
        }
    }

}
