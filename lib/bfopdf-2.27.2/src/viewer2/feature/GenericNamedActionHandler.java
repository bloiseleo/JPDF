// $Id: GenericNamedActionHandler.java 19623 2014-07-11 15:17:50Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.util.*;

/**
 * Create an action handler to handle "Named" actions. Unlike most of the ActionHandler
 * features, this one will only work inside a {@link PDFViewer} - applying it to a
 * standalone {@link DocumentPanel} won't work. In addition this is intended as a generic
 * fallback handler, and so should always be added after any more specific handlers.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">GenericNamedActionHandler</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class GenericNamedActionHandler extends ActionHandler {

    /**
     * Create a new GenericNamedActionHandler
     * @since 2.11
     */
    public GenericNamedActionHandler() {
        super("GenericNamedActionHandler");
    }

    public boolean matches(DocumentPanel panel, PDFAction action) {
        String type = action.getType();
        return type.startsWith("Named:");
    }

    public void run(DocumentPanel docpanel, PDFAction action) {
        run(docpanel, action.getType().substring(6));
    }

    /**
     * Run the specified "named" action.
     * @param docpanel the active DocumentPanel
     * @param action the named action to run
     * @since 2.11.12
     */
    public static void run(DocumentPanel docpanel, String action) {
        PDFViewer viewer = docpanel.getViewer();
        if (viewer != null) {
            ViewerFeature[] features = viewer.getFeatures();
            for (int i=0;i<features.length;i++) {
                ViewerFeature f = features[i];
                if (action.equals(f.getName())) {
                    if (f instanceof ViewerWidget) {
                        ((ViewerWidget)f).action(new ViewerEvent(viewer, docpanel));
                        return;
                    } else if (f instanceof SidePanelFactory) {
                        SidePanel sidepanel = ((SidePanelFactory)f).createSidePanel();
                        String panelname = sidepanel.getName();
                        for (Iterator<SidePanel> j = docpanel.getSidePanels().iterator();j.hasNext();) {
                            sidepanel = j.next();
                            if (sidepanel.getName().equals(panelname)) {
                                docpanel.setSelectedSidePanel(sidepanel);
                                return;
                            }
                        }
                    }
                }
            }
            // Not found - last resort hacks 
            if (action.equals("SaveAs")) {
                run(docpanel, "Save");
            }
        }
    }
}
