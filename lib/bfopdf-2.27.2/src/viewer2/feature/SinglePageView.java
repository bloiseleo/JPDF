// $Id: SinglePageView.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.io.*;
import java.util.*;

/**
 * This Widget changes the {@link DocumentViewport} of the current {@link DocumentPanel}
 * to a {@link SinglePageDocumentViewport}.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">SinglePage</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.5
 */
public class SinglePageView extends ToggleViewerWidget implements DocumentPanelListener
{
    /**
     * Create a new SinglePageView object
     */
    public SinglePageView() {
        super("SinglePage", "ViewMode");
        setButton("ViewMode", "PDFViewer.Feature.SinglePageView.icon", "PDFViewer.tt.ViewSinglePage");
    }

    protected void updateViewport(final DocumentViewport vp, boolean selected) {
        if (selected) {
            DocumentPanel panel = vp.getDocumentPanel();
            if (!(panel.getViewport() instanceof SinglePageDocumentViewport)) {
                panel.setViewport(new SinglePageDocumentViewport());
            }
        }
    }

    public void action(ViewerEvent event) {
        if (!isSelected()) {
            setSelected(!isSelected());
        }
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        if (event.getType()=="activated" || event.getType()=="viewportChanged") {
            setSelected(event.getDocumentPanel().getViewport() instanceof SinglePageDocumentViewport);
        }
    }
}
