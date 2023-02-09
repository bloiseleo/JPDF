// $Id: ThumbnailCutAction.java 39831 2021-04-23 08:55:01Z mike $

package org.faceless.pdf2.viewer2.feature;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.beans.*;
import java.util.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;

/**
 * Store the currently selected pages in the thumbnail panel for a
 * subsequent move operation.
 * <span class="featurename">The name of this feature is <span class="featureactualname">ThumbnailCut</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.15
 */
public class ThumbnailCutAction extends ViewerFeature implements ThumbnailPanel.ThumbnailSelectionAction {

    private PDFViewer viewer;

    public ThumbnailCutAction() {
        super("ThumbnailCut");
    }

    public void initialize(final PDFViewer viewer) {
        this.viewer = viewer;
    }

    public Action getAction(final ThumbnailPanel.View view) {
        if (!view.isFactoryEditable()) {
            return null;
        }
        return this.new CutAction(UIManager.getString("PDFViewer.Cut"), view);
    }

    class CutAction extends AbstractAction implements PropertyChangeListener, DocumentPanelListener {

        private final ThumbnailPanel.View view;
        private final DocumentPanel docpanel;

        CutAction(String name, ThumbnailPanel.View view) {
            super(name);
            this.view = view;
            docpanel = view.getDocumentPanel();
            int mask = Util.getMenuShortcutKeyMask(docpanel);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('X', mask, false));
            setEnabled(isActionEnabled());
            view.addPropertyChangeListener(this);
            docpanel.addDocumentPanelListener(this);
            docpanel.getPDF().addPropertyChangeListener(this);
        }

        private boolean isActionEnabled() {
            return view.isAnythingDraggableSelected() && !view.isEntireDraggablePDFSelected() && view.isEditable() && view.isDraggable();
        }

        public void propertyChange(PropertyChangeEvent event) {
            String name = event.getPropertyName();
            if (event.getSource() == docpanel.getPDF()) {
                if (name.equals("pages")) {
                    setEnabled(isActionEnabled());
                }
            } else {
                if (name.equals("selection") || name.equals("selected")) {
                    setEnabled(isActionEnabled());
                }
            }
        }

        public void documentUpdated(DocumentPanelEvent event) {
            if ("permissionChanged".equals(event.getType())) {
                setEnabled(isActionEnabled());
            } else if ("closing".equals(event.getType())) {
                view.removePropertyChangeListener(this);
                docpanel.removeDocumentPanelListener(this);
            }
        }

        public void actionPerformed(ActionEvent event) {
            // Set the source view for a paste action
            viewer.putClientProperty(Util.PACKAGE + ".pasteView", view);
            int len = view.getComponentCount();
            for (int i = 0; i < len; i++) {
                ThumbnailPanel.SinglePagePanel thumbnail = (ThumbnailPanel.SinglePagePanel) view.getComponent(i);
                thumbnail.setFlags(thumbnail.isSelected() ?
                        ThumbnailPanel.SinglePagePanel.FLAG_CUT :
                        ThumbnailPanel.SinglePagePanel.FLAG_NONE);
            }
            view.firePropertyChange("cut");
        }

    }

}
