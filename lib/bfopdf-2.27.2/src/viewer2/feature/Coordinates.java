// $Id: Coordinates.java 10762 2009-08-27 17:52:06Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.*;

/**
 * Creates a {@link JLabel} which displays the total number of pages on the Toolbar.
 * Note this feature is not enabled by default.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">Coordinates</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class Coordinates extends ViewerWidget implements DocumentPanelListener, PagePanelInteractionListener
{
    private JLabel label;

    public Coordinates() {
        super("Coordinates");
        label = new JLabel();
        label.setFont(null);
        label.setPreferredSize(new Dimension(100,15));
        setComponent("Coordinates", label);
        setToolBarEnabled(false);
        setToolBarFloating(true);
    }

    public boolean isEnabledByDefault() {
        return false;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        if (event.getType()=="viewportChanged") {
            event.getDocumentPanel().getViewport().addPagePanelInteractionListener(this);
        }
    }

    public void pageAction(PagePanelInteractionEvent event) {
        if (event.getType()=="mouseMoved") {
            Point2D p = event.getPoint();
            label.setText(((int)p.getX())+" "+((int)p.getY()));
        }
    }
}
