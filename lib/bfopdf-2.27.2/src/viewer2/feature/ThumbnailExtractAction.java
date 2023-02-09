// $Id: ThumbnailExtractAction.java 39831 2021-04-23 08:55:01Z mike $

package org.faceless.pdf2.viewer2.feature;

import javax.swing.*;
import java.beans.*;
import javax.swing.event.*;
import java.awt.event.*;
import javax.swing.undo.*;
import java.text.MessageFormat;
import java.util.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;

/**
 * This feature will allow pages to be extracted to a new Document via the {@link ThumbnailPanel}.
 * It is distinct from the drag/drop capability of the ThumbnailPanel, which allows pages to be
 * dragged between already open documents. This feature is only active when {@link MultiWindow} is
 * available, as it implies more than one document open in the Viewer.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ThumbnailExtract</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.12
 */
public class ThumbnailExtractAction extends ViewerFeature implements ThumbnailPanel.ThumbnailSelectionAction {

    public ThumbnailExtractAction() {
        super("ThumbnailExtract");
    }

    private static class ExtractAction extends AbstractAction implements PropertyChangeListener, DocumentPanelListener {
        private final ThumbnailPanel.View view;
        private final DocumentPanel docpanel;

        ExtractAction(String name, ThumbnailPanel.View view) {
            super(name);
            this.view = view;
            this.docpanel = view.getDocumentPanel();
            setEnabled(isActionEnabled());
            view.addPropertyChangeListener(this);
            docpanel.addDocumentPanelListener(this);
            docpanel.getPDF().addPropertyChangeListener(this);
        }

        public void actionPerformed(ActionEvent event) {
            final PDF pdf = docpanel.getPDF();

            if (JOptionPane.showConfirmDialog(docpanel, Util.getUIString("PDFViewer.ConfirmExtractPages", view.getSelectedPagesDescription()), UIManager.getString("PDFViewer.Confirm"), JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) {
                JInternalFrame frame = (JInternalFrame)SwingUtilities.getAncestorOfClass(JInternalFrame.class, docpanel);
                if (frame != null) {
                    List<PDFPage> l = new ArrayList<PDFPage>();
                    for (int i=0;i<view.getComponentCount();i++) {
                        ThumbnailPanel.SinglePagePanel panel = (ThumbnailPanel.SinglePagePanel)view.getComponent(i);
                        if (panel.isSelected()) {
                            l.add(panel.getPage());
                            panel.setSelected(false);
                        }
                    }
                    pdf.getPages().removeAll(l);
                    PDF newpdf = new PDF();
                    newpdf.getPages().addAll(l);
                    docpanel.setPage(docpanel.getPage(0));
                    String loadTitle = UIManager.getString("PDFViewer.ThumbnailExtractAction.loadTitle");
                    loadTitle = MessageFormat.format(loadTitle, null, docpanel.getWindowTitle());
                    docpanel.getViewer().loadPDF(newpdf, loadTitle);
                    // Not strictly correct, this will just close the other document - it won't
                    // put the pages back.
                    docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, Undo.DISCARD));
                }
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
        final DocumentPanel docpanel = view.getDocumentPanel();
        PDFViewer viewer = docpanel.getViewer();
        // We don't apply if the view isn't editable or we can't have multiple-windows
        if (!view.isFactoryEditable() || viewer == null || viewer.getFeature(MultiWindow.class) == null) {
            return null;
        }

        return new ExtractAction(UIManager.getString("PDFViewer.Extract")+"...", view);
    }
}
