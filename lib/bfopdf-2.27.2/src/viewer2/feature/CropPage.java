// $Id: CropPage.java 19814 2014-07-24 18:03:07Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import java.awt.*;
import java.awt.geom.*;
import javax.swing.undo.*;
import javax.swing.*;
import javax.swing.event.*;

public class CropPage extends ViewerFeature implements AreaSelectionAction, DocumentPanelListener {

    private boolean enabled;

    public CropPage() {
        super("CropPage");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        DocumentPanel docpanel = event.getDocumentPanel();
        String type = event.getType();
        if (type.equals("activated") || (type.equals("permissionChanged") && docpanel == docpanel.getViewer().getActiveDocumentPanel())) {
            enabled = docpanel.getPDF() != null && docpanel.hasPermission("PageEdit");
        }
    }

    public String getDescription() {
        return UIManager.getString("PDFViewer.Crop");
    }

    public void selectArea(final PagePanel panel, final Rectangle2D r) {
        if (!enabled) {
            return;
        }
        final PDFPage page = panel.getPage();
        final float[] orig = page.getBox("CropBox");
        page.setBox("CropBox", (float)r.getMinX(), (float)r.getMinY(), (float)r.getMaxX(), (float)r.getMaxY());

        panel.getDocumentPanel().fireUndoableEditEvent(new UndoableEditEvent(panel.getDocumentPanel(), new AbstractUndoableEdit() {
            public String getPresentationName() {
                return getDescription();
            }
            public void undo() {
                super.undo();
                page.setBox("CropBox", orig[0], orig[1], orig[2], orig[3]);
            }
            public void redo() {
                super.redo();
                page.setBox("CropBox", (float)r.getMinX(), (float)r.getMinY(), (float)r.getMaxX(), (float)r.getMaxY());
            }
        }));
    }

}
