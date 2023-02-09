// $Id: ThumbnailPasteAction.java 39831 2021-04-23 08:55:01Z mike $

package org.faceless.pdf2.viewer2.feature;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import java.awt.event.ActionEvent;
import java.beans.*;
import java.util.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;

/**
 * Moves the pages that have previously been stored with a cut operation to
 * before the currently selected page in the thumbnail panel, and clears the
 * list of such pages.
 * <span class="featurename">The name of this feature is <span class="featureactualname">ThumbnailPaste</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.15
 */
public class ThumbnailPasteAction extends ViewerFeature implements ThumbnailPanel.ThumbnailSelectionAction {

    private PDFViewer viewer;

    public ThumbnailPasteAction() {
        super("ThumbnailPaste");
    }

    public void initialize(PDFViewer viewer) {
        this.viewer = viewer;
    }

    public Action getAction(final ThumbnailPanel.View view) {
        if (!view.isFactoryEditable()) {
            return null;
        }
        return this.new PasteAction(UIManager.getString("PDFViewer.Paste"), view);
    }

    class PasteAction extends AbstractAction implements PropertyChangeListener, DocumentPanelListener {

        private final ThumbnailPanel.View view;
        private final DocumentPanel docpanel;

        PasteAction(String name, ThumbnailPanel.View view) {
            super(name);
            this.view = view;
            docpanel = view.getDocumentPanel();
            int mask = Util.getMenuShortcutKeyMask(docpanel);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('V', mask, false));
            setEnabled(isActionEnabled());
            viewer.addPropertyChangeListener(this);
            view.addPropertyChangeListener(this);
            docpanel.addDocumentPanelListener(this);
            docpanel.getPDF().addPropertyChangeListener(this);
            setEnabled(isActionEnabled());
        }

        private boolean isActionEnabled() {
            ThumbnailPanel.View view = (ThumbnailPanel.View) viewer.getClientProperty(Util.PACKAGE + ".pasteView");
            boolean selected = false;
            int cutcopy = 0;
            if (view != null) {
                int len = view.getComponentCount();
                for (int i = 0; i < len; i++) {
                    ThumbnailPanel.SinglePagePanel thumbnail = (ThumbnailPanel.SinglePagePanel) view.getComponent(i);
                    int flags = thumbnail.getFlags();
                    if ((flags & (ThumbnailPanel.SinglePagePanel.FLAG_CUT|ThumbnailPanel.SinglePagePanel.FLAG_COPY)) > 0) {
                        cutcopy++;
                    }
                    selected |= thumbnail.isSelected() && thumbnail.isDropTarget();
                }
            }
            return selected && cutcopy > 0 && view.isEditable() && view.isDraggable();
        }

        public void propertyChange(PropertyChangeEvent event) {
            String name = event.getPropertyName();
            if (name.equals("pages") ||
                    name.equals("selection") ||
                    name.equals("selected") ||
                    name.equals("cut") ||
                    name.equals("copy") ||
                    name.equals(Util.PACKAGE + ".pasteView")) {
                setEnabled(isActionEnabled());
            }
        }

        public void documentUpdated(DocumentPanelEvent event) {
            String type = event.getType();
            if ("permissionChanged".equals(type)) {
                setEnabled(isActionEnabled());
            } else if ("closing".equals(type)) {
                viewer.removePropertyChangeListener(this);
                view.removePropertyChangeListener(this);
                docpanel.removeDocumentPanelListener(this);
            }
        }

        public void actionPerformed(ActionEvent event) {
            ThumbnailPanel.View srcview = (ThumbnailPanel.View) viewer.getClientProperty(Util.PACKAGE + ".pasteView");
            if (srcview == null) {
                return; // we should not be enabled, anyway
            }
            // Find target to insert at
            PDFPage targetPage = null;
            int len = view.getComponentCount();
            for (int i = 0; i < len && targetPage == null; i++) {
                ThumbnailPanel.SinglePagePanel thumbnail = (ThumbnailPanel.SinglePagePanel) view.getComponent(i);
                if (thumbnail.isSelected()) {
                    targetPage = thumbnail.getPage();
                }
            }
            if (targetPage == null) {
                return;
            }

            // Get list of thumbnails to move
            final PDF dstpdf = targetPage.getPDF();
            PDF srcpdf = null;
            final List<PDFPage> list = new ArrayList<PDFPage>();
            final List<PDFPage> copylist = new ArrayList<PDFPage>();
            len = srcview.getComponentCount();
            for (int i = 0; i < len; i++) {
                ThumbnailPanel.SinglePagePanel thumbnail = (ThumbnailPanel.SinglePagePanel) srcview.getComponent(i);
                PDFPage srcpage = thumbnail.getPage();
                if (srcpage != null) {
                    srcpdf = srcpage.getPDF();
                    int flags = thumbnail.getFlags();
                    if ((flags & ThumbnailPanel.SinglePagePanel.FLAG_CUT) != 0) {
                        list.add(srcpage);
                        thumbnail.setFlags(ThumbnailPanel.SinglePagePanel.FLAG_NONE);
                    } else if ((flags & ThumbnailPanel.SinglePagePanel.FLAG_COPY) != 0) {
                        srcpage = new PDFPage(srcpage);
                        copylist.add(srcpage);
                        list.add(srcpage);
                        thumbnail.setFlags(ThumbnailPanel.SinglePagePanel.FLAG_NONE);
                    }
                }
            }
            if (list.isEmpty()) {
                return;
            }
            setEnabled(false);
            
            final List<PDFPage> oldsrcpages = new ArrayList<PDFPage>(srcpdf.getPages());
            List<PDFPage> dstpages = dstpdf.getPages();
            final List<PDFPage> olddstpages = new ArrayList<PDFPage>(dstpages);

            int index = dstpages.indexOf(targetPage);
            while (list.contains(targetPage)) {
                targetPage = ++index == dstpages.size() ? null : dstpages.get(index);
            }
            dstpages.removeAll(list);
            index = targetPage == null ? dstpages.size() : dstpages.indexOf(targetPage);
            dstpages.addAll(index, list);

            final List<PDFPage> newdstpages = new ArrayList<PDFPage>(dstpages);
            final PDF fsrcpdf = srcpdf;
            AbstractUndoableEdit edit = new AbstractUndoableEdit() {
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
                    dstpdf.getPages().removeAll(copylist);
                    for (PDFPage page : list) {
                        dstpdf.getPages().remove(page);
                    }
                    for (PDFPage page : list) {
                        int ix = oldsrcpages.indexOf(page);
                        if (ix >= 0) {
                            fsrcpdf.getPages().add(ix, page);
                        }
                    }
                }
                public void redo() {
                    super.redo();
                    for (PDFPage page : list) {
                        fsrcpdf.getPages().remove(page);
                    }
                    for (PDFPage page : list) {
                        dstpdf.getPages().add(newdstpages.indexOf(page), page);
                    }
                }
            };
            docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, edit));
        }

    }

}
