package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;

/**
 * Create a button that opens the file with the Desktop.open method.
 * The file must be saved to a temporary file first.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">OpenSystem</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.26
 */
public class OpenSystem extends ViewerWidget {

    public OpenSystem() {
        super("OpenSystem");
        setButton("Document", "PDFViewer.Feature.OpenSystem.icon", "PDFViewer.tt.Save");
        setMenu("File\tSaveToNetwork", 's');
    }

    public boolean isEnabledByDefault() {
        return false;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
    }

    public void action(ViewerEvent event) {
        File file = null;
        try {
            file = File.createTempFile("print", ".pdf");
            file.deleteOnExit();
            if (Save.save(file, event, Collections.<Exporter>singletonList(new PDFExporter()), false, false)) {
                final DocumentPanel docpanel = event.getDocumentPanel();
                final File ffile = file;
                docpanel.addDocumentPanelListener(new DocumentPanelListener() {
                    public void documentUpdated(DocumentPanelEvent event) {
                        if (event.getType().equals("stateChanged")) {
                            if ("save.completed".equals(event.getState())) {
                                try {
                                    Desktop.getDesktop().open(ffile);
                                } catch (IOException e) {
                                    Util.displayThrowable(e, docpanel);
                                }
                            }
                        }
                        docpanel.removeDocumentPanelListener(this);
                    }
                });
            }
        } catch (Exception e) {
            Util.displayThrowable(e, event.getViewer());
        }
    }

}
