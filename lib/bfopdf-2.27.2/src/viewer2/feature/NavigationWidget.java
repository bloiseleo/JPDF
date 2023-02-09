// $Id: NavigationWidget.java 17407 2013-04-18 10:13:14Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.PDFPage;
import org.faceless.pdf2.PDF;
import java.beans.*;

/**
 * Abstract superclass of navigation widgets that track the currently
 * displayed page.
 * @since 2.13.1
 */
public abstract class NavigationWidget extends ViewerWidget implements DocumentPanelListener, PropertyChangeListener {

    /**
     * The currently selected document panel.
     */
    protected DocumentPanel docpanel;

    /**
     * The current document.
     */
    protected PDF pdf;

    public NavigationWidget(String s) {
        super(s);
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        DocumentPanel docpanel = event.getDocumentPanel();
        String type = event.getType();
        if ("activated".equals(type)) {
            this.docpanel = docpanel;
            pdf = docpanel.getPDF();
            pdf.addPropertyChangeListener(this);
            pageChanged();
        } else if ("deactivated".equals(type)) {
            if (pdf != null) {
                pdf.removePropertyChangeListener(this);
            }
            pdf = null;
            pageChanged();
            this.docpanel = null;
        } else if ("pageChanged".equals(type) || "viewportChanged".equals(type)) {
            if (docpanel == this.docpanel) {
                pageChanged();
            }
        }
    }

    public void propertyChange(PropertyChangeEvent event) {
        if (event.getSource() == pdf) {
            String name = event.getPropertyName();
            if ("pages".equals(name)) {
                pageChanged();
            }
        }
    }

    protected abstract void pageChanged();

}
