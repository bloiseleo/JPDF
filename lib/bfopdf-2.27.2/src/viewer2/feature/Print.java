// $Id: Print.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.awt.event.*;
import javax.swing.*;
import javax.print.*;

/**
 * Create a button that opens a print dialog.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">Print</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class Print extends ViewerWidget implements DocumentPanelListener {

    private final Action action;

    public Print() {
        super("Print");
        setButton("Document", "PDFViewer.Feature.Print.icon", "PDFViewer.tt.Print");
        setMenu("File\tPrint...", 'p');
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                Print.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
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
            try {
                event.getDocumentPanel().print(null, null);
            } catch (Exception e) {
                Util.displayThrowable(e, event.getViewer());
            }
        }
    }
}
