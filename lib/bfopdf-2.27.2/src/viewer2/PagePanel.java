// $Id: PagePanel.java 35489 2020-03-06 11:57:14Z mike $

package org.faceless.pdf2.viewer2;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.faceless.util.SoftInterruptibleThread;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.feature.WarningNotifier;

/**
 * <p>
 * The <code>PagePanel</code> class is the lowest-level class for rendering a {@link PDFPage}
 * as a {@link JPanel}. At it's most basic it will simply render the page via a {@link PagePainter},
 * but when included inside a {@link DocumentViewport} as part of a {@link DocumentPanel}
 * this class may also create subcomponents representing {@link PDFAnnotation}s, as created by the
 * {@link AnnotationComponentFactory} class.
 * </p><p>
 * Before a PagePanel is disposed of it should have <code>setParser(null)</code> called on it.
 * </p>
 * See the <a href="doc-files/tutorial.html">viewer tutorial</a> for more detail on how to use this class and the "viewer" package.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see DocumentViewport
 * @since 2.8
 */
public class PagePanel extends JPanel {

    private static final long DEFAULTMAXANNOTATIONPIXELS = 1700 * 2800;
    private long maxAnnotationPixels = DEFAULTMAXANNOTATIONPIXELS;        // Max size of annotation bitmap
    private static final long MIN_PIXELS_FOR_CLIP = 2097152L; // Enough for 100% A4 at 150dpi

    private static final String[] ANNOTATION_STATES = new String[] { "N", "D", "R" };

    private DocumentViewport viewport;          // The viewport this page is a member of
    private PDFPage page;                       // The page currently being displayed
    private PDFParser parser;                   // The PDFParser to create the pages
    private PainterThread paintthread;          // Thread that handles the page update

    private volatile float dpi;                          // The DPI
    private volatile float x1, y1, x2, y2;               // Scope of the viewport
    private volatile int orientation;                    // The Page orientation of the last paint

    private RenderingHints hints;
    private final Collection<PagePanelListener> listeners;         // Collection of PagePanelListener objects
    private final Collection<PagePanelInteractionListener> ilisteners;        // Collection of PagePanelInteractionListener objects
    private Map<PDFAnnotation,JComponent> annotcomponents;                // Map of PDFAnnotation -> JComponent.  Modify only in EDT!
    private Listener omnilistener;
    private boolean extract;
    private AffineTransform pageToScreen, screenToPage; // Transforms between page<=>screen coordinates

    private final int panelindex;               // For debugging
    private static int globalpanelindex;

    /**
     * Create a new PagePanel
     */
    public PagePanel() {
        super(null, true);
        Util.initialize();
        setOpaque(false);
        setFocusTraversalPolicy(this.new AnnotationFocusTraversalPolicy());
        listeners = new LinkedHashSet<PagePanelListener>();
        ilisteners = new LinkedHashSet<PagePanelInteractionListener>();
        annotcomponents = Collections.synchronizedMap(new HashMap<PDFAnnotation,JComponent>());
        omnilistener = new Listener();
        panelindex = globalpanelindex++;
        setFocusable(false);

        // Click the panel to remove focus from an editable annotation
        // widget
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
                JComponent viewport = (JComponent)SwingUtilities.getAncestorOfClass(DocumentViewport.class, PagePanel.this);
                if (viewport != null) {
                    viewport.requestFocusInWindow();
                }
            }
        });
    }

    /**
     * Set the {@link PDFParser} object which should be used to render the pages.
     * Setting this to null will end the background thread that renders this object,
     * and free any related resources.
     * @param parser the PDFParser
     */
    public synchronized void setParser(PDFParser parser) {
        if (parser == null) {
            dispose();
        } else {
            if (paintthread == null) {
                paintthread = new PainterThread();
                paintthread.start();
            }
        }
        this.parser = parser;
    }

    private synchronized void dispose() {
        if (paintthread != null) {
            paintthread.cancel();
        }
        synchronized (listeners) {
            listeners.clear();
        }
        synchronized (ilisteners) {
            ilisteners.clear();
        }
        if (page != null) {
            page.removePropertyChangeListener(omnilistener);
            for (Iterator<Map.Entry<PDFAnnotation,JComponent>> i = annotcomponents.entrySet().iterator();i.hasNext();) {
                Map.Entry<PDFAnnotation,JComponent> e = i.next();
                PDFAnnotation annot = e.getKey();
                JComponent comp = e.getValue();
                removeListeners(comp, annot);
            }
            page = null;
        }
        parser = null;
    }

    /**
     * Return a read-only mapping from PDFAnnotation->JComponent
     */
    Map<PDFAnnotation,JComponent> getAnnotationComponents() {
        return Collections.unmodifiableMap(annotcomponents);
    }

    /**
     * Return the area of the page that is considered to be the "whole page"
     * as far as the viewer is concerned - the {@link PDFPage#getBox ViewBox}
     * @return the area of the page that's the full page, in points
     * @see PDFPage#getBox
     * @see PDF#getOption
     */
    public static Rectangle2D getFullPageView(PDFPage page) {
        float[] box = page.getBox("ViewBox");
        return new Rectangle2D.Float(box[0], box[1], box[2]-box[0], box[3]-box[1]);
    }

    /**
     * Add a {@link PagePanelListener} to this PagePanel
     */
    public void addPagePanelListener(PagePanelListener listener) {
        if (listener != null) {
            synchronized(listeners) {
                listeners.add(listener);
            }
        }
    }

    /**
     * Remove a {@link PagePanelListener} from this PagePanel
     */
    public void removePagePanelListener(PagePanelListener listener) {
        synchronized(listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Add a {@link PagePanelInteractionListener} to this PagePanel
     */
    public void addPagePanelInteractionListener(PagePanelInteractionListener listener) {
        if (listener != null) {
            synchronized(ilisteners) {
                if (ilisteners.isEmpty()) {
                    addMouseListener(this.omnilistener);
                    addMouseMotionListener(this.omnilistener);
                }
                ilisteners.add(listener);
            }
        }
    }

    /**
     * Remove a {@link PagePanelInteractionListener} from this PagePanel
     */
    public void removePagePanelInteractionListener(PagePanelInteractionListener listener) {
        synchronized(ilisteners) {
            ilisteners.remove(listener);
            if (ilisteners.isEmpty()) {
                removeMouseListener(this.omnilistener);
                removeMouseMotionListener(this.omnilistener);
            }
        }
    }

    /**
     * Set the {@link RenderingHints} to be used when rendering pages
     * in this PagePanel.
     * @param hints the RenderingHints to use - may be null
     */
    public void setRenderingHints(RenderingHints hints) {
        this.hints = hints;
    }

    /**
     * Set whether to extract text while rendering this page to a {@link PageExtractor}
     * @param extract true to extract the text, false otherwise
     */
    public void setExtractText(boolean extract) {
        this.extract = extract;
    }

    /**
     * Return the {@link PageExtractor} associated with this PagePanel.
     * To render and extract text in a single pass, the {@link #setExtractText}
     * method should be called before rendering, otherwise this method
     * will run the extraction process in a separate pass.
     */
    public synchronized PageExtractor getPageExtractor() {
        if (parser != null && page != null) {
            try {
                return parser.getPageExtractor(page);
            } catch (IllegalArgumentException e) {
                // The page may not be part of the PDF.
                // This can happen during a move between documents.
                // We should probably have a more specific exception for
                // this case.
            }
        }
        return null;
    }

    /**
     * Redraw the page. The page will be rerendered in the background and when it's
     * complete this component will be resized and repainted. If annotations are being
     * created they will be repositioned and redrawn as well.
     * This method may be called from any thread.
     * <i>Note since 2.11.26, the page must be the same as any page passed into previous calls to this method.</i>
     *
     * @param page the page to draw
     * @param position the area of the page to draw, in points
     * @param dpi the resolution
     */
    public void setPage(PDFPage page, Rectangle2D position, float dpi) {
        setPage(page, (float)position.getMinX(), (float)position.getMinY(), (float)position.getMaxX(), (float)position.getMaxY(), dpi);
    }

    /**
     * Redraw the page. The page will be rerendered in the background and when it's
     * complete this component will be resized and repainted. If annotations are being
     * created they will be repositioned and redrawn as well.
     * This method may be called from any thread.
     * <i>Note since 2.11.26, the page must be the same as any page passed into previous calls to this method.</i>
     *
     * @param page the page to draw
     * @param position the area of the page to draw, in points
     * @param dpi the resolution
     * @param clip The graphics clip for this panel, in pixels. The panel will not
     * not render any of the page outside this rectangle.
     * @since 2.10.4
     */
    public void setPage(PDFPage page, Rectangle2D position, float dpi, Rectangle clip) {
        setPage(page, (float)position.getMinX(), (float)position.getMinY(), (float)position.getMaxX(), (float)position.getMaxY(), dpi, clip);
    }

    /**
     * Redraw the page. The page will be rerendered in the background and when it's
     * complete this component will be resized and repainted. If annotations are being
     * created they will be repositioned and redrawn as well.
     * This method may be called from any thread.
     * <i>Note since 2.11.26, the page must be the same as any page passed into previous calls to this method.</i>
     *
     * @param page the page to draw
     * @param x1 the left-most X co-ordinate to draw, in points from the bottom-left
     * @param y1 the bottom-most Y co-ordinate to draw, in points from the bottom-left
     * @param x2 the right-most X co-ordinate to draw, in points from the bottom-left
     * @param y2 the top-most Y co-ordinate to draw, in points from the bottom-left
     * @param dpi the resolution
     */
    public synchronized void setPage(PDFPage page, float x1, float y1, float x2, float y2, float dpi) {
        setPage(page, x1, y1, x2, y2, dpi, null);
    }

    /**
     * Redraw the page. The page will be rerendered in the background and when it's
     * complete this component will be resized and repainted. If annotations are being
     * created they will be repositioned and redrawn as well.
     * This method may be called from any thread.
     * <i>Note since 2.11.26, the page must be the same as any page passed into previous calls to this method.</i>
     *
     * @param page the page to draw
     * @param x1 the left-most X co-ordinate to draw, in points from the bottom-left
     * @param y1 the bottom-most Y co-ordinate to draw, in points from the bottom-left
     * @param x2 the right-most X co-ordinate to draw, in points from the bottom-left
     * @param y2 the top-most Y co-ordinate to draw, in points from the bottom-left
     * @param dpi the resolution
     * @param clip the graphics clip for this panel in pixels. The panel will not
     * render any of the page outside this rectangle.
     * @since 2.10.4
     */
    public synchronized void setPage(PDFPage page, float x1, float y1, float x2, float y2, float dpi, Rectangle clip) {
        if (this.page != null && page != this.page) {
            throw new IllegalArgumentException("Page already set");
        }
        if (parser==null) {
            throw new IllegalStateException("No Parser set");
        }
        if (dpi<=0 || dpi!=dpi) {
            throw new IllegalArgumentException("Invalid DPI "+dpi);
        }
        this.page = page;
        page.flush();
        page.addPropertyChangeListener(omnilistener);
        paintthread.pushPage(page, dpi, x1, y1, x2, y2, clip, false);
    }

    /**
     * Force a redraw of the current page
     * @since 2.11.7
     */
    public void redrawCurrentPage() {
        if (paintthread != null) {
            paintthread.redrawCurrentPage();
        }
    }

    /**
     * Get the {@link DocumentViewport} this PagePanel is contained inside, or
     * <code>null</code> if this PagePanel was not created as part of a
     * {@link DocumentPanel}
     */
    public DocumentViewport getViewport() {
        return viewport;
    }

    /**
     * Get the {@link DocumentPanel} this PagePanel is contained inside, or
     * <code>null</code> if this PagePanel was not created as part of a
     * {@link DocumentPanel}. A shortcut for <code>getViewport().getDocumentPanel()</code>
     */
    public DocumentPanel getDocumentPanel() {
        return viewport==null ? null : viewport.getDocumentPanel();
    }

    /**
     * Return the {@link PDFPage} currently being displayed by this viewport.
     * Note this method returns the page being displayed, not the page currently
     * being rendered, so it's value will not immediately reflect the page passed
     * in to {@link #setPage setPage()}, and will be <code>null</code> if the
     * first page has not yet finished rendering.
     */
    public synchronized PDFPage getPage() {
        return page;
    }

    /**
     * Return the clip area actually used by the PagePanel. This may
     * be what was requested by the last call to {@link #setPage setPage},
     * or it may be <code>null</code> if the PagePanel elected to draw
     * the entire page instead.
     * @since 2.11.25
     */
    public synchronized Rectangle2D getClip() {
        return (paintthread == null) ? null : paintthread.getClip();
    }

    /**
     * Returns the area of the page currently being displayed, in points with
     * (0,0) at the bottom-left. Like {@link #getPage} the return value of
     * this method will not immediately reflect the position passed in to
     * {@link #setPage setPage()}, and will be <code>null</code> if the first
     * page has not yet finished rendering.
     */
    public synchronized Rectangle2D getView() {
        Rectangle2D rect = null;
        if (page!=null) {
            int rot = (page.getPageOrientation()-orientation+360)%360;
            if (rot==0) {
                rect = new Rectangle2D.Float(x1, y1, x2-x1, y2-y1);
            } else {
                int w = page.getWidth();
                int h = page.getHeight();
                if (rot==90) {
                    rect = new Rectangle2D.Float(y1, h-x2, y2-y1, x2-x1);
                } else if (rot==180) {
                    rect = new Rectangle2D.Float(w-x2, h-y2, x2-x1, y2-y1);
                } else {
                    rect = new Rectangle2D.Float(h-y2, x1, y2-y1, x2-x1);
                }
            }
        }
        return rect;
    }

    /**
     * Return an AffineTransform that will map Page co-ordinates to Screen
     * co-ordinates.
     * @since 2.10.2
     */
    public synchronized AffineTransform getPageToScreenTransform() {
        return pageToScreen;
    }

    /**
     * Return an AffineTransform that will map Page co-ordinates to Screen
     * co-ordinates.
     * @since 2.10.3
     */
    public synchronized AffineTransform getScreenToPageTransform() {
        return screenToPage;
    }


    static boolean equalsRectangle2D(Rectangle2D r1, Rectangle2D r2) {  // To catch rounding error
        double x1d = Math.abs(r1.getMinX()-r2.getMinX());
        double x2d = Math.abs(r1.getMaxX()-r2.getMaxX());
        double y1d = Math.abs(r1.getMinY()-r2.getMinY());
        double y2d = Math.abs(r1.getMaxY()-r2.getMaxY());
        return x1d<0.01 && x2d<0.01 && y1d<0.01 && y2d<0.01;
    }

    /**
     * Return the resolution of the page currently being displayed.
     * Like {@link #getPage} the return value of this method will not
     * immediately reflect the position passed in to {@link #setPage setPage()},
     * and will be 0 if the fist page has not yet finished rendering.
     */
    public synchronized float getDPI() {
        return page==null ? 0 : dpi;
    }

    /**
     * Given a location on this panel in pixels, return the equivalent
     * position on the current page in points.
     * @see #getAWTPoint
     */
    public synchronized Point2D getPDFPoint(int x, int y) {
        AffineTransform t = getScreenToPageTransform();
        Point2D.Float f = new Point2D.Float(x, y);
        return t == null ? null : t.transform(f, f);
    }

    /**
     * Given a location on this panel in pixels, return the equivalent
     * position on the current page in points.
     * @see #getAWTPoint
     */
    public synchronized Point2D getPDFPoint(Point awt) {
        AffineTransform t = getScreenToPageTransform();
        Point2D.Float f = new Point2D.Float(awt.x, awt.y);
        return t == null ? null : t.transform(f, f);
    }

    /**
     * Given a location on the page in points, return the equivalent
     * position on this PagePanel in pixels.
     * @see #getPDFPoint
     */
    public synchronized Point getAWTPoint(float x, float y) {
        AffineTransform t = getPageToScreenTransform();
        Point2D.Float f = new Point2D.Float(x, y);
        if (t == null) return null;
        t.transform(f, f);
        return new Point((int)Math.round(f.x), (int)Math.round(f.y));
    }

    /**
     * Given a location on the page in points, return the equivalent
     * position on this PagePanel in pixels.
     * @see #getPDFPoint
     */
    public synchronized Point getAWTPoint(Point2D f) {
        AffineTransform t = getPageToScreenTransform();
        f = (Point2D)f.clone();
        if (t == null) return null;
        t.transform(f, f);
        return new Point((int)Math.round(f.getX()), (int)Math.round(f.getY()));
    }

    public Dimension getPreferredSize() {
        BufferedImage pageimage = (paintthread == null) ? null : paintthread.getImage();
        if (pageimage == null) {
            return super.getPreferredSize();
        } else {
            return new Dimension(pageimage.getWidth(), pageimage.getHeight());
        }
    }

    /**
     * Set the viewport this PagePanel belongs to
     * @since 2.11
     */
    public void setViewport(DocumentViewport viewport) {
        this.viewport = viewport;
        if (viewport != null) {
            DocumentPanel docpanel = viewport.getDocumentPanel();
            if (docpanel != null) {
                PDFViewer viewer = docpanel.getViewer();
                PropertyManager manager = viewer == null ? PDF.getPropertyManager() : viewer.getPropertyManager();
                maxAnnotationPixels = DEFAULTMAXANNOTATIONPIXELS;
                if (manager.getProperty("maxAnnotationBytes") != null) {
                    try {
                        maxAnnotationPixels = Long.parseLong(manager.getProperty("maxAnnotationBytes")) / 4;
                    } catch (Exception e) { }
                }
            }
        }
    }

    /**
     * After the repaint has completed, this method is called in
     * the EDT to update the values in this PagePainter object.
     */
    private synchronized void updatePagePosition(float x1, float y1, float x2, float y2, float dpi, float zoom) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Not on EDT");
        }
        if (page == null) {
            return;
        }
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.dpi = dpi;
        this.orientation = page.getPageOrientation();
        pageToScreen = new AffineTransform(zoom, 0, 0, -zoom, -x1*zoom, y2*zoom);
        try {
            screenToPage = pageToScreen.createInverse();
        } catch (NoninvertibleTransformException e) {
            throw new Error();  // Can't happen
        }
    }

    /**
     * Raise a PagePanelEvent. May be called on any thread, although
     * in practice the "redrawing" event is not on the EDT, everything
     * else is.
     */
    void raisePagePanelEvent(PagePanelEvent event) {
        if (page == null) {
            return;
        }
        boolean debug = (getDocumentPanel()==null || getDocumentPanel().getViewer()==null ? PDF.getPropertyManager() : getDocumentPanel().getViewer().getPropertyManager()).getProperty("debug.Event")!=null;
        if (debug) {
            if (SwingUtilities.isEventDispatchThread()) {
                System.err.println("[PDF] Raise PagePanel#"+panelindex+" "+event);
            } else {
                System.err.println("[PDF] Raise PagePanel#"+panelindex+" "+event+" (not in event thread)");
            }
        }
        PagePanelListener[] l = new PagePanelListener[0];
        synchronized(listeners) {
            l = listeners.toArray(l);
        }
        for (int i=0;i<l.length;i++) {
            l[i].pageUpdated(event);
        }
    }

    //------------------------------------------------------------------------------------
    // Methods dealing with annotations
    //------------------------------------------------------------------------------------

    public void doLayout() {
        for (int i=0;i<getComponentCount();i++) {
            // Ordering may have changed, so reposition all annots
            Component comp = getComponent(i);
            updateComponentPosition(comp);
            comp.validate();
        }
        super.doLayout();
    }

    /**
     * For those JComponents based on the position of a PDFAnnotation
     * or a PDF-space rectangle, adjust the components position, visibility
     * and any other attributes that may be required.
     * Only called from the EDT.
     *
     * @param comp the JComponent, which will be adjusted if it has a
     * "pdf.annotation" or "pdf.rect" client property set.
     */
    private void updateComponentPosition(Component awtcomp) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Not on EDT");
        }
        if (!(awtcomp instanceof JComponent)) {
            return;
        }
        JComponent comp = (JComponent)awtcomp;
        PDFAnnotation annot = (PDFAnnotation)comp.getClientProperty("pdf.annotation");
        float[] rect;
        if (annot != null) {
            PDFPage annotpage = annot.getPage();
            if (page == annotpage && page != null) {
                comp.setVisible(annot.isVisible());
                rect = getAnnotationBounds(annot);
                List<PDFAnnotation> annotlist = page.getAnnotations();
                int order = annotlist.size() - annotlist.indexOf(annot) - 1;
                // Annotations will always be positioned in front of non-annotation items
                if (order >= 0 && order < getComponentCount()) {
                    setComponentZOrder(comp, order);
                }
            } else {
                comp.setVisible(false); // Annotation will be deleted elsewhere
                rect = null;
            }
        } else {
            rect = (float[])comp.getClientProperty("pdf.rect");
        }
        if (rect != null) {
            boolean nozoom = annot instanceof AnnotationNote;
            float zoom = dpi/72f;
            float annotzoom = nozoom ? Math.min(zoom, 1) : zoom;
            int x = (int)Math.round((rect[0]-x1) * zoom);
            int y = (int)Math.round((y2-rect[3]) * zoom);
            int w = (int)Math.round((rect[2]-rect[0]) * annotzoom);
            int h = (int)Math.round((rect[3]-rect[1]) * annotzoom);
            if (w < 0) {
                x += w;
                w =- w;
            }
            if (h < 0) {
                y += h;
                h =- h;
            }

            comp.setBounds(x, y, w+1, h+1);
        }
    }

    /**
     * Remove any listeners from the annotation that refer to the Component.
     * Must be called before a component is freed, eg when the PagePanel is
     * discarded.
     */
    private void removeListeners(JComponent comp, PDFAnnotation annot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Not on EDT");
        }
        annot.removePropertyChangeListener(omnilistener);
        if (annot instanceof WidgetAnnotation) {
            FormElement field = ((WidgetAnnotation)annot).getField();
            field.removePropertyChangeListener(omnilistener);
            PropertyChangeListener pcl = (PropertyChangeListener) comp.getClientProperty("bfo.fieldPCL");
            if (pcl != null) {
                field.removePropertyChangeListener(pcl);
            }
        }
    }

    /**
     * Return the bounds for this PDFAnnotation. The bounds is just
     * getRectangle(), unless it's an open AnnotationNote
     */
    private float[] getAnnotationBounds(PDFAnnotation annot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Not on EDT");
        }
        float[] rect = annot.getRectangle();
        if (annot instanceof AnnotationNote) {
            rect = ((AnnotationNote)annot).getClosedRectangle();
        }
        return rect;
    }

    /**
     * Redraw the specified annotation on this page. This should be
     * called if the annotation is changed, if it's newly added to the
     * page or if it's been removed from the page.
     * May be called from any thread.
     *
     * @throws IllegalStateException if the PagePanel is not part of
     * a {@link DocumentPanel} and so has no annotations.
     */
    public void redrawAnnotation(final PDFAnnotation annot) {
        if (paintthread != null && annot != null) {
            paintthread.pushAnnotation(annot);
        }
    }

    /**
     * Return true if this PagePanel draws annotations as children
     */
    private boolean hasAnnotations() {
        return viewport!=null && viewport.getDocumentPanel()!=null && !viewport.getDocumentPanel().getAnnotationFactories().isEmpty();
    }

    /**
     * Create a JComponent for a PDFAnnotation. This calls the appropriate factory
     * for the annotation and then "binds" the location of the JComponent to the
     * PDFAnnotation by setting the "pdf.annotation" client property, which is
     * used by the methods in this class to link the JComponent with the PDFAnnotation
     * and position it correctly.
     * Only called from the EDT (as it creates components)
     */
    private JComponent getComponentForAnnotation(PDFAnnotation annot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Not on EDT");
        }
        JComponent comp = (JComponent) annotcomponents.get(annot);
        PDFPage annotPage = annot.getPage();
        if (annotPage != page) {
            annotcomponents.remove(annot);
            if (comp != null) {
                remove(comp);
                repaint();
                comp = null;
            }
        } else {
            if (comp == null) {
                Collection<AnnotationComponentFactory> factories = viewport.getDocumentPanel().getAnnotationFactories();
                for (Iterator<AnnotationComponentFactory> i = factories.iterator(); comp == null && i.hasNext(); ) {
                    AnnotationComponentFactory factory = i.next();
                    if (factory.matches(annot)) {
                        comp = factory.createComponent(this, annot);
                    }
                }
                if (comp == null) {
                    comp = AnnotationComponentFactory.getDefaultFactory().createComponent(this, annot);
                }
                AnnotationComponentFactory.bindComponentLocation(comp, annot);
                annot.addPropertyChangeListener(omnilistener);
                if (annot instanceof WidgetAnnotation) {
                    FormElement field = ((WidgetAnnotation) annot).getField();
                    if (field != null) {
                        field.addPropertyChangeListener(omnilistener);
                    }
                }
                comp.putClientProperty("images", new BufferedImage[3]);
                comp.putClientProperty("imagecomposites", new Composite[3]);

                add(comp);
                annotcomponents.put(annot, comp);
            }
            updateComponentPosition(comp);
        }
        return comp;
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage image;
        Rectangle clip;
        float dpi, currentdpi;
        synchronized (paintthread) {
            image = paintthread.getImage();
            clip = paintthread.getClip();
            dpi = paintthread.getDPI();
            currentdpi = paintthread.getCurrentDPI();
        }
        if (image != null) {
            int x = 0, y = 0, w = image.getWidth(), h = image.getHeight();
            if (clip != null) {
                x = clip.x;
                y = clip.y;
                w = clip.width;
                h = clip.height;
            }
            //System.err.println("dpi="+dpi+" x="+x+" y="+y+" w="+w+" h="+h);
            if (currentdpi != dpi) {
                float scale = currentdpi / dpi;
                x *= scale;
                y *= scale;
                w *= scale;
                h *= scale;
                //System.err.println("\tcurrentdpi="+currentdpi+" x="+x+" y="+y+" w="+w+" h="+h);
            }
            g.drawImage(image, x, y, w, h, this);
        }
    }

    //---------------------------------------------------------------------------------

    private class Listener implements MouseMotionListener, MouseListener, PropertyChangeListener {
        private void raiseEvent(PagePanelInteractionEvent event) {
            PagePanelInteractionListener[] l = new PagePanelInteractionListener[0];
            synchronized(ilisteners) {
                l = ilisteners.toArray(l);
            }
            for (int i=0;i<l.length;i++) {
                l[i].pageAction(event);
            }
        }

        public void mouseEntered(MouseEvent event) {
            if (pageToScreen != null && screenToPage != null) {
                raiseEvent(PagePanelInteractionEvent.createMouseEntered(PagePanel.this, event));
            }
        }
        public void mouseExited(MouseEvent event) {
            if (pageToScreen != null && screenToPage != null) {
                raiseEvent(PagePanelInteractionEvent.createMouseExited(PagePanel.this, event));
            }
        }
        public void mousePressed(MouseEvent event) {
            if (pageToScreen != null && screenToPage != null) {
                raiseEvent(PagePanelInteractionEvent.createMousePressed(PagePanel.this, event));
            }
        }
        public void mouseReleased(MouseEvent event) {
            if (pageToScreen != null && screenToPage != null) {
                raiseEvent(PagePanelInteractionEvent.createMouseReleased(PagePanel.this, event));
            }
        }
        public void mouseClicked(MouseEvent event) {
            if (pageToScreen != null && screenToPage != null) {
                raiseEvent(PagePanelInteractionEvent.createMouseClicked(PagePanel.this, event));
            }
        }
        public void mouseMoved(MouseEvent event) {
            if (pageToScreen != null && screenToPage != null) {
                raiseEvent(PagePanelInteractionEvent.createMouseMoved(PagePanel.this, event));
            }
        }
        public void mouseDragged(MouseEvent event) {
            if (pageToScreen != null && screenToPage != null) {
                raiseEvent(PagePanelInteractionEvent.createMouseDragged(PagePanel.this, event));
            }
        }

        public void propertyChange(final PropertyChangeEvent event) {
            Object source = event.getSource();
            if (source == page) {
                String type = event.getPropertyName();
                if (type.equals("annotations")) {
                    PDFAnnotation oldannot = (PDFAnnotation)event.getOldValue();
                    PDFAnnotation newannot = (PDFAnnotation)event.getNewValue();
                    redrawAnnotation(oldannot);
                    redrawAnnotation(newannot);
                    doLayout();
                } else {
                    if (type.equals("orientation") || type.equals("content") || type.equals("mediabox")) {
                        parser.resetPageExtractor(page);
                    }
                    redrawCurrentPage();
                }
            } else if (source instanceof PDFAnnotation) {
                PDFAnnotation annot = (PDFAnnotation) source;
                redrawAnnotation(annot);
            } else if (source instanceof FormElement) {
                FormElement field = (FormElement) source;
                List<WidgetAnnotation> annots = field.getAnnotations();
                for (Iterator<WidgetAnnotation> i = annots.iterator(); i.hasNext(); ) {
                    WidgetAnnotation annot = i.next();
                    redrawAnnotation(annot);
                }
            }
        }

    }

    private class AnnotationFocusTraversalPolicy extends FocusTraversalPolicy {

        private PDFAnnotation getAnnot(Component comp) {
            if (comp instanceof JComponent) {
                return (PDFAnnotation)((JComponent)comp).getClientProperty("pdf.annotation");
            } else {
                return null;
            }
        }

        public Component getComponentAfter(Container root, Component comp) {
            boolean seen = false;
            PDFAnnotation annot = getAnnot(comp);
            if (page != null) {
                List<PDFAnnotation> annots = page.getAnnotations();
                for (Iterator<PDFAnnotation> j = annots.iterator(); j.hasNext(); ) {
                    PDFAnnotation jannot =  j.next();
                    if (seen) {
                        Component ret = (Component) annotcomponents.get(jannot);
                        return ret;
                    }
                    if (jannot == annot) {
                        seen = true;
                    }
                }
            }
            return root;
        }

        public Component getComponentBefore(Container root, Component comp) {
            boolean seen = false;
            PDFAnnotation annot = getAnnot(comp);
            if (page != null) {
                List<PDFAnnotation> annots = new ArrayList<PDFAnnotation>(page.getAnnotations());
                Collections.reverse(annots);
                for (Iterator<PDFAnnotation> j = annots.iterator(); j.hasNext(); ) {
                    PDFAnnotation jannot = j.next();
                    if (seen) {
                        Component ret = (Component) annotcomponents.get(jannot);
                        return ret;
                    }
                    if (jannot == annot) {
                        seen = true;
                    }
                }
            }
            return root;
        }

        public Component getDefaultComponent(Container root) {
            //return getFirstComponent(root);
            return root;
        }

        public Component getFirstComponent(Container root) {
            if (page != null) {
                List<PDFAnnotation> l = page.getAnnotations();
                if (!l.isEmpty()) {
                    Component ret = (Component)annotcomponents.get(l.get(0));
                    return ret;
                }
            }
            return root;
        }

        public Component getLastComponent(Container root) {
            Component lastComponent = root;
            if (page != null) {
                List<PDFAnnotation> l = page.getAnnotations();
                if (!l.isEmpty()) {
                    lastComponent = (Component)annotcomponents.get(l.get(l.size() - 1));
                }
            }
            return lastComponent;
        }

    }

    /**
     * The thread that paints the page in the background. The methods in
     * this class interacts with the Event Dispatch Thread at various points,
     * this interaction must be carefully controlled. Methods within this
     * subclass that must be run on the EDT are marked as such. Methods
     * in the parent class aren't generally called except for those to do
     * with annotations and updatePagePositions. All those that have EDT
     * requirements have EDT checking.
     */
    private class PainterThread extends SoftInterruptibleThread {

        private Runnable SET_WAIT_CURSOR = new Runnable() {
            public void run() {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        };

        private PageEvent nextevent, lastevent, paintingevent;
        private volatile boolean interrupted, active = true;
        private Queue<AnnotationEvent> nextannotationevent = new PriorityQueue<AnnotationEvent>();
        private AnnotationEvent paintingannotationevent;
        private float currentdpi;

        PainterThread() {
            super("BFO-PainterThread");
            setDaemon(true);
        }

        public boolean isSoftInterrupted() {
            return interrupted || !active;
        }

        BufferedImage getImage() {
            return lastevent == null ? null : lastevent.image;
        }

        float getDPI() {
            return lastevent == null ? 0 : lastevent.dpi;
        }

        float getCurrentDPI() {
            return currentdpi;
        }

        Rectangle getClip() {
            return lastevent == null ? null : lastevent.clip;
        }

        // Logic:
        // 1. Changing page or DPI - redraw content, redraw all annotations
        // 2. Changing page content, page and dpi the same - redraw content
        // 3. Changing annotation - redraw annotation

        private class PageEvent {
            final PDFPage page;
            final PagePainter painter;
            final float dpi, x1, y1, x2, y2, cx1, cy1, cx2, cy2, zoom;
            final Rectangle clip;
            final int pageorientation;
            BufferedImage image;

            PageEvent(PagePainter painter, PDFPage page, float dpi, float x1, float y1, float x2, float y2, Rectangle clip) {
                this.painter = painter;
                this.page = page;
                this.dpi = dpi;
                this.x1 = x1;
                this.y1 = y1;
                this.x2 = x2;
                this.y2 = y2;
                this.zoom = dpi / 72;
                this.clip = clip;
                this.pageorientation = page.getPageOrientation();
                if (clip == null) {
                    this.cx1 = x1;
                    this.cy1 = y1;
                    this.cx2 = x2;
                    this.cy2 = y2;
                } else {
                    Rectangle2D crop = getFullPageView(page);
                    this.cx1 = (clip.x / zoom) + (float)crop.getMinX();
                    this.cy2 = ((y2 - y1) - clip.y / zoom) + (float)crop.getMinY();;
                    this.cx2 = cx1 + clip.width / zoom;
                    this.cy1 = cy2 - clip.height / zoom;
                }
            }

            public String toString() {
                StringBuilder buf = new StringBuilder("PageEvent");
                buf.append("[page=");
                buf.append(page.getPageNumber());
                buf.append(",x1=");
                buf.append(x1);
                buf.append(",y1=");
                buf.append(y1);
                buf.append(",x2=");
                buf.append(x2);
                buf.append(",y2=");
                buf.append(y2);
                buf.append(",cx1=");
                buf.append(cx1);
                buf.append(",cy1=");
                buf.append(cy1);
                buf.append(",cx2=");
                buf.append(cx2);
                buf.append(",cy2=");
                buf.append(cy2);
                buf.append("]");
                return buf.toString();
            }

            boolean isSameZoom(PageEvent event) {
                return event != null && event.page == page && Math.abs(event.dpi - dpi) < 0.001 && event.pageorientation == pageorientation;
            }

            boolean isSameRegion(PageEvent event) {
                return isSameZoom(event) && (clip == null ? event.clip == null : clip.equals(event.clip));
            }

            void complete(BufferedImage image) {
                this.image = image;
                paintingevent = null;
                lastevent = this;

                SwingUtilities.invokeLater(new Runnable() {     // Then, once in the EDT
                    public void run() {                         // set some values:
                        pushAnnotationsForPage(lastevent);
                        if (isCursorSet() && getCursor() == Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)) {
                            setCursor(null);
                        }
                        if (active && lastevent == PageEvent.this) {
                            raisePagePanelEvent(PagePanelEvent.createPageRedrawn(PagePanel.this, page));

                        }
                        revalidate();
                        repaint();
                    }
                });
            }
        }

        private final class AnnotationEvent implements Comparable<Object> {
            final PageEvent pageevent;
            final PDFAnnotation annot;
            final JComponent component;
            final int state;
            final float zoom;
            final float[] rect;

            AnnotationEvent(PageEvent pageevent, PDFAnnotation annot, int state) {
                if (!SwingUtilities.isEventDispatchThread()) {
                    throw new IllegalStateException("Not on EDT");
                }
                this.pageevent = pageevent;
                this.annot = annot;
                this.state = state;
                this.zoom = nextevent != null ? nextevent.zoom : paintingevent != null ? paintingevent.zoom : lastevent.zoom;
                rect = getAnnotationBounds(annot);
                this.component = getComponentForAnnotation(annot);
            }

            void complete(final BufferedImage image, final Composite composite) {
                if (component == null) {
                    return;     // We can check this outside EDT, it's final
                }
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        updateComponentPosition(component);
                        BufferedImage[] images = (BufferedImage[])component.getClientProperty("images");
                        Composite[] composites = (Composite[])component.getClientProperty("imagecomposites");
                        images[state] = image;
                        composites[state] = composite;
                        // Parts of the panel that were previously covered by an
                        // annotation component may now be uncovered, let AWT handle
                        // redrawing the required parts
                        component.revalidate(); // This must be on EDT, hence invokeLater
                        repaint();
                    }
                });
            }

            public int compareTo(Object other) {
                if (other instanceof AnnotationEvent) {
                    AnnotationEvent ae = (AnnotationEvent) other;
                    return state - ae.state;
                }
                return 0;
            }

            public boolean equals(Object other) {
                if (other instanceof AnnotationEvent) {
                    AnnotationEvent ae = (AnnotationEvent) other;
                    return annot == ae.annot && state == ae.state;
                }
                return false;
            }

            public int hashCode() {
                return annot.hashCode() | state;
            }

            public String toString() {
                StringBuilder buf = new StringBuilder("AnnotationEvent");
                buf.append("[annot=");
                buf.append(annot);
                buf.append(",state=");
                buf.append(ANNOTATION_STATES[state]);
                buf.append("]");
                return buf.toString();
            }

        }

        synchronized void cancel() {
            active = false;
            nextannotationevent.clear();
            nextevent = null;
            paintingevent = null;
            notifyAll();
        }

        synchronized void pushPage(PDFPage page, float dpi, float x1, float y1, float x2, float y2, Rectangle clip, boolean force) {
            if (!SwingUtilities.isEventDispatchThread()) {
                Util.LOGGER.warning("PP1", "Not on Swing EventDispathThread", new IllegalStateException());
            }
            if (lastevent != null && lastevent.page.getPDF() != page.getPDF()) {
                lastevent = null;
            }
            boolean debug = (getDocumentPanel()==null || getDocumentPanel().getViewer()==null ? PDF.getPropertyManager() : getDocumentPanel().getViewer().getPropertyManager()).getProperty("debug.Event")!=null;
            Dimension size = getSize();
            if (size.width > 0 && size.height > 0 && (long)size.width * (long)size.height < MIN_PIXELS_FOR_CLIP) {
                // We may as well render the whole component as it is cheap
                // enough and we don't want to rerender with another
                // slightly different clip
                clip = null;
            }

            if (clip != null && lastevent != null && lastevent.clip != null && Math.abs(currentdpi - dpi) < 0.001) {
                Rectangle uclip = lastevent.clip.union(clip);
                if (((long) uclip.width * (long) uclip.height) < MIN_PIXELS_FOR_CLIP) {
                    clip = uclip;
                }
            }

            PagePainter painter = (lastevent != null) ? lastevent.painter : parser.getPagePainter(page);
            painter.setPaintAnnotations(false);
            PageEvent event = new PageEvent(painter, page, dpi, x1, y1, x2, y2, clip);
            if (!force && dpi == currentdpi && event.isSameRegion(lastevent)) {
                if (debug) {
                    System.err.println("[PDF] PagePanel.redraw(P"+(page==null?-1:(page.getPageNumber()-1))+", lastevent="+lastevent+" duplicate");
                }
                return;
            }

            currentdpi = dpi;
            interrupted = true;
            paintingevent = null;

            if (debug) {
                System.err.println("[PDF] PagePanel.redraw(P"+(page==null?-1:(page.getPageNumber()-1))+", event="+event);
            }
            nextevent = event;

            notifyAll();
        }

        synchronized void pushAnnotationsForPage(PageEvent pe) {
            nextannotationevent.clear();
            List<PDFAnnotation> l = pe.page.getAnnotations();
            for (int i = 0; i < l.size(); i++) {
                PDFAnnotation annot = l.get(i);
                for (int j = 0; j < ANNOTATION_STATES.length; j++) {
                    String state = ANNOTATION_STATES[j];
                    if (j == 0 || annot.hasAppearanceState(state)) {
                        nextannotationevent.add(new AnnotationEvent(pe, annot, j));
                    }
                }
            }
            updatePagePosition(pe.x1, pe.y1, pe.x2, pe.y2, pe.dpi, pe.zoom);
            notifyAll();
        }

        synchronized void redrawCurrentPage() {
            if (lastevent != null && active) {
                pushPage(lastevent.page, lastevent.dpi, lastevent.x1, lastevent.y1, lastevent.x2, lastevent.y2, lastevent.clip, true);
            }
        }

        private synchronized PageEvent popPageEvent() {
            paintingevent = nextevent;
            nextevent = null;
            return paintingevent;
        }

        synchronized void pushAnnotation(PDFAnnotation annot) {
            if (!SwingUtilities.isEventDispatchThread()) {
                throw new IllegalStateException("Not on EDT");
            }
            if (lastevent == null) {
                // Page has not been pushed yet
                return;
            }
            for (int j = 0; j < ANNOTATION_STATES.length; j++) {
                String state = ANNOTATION_STATES[j];
                if (j == 0 || annot.hasAppearanceState(state)) {
                    AnnotationEvent ae = new AnnotationEvent(lastevent, annot, j);
                    if (ae.component != null) {
                        // Replace any existing event with this one, as this may
                        // have changes in state absent from the last event (eg rect)
                        nextannotationevent.remove(ae);
                        nextannotationevent.add(ae);
                    }
                }
            }
            notifyAll();
        }

        synchronized AnnotationEvent popAnnotationEvent() {
            paintingannotationevent = (AnnotationEvent) nextannotationevent.poll();
            return paintingannotationevent;
        }

        synchronized void waitForEvent() {
            if (nextevent == null && nextannotationevent.isEmpty() && active) {
                try {
                    wait();
                } catch (InterruptedException e) { }
            }
            interrupted = false;
        }

        public void run() {
            DocumentPanel docpanel = getDocumentPanel();
            PDFViewer viewer = docpanel == null ? null : docpanel.getViewer();
            WarningNotifier alert = viewer == null ? null : viewer.getFeature(WarningNotifier.class);
            if (alert != null) {
                alert.register(this, docpanel);
            }
            try {
                while (active) {
                    waitForEvent();

                    try {
                        handlePageEvent(popPageEvent());
                        handleAnnotationEvent(popAnnotationEvent());
                    } catch (RuntimeException e) {
                        if (docpanel != null && docpanel.isDisplayable()) {
                            Util.displayThrowable(e, PagePanel.this);
                        }
                    }
                }
            } finally {
                if (alert != null) {
                    alert.unregister(this);
                }
            }
        }

        void handlePageEvent(PageEvent pe) {
            if (pe == null || !active) {
                return;
            }
            if (pe.cx2 > pe.cx1 && pe.cy2 > pe.cy1) {
                SwingUtilities.invokeLater(SET_WAIT_CURSOR);
                BufferedImage img = null;
                raisePagePanelEvent(PagePanelEvent.createPageRedrawing(PagePanel.this, pe.page));
                if (pe.page == null) {
                    int iw = (int)Math.ceil((pe.x2-pe.x1) * pe.zoom);
                    int ih = (int)Math.ceil((pe.y2-pe.y1) * pe.zoom);
                    img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
                    Graphics g = img.getGraphics();
                    g.setColor(new Color(0xC0C0C0));
                    g.fillRect(0, 0, iw, ih);
                } else if (pe.painter != null) {
                    if (extract && parser != null) {
                        try {
                            pe.painter.setPageExtractor(parser.getPageExtractor(pe.page));
                        } catch (IllegalArgumentException e) {
                            // bogus page is not part of this pdf exception
                        }
                    }
                    img = pe.painter.getSubImage(pe.cx1, pe.cy1, pe.cx2, pe.cy2, pe.dpi, getColorModel());
                }
                if (img != null && !interrupted) {            // Image was completed.
                    pe.complete(img);
                } else {
                    // interrupted
                }
            }
            paintingevent = null;
        }

        void handleAnnotationEvent(final AnnotationEvent ae) {
            if (ae == null || ae.pageevent != lastevent || !active) {
                return;
            }
            float annotzoom = (ae.annot instanceof AnnotationNote) ? Math.min(1, ae.zoom) : ae.zoom;
            int w = ae.rect == null ? 0 : (int)Math.ceil((ae.rect[2] - ae.rect[0]) * annotzoom) + 1;
            int h = ae.rect == null ? 0 : (int)Math.ceil((ae.rect[3] - ae.rect[1]) * annotzoom) + 1;
            if (((long)w * (long)h) > maxAnnotationPixels) {
                // The raster for the annotation is going to be too big to fit in memory.
                // Use a smaller raster and scale the annotation onto it.
                annotzoom = annotzoom * maxAnnotationPixels / w / h;
                w = (int)Math.ceil((ae.rect[2] - ae.rect[0]) * annotzoom) + 1;
                h = (int)Math.ceil((ae.rect[3] - ae.rect[1]) * annotzoom) + 1;
            }
            if (w >= 1 && h >= 1) {
                AffineTransform tran = new AffineTransform(annotzoom, 0, 0, -annotzoom, -ae.rect[0]*annotzoom, ae.rect[3]*annotzoom);
                BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = (Graphics2D)image.getGraphics();
                if (hints != null) {
                    g.setTransform(tran);
                }
                g.setRenderingHints(hints);

                PagePainter painter = ae.pageevent.painter;
                painter.setPageExtractor(null);
                String state = ANNOTATION_STATES[ae.state];
                if (painter.paintAnnotation(ae.annot, state, g, ae.rect)) {
                    ae.complete(image, painter.getAnnotationComposite());
                } else {
                    // interrupted
                }
            } else {        // Too small! Remove the images
                ae.complete(null, null);
            }
            paintingannotationevent = null;
        }

    }


}
