// $Id: DualPageDocumentViewport.java 39804 2021-04-21 17:29:29Z mike $

package org.faceless.pdf2.viewer2;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.beans.*;
import java.util.List;
import javax.swing.*;
import org.faceless.pdf2.*;

/**
 * A {@link DocumentViewport} that displays two pages at a time.
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see DocumentPanel
 * @since 2.13
 */
public class DualPageDocumentViewport extends DocumentViewport {

    /**
     * Constant indicating that odd numbered pages should be shown on the
     * right hand side.
     */
    public static final int ODD_PAGES_ON_RIGHT = 0;

    /**
     * Constant indicating that odd numbered pages should be shown on the
     * left hand side.
     */
    public static final int ODD_PAGES_ON_LEFT = 1;

    private JScrollPane scrollPane;
    private View view;
    private float zoom;

    private Listener listener;
    private RenderingHints hints;
    private ArrayList<PagePanelListener> pagePanelListeners;
    private ArrayList<PagePanelInteractionListener> pagePanelInteractionListeners;
    private PDF pdf;

    private int handedness;

    /**
     * Constructs a new DualPageDocumentViewport that shows odd numbered
     * pages on the right hand side.
     */
    public DualPageDocumentViewport() {
        this(ODD_PAGES_ON_LEFT);
    }

    /**
     * Constructs a new DualPageDocumentViewport that shows odd numbered
     * pages on the specified side.
     * @param handedness ODD_PAGES_ON_RIGHT or ODD_PAGES_ON_LEFT
     */
    public DualPageDocumentViewport(int handedness) {
        setHandedness(handedness);
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
        zoom = 1;

        // event management
        listener = this.new Listener();
        addComponentListener(listener);
        scrollPane.getHorizontalScrollBar().addAdjustmentListener(listener);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(listener);

        pagePanelListeners = new ArrayList<PagePanelListener>();
        pagePanelInteractionListeners = new ArrayList<PagePanelInteractionListener>();

        Util.fixScrollPaneKeyBindings(scrollPane);
    }

    public int getHandedness() {
        return handedness;
    }

    public void setHandedness(int handedness) {
        if (handedness != ODD_PAGES_ON_RIGHT && handedness != ODD_PAGES_ON_LEFT) {
            throw new IllegalArgumentException(Integer.toString(handedness));
        }
        this.handedness = handedness;
    }

    public void setDocumentPanel(DocumentPanel panel) {
        super.setDocumentPanel(panel);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(mouseWheelUnit);
        scrollPane.getVerticalScrollBar().setUnitIncrement(mouseWheelUnit);
        PDF pdf = panel == null ? null : panel.getPDF();
        if (pdf != this.pdf) {
            view.cleanup();
            this.pdf = pdf;
            if (pdf != null) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        PDFPage page = getDocumentPanel().getPage();
                        if (page == null) {
                            page = getDocumentPanel().getPage(0);
                        }
                        setPage(page, Double.NaN, Double.NaN, getZoom());
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

    public PDFPage getPage() {
        return view.getPage();
    }

    public void setPage(PDFPage page, double x, double y, double zoom) {
        if (getDocumentPanel().getPageNumber(page) < 0) {
            throw new IllegalArgumentException("Page is not from this DocumentPanel");
        }
        setZoom((float) zoom);
        Rectangle2D crop = PagePanel.getFullPageView(page);
        ensureVisible(page, crop.getMinX() + x, crop.getMaxY() - y, false);
    }

    public void ensureVisible(PDFPage page, double x, double y) {
        ensureVisible(page, x, y, true);
    }

    public int getNextSelectablePageIndex(PDFPage page) {
        if (page != null) {
            int ix = getDocumentPanel().getPageNumber(page);
            int num = getDocumentPanel().getNumberOfPages();
            if (ix >= 0 && ix < num - 1) {
                return Math.min(num - 1, ix + (((ix&1)==0) == (handedness == ODD_PAGES_ON_LEFT) ? 2 : 1));
            }
        }
        return -1;
    }

    public int getPreviousSelectablePageIndex(PDFPage page) {
        if (page != null) {
            int ix = getDocumentPanel().getPageNumber(page);
            if (ix > 0) {
                return Math.max(0, ix - (((ix&1)==0) == (handedness == ODD_PAGES_ON_LEFT) ? 1 : 2));
            }
        }
        return -1;
    }

    /**
     * @param x x coordinate in PDF units
     * @param y y coordinate in PDF units
     * @param centre if true, centre this location in the viewport,
     * otherwise cause it to be displayed at the top left of the viewport.
     */
    private void ensureVisible(PDFPage page, double x, double y, boolean centre) {
        PDFPage oldPage = view.getPage();
        Collection<PagePanel> oldPagePanels = view.getPagePanels();
        view.setPage(page, false);
        Rectangle pageRect = view.getPageRectangle(page);

        if (pageRect != null) {
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
            if (getPagePanel() != null && getPagePanel().getClip() != null) {
                hsb.setValue(xoffset);  // Don't want to smooth scroll if we're clipping
                vsb.setValue(yoffset);  // the page rectangles.
            } else {
                smoothScroll(xoffset, yoffset, hsb, vsb);
            }
        }
        if (page != oldPage) {
            DocumentPanel docpanel = getDocumentPanel();
            if (docpanel != null) {
                DocumentPanelEvent dpe = DocumentPanelEvent.createPageChanged(docpanel);
                dpe.setPreviousPage(oldPage);
                docpanel.raiseDocumentPanelEvent(dpe);
            }
            Collection<PagePanel> newPagePanels = view.getPagePanels();
            for (Iterator<PagePanel> i = oldPagePanels.iterator(); i.hasNext(); ) {
                PagePanel oldPagePanel = i.next();
                if (!newPagePanels.contains(oldPagePanel)) {
                    oldPagePanel.raisePagePanelEvent(PagePanelEvent.createPageHidden(oldPagePanel, oldPage));
                }
            }
            for (Iterator<PagePanel> i = newPagePanels.iterator(); i.hasNext(); ) {
                PagePanel newPagePanel = i.next();
                if (!oldPagePanels.contains(newPagePanel)) {
                    newPagePanel.raisePagePanelEvent(PagePanelEvent.createPageVisible(newPagePanel, page));
                }
            }
        }
    }

    public boolean isPageVisible(PDFPage page, double x, double y) {
        Rectangle pageRect = view.getPageRectangle(page);
        if (pageRect != null) {
            Rectangle target = (Rectangle) pageRect.clone();
            float dpi = getZoom() * Util.getScreenResolution(this);
            if (!Double.isNaN(x)) {
                int xoffset = Math.round((float) x * (dpi / 72));
                target.x += xoffset;
                target.width = 8;
            }
            if (!Double.isNaN(y)) {
                int yoffset = Math.round((float) y * (dpi / 72));
                // convert to PDF axis space
                yoffset = pageRect.height - yoffset;
                target.y += yoffset;
                target.height = 8;
            }
            return getClip().intersects(target);
        }
        return false;
    }

    public JComponent getView() {
        return (JComponent)scrollPane.getViewport().getView();
    }

    public float getZoom() {
        if (zoom==0) {
            zoom = getTargetZoom(getZoomMode(), getPage());
        }
        return zoom;
    }

    public void setZoom(float zoom) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Must be on EDT");
        }
        PDFViewer viewer = getDocumentPanel().getViewer();
        if (zoom < (((float) viewer.getMinZoom()) / 100f) || zoom > (((float) viewer.getMaxZoom()) / 100f)) {
            return;
        }
        if (zoom == zoom && zoom > 0.01 && Math.abs(zoom - this.zoom) > 0.01) {
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            float hv = (float) hsb.getValue();
            float vv = (float) vsb.getValue();
            float hm = (float) hsb.getMaximum();
            float vm = (float) vsb.getMaximum();
            this.zoom = zoom;
            view.setSize(view.getPreferredSize());
            listener.setEnabled(false); // don't send adjustment events
            Dimension viewsize = view.getSize();
            hv *= ((float) viewsize.width) / hm;
            vv *= ((float) viewsize.height) / vm;
            hsb.setMaximum(viewsize.width);
            vsb.setMaximum(viewsize.height);
            hsb.setValue(Math.round(hv));
            vsb.setValue(Math.round(vv));
            listener.setEnabled(true);
            view.revalidate();
            view.repaint();
        }
    }

    private int pointsToPixels(float points) {
        return Math.round(points * (getZoom() * Util.getScreenResolution(this) / 72));
    }

    private float pixelsToPoints(int pixels) {
        return (float)pixels / (getZoom() * Util.getScreenResolution(this) / 72);
    }

    public Dimension getViewportSize() {
        return scrollPane.getViewport().getSize();
    }

    public PagePanel getPagePanel() {
        return view.getPagePanel();
    }

    public Collection<PagePanel> getPagePanels() {
        return view.getPagePanels();
    }

    public PDFPage getRenderingPage() {
        return getPage();
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
        scrollPane.getHorizontalScrollBar().setValue(horizontal);
        scrollPane.getVerticalScrollBar().setValue(vertical);
        view.revalidate();
        view.repaint();
    }

    public void setRenderingHints(RenderingHints hints) {
        this.hints = hints;
        for (Iterator<PagePanel> i = view.getPagePanels().iterator();i.hasNext();) {
            i.next().setRenderingHints(hints);
        }
    }

    public void addPagePanelListener(PagePanelListener listener) {
        synchronized (pagePanelListeners) {
            if (!pagePanelListeners.contains(listener)) {
                pagePanelListeners.add(listener);
            }
        }
    }

    public void removePagePanelListener(PagePanelListener listener) {
        synchronized (pagePanelListeners) {
            pagePanelListeners.remove(listener);
        }
    }

    public void addPagePanelInteractionListener(PagePanelInteractionListener listener) {
        synchronized (pagePanelInteractionListeners) {
            if (!pagePanelInteractionListeners.contains(listener)) {
                pagePanelInteractionListeners.add(listener);
            }
        }
    }

    public void removePagePanelInteractionListener(PagePanelInteractionListener listener) {
        synchronized (pagePanelInteractionListeners) {
            pagePanelInteractionListeners.remove(listener);
        }
    }

    public void setZoomMode(int zoommode) {
        super.setZoomMode(zoommode);
        if (getPage() != null) {
            setZoom(getTargetZoom(zoommode, getPage()));
        }
    }

    public float getTargetZoom(int zoommode, PDFPage page) {
        float outzoom = zoom;
        if (page != null) {
            DocumentPanel docpanel = getDocumentPanel();
            int pagenumber = docpanel.getPageNumber(page);
            int pagecount  = docpanel.getNumberOfPages();
            boolean isOdd = (pagenumber % 2) == 0; // even pages have odd numbers :)
            boolean onRight = handedness == ODD_PAGES_ON_RIGHT ? isOdd : !isOdd;
            PDFPage leftPage, rightPage;
            Rectangle2D.Float leftPageRect = null, rightPageRect = null;
            if (onRight) {
                leftPage = (pagenumber > 0) ? docpanel.getPage(pagenumber - 1) : null;
                rightPage = page;
            } else {
                leftPage = page;
                rightPage = (pagenumber < (pagecount - 1)) ? docpanel.getPage(pagenumber + 1) : null;
            }
            if (leftPage != null) {
                leftPageRect = (Rectangle2D.Float) PagePanel.getFullPageView(leftPage);
            }
            if (rightPage != null) {
                rightPageRect = (Rectangle2D.Float) PagePanel.getFullPageView(rightPage);
            }
            if (leftPageRect == null) {
                leftPageRect = (Rectangle2D.Float) rightPageRect.clone();
            }
            if (rightPageRect == null) {
                rightPageRect = (Rectangle2D.Float) leftPageRect.clone();
            }
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            float prw = leftPageRect.width + rightPageRect.width;
            float prh = Math.max(leftPageRect.height, rightPageRect.height);

            Dimension size = getSize();
            float w = (float)Math.ceil(prw * Util.getScreenResolution(this) / 72) + (margin * 4) + interpagemargin + vsb.getWidth();
            float h = (float)Math.ceil(prh * Util.getScreenResolution(this) / 72) + (margin * 2) + hsb.getHeight();
            switch (zoommode) {
              case ZOOM_FITWIDTH:
                outzoom = (float) size.width / w;
                break;
              case ZOOM_FITHEIGHT:
                outzoom = (float) size.height / h;
                break;
              case ZOOM_FIT:
                float zw = (float) size.width / w;
                float zh = (float) size.height / h;
                outzoom = zw < zh ? zw : zh;
                break;
              default:
                outzoom = zoom==0 ? 1 : zoom;
            }
        }
        return outzoom;
    }

    public void propertyChange(PropertyChangeEvent event) {
        String name = event.getPropertyName();
        Object source = event.getSource();
        boolean relayout = false;
        if (name.equals("pages") && source == pdf) {
            relayout = true;
        } else if ((name.endsWith("Box") || name.equals("orientation")) && source instanceof PDFPage) {
            relayout = true;
        }
        if (relayout) {
            int num = view.getPageNumber();
            num = Math.min(num, getDocumentPanel().getNumberOfPages() - 1);
            PDFPage page = getDocumentPanel().getPage(num);
            view.setPage(page, true);

            smoothScroll(0, 0, null, null);
        }
    }

    private class View extends JPanel implements PagePanelListener, PagePanelInteractionListener, MouseListener, MouseMotionListener {

        static final int MINSIZE = 100;

        private PDFPage leftPage, rightPage, currentPage;
        private PagePanel leftPagePanel, rightPagePanel, currentPagePanel;
        private Rectangle leftPageRect, rightPageRect;
        private Rectangle2D.Float leftFullPageRect, rightFullPageRect;
        private BitSet visiblePages;            // Bitset showing which pages are visible

        private boolean draggable, pageRectsCentered;
        private Point mouseDownEvent;
        private Point mouseDownScroll;
        private int pagenumber;

        View() {
            setLayout(null); // we will handle layout
            setFocusable(false);
            addMouseListener(this);
            addMouseMotionListener(this);
        }

        void setPage(PDFPage page, boolean forcevalidate) {
            currentPage = page;
            if (leftPage != null && leftPage == page) {
                currentPagePanel = leftPagePanel;
                if (!forcevalidate) {
                    return;
                }
            }
            if (rightPage != null && rightPage == page) {
                currentPagePanel = rightPagePanel;
                if (!forcevalidate) {
                    return;
                }
            }
            DocumentPanel docpanel = getDocumentPanel();
            pagenumber = docpanel.getPageNumber(page);
            int pagecount  = docpanel.getNumberOfPages();
            boolean isOdd = (pagenumber % 2) == 0; // even pages have odd numbers :)
            boolean onRight = handedness == ODD_PAGES_ON_RIGHT ? isOdd : !isOdd;
            if (onRight) {
                setLeftPage((pagenumber > 0) ? docpanel.getPage(pagenumber - 1) : null);
                setRightPage(page);
                currentPagePanel = rightPagePanel;
            } else {
                setLeftPage(page);
                setRightPage((pagenumber < (pagecount - 1)) ? docpanel.getPage(pagenumber + 1) : null);
                currentPagePanel = leftPagePanel;
            }
            revalidate();
            repaint();
        }

        void setLeftPage(PDFPage page) {
            if (leftPagePanel != null) {
                removePagePanel(leftPagePanel);
            }
            leftPage = page;
            if (page == null) {
                leftPagePanel = null;
                leftPageRect = null;
                leftFullPageRect = null;
            } else {
                leftFullPageRect = (Rectangle2D.Float) PagePanel.getFullPageView(page);
                leftPageRect = new Rectangle();
                leftPagePanel = addPagePanel(page);
            }
        }

        void setRightPage(PDFPage page) {
            if (rightPagePanel != null) {
                removePagePanel(rightPagePanel);
            }
            rightPage = page;
            if (page == null) {
                rightPagePanel = null;
                rightPageRect = null;
                rightFullPageRect = null;
            } else {
                rightFullPageRect = (Rectangle2D.Float) PagePanel.getFullPageView(page);
                rightPageRect = new Rectangle();
                rightPagePanel = addPagePanel(page);
            }
        }

        PDFPage getPage() {
            return currentPage;
        }

        PDFPage getLeftPage() {
            return leftPage;
        }

        PDFPage getRightPage() {
            return rightPage;
        }

        int getPageNumber() {
            return pagenumber;
        }

        PagePanel getPagePanel() {
            return currentPagePanel;
        }

        Rectangle getPageRectangle(PDFPage page) {
            if (leftPage != null && leftPage == page) {
                return leftPageRect;
            }
            if (rightPage != null && rightPage == page) {
                return rightPageRect;
            }
            return null;
        }

        public Dimension getPreferredSize() {
            Dimension ret = new Dimension();
            if (leftPage != null) {
                leftPageRect.x = 0;
                leftPageRect.y = 0;
                leftPageRect.width = pointsToPixels(leftFullPageRect.width);
                leftPageRect.height = pointsToPixels(leftFullPageRect.height);
                ret.height = leftPageRect.height;
                ret.width = leftPageRect.width;
            }
            if (rightPageRect != null) {
                rightPageRect.width = pointsToPixels(rightFullPageRect.width);
                rightPageRect.height = pointsToPixels(rightFullPageRect.height);
                if (rightPageRect.height > ret.height) {
                    ret.height = rightPageRect.height;
                }
                if (leftPageRect != null) {
                    rightPageRect.x = leftPageRect.width;
                    ret.width += rightPageRect.width;
                } else {
                    rightPageRect.x = rightPageRect.width;
                    ret.width = rightPageRect.width * 2;
                }
                rightPageRect.y = 0;
            } else {
                ret.width *= 2;
            }
            ret.height += margin*2;
            ret.width += margin*2 + interpagemargin;
            return ret;
        }

        public void doLayout() {
            Dimension size = getSize();
            if (size.width == 0 || size.height == 0) {
                return;
            }
            Dimension viewport = getPreferredSize();
            int cx = Math.max(size.width / 2, 0);
            int cy = Math.max(size.height / 2, 0);
            float dpi = (float)zoom * Util.getScreenResolution(this);
            if (leftPage != null) {
                leftPageRect.x = cx - (viewport.width / 2) + margin;
                leftPageRect.y = cy - (leftPageRect.height / 2);
                leftPagePanel.setBounds(leftPageRect);
                leftPagePanel.setPage(leftPage, leftFullPageRect, dpi, null);
            }
            if (rightPage != null) {
                rightPageRect.x = cx + (viewport.width / 2) - rightPageRect.width - margin;
                rightPageRect.y = cy - (rightPageRect.height / 2);
                rightPagePanel.setBounds(rightPageRect);
                rightPagePanel.setPage(rightPage, rightFullPageRect, dpi, null);
            }
        }

        /**
         * @page the page to display
         */
        private PagePanel addPagePanel(PDFPage page) {
            PagePanel pagePanel = new PagePanel();
            pagePanel.setViewport(DualPageDocumentViewport.this);
            pagePanel.setParser(getDocumentPanel().getParser(page));
            if (hints != null) {
                pagePanel.setRenderingHints(hints);
            }
            pagePanel.addPagePanelListener(this);
            pagePanel.addPagePanelInteractionListener(this);
            pagePanel.addMouseListener(this);
            pagePanel.addMouseMotionListener(this);
            add(pagePanel);

            pagePanel.raisePagePanelEvent(PagePanelEvent.createPageVisible(pagePanel, page));

            return pagePanel;
        }

        private void removePagePanel(PagePanel pagePanel) {
            pagePanel.removePagePanelListener(this);
            remove(pagePanel);
            pagePanel.setParser(null);
            pagePanel.raisePagePanelEvent(PagePanelEvent.createPageHidden(pagePanel, pagePanel.getPage()));
        }

        public void paintComponent(Graphics gg) {
            Graphics2D g = (Graphics2D) gg;
            super.paintComponent(g);
            if (zoom != 0) {
                // Paint page borders
                for (int i = 0; i < getComponentCount(); i++) {
                    Component c = getComponent(i);
                    if (c.isVisible()) {
                        paintPageBorder(g, c.getBounds());
                    }
                }
            }
        }

        public void pageUpdated(PagePanelEvent event) {
            PagePanelListener[] l;
            synchronized (pagePanelListeners) {
                l = pagePanelListeners.toArray(new PagePanelListener[pagePanelListeners.size()]);
            }
            for (int i = 0; i < l.length; i++) {
                l[i].pageUpdated(event);
            }
        }

        public void pageAction(PagePanelInteractionEvent event) {
            PagePanelInteractionListener[] l;
            synchronized (pagePanelInteractionListeners) {
                l = (PagePanelInteractionListener[]) pagePanelInteractionListeners.toArray(new PagePanelInteractionListener[pagePanelInteractionListeners.size()]);
            }
            for (int i = 0; i < l.length; i++) {
                l[i].pageAction(event);
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
                smoothScroll(0, 0, null, null);
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
                smoothScroll(0, 0, null, null);
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

        void cleanup() {
            if (leftPagePanel != null) {
                removePagePanel(leftPagePanel);
                leftPagePanel = null;
            }
            if (rightPagePanel != null) {
                removePagePanel(rightPagePanel);
                rightPagePanel = null;
            }
        }

        Collection<PagePanel> getPagePanels() {
            List<PagePanel> l = new ArrayList<PagePanel>();
            if (leftPagePanel != null) {
                l.add(leftPagePanel);
            }
            if (rightPagePanel != null) {
                l.add(rightPagePanel);
            }
            return Collections.unmodifiableList(l);
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
            smoothScroll(0, 0, null, null);
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
                view.repaint();
                DocumentPanel docpanel = getDocumentPanel();
                if (docpanel != null && !isSmoothScrolling()) {
                    docpanel.raiseDocumentPanelEvent(DocumentPanelEvent.createPagePositionChanged(docpanel));
                }
            }
        }

    }

}
