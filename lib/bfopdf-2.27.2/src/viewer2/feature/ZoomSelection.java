// $Id: ZoomSelection.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.util.*;
import java.awt.geom.*;

/**
 * <p>
 * Creates a button which will zoom the document to a selected region
 * </p>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ZoomSelection</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10
 */
public class ZoomSelection extends AbstractRegionSelector {

    public ZoomSelection() {
        super("ZoomSelection");
        setButton("Mode", "PDFViewer.Feature.ZoomSelection.icon", "PDFViewer.tt.ZoomSelection");
        setMenu("View\tZoom\tZoomSelection");
    }

    public void action(final PagePanel panel, Point2D start, Point2D end) {
        float x1 = (float)start.getX();
        float y1 = (float)start.getY();
        float x2 = (float)end.getX();
        float y2 = (float)end.getY();
        if (x1 != x2 && y1 != y2) {
            final PDFPage page = panel.getPage();
            PDFAction action = PDFAction.goToFitRectangle(page, x1, y1, x2, y2);
            DocumentPanel docpanel = panel.getDocumentPanel();
            docpanel.getViewport().setZoomMode(DocumentViewport.ZOOM_NONE);
            docpanel.runAction(action);
        }
    }
}
