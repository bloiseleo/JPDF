// $Id: DragScroll.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.*;

/**
 * This widget allows the document to be dragged and scrolled. This widget is
 * selected by default if no other items in its group are selected.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">DragScroll</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.5
 */
public class DragScroll extends ToggleViewerWidget implements DocumentPanelListener
{
    /**
     * The name of the {@link ToggleViewerWidget} Group that this widget is a part of.
     * All other widgets that want to control actions on the DocumentViewport itself
     * should be a part of this group.
     */
    public static final String GROUP = "Mode";

    public DragScroll() {
        super("DragScroll", GROUP);
    }

    public void initialize(PDFViewer viewer) {
        ViewerFeature[] features = viewer.getFeatures();
        for (int i=0;i<features.length;i++) {
            if (features[i] instanceof ToggleViewerWidget && ((ToggleViewerWidget)features[i]).getGroupName()==GROUP && features[i]!=this) {
                setButton(GROUP, "PDFViewer.Feature.DragScroll.icon", "PDFViewer.tt.DragScroll");
            }
        }
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public void action(ViewerEvent event) {
        if (!isSelected()) setSelected(!isSelected());        // Can't turn off through mouse click
    }

    protected void updateViewport(DocumentViewport vp, boolean selected) {
        vp.setDraggable(selected);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        if (type == "viewportChanged" || type == "activated") {
            if (getGroupSelection(getGroupName()) == null) {
                PropertyManager manager = getViewer()==null ? PDF.getPropertyManager() : getViewer().getPropertyManager();
                String defaultmode = manager == null ? null : manager.getProperty("default"+getGroupName());
                if (defaultmode == null && manager != null) {
                    defaultmode = manager.getProperty("Default"+getGroupName());  // legacy
                }
                if (getName().equals(defaultmode) || defaultmode == null) {
                    setSelected(true);
                }
            }
            if (isSelected()) {
                updateViewport(event.getDocumentPanel().getViewport(), true);
            }
        }
    }

}
