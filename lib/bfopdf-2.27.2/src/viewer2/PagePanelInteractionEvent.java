// $Id: PagePanelInteractionEvent.java 20861 2015-02-11 10:58:38Z mike $

package org.faceless.pdf2.viewer2;

import java.awt.event.*;
import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import org.faceless.pdf2.*;

/**
 * Represents an interaction (currently only mouse) event on a {@link PagePanel}.
 * To capture these events, implement the {@link PagePanelInteractionListener} interface
 * and register via the {@link PagePanel#addPagePanelInteractionListener PagePanel.addPagePanelInteractionListener()} or
 * {@link DocumentViewport#addPagePanelInteractionListener DocumentViewport.addPagePanelInteractionListener()} methods.
 * Be sure to check the {@link #getType} method to see what sort of event it is - current values
 * include:
 * <table summary="" class="defntable">
 * <tr><th>mouseEntered</th><td>Raised when the mouseEntered event is raised</tr>
 * <tr><th>mouseExited</th><td>Raised when the mouseExited event is raised</tr>
 * <tr><th>mousePressed</th><td>Raised when the mousePressed event is raised</tr>
 * <tr><th>mouseReleased</th><td>Raised when the mouseReleased event is raised</tr>
 * <tr><th>mouseClicked</th><td>Raised when the mouseClicked event is raised</tr>
 * <tr><th>mouseMoved</th><td>Raised when the mouseMoved event is raised</tr>
 * <tr><th>mouseDragged</th><td>Raised when the mouseDragged event is raised</tr>
 * </table>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class PagePanelInteractionEvent
{
    private final PagePanel panel;
    private Point2D point;
    private MouseEvent event;
    private PDFPage page;
    private String type;

    static PagePanelInteractionEvent createMouseEntered(PagePanel panel, MouseEvent event) {
        return new PagePanelInteractionEvent("mouseEntered", panel, event);
    }
    static PagePanelInteractionEvent createMouseExited(PagePanel panel, MouseEvent event) {
        return new PagePanelInteractionEvent("mouseExited", panel, event);
    }
    static PagePanelInteractionEvent createMousePressed(PagePanel panel, MouseEvent event) {
        return new PagePanelInteractionEvent("mousePressed", panel, event);
    }
    static PagePanelInteractionEvent createMouseReleased(PagePanel panel, MouseEvent event) {
        return new PagePanelInteractionEvent("mouseReleased", panel, event);
    }
    static PagePanelInteractionEvent createMouseClicked(PagePanel panel, MouseEvent event) {
        return new PagePanelInteractionEvent("mouseClicked", panel, event);
    }
    static PagePanelInteractionEvent createMouseMoved(PagePanel panel, MouseEvent event) {
        return new PagePanelInteractionEvent("mouseMoved", panel, event);
    }
    static PagePanelInteractionEvent createMouseDragged(PagePanel panel, MouseEvent event) {
        return new PagePanelInteractionEvent("mouseDragged", panel, event);
    }

    private PagePanelInteractionEvent(String type, PagePanel panel, MouseEvent event) {
        this.type = type;
        this.panel = panel;
        this.page = panel.getPage();
        this.point = panel.getPDFPoint(event.getX(), event.getY());
        this.event = event;
    }

    public String toString() {
        return "[P:"+type+": "+panel+"]";
    }

    /**
     * Get the PagePanel this event refers to
     */
    public PagePanel getPagePanel() {
        return panel;
    }

    /**
     * Get the type of event
     */
    public String getType() {
        return type;
    }

    /**
     * Get the Page this event refers to
     */
    public PDFPage getPage() {
        return page;
    }

    /**
     * Return the Point in PDF-space where this event occured
     */
    public Point2D getPoint() {
        return point;
    }

    /**
     * Return the MouseEvent that caused this event
     * @since 2.8.5
     */
    public MouseEvent getMouseEvent() {
        return event;
    }

    /**
     * If the MouseEvent that triggered this event was a "mouseDragged" MouseEvent,
     * and the mouse has been dragged from the original PagePanel to a new PagePanel,
     * return a new PagePanelInteractionEvent which reflects the event on the new
     * PagePanel. If the mouse was dragged onto something other than a PagePanel,
     * return null. If the mouse is still on the original PagePanel, return this
     * event.
     * @since 2.11.19
     */
    public PagePanelInteractionEvent getEventOnNewPanel() {
        if (panel.contains(event.getPoint())) {
            return this;
        } else {
            DocumentViewport vp = panel.getViewport();
            Point p = SwingUtilities.convertPoint(panel, event.getPoint(), vp);
            Component c = SwingUtilities.getDeepestComponentAt(vp, p.x, p.y);
            if (c != null) {
                PagePanel newpanel = (PagePanel)SwingUtilities.getAncestorOfClass(PagePanel.class, c);
                if (newpanel != null) {
                    MouseEvent newevent = SwingUtilities.convertMouseEvent(panel, event, newpanel);
                    return new PagePanelInteractionEvent(type, newpanel, newevent);
                }
            }
        }
        return null;
    }

}
