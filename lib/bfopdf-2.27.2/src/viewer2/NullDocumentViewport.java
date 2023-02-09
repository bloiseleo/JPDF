// $Id: NullDocumentViewport.java 19621 2014-07-11 11:15:12Z mike $

package org.faceless.pdf2.viewer2;

import org.faceless.pdf2.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;

/**
 * A DocumentViewport that doesn't display anything. There's no
 * use for this class in the normal viewer, but it's sometimes
 * useful when only a {@link SidePanel} should be displayed in the
 * {@link DocumentPanel}
 * @since 2.12
 */
public class NullDocumentViewport extends DocumentViewport {

    private PDFPage page;

    public Dimension getPreferredSize() {
        return new Dimension(0, 0);
    }

    public void setRenderingHints(RenderingHints hints) {
    }

    public void setPage(PDFPage page, double x, double y, double zoom) {
        this.page = page;
    }

    public void setZoom(float zoom) {
    }

    public float getZoom() {
        return 1;
    }

    public PagePanel getPagePanel() {
        return null;
    }

    public JComponent getView() {
        return null;
    }

    public Collection<PagePanel> getPagePanels() {
        return Collections.<PagePanel>emptySet();
    }

    public PDFPage getPage() {
        return page;
    }

    public PDFPage getRenderingPage() {
        return page;
    }

    public Dimension getViewportSize() {
        return new Dimension(0, 0);
    }

    public Adjustable getAdjustable(int position) {
        return null;
    }

    public void setAdjustableValues(int horizontal, int vertical) {
    }

    public void addPagePanelListener(PagePanelListener listener) {
    }

    public void removePagePanelListener(PagePanelListener listener) {
    }

    public void addPagePanelInteractionListener(PagePanelInteractionListener listener) {
    }

    public void removePagePanelInteractionListener(PagePanelInteractionListener listener) {
    }

    public boolean isDraggable() {
        return false;
    }

    public void setDraggable(boolean draggable) {
    }

}
