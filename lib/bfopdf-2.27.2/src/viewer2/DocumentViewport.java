// $Id: DocumentViewport.java 39802 2021-04-21 15:32:05Z mike $

package org.faceless.pdf2.viewer2;

import org.faceless.pdf2.*;
import java.awt.*;
import java.beans.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import java.awt.geom.*;

/**
 * A <code>DocumentViewport</code> displays a view of a PDF inside a {@link DocumentPanel},
 * although it can also be instantiated on it's own if required. It typically will contain
 * one or more {@link PagePanel} objects along with scrollbars and whatever else is required
 * to display the document in limited screen space.
 * See the <a href="doc-files/tutorial.html">viewer tutorial</a> for more detail on how to use this class and the "viewer" package.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see PagePanel
 * @see DocumentPanel
 * @since 2.8
 */
public abstract class DocumentViewport extends JPanel implements PropertyChangeListener {

    protected static final Cursor CURSOR_GRAB, CURSOR_GRABBING;
    protected int mouseWheelUnit;
    private int smoothScrollTime, smoothScrollDistance;
    private volatile Thread smoothScrollThread;
    private Color bordercolor = new Color(0xFF666666, true);
    private Color shadowcolor = new Color(0x80666666, true);
    protected int margin = 4, interpagemargin = 10;

    static {
        // Resize cursors if necessary - and it is on Windows, where they're 32x32
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension dim = tk.getBestCursorSize(16, 16);
        Image image = new ImageIcon(PDFViewer.class.getResource("resources/cursor_grab.png")).getImage();
        if (image.getWidth(null)!=dim.getWidth() || image.getHeight(null)!=dim.getHeight()) {
            java.awt.image.BufferedImage newimage = new java.awt.image.BufferedImage((int)dim.getWidth(), (int)dim.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics g = newimage.createGraphics();
            g.drawImage(image, 0, 0, null);
            image = newimage;
        }
        CURSOR_GRAB = tk.createCustomCursor(image, new Point(8, 8), "Grab");
        image = new ImageIcon(PDFViewer.class.getResource("resources/cursor_grabbing.png")).getImage();
        if (image.getWidth(null)!=dim.getWidth() || image.getHeight(null)!=dim.getHeight()) {
            java.awt.image.BufferedImage newimage = new java.awt.image.BufferedImage((int)dim.getWidth(), (int)dim.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics g = newimage.createGraphics();
            g.drawImage(image, 0, 0, null);
            image = newimage;
        }
        CURSOR_GRABBING = tk.createCustomCursor(image, new Point(8, 8), "Grabbing");

    }

    private DocumentPanel docpanel;
    private int zoommode;

    /**
     * A value that can be passed to {@link #setZoomMode} to specify that no re-zooming
     * of the page should be performed when the Viewport is resized; this is the default.
     * @since 2.10.3
     */
    public static final int ZOOM_NONE = 0;

    /**
     * A value that can be passed in to {@link #setZoomMode} to specify that the document
     * should be zoomed to fit the entire page in the Viewport.
     * @since 2.10.3
     */
    public static final int ZOOM_FIT = 1;

    /**
     * A value that can be passed in to {@link #setZoomMode} to specify that the document
     * shuold be zoomed to ensure the width fits the width of the Viewport.
     * @since 2.10.3
     */
    public static final int ZOOM_FITWIDTH = 2;

    /**
     * A value that can be passed in to {@link #setZoomMode} to specify that the document
     * should be zoomed to ensure the height fits the height of the Viewport.
     * @since 2.10.3
     */
    public static final int ZOOM_FITHEIGHT = 3;

    public DocumentViewport() {
        super();
    }

    public DocumentViewport(LayoutManager manager) {
        super(manager);
    }

    protected final RenderingHints getDefaultRenderingHints() {
        RenderingHints hints = new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return hints;
    }

    /**
     * Set the {@link RenderingHints} that should be used when rendering the pages
     */
    public abstract void setRenderingHints(RenderingHints hints);

    /**
     * Control how the document in the viewport is redrawn when the Viewport is resized.
     * The default is {@link #ZOOM_NONE}, which means no action is performed, but other
     * actions include {@link #ZOOM_FIT}, {@link #ZOOM_FITWIDTH} and {@link #ZOOM_FITHEIGHT},
     * which will ensure that the document is always zoomed to fit completely, fit the width
     * or fit the height of the viewport respectively
     * @since 2.10.3
     */
    public void setZoomMode(int mode) {
        this.zoommode = mode;
        if (docpanel != null) {
            docpanel.setPreference("zoomMode", mode == ZOOM_FIT ? "fit" : mode == ZOOM_FITHEIGHT ? "fitheight" : mode == ZOOM_FITWIDTH ? "fitwidth" : "none");
        }
    }

    /**
     * Return the current value of the zoom-mode, as set by {@link #setZoomMode}.
     * The returnd value is one of {@link #ZOOM_FIT}, {@link #ZOOM_FITWIDTH}, {@link #ZOOM_FITHEIGHT}
     * or {@link #ZOOM_NONE} (the default).
     * @since 2.10.3
     */
    public int getZoomMode() {
        return this.zoommode;
    }

    /**
     * Return the appropriate zoom level when switching to the specified page.
     * If the value of <code>zoommode</code> is {@link #ZOOM_NONE} then this
     * just returns the value of {@link #getZoom}, otherwise the returned zoom level
     * will correctly fit the page to the Viewport.
     * @param page the PDFPage that we are calculating the zoom for.
     * @see #getZoomMode
     * @since 2.10.3
     */
    public float getTargetZoom(int zoommode, PDFPage page) {
        float zoom = getZoom();
        if (zoommode!=ZOOM_NONE && page!=null) {
            final int PAD = 4;
            Rectangle2D fullsize = PagePanel.getFullPageView(page);
            Dimension avail = getViewportSize();
            double availw = avail.getWidth() - PAD;
            double availh = avail.getHeight() - PAD;
            int dpi = Util.getScreenResolution(docpanel == null ? this : docpanel);
            if (zoommode==ZOOM_FIT) {
                zoom = (float)Math.min(availw/fullsize.getWidth(), availh/fullsize.getHeight()) / dpi * 72;
            } else if (zoommode==ZOOM_FITWIDTH) {
                zoom = (float)(availw/fullsize.getWidth()/dpi*72);
            } else if (zoommode==ZOOM_FITHEIGHT) {
                zoom = (float)(availh/fullsize.getWidth()/dpi*72);
            }
        }
        return zoom;
    }

    /**
     * Set the currently displayed page. The exact implementation of this depends on
     * the type of viewport, but the idea is that the specified page and position
     * becomes the primary focus of this viewport.
     * @param page the page to display
     * @param x the left-most X position of the page, relative to {@link PagePanel#getFullPageView}. A value of NaN means keep the current value. 0 means the left edge
     * @param y the top-most Y position of the page, relative to {@link PagePanel#getFullPageView}. A value of NaN means keep the current value. 0 means the top edge
     * @param zoom the zoom level. A value of &lt;= 0 or NaN means keep the current zoom. A value of one means 72dpi
     */
    public abstract void setPage(PDFPage page, double x, double y, double zoom);

    /**
     * Ensure the specified point on the page is visible. The zoom level of the
     * page is not changed, but the page itself may be changed or repositioned
     * to ensure the specified point is visible in the centre area.
     * @param x the X position of the page in absolute PDF points (ie measured from the bottom left)
     * @param y the Y position of the page in absolute PDF points (ie measured from the bottom left)
     * @since 2.10.3
     */
    public void ensureVisible(PDFPage page, double x, double y) {
    }

    /**
     * Indicates whether the specified point in the specified page is
     * visible in the viewport. If <code>x</code> or <code>y</code> is
     * <code>NaN</code>, indicates whether any part of the page is visible.
     * @param x the X position of the page in points
     * @param y the Y position of the page in points
     * @since 2.10.4
     */
    public boolean isPageVisible(PDFPage page, double x, double y) {
        return false;
    }

    /**
     * Set the zoom level of this DocumentViewport. The page and position should remain
     * unchanged if possible.
     */
    public abstract void setZoom(float zoom);

    /**
     * Get the current zoom level
     */
    public abstract float getZoom();

    /**
     * Get the currently displayed PagePanel. As for {@link #setPage setPage()}, the exact
     * implementation of this method depends on the type of viewport, but the idea is
     * it returns the PagePanel that is the primary focus of this viewport.
     */
    public abstract PagePanel getPagePanel();

    /**
     * Get the JComponent that directly contains the PagePanel objects, not including
     * scrollbars or other similar items.
     * @since 2.10.4
     */
    public abstract JComponent getView();

    /**
     * Returns a read only Collection containing all the PagePanels currently
     * displayed in this Viewport.
     * @since 2.10.4
     */
    public Collection<PagePanel> getPagePanels() {
        return Collections.singleton(getPagePanel());
    }

    /**
     * Return the PDFPage in use by {@link #getPagePanel}
     */
    public PDFPage getPage() {
        PagePanel panel = getPagePanel();
        return panel==null ? null : panel.getPage();
    }

    /**
     * Return the page that is currently in the process of rendering. If the page has
     * rendered this method returns the same as {@link #getPage}
     */
    public abstract PDFPage getRenderingPage();

    /**
     * Return the index of the page that should be displayed when the specified
     * page is selected in this viewport and the "next page" is requested, via a button
     * or other action. By default this is just the next page in the PDF
     * @return the index of the next page to display, or -1 if no such page exists.
     * @since 2.14
     */
    public int getNextSelectablePageIndex(PDFPage page) {
        if (page != null) {
            int ix = getDocumentPanel().getPageNumber(page);
            if (ix >= 0 && ix + 1 < getDocumentPanel().getNumberOfPages()) {
                return ix + 1;
            }
        }
        return -1;
    }

    /**
     * Return the index of the page that should be displayed when the specified
     * page is selected in this viewport and the "previous page" is requested, via a button
     * or other action. By default this is just the previous page in the PDF
     * @return the index of the previous page to display, or -1 if no such page exists.
     * @since 2.14
     */
    public int getPreviousSelectablePageIndex(PDFPage page) {
        if (page != null) {
            int ix = getDocumentPanel().getPageNumber(page);
            if (ix > 0) {
                return ix - 1;
            }
        }
        return -1;
    }

    /**
     * Return the size in pixels of the space available to display pages in this viewport,
     * not including scrollbars or other decoration
     */
    public abstract Dimension getViewportSize();

    /**
     * Return the "Adjustable" object for the specified position - typically this
     * is the horizontal or vertical {@link JScrollBar}, although this method may
     * return <code>null</code> or accept other parameters. The adjustable is
     * measured in AWT space (pixels from the top left). This method may return
     * null if there is no adjustable.
     * @param position one of {@link Adjustable#HORIZONTAL} or {@link Adjustable#VERTICAL}
     * @since 2.10
     */
    public abstract Adjustable getAdjustable(int position);

    /**
     * Set the values of the adjustables returned by {@link #getAdjustable}.
     * Calling this method is preferable to setting the values of each adjustable
     * individually when both are to be set
     * @since 2.10.4
     * @param horizontal the value for the {@link Adjustable#HORIZONTAL} adjustable.
     * @param vertical the value for the {@link Adjustable#VERTICAL} adjustable.
     */
    public abstract void setAdjustableValues(int horizontal, int vertical);

    /**
     * Return the {@link DocumentPanel} containing this DocumentViewport.
     */
    public DocumentPanel getDocumentPanel() {
        return docpanel;
    }

    /**
     * Set the DocumentPanel that contains this DocumentViewport. If overriding
     * this method you must first call <code>super.setDocumentPanel(panel)</code>
     * @since 2.11.4 (when it was made public)
     */
    public void setDocumentPanel(DocumentPanel panel) {
        this.docpanel = panel;
        if (panel != null) {
            setLocale(panel.getLocale());
            mouseWheelUnit = 16;
            smoothScrollTime = smoothScrollDistance = 500;
            PropertyManager manager = panel.getViewer() == null ? PDF.getPropertyManager() : panel.getViewer().getPropertyManager();
            if (manager != null) {
                String p = manager.getProperty("mouseWheelUnit");
                if (p != null) {
                    try {
                        mouseWheelUnit = Integer.parseInt(p);
                    } catch (NumberFormatException e) {}
                }
                p = manager.getProperty("smoothScrollTime");
                if (p != null) {
                    try {
                        smoothScrollTime = Integer.parseInt(p);
                    } catch (NumberFormatException e) {}
                }
                p = manager.getProperty("smoothScrollDistance");
                if (p != null) {
                    try {
                        smoothScrollDistance = Integer.parseInt(p);
                    } catch (NumberFormatException e) {}
                }
                p = manager.getProperty("viewportMargin");
                if (p != null) {
                    try {
                        margin = Integer.parseInt(p);
                    } catch (Exception e) { }
                }
                p = manager.getProperty("viewportGap");
                if (p != null) {
                    try {
                        interpagemargin = Integer.parseInt(p);
                    } catch (Exception e) { }
                }

                p = manager.getProperty("viewportBorderColor");
                if (p != null) {
                    if (p.equals("none") || p.equals("transparent")) {
                        bordercolor = null;
                    } else {
                        try {
                            int v = Integer.parseInt(p, 16);
                            if ((v & 0xFF000000) == 0) {
                                v |= 0xFF000000;
                            }
                            bordercolor = new Color(v, true);
                        } catch (NumberFormatException e) {}
                    }
                }
                p = manager.getProperty("viewportShadowColor");
                if (p != null) {
                    if (p.equals("none") || p.equals("transparent")) {
                        shadowcolor = null;
                    } else {
                        try {
                            int v = Integer.parseInt(p, 16);
                            if ((v & 0xFF000000) == 0) {
                                v |= 0xFF000000;
                            }
                            shadowcolor = new Color(v, true);
                        } catch (NumberFormatException e) {}
                    }
                }
            }
        }
    }

    /**
     * Add a {@link PagePanelListener} to any {@link PagePanel} objects that have been
     * or will be created by this DocumentViewport
     * @param listener the listener
     */
    public abstract void addPagePanelListener(PagePanelListener listener);

    /**
     * Remove a {@link PagePanelListener} from any {@link PagePanel} objects that have been
     * created by this DocumentViewport
     * @param listener the listener
     */
    public abstract void removePagePanelListener(PagePanelListener listener);

    /**
     * Add a {@link PagePanelInteractionListener} to any {@link PagePanel} objects that have been
     * or will be created by this DocumentViewport
     * @param listener the listener
     */
    public abstract void addPagePanelInteractionListener(PagePanelInteractionListener listener);

    /**
     * Remove a {@link PagePanelInteractionListener} from any {@link PagePanel} objects
     * that have been created by this DocumentViewport
     * @param listener the listener
     */
    public abstract void removePagePanelInteractionListener(PagePanelInteractionListener listener);

    /**
     * Indicates whether the contents of this viewport can be dragged with
     * the mouse to position them.
     * @since 2.11.15
     */
    public abstract boolean isDraggable();

    /**
     * Sets whether to allow the contents of this viewport to be dragged
     * with the mouse.
     * @param draggable if true, contents may be dragged
     * @since 2.11.15
     */
    public abstract void setDraggable(boolean draggable);

    /**
     * The viewport implements PropertyChangeListener, and will be called
     * whenever the PDF has been updated. By default it's a no-op.
     * @since 2.11.25
     */
    public void propertyChange(PropertyChangeEvent event) {
    }

    /**
     * Smoothly adjust the supplied scrollbars from their current position
     * to the specified position, or (if hsb or vsb are null) cancel any
     * running adjustment. The timing in ms and max distance in pixels for
     * a smooth scroll are set by the <code>smoothScrollTime</code> and
     * <code>smoothScrollDistance</code> properties on the DocumentPanel,
     * and both default to 500
     * @param x the desired position of the horizontal scrollbar
     * @param y the desired position of the vertical scrollbar
     * @param hsb the horizontal scrollbar
     * @param vsb the vertical scrollbar
     * @since 2.11.25
     */
    protected void smoothScroll(int x, int y, final JScrollBar hsb, final JScrollBar vsb) {
        if (hsb == null || vsb == null || smoothScrollTime == 0 || smoothScrollDistance == 0) {
            smoothScrollThread = null;
            return;
        }
        final int sx = hsb.getValue();
        final int sy = vsb.getValue();
        final int dx = Math.max(hsb.getMinimum(), Math.min(hsb.getMaximum(), x));
        final int dy = Math.max(vsb.getMinimum(), Math.min(vsb.getMaximum(), y));

        if (Math.abs(dx - sx) > smoothScrollDistance || Math.abs(dy - sy) > smoothScrollDistance) {
            smoothScrollThread = null;
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    hsb.setValue(dx);
                    vsb.setValue(dy);
                }
            });
            return;
        }

        final long start = System.currentTimeMillis();

        smoothScrollThread = new Thread("BFO-Viewport-Smoothscroll") {
            public void run() {
                final double max = Math.PI / 2;
                double prop = 0;
                final Thread thisthread = this;
                while (prop < max && smoothScrollThread == thisthread) {
                    prop = (System.currentTimeMillis() - start) * max / smoothScrollTime;
                    final int nx = sx + (int)Math.round((dx-sx) * Math.sin(Math.min(prop, max)));
                    final int ny = sy + (int)Math.round((dy-sy) * Math.sin(Math.min(prop, max)));
                    try {
                        SwingUtilities.invokeAndWait(new Runnable() {
                            public void run() {
                                if (nx == dx && ny == dy) {
                                    vsb.setValueIsAdjusting(nx != dx);
                                    hsb.setValueIsAdjusting(ny != dy);
                                    // To ensure isSmoothScrolling==false in final setValue
                                    if (smoothScrollThread == thisthread) {
                                        smoothScrollThread = null;
                                    }
                                }
                                hsb.setValue(nx);
                                vsb.setValue(ny);
                            }
                        });
                        Thread.sleep(50);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        };
        smoothScrollThread.start();
    }

    /**
     * Return true if this viewport is smooth scrolling
     * @since 2.11.25
     */
    protected boolean isSmoothScrolling() {
        return smoothScrollThread != null;
    }

    /**
     * Return the Color to be used to paint the border of each page
     * in this viewport
     * @since 2.16.1
     */
    public Color getBorderColor() {
        return bordercolor;
    }

    /**
     * Return the Color to be used to paint the shadow for each page
     * in this viewport
     * @since 2.16.1
     */
    public Color getShadowColor() {
        return shadowcolor;
    }

    /**
     * Paint the border of the page
     */
    protected void paintPageBorder(Graphics g, Rectangle r) {
        int x = r.x - 1;
        int y = r.y - 1;
        int w = r.width + 2;
        int h = r.height + 2;
        Color c = getBorderColor();
        if (c != null) {
            g.setColor(c);
            g.drawRect(x, y, w, h);
        }
        c = getShadowColor();
        if (c != null) {
            y += h;
            int rgb = c.getRGB();
            int alpha = (rgb >> 24) & 0xFF;
            while (alpha > 0) {
                g.setColor(new Color((rgb & 0xFFFFFF) | (alpha<<24), true));
                x++;
                w -= 2;
                y++;
                g.drawLine(x, y, x+w, y);
                alpha -= 0x50;
            }
        }
    }


}
