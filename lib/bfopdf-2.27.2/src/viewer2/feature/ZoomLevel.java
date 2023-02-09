// $Id: ZoomLevel.java 22446 2016-01-13 14:11:12Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.event.*;
import java.awt.*;
import java.util.*;
import java.beans.*;

/**
 * Create a widget which displays the current zoom level, and allows the user to 
 * edit it to set the zoom level. The range of levels is taken from the PDFViewer's
 * {@link PDFViewer#getMinZoom}, {@link PDFViewer#getMaxZoom} and {@link PDFViewer#getZoomIntervals}
 * methods.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ZoomLevel</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class ZoomLevel extends ViewerWidget implements DocumentPanelListener, PropertyChangeListener {

    private PDFViewer viewer;
    private final ZoomLevelModel model;
    private final JComboBox<Float> field;
    private boolean fromupdate;

    public ZoomLevel() {
        super("ZoomLevel");
        model = this.new ZoomLevelModel();
        field = new JComboBox<Float>(model);
        field.setRenderer(new CellRenderer(field.getRenderer()));
        field.setEditor(this.new Editor(field.getEditor()));
        field.putClientProperty("JComponent.sizeVariant", "small");         // Improve OS X/Nimbus appearance
        field.setEditable(true);
        field.setFont(null);
        field.setEnabled(false);
        field.setPrototypeDisplayValue(64f);    // Why does this give such bad results?
        if (field.getEditor().getEditorComponent() instanceof JTextField) {
            int cols = Util.isLAFAqua() ? 3 : 4;
            ((JTextField)field.getEditor().getEditorComponent()).setColumns(cols);
        }
        field.setToolTipText(UIManager.getString("PDFViewer.tt.ZoomLevel"));
        field.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                DocumentPanel docpanel = getViewer().getActiveDocumentPanel();
                float newzoom = ((Float) field.getSelectedItem()).floatValue();
                if (docpanel != null) {
                    float oldzoom = docpanel.getZoom();
                    try {
                        if (Math.abs(newzoom-oldzoom) > 0.01) {
                            docpanel.getViewport().setZoomMode(DocumentViewport.ZOOM_NONE);
                            docpanel.setZoom(newzoom);
                            if (!fromupdate) {  // This is a result of a click! Turn off ZoomFitN
                                ToggleViewerWidget w = getViewer().getFeature(ZoomFit.class);
                                if (w != null && w.isSelected()) {
                                    w.setSelected(false);
                                }
                                w = getViewer().getFeature(ZoomFitWidth.class);
                                if (w != null && w.isSelected()) {
                                    w.setSelected(false);
                                }
                                w = getViewer().getFeature(ZoomFitHeight.class);
                                if (w != null && w.isSelected()) {
                                    w.setSelected(false);
                                }

                                docpanel.getViewport().setZoomMode(DocumentViewport.ZOOM_NONE);
                            }
                            fromupdate = false;
                        }
                    } catch (Exception e) {
                        field.setSelectedItem(oldzoom);
                    }

                    Component focusowner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    if (focusowner != null && (focusowner == field || field.isAncestorOf(focusowner))) {
                        docpanel.getViewport().requestFocusInWindow();
                    }
                }
            }
        });
        setComponent("Navigation", field);

        // Needed for Nimbus L&F
        field.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                field.getEditor().selectAll();
            }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        this.viewer = viewer;
        viewer.addDocumentPanelListener(this);
        viewer.addPropertyChangeListener(this);
        // Note - initial values from the PDFViewer are set by the propertyChangeEvent
        // which is called after this initialize method.
    }

    public void propertyChange(PropertyChangeEvent e) {
        String name = e.getPropertyName();
        if ("minZoom".equals(name) || "maxZoom".equals(name)) {
            int min = viewer.getMinZoom();
            int max = viewer.getMaxZoom();
            model.setRange(min / 100f, max / 100f);
        } else if ("zoomIntervals".equals(name)) {
            model.fireContentsChanged();
        }
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        if (type=="redrawn" || type=="activated") {
            fromupdate = true;          // We're called from a DocumentPanel being updated....
            float zoom = event.getDocumentPanel().getZoom();
            field.setSelectedItem(zoom);
        }
        if (type == "activated") {
            field.setEnabled(true);
        } else if (type == "deactivated") {
            field.setEnabled(false);
        }
    }

    private static class CellRenderer implements ListCellRenderer<Float> {

        private final ListCellRenderer<? super Float> r;

        CellRenderer(ListCellRenderer<? super Float> r) {
            this.r = r;
        }
    
        public Component getListCellRendererComponent(JList<? extends Float> list, Float value, int index, boolean isSelected, boolean cellHasFocus) {
            int percent = Math.round(value.floatValue() * 100f);
            JLabel label = (JLabel) r.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setText(Integer.toString(percent) + "%");
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            return label;
        }

    }

    private class Editor extends BasicComboBoxEditor implements ActionListener {

        private java.util.List<ActionListener> al = new ArrayList<ActionListener>();
        Editor(ComboBoxEditor r) {
            editor = (JTextField) r.getEditorComponent();
            editor.setHorizontalAlignment(JTextField.CENTER);
            setItem(model.getSelectedItem());
            editor.addActionListener(this);
        }

        public void setItem(Object obj) {
            super.setItem(obj);
            Float value = (Float) obj;
            int percent = (int) Math.round(value.floatValue() * 100f);
            editor.setText(Integer.toString(percent) + "%");
            if (editor.hasFocus()) {
                editor.selectAll();
            }
        }

        public Object getItem() {
            return model.getSelectedItem();
        }

        public void selectAll() {
            editor.selectAll();
        }

        public void addActionListener(ActionListener l) {
            synchronized (al) {
                al.add(l);
            }
        }

        public void removeActionListener(ActionListener l) {
            synchronized (al) {
                al.remove(l);
            }
        }

        public void actionPerformed(ActionEvent event) {
            String text = editor.getText().trim();
            if (text.endsWith("%")) {
                text = text.substring(0, text.length() - 1);
            }
            try {
                float value = Float.parseFloat(text);
                if (value < viewer.getMinZoom() || value > viewer.getMaxZoom()) {
                    throw new IllegalArgumentException(text);
                }
                Float selected = Float.valueOf(value / 100);
                model.setSelectedItem(selected);
                setItem(selected);
                ActionListener[] ala;
                synchronized (al) {
                    ala = new ActionListener[al.size()];
                    al.toArray(ala);
                }
                for (ActionListener l : ala) {
                    l.actionPerformed(event);
                }
            } catch (RuntimeException e) {
                setItem(model.getSelectedItem());
            }
        }

    }

    private class ZoomLevelModel implements ComboBoxModel<Float> {

        private float selected = 1f;
        private java.util.List<ListDataListener> ldl = new ArrayList<ListDataListener>();
        
        public Object getSelectedItem() {
            return Float.valueOf(selected);
        }

        public void setSelectedItem(Object obj) {
            if (obj instanceof Number) {
                float value = ((Number) obj).floatValue();
                if (selected != value) {
                    selected = value;
                    fireContentsChanged();
                }
            }
        }

        void setRange(float min, float max) {
            if (selected < min) {
                selected = min;
                fireContentsChanged();
            } else if (selected > max) {
                selected = max;
                fireContentsChanged();
            }
        }

        public int getSize() {
            if (viewer == null) {
                return 0;
            }
            return viewer.getZoomIntervals().length;
        }

        public Float getElementAt(int index) {
            if (viewer == null) {
                return null;
            }
            return Float.valueOf(viewer.getZoomIntervals()[index] / 100f);
        }

        public void addListDataListener(ListDataListener l) {
            synchronized (ldl) {
                ldl.add(l);
            }
        }

        public void removeListDataListener(ListDataListener l) {
            synchronized (ldl) {
                ldl.remove(l);
            }
        }

        void fireContentsChanged() {
            ListDataEvent lde = new ListDataEvent(ZoomLevel.this, ListDataEvent.CONTENTS_CHANGED, -1, -1);
            ListDataListener[] ldla;
            synchronized (ldl) {
                ldla = new ListDataListener[ldl.size()];
                ldl.toArray(ldla);
            }
            for (ListDataListener l : ldla) {
                l.contentsChanged(lde);
            }
        }

    }

}
