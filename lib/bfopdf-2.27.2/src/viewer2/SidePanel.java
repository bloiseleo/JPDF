// $Id: SidePanel.java 16027 2012-07-13 11:18:01Z mike $

package org.faceless.pdf2.viewer2;

import org.faceless.pdf2.PDF;
import javax.swing.Icon;

/**
 * Represents a side-panel in a {@link DocumentPanel}. Typical uses are to
 * display Thumbnails, Document outlines and so on. Any class implementing this
 * interace must be a {@link java.awt.Component}.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see SidePanelFactory
 * @since 2.8
 */
public interface SidePanel
{
    /**
     * Set the {@link DocumentPanel} this SidePanel is a member of. This method
     * is called every time the DocumentPanel has it's document changed - the
     * SidePanel should be reinitialized in this call. If the SidePanel
     * is removed, this method will be called with <code>null</code> as it's
     * argument.
     */
    public void setDocumentPanel(DocumentPanel panel);

    /**
     * Called when the panel is made visible
     */
    public void panelVisible();

    /**
     * Called when the panel is made hidden
     */
    public void panelHidden();

    /**
     * Return the non-localized display name of this SidePanel.
     * @since 2.10.3
     */
    public String getName();

    /**
     * Return the Icon to use when displaying this SidePanel
     * @since 2.11.7
     */
    public Icon getIcon();
}
