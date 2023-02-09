// $Id: ThumbnailDeleteAction.java 39831 2021-04-23 08:55:01Z mike $

package org.faceless.pdf2.viewer2.feature;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.beans.*;
import java.awt.event.*;
import javax.swing.undo.*;
import java.util.*;
import java.util.List;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;

/**
 * This feature will allow pages to be deleted via the {@link ThumbnailPanel}.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ThumbnailDelete</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.12
 */
public class ThumbnailDeleteAction extends ViewerFeature implements ThumbnailPanel.ThumbnailSelectionAction {

    public ThumbnailDeleteAction() {
        super("ThumbnailDelete");
    }


    private static class DeleteAction extends AbstractAction implements PropertyChangeListener, DocumentPanelListener {
        private final ThumbnailPanel.View view;
        private final DocumentPanel docpanel;

        DeleteAction(String name, ThumbnailPanel.View view) {
            super(name);
            this.view = view;
            this.docpanel = view.getDocumentPanel();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
            // Note: magic in ThumbnailPanel.View will also use BACKSPACE for this
            // event if it's not otherwise defined. Macs don't have Delete key.
            setEnabled(isActionEnabled());
            view.addPropertyChangeListener(this);
            docpanel.addDocumentPanelListener(this);
            docpanel.getPDF().addPropertyChangeListener(this);
        }

        public void actionPerformed(ActionEvent event) {
            final PDF pdf = docpanel.getPDF();

            if (JOptionPane.showConfirmDialog(docpanel, Util.getUIString("PDFViewer.ConfirmDeletePages", view.getSelectedPagesDescription()), UIManager.getString("PDFViewer.Confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                List<PDFPage> l = new ArrayList<PDFPage>();
                for (int i=0;i<view.getComponentCount();i++) {
                    ThumbnailPanel.SinglePagePanel panel = (ThumbnailPanel.SinglePagePanel)view.getComponent(i);
                    if (panel.isSelected()) {
                        l.add(panel.getPage());
                        panel.setSelected(false);
                    }
                }
                int pagenumber = docpanel.getPageNumber();
                final List<PDFPage> oldpages = new ArrayList<PDFPage>(docpanel.getNumberOfPages());
                final List<PDFPage> newpages = new ArrayList<PDFPage>(docpanel.getNumberOfPages());
                for (int i=0;i<docpanel.getNumberOfPages();i++) {
                    PDFPage page = docpanel.getPage(i);
                    oldpages.add(page);
                    if (!l.contains(page)) {
                        newpages.add(page);
                    }
                }
                pdf.getPages().removeAll(l);
                final PDFPage beforevisiblepage;
                if (l.contains(docpanel.getPage())) {
                    pagenumber = Math.min(docpanel.getNumberOfPages() - 1, pagenumber);
                    docpanel.setPage(beforevisiblepage = docpanel.getPage(pagenumber));
                } else {
                    beforevisiblepage = docpanel.getViewport().getRenderingPage();
                }
                docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new AbstractUndoableEdit() {
                    public String getPresentationName() {
                        return UIManager.getString("PDFViewer.Pages");
                    }
                    public boolean canUndo() {
                        return docpanel != null;
                    }
                    public boolean canRedo() {
                        return docpanel != null;
                    }
                    public void undo() {
                        super.undo();
                        // Makes list longer
                        List<PDFPage> pages = pdf.getPages();
                        for (int i=0;i<oldpages.size();i++) {
                            PDFPage page = oldpages.get(i);
                            PDFPage page2 = i >= pages.size() ? pages.get(i) : null;
                            if (page != page2) {
                                pages.add(i, page);
                            }
                        }
                    }
                    public void redo() {
                        super.redo();
                        // Makes list shorter
                        List<PDFPage> pages = pdf.getPages();
                        for (int i=0;i<pages.size();i++) {
                            PDFPage page = pages.get(i);
                            PDFPage page2 = i >= newpages.size() ? newpages.get(i) : null;
                            if (page != page2) {
                                pages.remove(i--);
                            }
                        }
                        // We may have deleted invisible page
                        if (beforevisiblepage != null) {
                            docpanel.setPage(beforevisiblepage);
                        }
                    }
                }));
            }
        }

        public void propertyChange(PropertyChangeEvent event) {
            String name = event.getPropertyName();
            if (event.getSource() == docpanel.getPDF()) {
                if (name.equals("pages")) {
                    setEnabled(isActionEnabled());
                }
            } else {
                if (name.equals("selection") || name.equals("selected")) {
                    setEnabled(isActionEnabled());
                }
            }
        }

        public void documentUpdated(DocumentPanelEvent event) {
            if ("permissionChanged".equals(event.getType())) {
                setEnabled(isActionEnabled());
            } else if ("closing".equals(event.getType())) {
                view.removePropertyChangeListener(this);
                docpanel.removeDocumentPanelListener(this);
            }
        }

        private boolean isActionEnabled() {
            if (view.getDocumentPanel() == null || view.getDocumentPanel().getPDF() == null) {
                return false;
            }
            return view.isAnythingDraggableSelected() && !view.isEntireDraggablePDFSelected() && view.isEditable() && view.isDraggable();
        }
    }

    public Action getAction(final ThumbnailPanel.View view) {
        if (!view.isFactoryEditable()) {
            return null;
        }

        return new DeleteAction(UIManager.getString("PDFViewer.Delete")+"...", view);
    }

}
