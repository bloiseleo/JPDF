// $Id: ThumbnailCopyAction.java 31568 2019-03-14 09:31:57Z mike $

package org.faceless.pdf2.viewer2.feature;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.beans.*;
import java.util.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;

/**
 * Store a copy of the currently selected pages in the thumbnail panel for a
 * subsequent move operation.
 * <span class="featurename">The name of this feature is <span class="featureactualname">ThumbnailCopy</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.23.3
 */
public class ThumbnailCopyAction extends ViewerFeature implements ThumbnailPanel.ThumbnailSelectionAction {

    private PDFViewer viewer;

    public ThumbnailCopyAction() {
        super("ThumbnailCopy");
    }

    public void initialize(final PDFViewer viewer) {
        this.viewer = viewer;
    }

    public Action getAction(final ThumbnailPanel.View view) {
        return this.new CopyAction(UIManager.getString("PDFViewer.Copy"), view);
    }

    class CopyAction extends AbstractAction implements PropertyChangeListener, DocumentPanelListener {

        private final ThumbnailPanel.View view;
        private final DocumentPanel docpanel;

        CopyAction(String name, ThumbnailPanel.View view) {
            super(name);
            this.view = view;
            docpanel = view.getDocumentPanel();
            int mask = Util.getMenuShortcutKeyMask(docpanel);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('C', mask, false));
            setEnabled(isActionEnabled());
            view.addPropertyChangeListener(this);
            docpanel.addDocumentPanelListener(this);
            docpanel.getPDF().addPropertyChangeListener(this);
        }

        private boolean isActionEnabled() {
            int selected = 0;
            int len = view.getComponentCount();
            for (int i = 0; i < len; i++) {
                ThumbnailPanel.SinglePagePanel thumbnail = (ThumbnailPanel.SinglePagePanel) view.getComponent(i);
                if (thumbnail.isSelected()) {
                    selected++;
                }
            }
            return selected > 0;
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
                thumbnail.setFlags(thumbnail.isSelected() ?  ThumbnailPanel.SinglePagePanel.FLAG_COPY : ThumbnailPanel.SinglePagePanel.FLAG_NONE);
            }
            view.firePropertyChange("copy");
        }

    }

}
