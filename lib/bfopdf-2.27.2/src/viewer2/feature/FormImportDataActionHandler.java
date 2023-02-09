// $Id: FormImportDataActionHandler.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.util.concurrent.Callable;
import javax.swing.*;
import java.io.*;
import java.net.*;

/**
 * Create an action handler to deal with "FormImportData" {@link PDFAction}.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormImportDataActionHandler</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class FormImportDataActionHandler extends ActionHandler {

    /**
     * Create a new FormImportDataActionHandler
     * @since 2.11
     */
    public FormImportDataActionHandler() {
        super("FormImportDataActionHandler");
    }

    public boolean matches(DocumentPanel panel, PDFAction action) {
        return action.getType().equals("FormImportData");
    }

    public void run(final DocumentPanel docpanel, final PDFAction action) {
        try {
            JSEngine.doPrivileged(new Callable<Void>() {
                public Void call() throws IOException {
                    InputStream in = null;
                    try {
                        URL url = Util.toURL(docpanel, action.getURL());
                        in = url.openConnection().getInputStream();
                        FDF fdf = new FDF(in);
                        docpanel.getPDF().importFDF(fdf);
                    } finally {
                        if (in != null) try { in.close(); } catch (IOException e) {}
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            Util.displayThrowable(e, docpanel.getViewer());
        }
    }

}
