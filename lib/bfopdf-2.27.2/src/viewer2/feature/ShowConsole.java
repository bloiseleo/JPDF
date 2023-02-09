// $Id: ShowConsole.java 18841 2014-01-13 13:29:36Z chris $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * <p>
 * Create a menu item to display the JavaScript Console
 * </p>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ShowConsole</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.9
 */
public class ShowConsole extends ViewerWidget
{
    public ShowConsole() {
        super("ShowConsole");
        setMenu("Window\tJavaScriptConsole...", 'j');
        setDocumentRequired(false);
    }

    public void action(ViewerEvent event) {
        event.getViewer().getJSManager().consoleShow();
    }
}
