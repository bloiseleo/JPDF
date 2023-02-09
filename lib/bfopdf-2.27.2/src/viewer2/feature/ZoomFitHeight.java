// $Id: ZoomFitHeight.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;

/**
 * Creates a menu item which zooms the Document to fit it's height the current viewport.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FitHeight</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class ZoomFitHeight extends ToggleViewerWidget implements DocumentPanelListener {

    public ZoomFitHeight() {
        super("FitHeight", "Zoom");
        setButton("Navigation", "PDFViewer.Feature.ZoomFitHeight.icon", "PDFViewer.tt.ZoomFitHeight");
        setMenu("+View\tZoom\tZoomFitHeight");
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public boolean isButtonEnabledByDefault() {
        return false;
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        if (type=="redrawn" || type=="activated" || type=="viewportChanged") {
            int mode = event.getDocumentPanel().getViewport().getZoomMode();
            setSelected(mode==DocumentViewport.ZOOM_FITHEIGHT);
        }
    }

    public void action(ViewerEvent event) {
        DocumentPanel docpanel = event.getDocumentPanel();
        PDFPage page = null;
        if (docpanel!=null && (page=docpanel.getPage())!=null) {
            super.action(event);
            if (isSelected()) {
                docpanel.getViewport().setZoomMode(DocumentViewport.ZOOM_FITHEIGHT);
                docpanel.runAction(PDFAction.goToFitHeight(page, Float.NaN));
            } else {
                docpanel.getViewport().setZoomMode(DocumentViewport.ZOOM_NONE);
            }
        }
    }
}
