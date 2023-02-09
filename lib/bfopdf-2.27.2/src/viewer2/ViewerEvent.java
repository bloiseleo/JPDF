// $Id: ViewerEvent.java 17403 2013-04-18 09:01:02Z mike $

package org.faceless.pdf2.viewer2;

import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import org.faceless.pdf2.*;

/**
 * An event which is raised by the Viewer when a {@link ViewerWidget} is
 * activated. This is passed to the {@link ViewerWidget#action} method
 * for processing.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class ViewerEvent {

    private ActionEvent event;
    private final PDFViewer viewer;
    private final DocumentPanel documentpanel;
    private final DocumentViewport viewport;

    public ViewerEvent(ActionEvent event, PDFViewer viewer) {
        this(viewer, viewer.getActiveDocumentPanel());
        this.event = event;
    }

    /**
     * Create a new ViewerEvent manually
     * @param viewer the PDFViewer the event relates to
     * @param panel the DocumentPanel the event relates to
     */
    public ViewerEvent(PDFViewer viewer, DocumentPanel panel) {
        this.viewer = viewer;
        this.documentpanel = panel!=null ? panel : viewer.getActiveDocumentPanel();
        this.viewport = documentpanel!=null ? documentpanel.getViewport() : null;
    }

    /**
     * Return the viewer this Event was raised on
     */
    public PDFViewer getViewer() {
        return viewer;
    }

    /**
     * Return the active {@link DocumentPanel} of the PDFViewer, or
     * <code>null</code> if no DocumentPanel is active.
     */
    public DocumentPanel getDocumentPanel() {
        return documentpanel;
    }

    /**
     * Return the {@link PDF} of the DocumentPanel
     */
    public PDF getPDF() {
        return documentpanel==null ? null : documentpanel.getPDF();
    }

    /**
     * Return the Component this event was raised on
     * (for manually created events this returns null)
     */
    public Component getComponent() {
        return event==null? null : (Component)event.getSource();
    }
}
