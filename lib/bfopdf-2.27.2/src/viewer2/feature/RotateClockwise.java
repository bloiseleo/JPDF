// $Id: RotateClockwise.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.undo.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.beans.*;

/**
 * Create a button that will rotate the page 90 degrees clockwise.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">RotateClockwise</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.3
 */
public class RotateClockwise extends ViewerWidget implements ThumbnailPanel.ThumbnailSelectionAction {

    private final RotateAction action;
    private final int diff;

    public RotateClockwise() {
        this("RotateClockwise", 90);
    }

    public RotateClockwise(String name, int diff) {
        super(name);
        this.diff = diff;
        setButton("Edit", "PDFViewer.Feature."+name+".icon", "PDFViewer.tt."+name);
        action = new RotateAction(this, diff, false);
    }

    protected ActionListener createActionListener() {
        return action;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        // Only add a separator before if the predecing item is not
        // a RotateClockwise menu item. This ensures that RotateClockwise
        // and RotateAntiClockwise are grouped.
        JMenu menu = viewer.getMenu("Edit");
        int count = menu.getMenuComponentCount();
        if (count > 0) {
            Component last = menu.getMenuComponent(count - 1);
            if (last instanceof JMenuItem) {
                JMenuItem lastItem = (JMenuItem) last;
                if (!(lastItem.getClientProperty("bfo.RotateClockwise") instanceof RotateClockwise)) {
                    menu.addSeparator();
                }
            }
        }
        JMenuItem item = viewer.setMenu("Edit\t" + getName(), '\u0000', true, action);
        item.putClientProperty("bfo.RotateClockwise", this);
        viewer.addDocumentPanelListener(action);
    }

    public boolean isButtonEnabledByDefault() {
        return false;
    }

    public Action getAction(final ThumbnailPanel.View view) {
        if (!view.isFactoryEditable()) {
            return null;
        }

        RotateAction action = new RotateAction(this, diff, true) {
            List<PDFPage> getPages() {
                List<PDFPage> l = new ArrayList<PDFPage>();
                for (int i=0;i<view.getComponentCount();i++) {
                    ThumbnailPanel.SinglePagePanel panel = (ThumbnailPanel.SinglePagePanel)view.getComponent(i);
                    if (panel.isSelected()) {
                        l.add(panel.getPage());
                    }
                }
                return l;
            }
        };
        view.getDocumentPanel().addDocumentPanelListener(action);
        return action;
    }

    private static class RotateAction extends AbstractAction implements DocumentPanelListener {
        protected DocumentPanel docpanel;
        private boolean documentpanelspecific;
        private final int diff;

        RotateAction(RotateClockwise feature, int diff, boolean documentpanelspecific) {
            this.documentpanelspecific = documentpanelspecific;
            this.diff = diff;
            if (diff == 90) {
                putValue(Action.NAME, UIManager.getString("PDFViewer.RotateClockwise"));
            } else if (diff == -90) {
                putValue(Action.NAME, UIManager.getString("PDFViewer.RotateAntiClockwise"));
            } else {
                throw new IllegalArgumentException();
            }
        }

        List<PDFPage> getPages() {
            PDFViewer viewer = docpanel.getViewer();
            if (viewer != null) {
                return Collections.singletonList(viewer.getActiveDocumentPanel().getPage());
            }
            return Collections.<PDFPage>emptyList();
        }

        public void actionPerformed(ActionEvent event) {
            if (docpanel != null) {
                final List<PDFPage> pages = new ArrayList<PDFPage>(getPages());
                for (Iterator<PDFPage> i = pages.iterator();i.hasNext();) {
                    PDFPage page = i.next();
                    page.setPageOrientation(page.getPageOrientation() + diff);
                }

                docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new AbstractUndoableEdit() {
                    public String getPresentationName() {
                        return (String)getValue(Action.NAME);
                    }
                    public void undo() {
                        super.undo();
                        undoredo(-diff);
                    }
                    public void redo() {
                        super.redo();
                        undoredo(diff);
                    }
                    private void undoredo(int diff) {
                        for (Iterator<PDFPage> i = pages.iterator();i.hasNext();) {
                            PDFPage page = i.next();
                            page.setPageOrientation(page.getPageOrientation() + diff);
                        }
                    }
                }));
            }
        }

        public void documentUpdated(DocumentPanelEvent event) {
            String type = event.getType();
            DocumentPanel eventdocpanel = event.getDocumentPanel();
            PDFViewer viewer = eventdocpanel.getViewer();
            if (type.equals("activated") || (type.equals("permissionChanged") && eventdocpanel == viewer.getActiveDocumentPanel())) {
                docpanel = eventdocpanel;
                setEnabled(docpanel.getPDF() != null && docpanel.hasPermission("Assemble"));
            } else if (type.equals("deactivated")) {
                docpanel = null;
                setEnabled(false);
            } else if (type.equals("closing") && documentpanelspecific) {
                event.getDocumentPanel().removeDocumentPanelListener(this);
                docpanel = null;
                setEnabled(false);
            }
        }
    }

}
