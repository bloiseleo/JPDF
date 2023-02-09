// $Id: ZoomFit.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;

/**
 * Creates a button/menu item which zooms the Document to fit the current viewport.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FitPage</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class ZoomFit extends ToggleViewerWidget implements DocumentPanelListener
{
    public ZoomFit() {
        super("FitPage", "Zoom");
        setButton("Navigation", "PDFViewer.Feature.ZoomFit.icon", "PDFViewer.tt.ZoomFit");
        setMenu("+View\tZoom\tZoomFit");
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        if (type=="redrawn" || type=="activated" || type=="viewportChanged") {
            int mode = event.getDocumentPanel().getViewport().getZoomMode();
            setSelected(mode==DocumentViewport.ZOOM_FIT);
        }
    }

    public void action(ViewerEvent event) {
        DocumentPanel docpanel = event.getDocumentPanel();
        PDFPage page = null;
        if (docpanel!=null && (page=docpanel.getPage())!=null) {
            super.action(event);
            if (isSelected()) {
                docpanel.getViewport().setZoomMode(DocumentViewport.ZOOM_FIT);
                docpanel.runAction(PDFAction.goToFit(page));
            } else {
                docpanel.getViewport().setZoomMode(DocumentViewport.ZOOM_NONE);
            }
        }
    }
}
