// $Id: PagePanelEvent.java 20861 2015-02-11 10:58:38Z mike $

package org.faceless.pdf2.viewer2;

import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import org.faceless.pdf2.*;

/**
 * Represents an event on a {@link PagePanel} indicating that document has changed
 * somehow. To capture these events, implement the {@link PagePanelListener} interface
 * and register via the {@link PagePanel#addPagePanelListener PagePanel.addPagePanelListener()} or
 * {@link DocumentViewport#addPagePanelListener DocumentViewport.addPagePanelListener()} methods.
 * Be sure to check the {@link #getType} method to see what sort of event it is - current values
 * include:
 * <table summary="" class="defntable">
 * <tr><th>redrawing</th><td>Raised just before the page starts redrawing</td></tr>
 * <tr><th>redrawn</th><td>Raised when the page has completed redrawing</td></tr>
 * <tr><th>hidden</th><td>Raised when the page is no longer displayed by the PagePanel</td></tr>
 * <tr><th>visible</th><td>Raised when the page is newly displayed by the PagePanel</td></tr>
 * </table>
 * PagePanelEvent's are created via the static "create" methods, but unless you are implementing
 * your own Viewport or similar there should be no reason to call these methods.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class PagePanelEvent {

    private final PagePanel panel;
    private final String type;
    private final PDFPage page;

    /**
     * Create a "redrawing" PagePanelEvent
     */
    public static PagePanelEvent createPageRedrawing(PagePanel panel, PDFPage page) {
        return new PagePanelEvent("redrawing", panel, page);
    }

    /**
     * Create a "redrawn" PagePanelEvent
     */
    public static PagePanelEvent createPageRedrawn(PagePanel panel, PDFPage page) {
        return new PagePanelEvent("redrawn", panel, page);
    }

    /**
     * Create a "visible" PagePanelEvent
     */
    public static PagePanelEvent createPageVisible(PagePanel panel, PDFPage page) {
        return new PagePanelEvent("visible", panel, page);
    }

    /**
     * Create a "hidden" PagePanelEvent
     */
    public static PagePanelEvent createPageHidden(PagePanel panel, PDFPage page) {
        return new PagePanelEvent("hidden", panel, page);
    }

    private PagePanelEvent(String type, PagePanel panel, PDFPage page) {
        this.type = type;
        this.panel = panel;
        this.page = page;
    }

    public String toString() {
        return "[P:"+type+"]";
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
}
