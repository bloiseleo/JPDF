// $Id: AbstractRegionSelector.java 35753 2020-03-19 12:01:00Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.*;

/**
 * An abstract superclass for any widgets that require a region to be selected. Subclasses
 * should override the {@link #action(PagePanel, Point2D, Point2D)} method.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.5
 */
public abstract class AbstractRegionSelector extends ToggleViewerWidget implements DocumentPanelListener, PagePanelInteractionListener {

    static final int CRAWLSPEED = 60, CRAWLLENGTH = 4, POINTDIAMETER = 15;
    private volatile JComponent rubberbox;
    private Point startpoint;
    private Shape regionShape;
    private Shape scaledRegionShape;
    private int scaledRegionWidth, scaledRegionHeight;

    protected AbstractRegionSelector(String name) {
        super(name, DragScroll.GROUP);
        setRegionShape(null);
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public void action(ViewerEvent event) {
        if (!isSelected()) {
            setSelected(true);
        }
    }

    /**
     * <p>
     * Set the region that is being selected with this operation. Any
     * shape can be specified, but if the shape is a rectangle then
     * it has extended functionality: the width or height of the rectangle
     * can be 0 (to select a line or point), or Float.NaN to allow
     * stretching in that dimension. So, for example, to allow the
     * selection of a Rectangle of any size, pass in 
     * <code>new Rectangle2D.Float(0, 0, Float.NaN, Float.NaN)</code>
     * (this is also the default value).
     * </p><p>
     * The specified shape is in PDF coordinates, and they will be clipped
     * to the dimensions of the page. The "x" and "y" values of the shape
     * are ignored.
     * </p>
     * @since 2.24.1
     */
    public void setRegionShape(Shape shape) {
        if (shape == null) {
            shape = new Rectangle2D.Float(0, 0, Float.NaN, Float.NaN);
        }
        this.regionShape = shape;
    }

    protected void updateViewport(DocumentViewport vp, boolean selected) {
        if (selected) {
            vp.addPagePanelInteractionListener(this);
            if (vp.getPagePanel() != null) {
                vp.getPagePanel().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            }
        } else {
            vp.removePagePanelInteractionListener(this);
            if (vp.getPagePanel() != null) {
                vp.getPagePanel().setCursor(null);
            }
        }
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        if ("viewportChanged".equals(type) || "activated".equals(type)) {
            if (getGroupSelection(getGroupName()) == null) {
                PropertyManager manager = getViewer()==null ? PDF.getPropertyManager() : getViewer().getPropertyManager();
                String defaultmode = manager == null ? null : manager.getProperty("default"+getGroupName());
                if (defaultmode == null && manager != null) {
                    defaultmode = manager.getProperty("Default"+getGroupName());        // legacy
                }
                if (getName().equals(defaultmode)) {
                    setSelected(true);
                }
            }
            if (isSelected()) {
                updateViewport(event.getDocumentPanel().getViewport(), true);
            }
        }
    }

    /**
     * Create the JComponent that it used to display the "rubber box". If you need to
     * display some custom appearance when overriding this class, this method should
     * be overridden.
     * @since 2.11.7
     */
    protected JComponent createRubberBoxComponent() {
        return new JPanel() {
            int count;
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (rubberbox != null) {
                    paintRubberBandComponent(this, (Graphics2D)g);
                    BasicStroke stroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 15, new float[] { CRAWLLENGTH, CRAWLLENGTH }, (int)((System.currentTimeMillis()/CRAWLSPEED)%(CRAWLLENGTH*2)));
                    g.setColor(Color.black);
                    ((Graphics2D)g).setStroke(stroke);
                    if (scaledRegionShape != null) {
                        ((Graphics2D)g).draw(scaledRegionShape);
                    } else {
                        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    }
                }
            }
        };
    }

    public void pageAction(PagePanelInteractionEvent event) {
        PagePanel panel = event.getPagePanel();
        if (isSelected()) {
            if (event.getType() == "mousePressed") {
                float scale = panel.getDocumentPanel().getZoom();
                if (regionShape instanceof Rectangle2D) {
                    Rectangle2D r = (Rectangle2D)regionShape;
                    double w = r.getWidth();
                    double h = r.getHeight();
                    this.scaledRegionWidth = Double.isNaN(w) || w < 0 ? -1 : w > 99999999 ? panel.getWidth() : Math.min(panel.getWidth(), (int)Math.ceil(w * scale));
                    this.scaledRegionHeight = Double.isNaN(h) || h < 0 ? -1 : h > 99999999 ? panel.getHeight() : Math.min(panel.getHeight(), (int)Math.ceil(h * scale));
                    this.scaledRegionShape = null;
                } else {
                    Rectangle2D r = regionShape.getBounds2D();
                    this.scaledRegionShape = new AffineTransform(scale, 0, 0, -scale, -r.getMinX() * scale, r.getMaxY() * scale).createTransformedShape(regionShape);
                    Rectangle rr = scaledRegionShape.getBounds();
                    this.scaledRegionWidth = rr.width;
                    this.scaledRegionHeight = rr.height;
                }
                if (rubberbox == null) {
                    rubberbox = createRubberBoxComponent();
                    panel.add(rubberbox);
                }
                Point p = event.getMouseEvent().getPoint();
                startpoint = p;
                rubberbox.setOpaque(false);
                if (scaledRegionWidth > 0) {
                    p.x = Math.max(0, Math.min(p.x, panel.getWidth() - scaledRegionWidth));
                }
                if (scaledRegionHeight > 0) {
                    p.y = Math.max(0, Math.min(p.y, panel.getHeight() - scaledRegionHeight));
                }
                rubberbox.setLocation(p);
                if (scaledRegionWidth == 0 && scaledRegionHeight == 0) {
                    rubberbox.setLocation(new Point(p.x - (POINTDIAMETER/2), p.y - (POINTDIAMETER/2)));
                    rubberbox.setSize(POINTDIAMETER, POINTDIAMETER);
                } else if (scaledRegionWidth == 0) {
                    rubberbox.setSize(1, scaledRegionHeight < 0 ? 0 : scaledRegionHeight);
                } else if (scaledRegionHeight == 0) {
                    rubberbox.setSize(scaledRegionWidth < 0 ? 0 : scaledRegionWidth, 1);
                } else {
                    rubberbox.setSize(0, 0);
                }
                new Thread() {
                    public void run() {
                        try {
                            while (rubberbox != null) {
                                Thread.sleep(CRAWLSPEED);
                                JComponent c = rubberbox;
                                if (c != null) {
                                    c.repaint();
                                }
                            }
                        } catch (InterruptedException e) { }
                    }
                }.start();
            } else if (event.getType()=="mouseDragged" && startpoint!=null) {
                Point endpoint = event.getMouseEvent().getPoint();
                endpoint.x = Math.min(Math.max(0, endpoint.x), panel.getWidth());
                endpoint.y = Math.min(Math.max(0, endpoint.y), panel.getHeight());
                if (scaledRegionWidth >= 0) {
                    startpoint.x = Math.max(0, Math.min(endpoint.x, panel.getWidth() - Math.max(1, scaledRegionWidth)));
                    endpoint.x = startpoint.x + scaledRegionWidth;
                }
                if (scaledRegionHeight >= 0) {
                    startpoint.y = Math.max(0, Math.min(endpoint.y, panel.getHeight() - Math.max(1, scaledRegionHeight)));
                    endpoint.y = startpoint.y + scaledRegionHeight;
                }
                if (scaledRegionWidth == 0 && scaledRegionHeight == 0) {
                    rubberbox.setLocation(new Point(startpoint.x - (POINTDIAMETER/2), startpoint.y - (POINTDIAMETER/2)));
                    rubberbox.setSize(POINTDIAMETER, POINTDIAMETER);
                } else {
                    int w = scaledRegionWidth == 0 ? 1 : Math.abs(startpoint.x - endpoint.x);
                    int h = scaledRegionHeight == 0 ? 1 : Math.abs(startpoint.y - endpoint.y);
                    rubberbox.setLocation(Math.min(startpoint.x, endpoint.x), Math.min(startpoint.y, endpoint.y));
                    rubberbox.setSize(w, h);
                }
            } else if (event.getType()=="mouseReleased" && startpoint!=null) {
                Point endpoint = event.getMouseEvent().getPoint();
                endpoint.x = Math.min(Math.max(0, endpoint.x), panel.getWidth());
                endpoint.y = Math.min(Math.max(0, endpoint.y), panel.getHeight());
                if (scaledRegionWidth >= 0) {
                    startpoint.x = Math.max(0, Math.min(endpoint.x, panel.getWidth() - scaledRegionWidth));
                    endpoint.x = startpoint.x + scaledRegionWidth;
                }
                if (scaledRegionHeight >= 0) {
                    startpoint.y = Math.max(0, Math.min(endpoint.y, panel.getHeight() - scaledRegionHeight));
                    endpoint.y = startpoint.y + scaledRegionHeight;
                }
                Point2D p0 = panel.getPDFPoint(Math.min(startpoint.x, endpoint.x), Math.min(startpoint.y, endpoint.y));
                Point2D p1 = panel.getPDFPoint(Math.max(startpoint.x, endpoint.x), Math.max(startpoint.y, endpoint.y));
                action(panel, p0, p1);
                startpoint = null;
                panel.remove(rubberbox);
                rubberbox = null;
                scaledRegionShape = null;
                scaledRegionWidth = scaledRegionHeight = -1;
            }
        }
    }

    /**
     * Paint the component while the "rubber band" box is being stretched.
     * This method may be overriden if something is to be painted inside the
     * box during this time.
     * @param component the "rubber band" box being drawn
     * @param g the Graphic2D object to draw on.
     */
    public void paintRubberBandComponent(JComponent component, Graphics2D g) {
    }

    /**
     * Called when an area of the PDF has been selected.
     * @param panel the PagePanel the selection was made on.
     * @param start the start point of the selection, in PDF-units
     * @param end the end point of the selection, in PDF-units
     */
    public void action(PagePanel panel, Point2D start, Point2D end) {
    }

}
