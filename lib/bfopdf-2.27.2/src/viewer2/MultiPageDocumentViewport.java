// $Id: MultiPageDocumentViewport.java 39857 2021-04-26 11:26:38Z mike $

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
 * A {@link DocumentViewport} that displays all the pages of a PDF in one column
 * (or row) in the viewport. Navigating between pages is done by scrolling the viewport.
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see DocumentPanel
 * @since 2.10.4
 */
public class MultiPageDocumentViewport extends DocumentViewport {

    static final int HORIZPAD = 2, VERTPAD = 4;
    private JScrollPane scrollPane;
    private View view;
    private float zoom, dpi;

    private Listener listener;
    private RenderingHints hints;
    private ArrayList<PagePanelListener> pagePanelListeners;
    private ArrayList<PagePanelInteractionListener> pagePanelInteractionListeners;
    private PDF pdf;

    /**
     * Constructs a new MultiPageDocumentViewport with a vertical
     * orientation.
     */
    public MultiPageDocumentViewport() {
        this(Adjustable.VERTICAL);
    }

    /**
     * Constructs a new multi-page document viewport with the specified
     * orientation.
     * @param orientation Adjustable.VERTICAL or Adjustable.HORIZONTAL
     */
    public MultiPageDocumentViewport(int orientation) {
        view = this.new View(orientation);
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
        dpi = zoom * Util.getScreenResolution(this);

        // event management
        listener = this.new Listener();
        addComponentListener(listener);
        scrollPane.getHorizontalScrollBar().addAdjustmentListener(listener);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(listener);

        pagePanelListeners = new ArrayList<PagePanelListener>();
        pagePanelInteractionListeners = new ArrayList<PagePanelInteractionListener>();

        Util.fixScrollPaneKeyBindings(scrollPane);
    }

    public void setDocumentPanel(DocumentPanel panel) {
        super.setDocumentPanel(panel);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(mouseWheelUnit);
        scrollPane.getVerticalScrollBar().setUnitIncrement(mouseWheelUnit);
        PDF pdf = panel == null ? null : panel.getPDF();
        if (pdf != this.pdf) {
            if (this.pdf != null) {
                this.pdf = null;
                view.init();
            }
            this.pdf = pdf;
            if (pdf != null) {
                view.init();
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        view.updateLayout(true);
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
            throw new IllegalArgumentException("Page is not in this DocumentPanell");
        }
        setZoom((float) zoom);
        Rectangle2D crop = PagePanel.getFullPageView(page);
        ensureVisible(page, x + crop.getMinX(), crop.getMaxY() - y, false);
    }

    public void ensureVisible(PDFPage page, double x, double y) {
        ensureVisible(page, x, y, true);
    }

    /**
     * @param x x coordinate in PDF units relative to MediaBox - 0 is left. May be out of range or NaN
     * @param y y coordinate in PDF units relative to MediaBox - 0 is bottom. May be out of range or NaN
     * @param centre if true, centre this location in the viewport,
     * otherwise cause it to be displayed at the top left of the viewport.
     */
    private void ensureVisible(PDFPage page, double x, double y, boolean centre) {
        DocumentPanel docpanel = getDocumentPanel();
        int pagenumber = docpanel.getPageNumber(page);
        if (pagenumber < 0 || pagenumber >= view.pageRects.size()) {
            return;
        }
        View.PageRect pr = view.pageRects.get(pagenumber);
        Rectangle pageRect = pr.getBounds();
        if (pageRect != null) {
            Rectangle2D crop = PagePanel.getFullPageView(page);
            int xoffset = pageRect.x, yoffset = pageRect.y;
            if (x > crop.getMinX()) {   // Also confirms !NaN
                xoffset += pointsToPixels((float)(Math.min(crop.getMaxX(), x) - crop.getMinX()));
            }
            if (y < crop.getMaxY()) {   // Also confirms !NaN
                yoffset += pointsToPixels((float)(crop.getHeight() - Math.max(0, y - crop.getMinY())));
            }

            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            if (centre) {
                xoffset -= hsb.getVisibleAmount() / 2;
                yoffset -= vsb.getVisibleAmount() / 2;
            }
            LinearizedSupport ls = docpanel.getLinearizedSupport();
            if (!ls.isFullyLoaded() || (getPagePanel() != null && getPagePanel().getClip() != null)) {
                hsb.setValue(xoffset);  // Don't want to smooth scroll if we're clipping
                vsb.setValue(yoffset);  // the page rectangles.
                view.clipChanged();
            } else {
                smoothScroll(xoffset, yoffset, hsb, vsb);
            }
        }
    }

    public boolean isPageVisible(PDFPage page, double x, double y) {
        Rectangle pageRect = view.getBounds();  // Never null
        Rectangle target = (Rectangle) pageRect.clone();
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

    public JComponent getView() {
        return (JComponent)scrollPane.getViewport().getView();
    }

    public float getZoom() {
        if (zoom == 0) {
            zoom = getTargetZoom(getZoomMode(), getPage());
            dpi = zoom * Util.getScreenResolution(this);
        }
        return zoom;
    }

    public void setZoom(float zoom) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Must be on EDT");
        }
        PDFViewer viewer = getDocumentPanel().getViewer();
        if (viewer != null && (zoom < viewer.getMinZoom() / 100f || zoom > viewer.getMaxZoom() / 100f)) {
            return;
        }
        if (zoom == zoom && zoom > 0.01 && Math.abs(zoom - this.zoom) > 0.01) {
            final JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            final JScrollBar vsb = scrollPane.getVerticalScrollBar();
            final int hx = hsb.getVisibleAmount();
            final int vx = vsb.getVisibleAmount();
            final float hv = (float)(hsb.getValue() + hx/2) / hsb.getMaximum();
            final float vv = (float)(vsb.getValue() + vx/2) / vsb.getMaximum();
            this.zoom = zoom;
            dpi = zoom * Util.getScreenResolution(this);
            view.updateLayout(true);

            final Dimension viewsize = view.getSize();
            listener.setEnabled(false); // don't send adjustment events
            hsb.setValues(Math.round(viewsize.width * hv) - hx/2, hx, 0, viewsize.width);
            vsb.setValues(Math.round(viewsize.height * vv) - vx/2, vx, 0, viewsize.height);
            listener.setEnabled(true);
            view.clipChanged();
            view.repaint();
        }
    }

    private int pointsToPixels(float points) {
        return Math.round(points * dpi / 72);
    }

    private float pixelsToPoints(int pixels) {
        return (float)pixels / (dpi / 72);
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
        if (page!=null) {
            // Compute size of given page in pixels, including border
            Rectangle2D.Float fpr = (Rectangle2D.Float) PagePanel.getFullPageView(page);
            int ppw = pointsToPixels(fpr.width) + HORIZPAD;
            int pph = pointsToPixels(fpr.height) + VERTPAD;
            
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            Rectangle size = scrollPane.getViewportBorderBounds();
            switch (zoommode) {
              case ZOOM_FITWIDTH:
                size.width -= vsb.getWidth();
                outzoom = ((float) size.width / ppw) * zoom;
                break;
              case ZOOM_FITHEIGHT:
                size.height -= hsb.getHeight();
                outzoom = ((float) size.height / pph) * zoom;
                break;
              case ZOOM_FIT:
                size.width -= vsb.getWidth();
                size.height -= hsb.getHeight();
                float zw = ((float) size.width / ppw) * zoom;
                float zh = ((float) size.height / pph) * zoom;
                outzoom = Math.min(zw, zh);
                break;
              default:
                outzoom = zoom == 0 ? 1 : zoom;
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
            smoothScroll(0, 0, null, null);
            view.init();
            view.updateLayout(false);
            view.clipChanged();
        }
    }

    private class View extends JPanel implements PagePanelListener, PagePanelInteractionListener, MouseListener, MouseMotionListener, Scrollable {

        static final int MINSIZE = 100;
        static final int DEFAULTWIDTH = 595;
        static final int DEFAULTHEIGHT = 842;

        private final boolean vertical;
        private Dimension maxPageSize;
        private PagePanel mainPagePanel;
        private PageRect mainPageRect;
        boolean needsLayout = true;
        private Rectangle lastClip;

        private boolean draggable;
        private Point mouseDownEvent;
        private Point mouseDownScroll;

        private Map<PDFPage,PageRect> pageToRect = new HashMap<PDFPage,PageRect>();
        private List<PageRect> pageRects = new ArrayList<PageRect>();

        View(int orientation) {
            if (orientation != Adjustable.VERTICAL && orientation != Adjustable.HORIZONTAL) {
                throw new IllegalArgumentException("Invalid orientation: "+orientation);
            }
            this.vertical = orientation == Adjustable.VERTICAL;
            setLayout(null); // we will handle layout
            setFocusable(false);
            addMouseListener(this);
            addMouseMotionListener(this);
        }

        /**
         * New or pages changed
         */
        void init() {
            if (pdf == null) {
                for (PageRect pr : pageRects) {
                    pr.unloadPagePanel();
                }
                pageToRect.clear();
                pageRects.clear();
                mainPagePanel = null;
                mainPageRect = null;
            } else {
                List<PageRect> toRemove = new ArrayList<PageRect>(pageRects);
                pageRects.clear();

                DocumentPanel docpanel = getDocumentPanel();
                int numpages = docpanel.getNumberOfPages();
                LinearizedSupport ls = docpanel.getLinearizedSupport();
                for (int i = 0; i < numpages; i++) {
                    PageRect pr = null;
                    PDFPage page  = null;
                    if (ls.isPageLoaded(i)) {
                        page = docpanel.getPage(i);
                        pr = pageToRect.get(page);
                    }
                    if (pr == null) {
                        // New page
                        pr = this.new PageRect(i);
                    } else {
                        pr.computePreferredSize();
                        int j = pr.getPageNumber();
                        if (i != j) {
                            // Existing page was reordered
                            pr.setPageNumber(i);
                        }
                    }
                    pageRects.add(pr);
                    toRemove.remove(pr);
                }
                for (PageRect pr : toRemove) {
                    pr.unloadPagePanel();
                }
            }
        }

        public Dimension getPreferredSize() {
            if (needsLayout) {
                updateLayout(false);
                needsLayout = false;
            }
            return getSize();
        }

        // Scrollable
        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        // Scrollable
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? scrollPane.getVerticalScrollBar().getUnitIncrement() : scrollPane.getHorizontalScrollBar().getUnitIncrement();
        }

        // Scrollable
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? scrollPane.getVerticalScrollBar().getBlockIncrement() : scrollPane.getHorizontalScrollBar().getBlockIncrement();
        }

        // Scrollable
        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        // Scrollable
        @Override public boolean getScrollableTracksViewportWidth() {
            // This kludge is to workaround https://bugs.openjdk.java.net/browse/JDK-8042020,
            // first reported in Java 7 and helpfully flagged as "Wont Fix".
            // See also https://stackoverflow.com/questions/11587292/jscrollpane-not-wide-enough-when-vertical-scrollbar-appears
            if (Util.isLAFNimbus()) {
                Dimension sz = getSize();
                Dimension vsz = scrollPane.getViewport().getSize();
                return sz.width - 1 <= vsz.width;
            } else {
                return false;
            }
        }

        void updateLayout(boolean dpiChanged) {
            maxPageSize = new Dimension(0, 0);
            Dimension size = new Dimension(MINSIZE, MINSIZE);
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            int len = pageRects.size();
            int xoff = 0, yoff = 0; // amount to move scrollbar
            int pagenumber = 0;
            PDFPage page = getPage();
            if (page != null) {
                pagenumber = getDocumentPanel().getPageNumber(page);
            }
            if (len > 0) {
                // default size is A4
                int defaultWidth = pointsToPixels(DEFAULTWIDTH) + HORIZPAD;
                int defaultHeight = pointsToPixels(DEFAULTHEIGHT) + VERTPAD;
                int y = 0;
                // set size and y
                for (int i = 0; i < len; i++) {
                    PageRect pr = pageRects.get(i);
                    if (dpiChanged) {
                        pr.computePreferredSize();
                    }
                    Dimension s = pr.getPreferredSize();
                    if (pr.getPage() == null) { // page not loaded yet
                        s.width = defaultWidth;
                        s.height = defaultHeight;
                        pr.setPreferredSize(s);
                    } else {
                        // use last actual size for new default size
                        defaultWidth = s.width;
                        defaultHeight = s.height;
                        if (i < pagenumber) {
                            Rectangle b = pr.getBounds();
                            // move scrollbar value by difference
                            xoff -= b.width - s.width;
                            yoff -= b.height - s.height;
                        }
                    }
                    if (maxPageSize.width < s.width) {
                        maxPageSize.width = s.width;
                    }
                    if (maxPageSize.height < s.height) {
                        maxPageSize.height = s.height;
                    }
                    if (vertical) {
                        pr.setBounds(0, y, s.width, s.height);
                        y += s.height + interpagemargin;
                    } else {
                        pr.setBounds(y, 0, s.width, s.height);
                        y += s.width + interpagemargin;
                    }
                }
                // centre layout
                Dimension ss = scrollPane.getSize();
                int mw = ss.width - Math.max(20, vsb.getWidth());
                int mh = ss.height - Math.max(20, hsb.getHeight());
                if (vertical) {
                    size.width = Math.max(mw, maxPageSize.width);
                    size.height = Math.max(mh, y - interpagemargin);
                    maxPageSize.height += interpagemargin;
                } else {
                    size.width = Math.max(mw, y - interpagemargin);
                    size.height = Math.max(mh, maxPageSize.height);
                    maxPageSize.width += interpagemargin;
                }
                int c = vertical ? size.width / 2 : size.height / 2;
                for (int i = 0; i < len; i++) {
                    PageRect pr = pageRects.get(i);
                    Rectangle pb = pr.getBounds();
                    if (vertical) {
                        pr.setLocation(c - (pb.width / 2), pb.y);
                    } else {
                        pr.setLocation(pb.x, c - (pb.height / 2));
                    }
                    pr.doLayout();
                }
            }

            Dimension oldSize = getSize();
            if (!size.equals(oldSize)) {
                setSize(size);
                hsb.setMaximum(size.width);
                vsb.setMaximum(size.height);
                hsb.setBlockIncrement(maxPageSize.width);
                vsb.setBlockIncrement(maxPageSize.height);
                if (vertical) {
                    vsb.setValue(vsb.getValue() + yoff);
                } else {
                    hsb.setValue(hsb.getValue() + xoff);
                }
            }
        }

        /**
         * Determines which pagepanels should be loaded or unloaded based on
         * the current clip window.
         */
        void clipChanged() {
            Rectangle clip = new Rectangle();
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            clip.x = hsb.getValue();
            clip.y = vsb.getValue();
            clip.width = hsb.getVisibleAmount();
            clip.height = vsb.getVisibleAmount();
            lastClip = clip;
            if (clip.width < 2 || clip.height < 2) {
                return;
            }
            int len = pageRects.size();
            int maxvispixels = 0;
            List<PageRect> pagesToLoad = new ArrayList<PageRect>();
            for (int i = 0; i < len; i++) {
                PageRect pr = pageRects.get(i);
                Rectangle pb = pr.getBounds();
                if (pb.intersects(clip)) {
                    // Determine if this is the new "main" panel
                    Rectangle visible = clip.intersection(pb);
                    visible.x -= pb.x;
                    visible.y -= pb.y;
                    if (maxvispixels < Integer.MAX_VALUE) {
                        if (visible.width == pb.width && visible.height == pb.height) {
                            mainPageRect = pr;
                            maxvispixels = Integer.MAX_VALUE;
                        }
                        int vispixels = visible.width * visible.height;
                        if (vispixels > maxvispixels) {
                            maxvispixels = vispixels;
                            mainPageRect = pr;
                        }
                    }
                    pr.setClip(visible);
                    pagesToLoad.add(pr);
                } else {
                    pr.unloadPagePanel();
                }
            }
            for (PageRect pr : pagesToLoad) {
                pr.loadPagePanel();
            }
        }

        PagePanel getPagePanel() {
            return mainPagePanel;
        }

        Collection<PagePanel> getPagePanels() {
            Collection<PagePanel> acc = new ArrayList<PagePanel>();
            int len = getComponentCount();
            for (int i = 0; i < len; i++) {
                Component c = getComponent(i);
                if (c instanceof PagePanel) {
                    acc.add((PagePanel) c);
                }
            }
            return acc;
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            // Paint the visible pagerects as they are not actual comps
            for (PageRect pr : pageRects) {
                if (pr.isVisible()) {
                    Rectangle bounds = pr.getBounds();
                    g.translate(bounds.x, bounds.y);
                    pr.paintComponent(g);
                    g.translate(-bounds.x, -bounds.y);
                }
            }
        }

        public void pageUpdated(PagePanelEvent event) {
            PagePanelListener[] l;
            synchronized (pagePanelListeners) {
                l = (PagePanelListener[]) pagePanelListeners.toArray(new PagePanelListener[pagePanelListeners.size()]);
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

        /**
         * Manager for a PagePanel. It computes the panel dimensions and
         * adds and removes its associated PagePanel from the View
         * depending on whether the component is currently visible
         * in the viewport.
         * The rectangle is 2 pixels wider and 4 pixels higher than the
         * pagepanel in order to draw a nice border around the page.
         */
        class PageRect implements Runnable {

            private int pagenumber;
            private boolean onscreen;
            private PDFPage page;
            private Rectangle2D.Float fullPageRect;
            private PagePanel panel;
            private JLabel spinner;
            private Rectangle clip;
            private Dimension ps = new Dimension();
            private Rectangle bounds = new Rectangle();

            PageRect(int pagenumber) {
                setPageNumber(pagenumber);
            }

            PDFPage getPage() {
                return page;
            }

            int getPageNumber() {
                return pagenumber;
            }

            void setPageNumber(int pagenumber) {
                if (this.pagenumber == pagenumber) {
                    return;
                }
                if (panel != null) {
                    unloadPagePanel();
                }
                page = null;
                this.pagenumber = pagenumber;
                DocumentPanel docpanel = getDocumentPanel();
                LinearizedSupport ls = docpanel.getLinearizedSupport();
                if (ls.isPageLoaded(pagenumber)) {
                    page = docpanel.getPage(pagenumber);
                    pageToRect.put(page, this);
                    computePreferredSize();
                } else {
                    ls.invokeOnPageLoad(pagenumber, this);
                }
            }

            Rectangle2D.Float getFullPageRect() {
                return fullPageRect;
            }

            public void run() {
                // Page was loaded possibly after layout
                if (pdf == null) {
                    return;
                }
                page = getDocumentPanel().getPage(pagenumber);
                pageToRect.put(page, this);
                int ow = ps.width, oh = ps.height;
                computePreferredSize();
                if (ow != ps.width || oh != ps.height) {
                    needsLayout = true;
                    revalidate();
                }
                repaint();
            }

            void computePreferredSize() {
                if (page == null) {
                    return;
                }
                fullPageRect = (Rectangle2D.Float) PagePanel.getFullPageView(page);
                ps.width = pointsToPixels(fullPageRect.width) + HORIZPAD;
                ps.height = pointsToPixels(fullPageRect.height) + VERTPAD;
            }

            public Dimension getPreferredSize() {
                return ps;
            }

            public void setPreferredSize(Dimension ps) {
                this.ps = ps;
            }

            public Rectangle getBounds() {
                return bounds;
            }

            public void setBounds(int x, int y, int width, int height) {
                bounds.x = x;
                bounds.y = y;
                bounds.width = width;
                bounds.height = height;
            }

            public void setLocation(int x, int y) {
                bounds.x = x;
                bounds.y = y;
            }

            void doLayout() {
                if (panel != null) {
                    panel.setBounds(bounds.x + HORIZPAD/2, bounds.y + HORIZPAD/2, bounds.width - HORIZPAD, bounds.height - VERTPAD);
                } else if (spinner != null) {
                    spinner.setBounds(bounds.x + HORIZPAD/2, bounds.y + HORIZPAD/2, bounds.width - HORIZPAD, bounds.height - VERTPAD);
                }
            }

            PagePanel getPagePanel() {
                return panel;
            }

            void loadPagePanel() {
                onscreen = true;
                if (page != null) {
                    pagePanelLoad();
                } else {
                    if (spinner == null) {
                        Icon icon = UIManager.getIcon("PDFViewer.Spinner.icon");
                        spinner = new JLabel(icon);
                        doLayout();
                        view.add(spinner);
                        LinearizedSupport ls = getDocumentPanel().getLinearizedSupport();
                        ls.invokeOnPageLoad(pagenumber, this.new PanelLoader());
                    }
                    getDocumentPanel().schedule(new Runnable() {
                        public void run() {
                            if (onscreen && pdf != null) {
                                getDocumentPanel().getPage(pagenumber);
                            }
                        }
                    }, 100);
                }
            }

            private class PanelLoader implements Runnable {
                
                public void run() {
                    if (pdf == null) {
                        return;
                    }
                    page = getDocumentPanel().getPage(pagenumber);
                    int ow = ps.width, oh = ps.height;
                    computePreferredSize();
                    if (ow != ps.width || oh != ps.height) {
                        needsLayout = true;
                    }
                    pagePanelLoad();
                }

            }

            private void pagePanelLoad() {
                if (page == null) { // must be loaded now
                    page = getDocumentPanel().getPage(pagenumber);
                }
                DocumentPanel docpanel = getDocumentPanel();
                pageToRect.put(page, this);
                if (panel == null && docpanel != null) {
                    panel = new PagePanel();
                    panel.setViewport(MultiPageDocumentViewport.this);
                    panel.setParser(docpanel.getParser(page));
                    if (hints != null) {
                        panel.setRenderingHints(hints);
                    }
                    panel.addPagePanelListener(View.this);
                    panel.addPagePanelInteractionListener(View.this);
                    panel.addMouseListener(View.this);
                    panel.addMouseMotionListener(View.this);
                    panel.setPage(page, fullPageRect, dpi, clip);
                    if (spinner != null) {
                        view.remove(spinner);
                        spinner = null;
                    }
                    doLayout();
                    view.add(panel);
                    panel.raisePagePanelEvent(PagePanelEvent.createPageVisible(panel, page));
                    revalidate();
                    repaint();
                }
            
                // Maybe fire pageChanged and pagePositionChanged events
                if (this == mainPageRect && docpanel != null && mainPagePanel != panel) {
                    PDFPage oldPage = (mainPagePanel == null) ? null : mainPagePanel.getPage();
                    mainPagePanel = panel;
                    DocumentPanelEvent dpe = DocumentPanelEvent.createPageChanged(docpanel);
                    dpe.setPreviousPage(oldPage);
                    docpanel.raiseDocumentPanelEvent(dpe);
                }
            }

            void setClip(Rectangle clip) {
                this.clip = clip;
                clipChanged();
            }

            void clipChanged() {
                if (panel != null) {
                    panel.setPage(page, fullPageRect, dpi, clip);
                }
            }

            void unloadPagePanel() {
                if (panel != null) {
                    panel.removePagePanelListener(View.this);
                    panel.removePagePanelInteractionListener(View.this);
                    panel.removeMouseListener(View.this);
                    panel.removeMouseMotionListener(View.this);
                    view.remove(panel);
                    panel.setParser(null);
                    panel.raisePagePanelEvent(PagePanelEvent.createPageHidden(panel, page));
                    panel = null;
                }
                onscreen = false;
            }

            boolean isVisible() {
                return (panel != null || spinner != null);
            }

            public void paintComponent(Graphics g) {
                // Paint page border and lower shadow
                Rectangle r = (Rectangle)getBounds().clone();
                r.x = r.y = 1;
                r.width -= 2;
                r.height -= 2;
                paintPageBorder(g, r);
            }

        }

    }

    private class Listener implements ComponentListener, AdjustmentListener {

        private boolean enabled = true;

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void componentResized(ComponentEvent e) {
            view.updateLayout(false); // re-centre
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
                view.clipChanged();
                DocumentPanel docpanel = getDocumentPanel();
                if (docpanel != null && !isSmoothScrolling()) {
                    docpanel.raiseDocumentPanelEvent(DocumentPanelEvent.createPagePositionChanged(docpanel));
                }
            }
        }

    }

}
