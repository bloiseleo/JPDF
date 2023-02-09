// $Id: DirtyListener.java 39802 2021-04-21 15:32:05Z mike $

package org.faceless.pdf2.viewer2;

import java.beans.*;
import java.util.*;
import org.faceless.pdf2.*;

/**
 * Listener to mark document panel dirty when any document changes occur.
 */
class DirtyListener implements PropertyChangeListener {

    private final DocumentPanel docpanel;
    private PDF pdf;
    private Set<PDFPage> pages = new HashSet<PDFPage>();
    private Set<PDFAnnotation> annots = new HashSet<PDFAnnotation>();

    DirtyListener(DocumentPanel docpanel) {
        this.docpanel = docpanel;
    }

    void bind() {
        if (this.pdf != null) {
            throw new IllegalStateException("Listener already bound");
        }
        this.pdf = docpanel.getPDF();
        int n = docpanel.getNumberOfPages();
        boolean complete = true;
        for (int i=0;i<n;i++) {
            if (docpanel.getLinearizedSupport().isPageLoaded(i)) {
                bind(docpanel.getPage(i));
            } else {
                complete = false;
            }
        }
        pdf.addPropertyChangeListener(this);
        docpanel.getLinearizedSupport().invokeOnDocumentLoad(new Runnable() {
            public void run() {
                Form form = DirtyListener.this.pdf.getForm();
                if (form != null) {
                    for (FormElement field : form.getElements().values()) {
                        field.addPropertyChangeListener(DirtyListener.this);
                    }
                }
            }
        });
    }

    private void bind(PDFPage page) {
        page.addPropertyChangeListener(this);
        pages.add(page);
        List<PDFAnnotation> pageannots = page.getAnnotations();
        for (int j = 0; j < pageannots.size(); j++) {
            PDFAnnotation annot = pageannots.get(j);
            annot.addPropertyChangeListener(this);
            annots.add(annot);
        }
    }

    void unbind() {
        for (Iterator<PDFPage> i = pages.iterator(); i.hasNext(); ) {
            PDFPage page = i.next();
            page.removePropertyChangeListener(this);
        }
        pages.clear();
        for (Iterator<PDFAnnotation> i = annots.iterator(); i.hasNext(); ) {
            PDFAnnotation annot = i.next();
            annot.removePropertyChangeListener(this);
        }
        annots.clear();
        pdf.removePropertyChangeListener(this);
        LoadState loadstate = pdf.getLoadState(-1);
        if (loadstate == null || loadstate.getBytesRemaining() == 0) {
            // Only remove all the listeners from the form if the
            // form has actually been loaded (which happens if the PDF
            // is not linearized, or if all pages have been loaded).
            // Otherwise we are forcing the entire document to load so
            // we can initialize the form, just to discard it.
            Form form = pdf.getForm();
            if (form != null) {
                for (FormElement field : new ArrayList<FormElement>(form.getElements().values())) {
                    if (field != null) {
                        field.removePropertyChangeListener(this);
                    }
                }
            }
        }
        this.pdf = null;
    }

    public void propertyChange(final PropertyChangeEvent e) {
        boolean dirty = true;
        DocumentViewport viewport = docpanel.getViewport();
        if (e.getSource() == pdf) {
            String name = e.getPropertyName();
            if ("pageLoaded".equals(name)) {
                int index = ((Integer) e.getNewValue()).intValue();
                if (index >= 0) {
                    bind(docpanel.getPage(index));
                }
                dirty = false;
            } else if ("pages".equals(name)) { // Special case - catch pages being deleted
                // Ensure all pages are loaded
                for (int i=0;i<docpanel.getNumberOfPages();i++) {
                    PDFPage page = docpanel.getPage(i);
                    page.addPropertyChangeListener(this);
                }
                PDFPage curpage = docpanel.getPage();
                if (curpage == null) {
                    curpage = viewport.getRenderingPage();
                }
                if (curpage != null && !pdf.getPages().contains(curpage)) {
                    int pagenumber = Math.max(0, docpanel.getLastPageNumber());
                    int max = docpanel.getNumberOfPages();
                    while (pagenumber >= max) {
                        pagenumber--;
                    }
                    if (pagenumber < 0) {
                        throw new IllegalStateException("Cannot display a PDF with no pages");
                    }
                    // No need to remove property chage listener, they're weak so will be gced.
                    docpanel.setPage(docpanel.getPage(pagenumber));
                    docpanel.raiseDocumentPanelEvent(DocumentPanelEvent.createRedrawn(docpanel));
                }
            }
        }
        if (dirty) {
            docpanel.setDirty(true);
            docpanel.firePropertyChange("pdfContentChanged", false, true);
        }
        ((PropertyChangeListener)viewport).propertyChange(e);
    }

}
