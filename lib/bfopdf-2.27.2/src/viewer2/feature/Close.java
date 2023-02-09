// $Id: Close.java 43613 2022-07-13 17:19:36Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import javax.swing.*;

/**
 * Create a "File : Close" menu item to close the current document.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">Close</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class Close extends ViewerWidget
{
    public Close() {
        super("Close");
        setMenu("File\tClose", 'w');
    }

    public void action(ViewerEvent event) {
        event.getViewer().closeDocumentPanel(event.getViewer().getActiveDocumentPanel());
    }
}
