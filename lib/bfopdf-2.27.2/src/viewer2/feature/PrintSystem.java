package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;

/**
 * Create a button that opens the system print dialog.
 * The file must be saved to a temporary file to print.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">PrintSystem</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class PrintSystem extends ViewerWidget implements DocumentPanelListener {

    private final Action action;

    public PrintSystem() {
        super("PrintSystem");
        setButton("Document", "PDFViewer.Feature.Print.icon", "PDFViewer.tt.Print");
        setMenu("File\tPrint...", 'p');
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                PrintSystem.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
    }

    public boolean isEnabledByDefault() {
        return false;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        DocumentPanel docpanel = event.getDocumentPanel();
        if (type.equals("activated") || (type.equals("permissionChanged") && docpanel == getViewer().getActiveDocumentPanel())) {
            action.setEnabled(docpanel.getPDF() != null && docpanel.hasPermission("Print"));
        } else if (type.equals("deactivated")) {
            action.setEnabled(false);
        }
    }

    public void action(ViewerEvent event) {
        if (action.isEnabled()) {
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
                                        Desktop.getDesktop().print(ffile);
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
}
