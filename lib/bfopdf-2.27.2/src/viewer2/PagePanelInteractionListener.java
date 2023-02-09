// $Id: PagePanelInteractionListener.java 10479 2009-07-10 09:51:07Z chris $

package org.faceless.pdf2.viewer2;

/**
 * A listener that should be implemented by any objects wanting to be notified
 * whenever a {@link PagePanelInteractionEvent} is raised.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see PagePanelInteractionEvent
 * @see PagePanel#addPagePanelInteractionListener
 * @see DocumentViewport#addPagePanelInteractionListener
 * @since 2.8
 */
public interface PagePanelInteractionListener
{
    /**
     * Called when a {@link PagePanelInteractionEvent} is raised
     */
    public void pageAction(PagePanelInteractionEvent event);
}
