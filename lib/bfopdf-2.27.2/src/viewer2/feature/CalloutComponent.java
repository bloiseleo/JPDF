package org.faceless.pdf2.viewer2.feature;

import java.awt.*;
import java.beans.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;

abstract class CalloutComponent extends JPanel implements MouseListener, MouseMotionListener, PropertyChangeListener {

    private static final int MARGIN = 5; // width of vertex mouseover sensitivity
    private final PagePanel pagePanel;
    private Point2D[] points;
    private boolean[] readonly;
    private Rectangle2D content;
    private final boolean contentReadonly;
    private Shape targetArea;

    private int selectedVertex = -1;
    private Point dragPointScreen;
    private Rectangle2D dragRect;
    private Point dragRectOffset;
    private final AnnotationText annot;
    private Point mousePressedLocation;

    CalloutComponent(AnnotationText annot, PagePanel pagePanel) {
        this.annot = annot;
        this.pagePanel = pagePanel;
        annot.addPropertyChangeListener(this);
        contentReadonly = annot.isReadOnly();
        if (!contentReadonly) {
            addMouseListener(this);
            addMouseMotionListener(this);
        }
        setBorder(AnnotationComponentFactory.FOCUSBORDER);
        setOpaque(false);
        annotationChanged();
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (selectedVertex == -1 && dragRect == null) {
            annotationChanged();
        }
    }

    void annotationChanged() {
        float[] callout = annot.getCallout();
        points = new Point2D.Float[callout.length / 2];
        readonly = new boolean[points.length];
        for (int i = 0; i < points.length; i++) {
            points[i] = new Point2D.Float(callout[i * 2], callout[(i * 2) + 1]);
            readonly[i] = contentReadonly;
        }
        readonly[points.length - 1] = true; // last point never editable
        float[] cr = annot.getContentRectangle();
        content = new Rectangle2D.Float(cr[0], cr[1], cr[2]-cr[0], cr[3]-cr[1]);
        repaint();
    }

    abstract void showPopup(Point point);

    abstract void redrawAnnotation(boolean rebuild, Rectangle2D content, Point2D[] points);

    protected boolean isAllowedDragPoint(Point p) {
        /*
           if (text != null && text.getBounds().contains(p)) {
           return false;
           }*/
        return true;
    }

    private Point getScreenPoint(Point2D point) {
        return (Point)pagePanel.getPageToScreenTransform().transform(point, new Point());
    }

    public void mouseClicked(MouseEvent event) {
        if (event.isPopupTrigger()) {
            showPopup(event.getPoint());
            reset();
            redrawAnnotation(false, null, null);
            repaint();
        }
    }

    private void reset() {
        selectedVertex = -1;
        dragPointScreen = null;
        dragRect = null;
        dragRectOffset = null;
        targetArea = null;
        setCursor(null);
    }

    public void mousePressed(MouseEvent event) {
        if (event.isPopupTrigger()) {
            mouseClicked(event);
            return;
        }
        mousePressedLocation = getLocation();
        boolean caught = false;
        dragRect = null;
        Point mousepoint = SwingUtilities.convertPoint(this, event.getPoint(), pagePanel);
        for (int i=0;!caught && i < points.length;i++) {
            if (readonly[i]) {
                continue;
            }
            Point screenpoint = getScreenPoint(points[i]);
            if (mousepoint.distance(screenpoint) < MARGIN) {
                selectedVertex = i;
                caught = true;
            }
        }
        if (caught) {
            dragPointScreen = mousepoint;
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            startDrag();
            return;
        } else {
            Rectangle2D bounds = pagePanel.getPageToScreenTransform().createTransformedShape(content).getBounds2D();
            Cursor cursor = getContentBoundsCursor(mousepoint, bounds);
            if (cursor != null) {
                dragRect = bounds;
                dragRectOffset = new Point(mousepoint.x - (int) bounds.getX(), mousepoint.y - (int) bounds.getY());
                setCursor(cursor);
                startDrag();
                return;
            }
        }
        dispatchMouseEvent(event);
    }

    void startDrag() {
        Rectangle pageBounds = getParent().getBounds();
        putClientProperty("images", new BufferedImage[3]);
        setBounds(0, 0, pageBounds.width, pageBounds.height);
        repaint();
        new Thread() {
            public void run() {
                try {
                    while (dragPointScreen!=null || dragRect != null) {
                        Thread.sleep(AbstractRegionSelector.CRAWLSPEED);
                        repaint();
                    }
                } catch (InterruptedException e) { }
            }
        }.start();
    }

    private void dispatchMouseEvent(MouseEvent event) {
        // TODO - currently only sends to parents, we really want to
        // send to next item in the stacking hierarchy in case two
        // components overlap
        Point offset = getLocation();
        event.translatePoint(offset.x, offset.y);
        getParent().dispatchEvent(event);
    }

    public void mouseEntered(MouseEvent event) {
        mouseMoved(event);
    }

    public void mouseExited(MouseEvent event) {
        if (targetArea != null) {
            targetArea = null;
            setCursor(null);
            repaint();
        }
    }

    public void mouseDragged(MouseEvent event) {
        if (selectedVertex > -1) {
            // Ensure drag point stays inside page bounds
            Point p = SwingUtilities.convertPoint(this, event.getPoint(), pagePanel);
            Rectangle b = getBounds();
            p.x = Math.max(b.x, Math.min(b.width-1, p.x));
            p.y = Math.max(b.y, Math.min(b.height-1, p.y));
            if (isAllowedDragPoint(p)) {
                dragPointScreen = p;
            }
            repaint();
        } else if (dragRect != null) {
            // Ensure drag point stays inside page bounds
            Point p = SwingUtilities.convertPoint(this, event.getPoint(), pagePanel);
            Rectangle b = getBounds();
            p.x = Math.max(b.x, Math.min(b.width-1, p.x));
            p.y = Math.max(b.y, Math.min(b.height-1, p.y));

            double px = p.getX();
            double py = p.getY();

            double w = dragRect.getX();
            double n = dragRect.getY();
            double e = w + dragRect.getWidth();
            double s = n + dragRect.getHeight();

            switch (getCursor().getType()) {
                case Cursor.NW_RESIZE_CURSOR:
                    if (e - px > 0 && s - py > 0) {
                        dragRect = new Rectangle2D.Double(px, py, e - px, s - py);
                        repaint();
                    }
                    break;
                case Cursor.NE_RESIZE_CURSOR:
                    if (px - w > 0 && s - py > 0) {
                        dragRect = new Rectangle2D.Double(w, py, px - w, s - py);
                        repaint();
                    }
                    break;
                case Cursor.SW_RESIZE_CURSOR:
                    if (e - px > 0 && py - n > 0) {
                        dragRect = new Rectangle2D.Double(px, n, e - px, py - n);
                        repaint();
                    }
                    break;
                case Cursor.SE_RESIZE_CURSOR:
                    if (px - w > 0 && py - n > 0) {
                        dragRect = new Rectangle2D.Double(w, n, px - w, py - n);
                        repaint();
                    }
                    break;
                case Cursor.N_RESIZE_CURSOR:
                    if (s - py > 0) {
                        dragRect = new Rectangle2D.Double(w, py, e - w, s - py);
                        repaint();
                    }
                    break;
                case Cursor.E_RESIZE_CURSOR:
                    if (px - w > 0) {
                        dragRect = new Rectangle2D.Double(w, n, px - w, s - n);
                        repaint();
                    }
                    break;
                case Cursor.W_RESIZE_CURSOR:
                    if (e - px > 0) {
                        dragRect = new Rectangle2D.Double(px, n, e - px, s - n);
                        repaint();
                    }
                    break;
                case Cursor.S_RESIZE_CURSOR:
                    if (py - n > 0) {
                        dragRect = new Rectangle2D.Double(w, n, e - w, py - n);
                        repaint();
                    }
                    break;
                case Cursor.HAND_CURSOR:
                    Rectangle2D.Double r = new Rectangle2D.Double(px - dragRectOffset.x, py - dragRectOffset.y, dragRect.getWidth(), dragRect.getHeight());
                    if (b.contains(r)) {
                        dragRect = r;
                        repaint();
                    }
                    break;
            }
        } else {
            dispatchMouseEvent(event);
        }
    }

    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            mouseClicked(e);
            return;
        }
        if (selectedVertex != -1) {
            Point screenpoint = SwingUtilities.convertPoint(this, e.getPoint(),
pagePanel);
            if (isAllowedDragPoint(screenpoint)) {
                Point2D pagepoint = pagePanel.getScreenToPageTransform().transform(screenpoint, null);
                points[selectedVertex] = pagepoint;
            }
            setLastCalloutPoint();
            redrawAnnotation(true, content, points);
            reset();
            repaint();
            return;
        } else if (dragRect != null) {
            content = pagePanel.getScreenToPageTransform().createTransformedShape(dragRect).getBounds2D();
            setLastCalloutPoint();
            redrawAnnotation(true, content, points);
            reset();
            repaint();
            return;
        }
        dispatchMouseEvent(e);
    }

    private void setLastCalloutPoint() {
        AffineTransform t = pagePanel.getPageToScreenTransform();
        Shape cs = t.createTransformedShape(content);
        Rectangle2D rect = cs.getBounds2D();
        GeneralPath callout = new GeneralPath();
        for (int i=0;i < points.length;i++) {
            Point p = getScreenPoint(points[i]);
            p = SwingUtilities.convertPoint(pagePanel, p, this);
            if (i == points.length - 1) {
                // move last point to edge of drag rectangle
                //edgifyPoint(p, dragRect);
                p = intersectionPoint(callout.getCurrentPoint(), rect);
                points[i] = pagePanel.getScreenToPageTransform().transform(p, null);
            }
            if (i == 0) {
                callout.moveTo((float) p.x, (float) p.y);
            } else {
                callout.lineTo((float) p.x, (float) p.y);
            }
        }
    }

    public void mouseMoved(MouseEvent event) {
        Point mousepoint = SwingUtilities.convertPoint(this, event.getPoint(), pagePanel);
        Point p = null;
        // See if mouse is over a callout vertex
        for (int i=0;p==null && i < points.length;i++) {
            if (readonly[i]) {
                continue;
            }
            Point screenpoint = getScreenPoint(points[i]);
            if (mousepoint.distance(screenpoint) < MARGIN) {
                p = SwingUtilities.convertPoint(pagePanel, screenpoint, this);
            }
        }
        if (p!=null) {
            targetArea = new Ellipse2D.Float(p.x - MARGIN, p.y - MARGIN, MARGIN
* 2, MARGIN * 2);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            repaint();
        } else {
            boolean doRepaint = false;

            // see if mouse is over the content border
            Rectangle2D bounds = pagePanel.getPageToScreenTransform().createTransformedShape(content).getBounds2D();
            Cursor cursor = getContentBoundsCursor(mousepoint, bounds);

            // clear target area
            if (targetArea != null) {
                targetArea = null;
                doRepaint = true;
            }
            setCursor(cursor);
            if (doRepaint) {
                repaint();
            }
        }
        if (targetArea==null) {
             dispatchMouseEvent(event);
        }
    }

    Cursor getContentBoundsCursor(Point2D target, Rectangle2D bounds) {
        double cx = bounds.getX();
        double cy = bounds.getY();
        Point2D.Double nw = new Point2D.Double(cx, cy);
        if (target.distance(nw) < MARGIN) {
            return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
        }
        double cw = bounds.getWidth();
        Point2D.Double ne = new Point2D.Double(cx+cw, cy);
        if (target.distance(ne) < MARGIN) {
            return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
        }
        double ch = bounds.getHeight();
        Point2D.Double sw = new Point2D.Double(cx, cy+ch);
        if (target.distance(sw) < MARGIN) {
            return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
        }
        Point2D.Double se = new Point2D.Double(cx+cw, cy+ch);
        if (target.distance(se) < MARGIN) {
            return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
        }
        Shape n = createEdgeTargetShape(nw, ne);
        if (n.contains(target)) {
            return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
        }
        Shape e = createEdgeTargetShape(ne, se);
        if (e.contains(target)) {
            return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
        }
        Shape w = createEdgeTargetShape(nw, sw);
        if (w.contains(target)) {
            return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
        }
        Shape s = createEdgeTargetShape(sw, se);
        if (s.contains(target)) {
            return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
        }
        if (bounds.contains(target)) {
            return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        }
        return null;
    }

    Shape createEdgeTargetShape(Point2D p1, Point2D p2) {
        BasicStroke stroke = new BasicStroke((float) (MARGIN*2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
        Line2D line = new Line2D.Double(p1, p2);
        return stroke.createStrokedShape(line);
    }

    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        if (dragPointScreen != null || dragRect != null) {
            g2.setColor(Color.black);
            BasicStroke stroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 15, new float[] { AbstractRegionSelector.CRAWLLENGTH, AbstractRegionSelector.CRAWLLENGTH }, (int)((System.currentTimeMillis()/AbstractRegionSelector.CRAWLSPEED)%(AbstractRegionSelector.CRAWLLENGTH*2)));
            g2.setStroke(stroke);

            Rectangle2D rect = dragRect;
            if (rect == null) {
                AffineTransform t = pagePanel.getPageToScreenTransform();
                Shape cs = t.createTransformedShape(content);
                rect = cs.getBounds2D();
            }
            GeneralPath callout = new GeneralPath();
            for (int i=0;i < points.length;i++) {
                Point p = i==selectedVertex ? dragPointScreen : getScreenPoint(points[i]);
                p = SwingUtilities.convertPoint(pagePanel, p, this);
                if (i == points.length - 1) {
                    // move last point to edge of drag rectangle
                    //edgifyPoint(p, dragRect);
                    p = intersectionPoint(callout.getCurrentPoint(), rect);
                }
                if (i == 0) {
                    callout.moveTo((float) p.x, (float) p.y);
                } else {
                    callout.lineTo((float) p.x, (float) p.y);
                }
            }
            g2.draw(callout);
            g2.draw(rect);
        } else {
            AnnotationComponentFactory.paintComponent(this, this.ui, g2);
            if (targetArea != null) {
                g2.setColor(new Color(0xA0FF0000, true));
                g2.fill(targetArea);
            }
        }
    }

    /*private void edgifyPoint(Point p, Rectangle2D rect) {
        int rx1 = (int) rect.getX();
        int ry1 = (int) rect.getY();
        int rx2 = rx1 + (int) rect.getWidth();
        int ry2 = ry1 + (int) rect.getHeight();
        int xr = (int) (rect.getWidth() / 3.0);
        int yr = (int) (rect.getHeight() / 3.0);
        if (p.x < rx1 + xr) {
            p.x = rx1;
        } else if (p.x > rx2 - xr) {
            p.x = rx2;
        } else {
            p.x = rx1 + ((rx2 - rx1) / 2);
        }
        if (p.y < ry1 + yr) {
            p.y = ry1;
        } else if (p.y > ry2 - yr) {
            p.y = ry2;
        } else {
            p.y = ry1 + ((ry2 - ry1) / 2);
        }
    }*/

    private Point intersectionPoint(Point2D src, Rectangle2D rect) {
        final double sx = src.getX();
        final double sy = src.getY();
        double dx = rect.getX() + (rect.getWidth() / 2.0);
        double dy = rect.getY() + (rect.getHeight() / 2.0);
        final double cx = rect.getX() + rect.getWidth();
        final double cy = rect.getY();
        final double ctheta = Math.atan((cx - dx) / (cy - dy));
        final double dtheta = Math.atan((sx - dx) / (sy - dy));
        switch (opcode(sy, dy, ctheta, dtheta)) {
            case 0: // top
                double top = rect.getY();
                dx += (sx - dx) * ((top - dy) / (sy - dy));
                dy = top;
                break;
            case 1: // left
                double left = rect.getX();
                dy += (sy - dy) * (left - dx) / (sx - dx);
                dx = left;
                break;
            case 2: // bottom
                double bottom = rect.getY() + rect.getHeight();
                dx += (sx - dx) * (bottom - dy) / (sy - dy);
                dy = bottom;
                break;
            case 3: // right
                double right = cx;
                dy += (sy - dy) * (right - dx) / (sx - dx);
                dx = right;
                break;
        }
        return new Point((int) dx, (int) dy);
    }

    private static int opcode(double sy, double dy, double ctheta, double dtheta) {
        if (sy < dy) { // top
            if (Math.abs(ctheta) > Math.abs(dtheta)) { // top
                return 0;
            } else {
                if (dtheta >= 0.0) { // left
                    return 1;
                } else { // right
                    return 3;
                }
            }
        } else { // bottom
            if (Math.abs(ctheta) > Math.abs(dtheta)) { // bottom
                return 2;
            } else {
                if (dtheta >= 0.0) { // right
                    return 3;
                } else { // left
                    return 1;
                }
            }
        }
    }

}
