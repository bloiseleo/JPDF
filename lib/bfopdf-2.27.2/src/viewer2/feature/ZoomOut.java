// $Id: ZoomOut.java 39834 2021-04-23 19:33:08Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.Insets;

/**
 * Create a button which zooms the document out to the next level.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ZoomOut</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class ZoomOut extends ViewerWidget
{
    public ZoomOut() {
        super("ZoomOut");
        setButton("Navigation", "PDFViewer.Feature.ZoomOut.icon", "PDFViewer.tt.ZoomOut");
        setMenu("View\tZoom\tZoomOut");
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        if (getComponent() instanceof AbstractButton) {
            JButton b = (JButton)getComponent();
            Insets i = UIManager.getInsets("PDFViewer.ViewerWidget.ZoomButton.margin");
            if (i != null) {
                if (b.getMargin() != null) {
                    i.left = b.getMargin().left;
                }
                b.setMargin(i);
            }
            i = UIManager.getInsets("PDFViewer.ViewerWidget.ZoomButton.padding");
            if (i != null) {
                int left = b.getBorder() != null ? b.getBorder().getBorderInsets(b).left : i.left;
                b.setBorder(BorderFactory.createEmptyBorder(i.top, left, i.bottom, i.right));
            }
        }
    }

    public void action(ViewerEvent event) {
        PDFViewer viewer = event.getViewer();
        DocumentPanel panel = viewer.getActiveDocumentPanel();
        float zoom = panel.getZoom();
        int[] intervals = viewer.getZoomIntervals();
        for (int i = intervals.length - 1; i >= 0; i--) {
            float level = ((float) intervals[i]) / 100f;
            if (level < zoom) {
                panel.setZoom(level);
                break;
            }
        }
    }
}
