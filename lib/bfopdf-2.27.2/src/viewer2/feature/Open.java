// $Id: Open.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import javax.swing.*;
import java.io.File;

/**
 * Create a button and menu item to load a document from the filesystem.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">Open</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class Open extends ViewerWidget {

    public Open() {
        super("Open");
        setButton("File", "PDFViewer.Feature.Open.icon", "PDFViewer.tt.Open");
        setToolBarEnabledAlways(true);
        setMenu("File\tOpen...", 'o');
        setDocumentRequired(false);
    }

    public boolean isEnabledByDefault() {
        return Util.hasFilePermission();
    }

    public void action(ViewerEvent event) {
        event.getViewer().loadPDF((File) null);
    }

}
