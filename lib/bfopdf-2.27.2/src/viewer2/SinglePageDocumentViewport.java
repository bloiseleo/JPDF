// $Id: SinglePageDocumentViewport.java 39804 2021-04-21 17:29:29Z mike $

package org.faceless.pdf2.viewer2;

import java.awt.*;
import java.beans.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import javax.swing.*;
import org.faceless.pdf2.*;

/**
 * A type of {@link DocumentViewport} that displays a single page. The Page can be zoomed
 * in or out, and if it grows beyond the size of this viewport scrollbars will be displayed.
 * Zoom levels are translated to DPI (as required by the PagePainter) using the
 * {@link Toolkit#getScreenResolution} method.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see DocumentPanel
 * @since 2.8
 */
public class SinglePageDocumentViewport extends DocumentViewport {

    private JScrollPane scrollPane;
    private View view;
    private float zoom;

    private Listener listener; // event handler
    private RenderingHints hints;
    private ArrayList<PagePanelListener> listeners;
    private ArrayList<PagePanelInteractionListener> ilisteners;
    private PagePanelEventDispatcher eventDispatcher;

    private PDF pdf; // the current PDF document
    private PDFPage page; // the current page

    /**
     * Create a new SinglePageDocumentViewport
     */
    public SinglePageDocumentViewport() {
        view = this.new View();
        setRenderingHints(getDefaultRenderingHints());
        setLayout(new BorderLayout());
        scrollPane = new JScrollPane(view) {
            protected JViewport createViewport() {
                return new JViewport() {
                    public void scrollRectToVisible(Rectangle r) {
                        // NB eat this event to prevent caret from scrolling viewport
                    }
                };
            }
        };
        add(scrollPane, BorderLayout.CENTER);

        // event management
        listener = this.new Listener();
        addComponentListener(listener);
        scrollPane.getHorizontalScrollBar().addAdjustmentListener(listener);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(listener);

        listeners = new ArrayList<PagePanelListener>();
        ilisteners = new ArrayList<PagePanelInteractionListener>();
        eventDispatcher = new PagePanelEventDispatcher();

        Util.fixScrollPaneKeyBindings(scrollPane);
    }

    public void setDocumentPanel(DocumentPanel panel) {
        super.setDocumentPanel(panel);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(mouseWheelUnit);
        scrollPane.getVerticalScrollBar().setUnitIncrement(mouseWheelUnit);
        PDF pdf = panel == null ? null : panel.getPDF();
        if (pdf != this.pdf) {
            view.cleanup();
            this.pdf = pdf;
            this.page = null;
            if (pdf != null) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        DocumentPanel panel = getDocumentPanel();
                        if (panel != null) {
                            PDFPage page = panel.getPage();
                            if (page == null) {
                                page = panel.getPage(0);
                            }
                            setPage(page, Double.NaN, Double.NaN, getZoom());
                            view.pagePanel.requestFocusInWindow();
                        }
                    }
                });
            }
        }
    }

    public boolean isDraggable() {
        return view.isDraggable();
    }

    public void setDraggable(boolean draggable) {
        view.setDraggable(draggable);
    }

    public void setPage(PDFPage page, double x, double y, double zoom) {
        if (getDocumentPanel().getPageNumber(page) < 0) {
            throw new IllegalArgumentException("Page is not from this DocumentPanel");
        }
        Rectangle2D crop = PagePanel.getFullPageView(page);
        ensureVisible(page, x + crop.getMinX(), crop.getMaxY() - y, false);
        setZoom((float) zoom);
    }

    public void ensureVisible(PDFPage page, double x, double y) {
        ensureVisible(page, x, y, true);
    }

    /**
     * @param x x coordinate in PDF units relative to MediaBox. May be NaN or out of range
     * @param y y coordinate in PDF units relative to MediaBox - 0 is bottom. May be NaN or out of range
     * @param centre if true, centre this location in the viewport,
     * otherwise cause it to be displayed at the top left of the viewport.
     */
    private void ensureVisible(PDFPage page, double x, double y, boolean centre) {
        PDFPage oldPage = this.page;
        PagePanel oldPagePanel = getPagePanel();
        this.page = page;
        view.setPage(page, false);

        Rectangle pageRect = view.pagePanel.getBounds();
        Rectangle2D crop = PagePanel.getFullPageView(page);
        int xoffset = pageRect.x, yoffset = pageRect.y;
        if (x > crop.getMinX()) {       // Also confirms !NaN
            xoffset += pointsToPixels((float)(Math.min(crop.getMaxX(), x) - crop.getMinX()));
        }
        if (y < crop.getMaxY()) {       // Also confirms !NaN
            yoffset += pointsToPixels((float)(crop.getHeight() - Math.max(0, y - crop.getMinY())));
        }

        JScrollBar hsb = scrollPane.getHorizontalScrollBar();
        JScrollBar vsb = scrollPane.getVerticalScrollBar();
        if (centre) {
            xoffset -= hsb.getVisibleAmount() / 2;
            yoffset -= vsb.getVisibleAmount() / 2;
        }
        hsb.setValue(xoffset - margin);
        vsb.setValue(yoffset - margin);

        if (page != oldPage) {
            DocumentPanel docpanel = getDocumentPanel();
            if (docpanel != null) {
                DocumentPanelEvent dpe = DocumentPanelEvent.createPageChanged(docpanel);
                dpe.setPreviousPage(oldPage);
                docpanel.raiseDocumentPanelEvent(dpe);
            }
            if (oldPagePanel != null) {
                oldPagePanel.raisePagePanelEvent(PagePanelEvent.createPageHidden(oldPagePanel, oldPage));
            }
            PagePanel newPagePanel = getPagePanel();
            if (newPagePanel != null) {
                newPagePanel.raisePagePanelEvent(PagePanelEvent.createPageVisible(newPagePanel, page));
            }
        }
    }

    public boolean isPageVisible(PDFPage page, double x, double y) {
        if (view.pagePanel == null) {
            return false;
        }
        Rectangle pageRect = view.pagePanel.getBounds();
        Rectangle target = (Rectangle) pageRect.clone();
        if (page == getPage()) {
            if (x == x && y == y) {
                float dpi = (float)getZoom() * Util.getScreenResolution(this);
                int xoffset = Math.round((float) x * dpi / 72);
                target.x += xoffset;
                target.width = 8;
                int yoffset = Math.round((float) y * dpi / 72);
                // convert to PDF axis space
                yoffset = pageRect.height - yoffset;
                target.y += yoffset;
                target.height = 8;
                return getClip().intersects(target);
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    public JComponent getView() {
        return (JComponent)scrollPane.getViewport().getView();
    }

    public float getZoom() {
        if (zoom == 0 && page != null) {
            zoom = getTargetZoom(getZoomMode(), page);
        }
        return zoom == 0 ? 1 : zoom;
    }

    public void setZoom(float zoom) {
        PDFViewer viewer = getDocumentPanel().getViewer();
        if (zoom < (((float) viewer.getMinZoom()) / 100f) || zoom > (((float) viewer.getMaxZoom()) / 100f)) {
            return;
        }
        if (zoom == zoom && zoom > 0.01 && zoom != this.zoom && page != null) {
            // Store where we're currently pointing
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            final int hx = hsb.getVisibleAmount();
            final int vx = vsb.getVisibleAmount();
            float hv = (float)(hsb.getValue() + hx/2) / hsb.getMaximum();
            float vv = (float)(vsb.getValue() + vx/2) / vsb.getMaximum();
            // Set zoom and new DPI
            this.zoom = zoom;
            view.setSize(view.getPreferredSize());
            // Set scrollbar values to point to the same location of the
            // document given new zoom level

            listener.setEnabled(false); // don't send adjustment events
            Dimension viewsize = view.getSize();
            hsb.setValues(Math.round(viewsize.width * hv) - hx/2, hx, 0, viewsize.width);
            vsb.setValues(Math.round(viewsize.height * vv) - vx/2, vx, 0, viewsize.height);
            listener.setEnabled(true);

            view.setPage(page, true);
            view.revalidate();
            view.repaint();
        }
    }

    private int pointsToPixels(float points) {
        return Math.round(points * (getZoom() * Util.getScreenResolution(this) / 72));
    }

    float pixelsToPoints(int pixels) {
        return (float)pixels / (getZoom() * Util.getScreenResolution(this) / 72);
    }

    public Dimension getViewportSize() {
        return scrollPane.getViewport().getSize();
    }

    public PagePanel getPagePanel() {
        return view.pagePanel;
    }

    public Collection<PagePanel> getPagePanels() {
        return view.pagePanel == null ? Collections.<PagePanel>emptySet(): Collections.singleton(view.pagePanel);
    }

    public PDFPage getRenderingPage() {
        return page;
    }

    private Rectangle getClip() {
        JScrollBar hsb = scrollPane.getHorizontalScrollBar();
        JScrollBar vsb = scrollPane.getVerticalScrollBar();
        return new Rectangle(hsb.getValue(), vsb.getValue(), hsb.getVisibleAmount(), vsb.getVisibleAmount());
    }

    public Adjustable getAdjustable(int position) {
        switch (position) {
            case Adjustable.HORIZONTAL:
                return scrollPane.getHorizontalScrollBar();
            case Adjustable.VERTICAL:
                return scrollPane.getVerticalScrollBar();
            default:
                throw new IllegalArgumentException(Integer.toString(position));
        }
    }

    public void setAdjustableValues(int horizontal, int vertical) {
        listener.setEnabled(false);
        scrollPane.getHorizontalScrollBar().setValue(horizontal);
        scrollPane.getVerticalScrollBar().setValue(vertical);
        listener.setEnabled(true);
        view.revalidate();
        repaint();
    }

    public void setRenderingHints(RenderingHints hints) {
        this.hints = hints;
        if (view.pagePanel != null) {
            view.pagePanel.setRenderingHints(hints);
        }
    }

    public void addPagePanelListener(PagePanelListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public void removePagePanelListener(PagePanelListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void addPagePanelInteractionListener(PagePanelInteractionListener listener) {
        synchronized (ilisteners) {
            if (!ilisteners.contains(listener)) {
                ilisteners.add(listener);
            }
        }
    }

    public void removePagePanelInteractionListener(PagePanelInteractionListener listener) {
        synchronized (ilisteners) {
            ilisteners.remove(listener);
        }
    }

    private class PagePanelEventDispatcher implements PagePanelListener, PagePanelInteractionListener {

        public void pageUpdated(PagePanelEvent event) {
            PagePanelListener[] l;
            synchronized (listeners) {
                l = (PagePanelListener[]) listeners.toArray(new PagePanelListener[listeners.size()]);
            }
            for (int i = 0; i < l.length; i++) {
                l[i].pageUpdated(event);
            }
        }

        public void pageAction(PagePanelInteractionEvent event) {
            PagePanelInteractionListener[] l;
            synchronized (ilisteners) {
                l = (PagePanelInteractionListener[]) ilisteners.toArray(new PagePanelInteractionListener[ilisteners.size()]);
            }
            for (int i = 0; i < l.length; i++) {
                l[i].pageAction(event);
            }
        }

    }

    public void setZoomMode(int zoommode) {
        super.setZoomMode(zoommode);
        PDFPage page = getPage();
        if (page != null) {
            setZoom(getTargetZoom(zoommode, page));
        }
    }

    public float getTargetZoom(int zoommode, PDFPage page) {
        float outzoom = zoom;
        if (page != null) {
            Dimension pps = PagePanel.getFullPageView(page).getBounds().getSize();
            float screenResolution = Util.getScreenResolution(this);
            pps.width = Math.round(pps.width * screenResolution / 72f);
            pps.height = Math.round(pps.height * screenResolution / 72f);
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            Dimension size = new Dimension(hsb.getVisibleAmount(), vsb.getVisibleAmount());
            switch (zoommode) {
                case ZOOM_FITWIDTH:
                    size.width -= vsb.getWidth();
                    outzoom = ((float) size.width / (float) pps.width);
                    break;
                case ZOOM_FITHEIGHT:
                    size.height -= hsb.getHeight();
                    outzoom = ((float) size.height / (float) pps.height);
                    break;
                case ZOOM_FIT:
                    size.width -= vsb.getWidth();
                    size.height -= hsb.getHeight();
                    float zw = ((float) size.width / (float) pps.width);
                    float zh = ((float) size.height / (float) pps.height);
                    outzoom = (zw < zh) ? zw : zh;
                    break;
                default:
                    outzoom = zoom == 0 ? 1 : zoom;
            }
        }
        return outzoom;
    }

    private class View extends JPanel implements MouseListener, MouseMotionListener {

        float lastZoom = Float.NaN;
        Rectangle lastClip;
        PagePanel pagePanel;

        boolean draggable;
        Point mouseDownEvent;
        Point mouseDownScroll;

        View() {
            setLayout(null);
            setFocusable(false);
            addMouseListener(this);
            addMouseMotionListener(this);
        }

        public Dimension getPreferredSize() {
            Dimension ret = new Dimension(0, 0);
            if (page != null) {
                ret = PagePanel.getFullPageView(page).getBounds().getSize();
                // now convert points to pixels
                ret.width = pointsToPixels((float) ret.width);
                ret.height = pointsToPixels((float) ret.height);
            }
            // and add page margins
            ret.height += margin * 2;
            ret.width += margin * 2;
            return ret;
        }

        void setPage(PDFPage page, boolean force) {
            if (force || pagePanel == null || pagePanel.getPage() != page) {
                removeAll();
                if (pagePanel != null) {
                    pagePanel.setParser(null);
                    pagePanel.removeMouseListener(this);
                    pagePanel.removeMouseMotionListener(this);
                }
                pagePanel = new PagePanel();
                pagePanel.setViewport(SinglePageDocumentViewport.this);
                pagePanel.setParser(getDocumentPanel().getParser(page));
                pagePanel.setRenderingHints(hints);
                pagePanel.addPagePanelListener(eventDispatcher);
                pagePanel.addPagePanelInteractionListener(eventDispatcher);
                pagePanel.addMouseListener(this);
                pagePanel.addMouseMotionListener(this);
                add(pagePanel);
                lastZoom = Float.NaN;
                doLayout();
                repaint();
            }
        }

        public void doLayout() {
            Dimension size = getSize();
            Dimension viewport = getPreferredSize();
            Rectangle clip = new Rectangle();
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            clip.x = hsb.getValue();
            clip.y = vsb.getValue();
            clip.width = hsb.getVisibleAmount();
            clip.height = vsb.getVisibleAmount();

            int x = margin, y = margin;
            // centre pages if entirely visible
            if (viewport.width < size.width) {
                x = (size.width - viewport.width) / 2;
            }
            if (viewport.height < size.height) {
                y = (size.height - viewport.height) / 2;
            }
            if (page != null) {
                Dimension pageSize = PagePanel.getFullPageView(page).getBounds().getSize();
                int pw = pointsToPixels(pageSize.width);
                int ph = pointsToPixels(pageSize.height);
                Rectangle pr = new Rectangle(x, y, pw, ph);
                if (!pagePanel.getBounds().equals(pr)) {
                    pagePanel.setBounds(pr);
                }
                float zoom = getZoom();
                if (zoom != lastZoom || !clip.equals(lastClip)) {
                    lastZoom = zoom;
                    lastClip = clip;
                    Rectangle2D fullPageView = PagePanel.getFullPageView(page);
                    float dpi = zoom * Util.getScreenResolution(this);
                    Rectangle pclip = pr.intersection(clip);
                    pclip.x -= pr.x;
                    pclip.y -= pr.y;
                    pagePanel.setPage(page, fullPageView, dpi, pclip);
                }
            }
        }

        public void paintComponent(Graphics g) {
            Rectangle bounds = getBounds();
            Rectangle r = null;
            if (pagePanel != null) {
                r = pagePanel.getBounds();
                if (r.contains(bounds)) {
                    return;
                }
            }
            // Paint background
            g.setColor(getBackground());
            g.fillRect(0, 0, bounds.width, bounds.height);

            if (r != null) {
                paintPageBorder(g, r);
            }
        }

        boolean isDraggable() {
            return draggable;
        }

        void setDraggable(boolean draggable) {
            this.draggable = draggable;
            setCursor(draggable ? CURSOR_GRAB : null);
        }

        public void mouseClicked(MouseEvent event) {}
        public void mouseEntered(MouseEvent event) {}
        public void mouseExited(MouseEvent event) {}
        public void mouseMoved(MouseEvent event) {}

        public void mousePressed(MouseEvent event) {
            if (draggable) {
                Component source = (Component) event.getSource();
                if (source == this) {
                    mouseDownEvent = event.getPoint();
                } else {
                    mouseDownEvent = new Point(event.getX() + source.getX(), event.getY() + source.getY());
                }
                Adjustable sbh = getAdjustable(Adjustable.HORIZONTAL);
                Adjustable sbv = getAdjustable(Adjustable.VERTICAL);
                mouseDownScroll = new Point(sbh.getValue(), sbv.getValue());
                setCursor(CURSOR_GRABBING);
            }
        }

        public void mouseReleased(MouseEvent event) {
            if (draggable) {
                mouseDownEvent = null;
                mouseDownScroll = null;
                setCursor(CURSOR_GRAB);
            }
        }

        public void mouseDragged(MouseEvent event) {
            if (draggable && mouseDownEvent != null) {
                Point point;
                Component source = (Component) event.getSource();
                if (source == this) {
                    point = event.getPoint();
                } else {
                    point = new Point(event.getX() + source.getX(), event.getY() + source.getY());
                }
                int dx = point.x - mouseDownEvent.x;
                int dy = point.y - mouseDownEvent.y;
                Adjustable sbh = getAdjustable(Adjustable.HORIZONTAL);
                Adjustable sbv = getAdjustable(Adjustable.VERTICAL);
                int sbx = sbh.getValue();
                int sby = sbv.getValue();
                setAdjustableValues(mouseDownScroll.x - dx, mouseDownScroll.y - dy);
                int sdx = sbh.getValue() - sbx;
                int sdy = sbv.getValue() - sby;
                mouseDownScroll.x += sdx;
                mouseDownScroll.y += sdy;
            }
        }

        // Removes the pagepanel when the PDF has changed.
        void cleanup() {
            if (pagePanel != null) {
                pagePanel.setParser(null);
                remove(pagePanel);
            }
            pagePanel = null;
        }

    }

    public void propertyChange(final PropertyChangeEvent event) {
        String name = event.getPropertyName();
        Object source = event.getSource();
        if ((name.endsWith("Box") || name.equals("orientation")) && source == page) {
            view.setPage(page, true);
        }
    }

    private class Listener implements ComponentListener, AdjustmentListener {

        private boolean enabled = true;

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void componentResized(ComponentEvent e) {
            int zoommode = getZoomMode();
            switch (zoommode) {
              case ZOOM_FITWIDTH:
              case ZOOM_FITHEIGHT:
              case ZOOM_FIT:
                setZoom(getTargetZoom(zoommode, getPage()));
                break;
            }
        }

        public void componentMoved(ComponentEvent e) {
        }

        public void componentShown(ComponentEvent e) {
        }

        public void componentHidden(ComponentEvent e) {
        }

        public void adjustmentValueChanged(AdjustmentEvent e) {
            if (enabled) {
                view.revalidate(); // NB this is required for Windows to actually call doLayout
                repaint();

                DocumentPanel docpanel = getDocumentPanel();
                if (docpanel != null) {
                    docpanel.raiseDocumentPanelEvent(DocumentPanelEvent.createPagePositionChanged(docpanel));
                }
            }
        }
    }
}
