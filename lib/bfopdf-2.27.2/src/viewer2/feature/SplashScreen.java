// $Id: SplashScreen.java 10509 2009-07-15 14:55:21Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.util.*;

/**
 * Create a splash screen which displays an "About" dialog when the PDFViewer is first displayed.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">SplashScreen</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.9
 */
public class SplashScreen extends About
{
    public SplashScreen() {
        super("SplashScreen");
    }

    public void initialize(final PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addHierarchyListener(new HierarchyListener() {
            public void hierarchyChanged(HierarchyEvent event) {
                if ((event.getChangeFlags()&event.SHOWING_CHANGED)!=0 && viewer.isShowing()) {
                    viewer.removeHierarchyListener(this);
                    showAboutDialog(viewer, false, 3500);
                }
            }
        });
    }
}
