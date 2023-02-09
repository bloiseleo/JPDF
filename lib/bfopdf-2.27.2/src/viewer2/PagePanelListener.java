// $Id: PagePanelListener.java 10479 2009-07-10 09:51:07Z chris $

package org.faceless.pdf2.viewer2;

/**
 * A listener that should be implemented by any objects wanting to be notified
 * whenever a {@link PagePanelEvent} is raised.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see PagePanelEvent
 * @see PagePanel#addPagePanelListener
 * @see DocumentViewport#addPagePanelListener
 * @since 2.8
 */
public interface PagePanelListener
{
    /**
     * Called when a {@link PagePanelEvent} is raised
     */
    public void pageUpdated(PagePanelEvent event);
}
