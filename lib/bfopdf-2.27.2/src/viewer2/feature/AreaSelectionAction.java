// $Id

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.PagePanel;
import java.awt.geom.Rectangle2D;

/**
 * A type of feature that will perform an action on an area selected
 * with the {@link SelectArea} feature. Any features implementing this
 * interface will be presented as an option by that feature.
 *
 * @see SelectArea
 * @see TextSelectionAction
 * @since 2.11.25
 */
public interface AreaSelectionAction {

    /**
     * Get the name of this Action, to appear in the popup menu.
     */
    public String getDescription();

    /**
     * Invoked when the area is selected.
     * @param pagepanel the PagePanel the area was selected on
     * @param area the selected area
     */
    public void selectArea(PagePanel pagepanel, Rectangle2D area);

    /**
     * Indicates whether this action is enabled.
     * @since 2.13
     */
    public boolean isEnabled();

}
