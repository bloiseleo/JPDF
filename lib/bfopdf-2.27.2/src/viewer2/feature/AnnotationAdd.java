// $Id: AnnotationAdd.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.DialogPanel;
import org.faceless.pdf2.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * An {@link AbstractRegionSelector} that allows new annotations to be added to the
 * PDF Page.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>factories</th><td>A comma-separated list of <code>AnnotationComponentFactory</code>
 * names that should be used to initialize this feature.</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationAdd</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class AnnotationAdd extends AbstractRegionSelector {

    private Set<AnnotationComponentFactory> factories;
    private Action action;

    public AnnotationAdd() {
        super("AnnotationAdd");
        setButton("Mode", "PDFViewer.Feature.AnnotationAdd.icon", "PDFViewer.tt.AnnotationAdd");
        factories = new LinkedHashSet<AnnotationComponentFactory>();
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                AnnotationAdd.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        ViewerFeature[] features = viewer.getFeatures();
        String val = getFeatureProperty(viewer, "factories");
        String[] reqfactories = null;
        if (val != null) {
            reqfactories = val.split(", *");
        }
        for (int i=0;i<features.length;i++) {
            if (features[i] instanceof AnnotationComponentFactory) {
                AnnotationComponentFactory factory = (AnnotationComponentFactory)features[i];
                boolean found = reqfactories == null;
                for (int j=0;!found && j<reqfactories.length;j++) {
                    found = reqfactories[j].equals(factory.getName());
                }
                if (found && factory.createNewAnnotation() != null) {
                    factories.add(factory);
                }
            }
        }
    }

    public void documentUpdated(DocumentPanelEvent event) {
        super.documentUpdated(event);
        String type = event.getType();
        if (type.equals("activated") || (type.equals("permissionChanged") && event.getDocumentPanel() == getViewer().getActiveDocumentPanel())) {
            action.setEnabled(event.getDocumentPanel().getPDF() != null && event.getDocumentPanel().hasPermission("Annotate"));
        } else if (type.equals("deactivated")) {
            action.setEnabled(false);
        }
        if (isSelected() && !action.isEnabled()) {
            setSelected(false);
        }
    }

    /**
     * Set the list of {@link AnnotationComponentFactory} objects that can be used
     * to add annotations. Each factory must return something useful from
     * {@link AnnotationComponentFactory#createNewAnnotation},
     * {@link AnnotationComponentFactory#getAnnotationType} and
     * {@link AnnotationComponentFactory#createEditComponent createEditComponent()}.
     * @param factories the factories
     * @since 2.10
     */
    public void setAnnotationFactories(AnnotationComponentFactory[] factories) {
        this.factories.clear();
        for (int i=0;i<factories.length;i++) {
            addAnnotationFactory(factories[i]);
        }
    }

    /**
     * Add an {@link AnnotationComponentFactory} so that it can be used to create
     * new annotatations. The factory must return something useful from
     * {@link AnnotationComponentFactory#createNewAnnotation},
     * {@link AnnotationComponentFactory#getAnnotationType} and
     * {@link AnnotationComponentFactory#createEditComponent createEditComponent()}.
     * @param factory the Factory
     * @since 2.10
     */
    public void addAnnotationFactory(AnnotationComponentFactory factory) {
        if (factory.createNewAnnotation()==null) {
            throw new IllegalArgumentException("Factory does not implement createNewAnnotation");
        }
        factories.add(factory);
    }

    /**
     * Remove an {@link AnnotationComponentFactory} from the list of factories
     * available to create a new annotation.
     * @param factory the Factory
     * @since 2.10
     */
    public void removeAnnotationFactory(AnnotationComponentFactory factory) {
        factories.remove(factory);
    }

    public void action(final PagePanel panel, Point2D start, Point2D end) {
        PDFPage page = panel.getPage();
        float[] box = page.getBox("ViewBox");
        Rectangle2D rect = new Rectangle2D.Float(box[0], box[1], box[2]-box[0], box[3]-box[1]);
        if (start.distance(end) < 4) {
            return;
        }
        if (rect.contains(start) && rect.contains(end)) {
            Object[] o = displayDialog(panel.getDocumentPanel().getViewer(), (float)start.getX(), (float)start.getY(), (float)end.getX(), (float)end.getY());
            final PDFAnnotation annot = (PDFAnnotation)o[0];
            final AnnotationComponentFactory factory = (AnnotationComponentFactory)o[1];

            if (annot != null) {
                factory.postEdit(annot, panel, "create");
                annot.setPage(panel.getPage());
                annot.rebuild();
                final DocumentPanel docpanel = panel.getViewport().getDocumentPanel();
                docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new AbstractUndoableEdit() {
                    public String getPresentationName() {
                        return factory.getAnnotationType();
                    }
                    public void undo() {
                        super.undo();
                        annot.setPage(null);
                    }
                    public void redo() {
                        super.redo();
                        annot.setPage(panel.getPage());
                        annot.rebuild();
                    }
                }));
            }
        }
    }

    /**
     * Display a dialog to create a new PDFAnnotation, and return it if OK or null if Cancelled.
     */
    private Object[] displayDialog(final PDFViewer root, final float x1, final float y1, final float x2, final float y2) {
        final JPanel body = new JPanel(new BorderLayout()) ;
        body.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        final PDFAnnotation[] annotationholder = new PDFAnnotation[1];
        final AnnotationComponentFactory[] factoryholder = new AnnotationComponentFactory[1];

        if (factories.size()==0) {
            throw new IllegalStateException("No Annotation Factories available");
        } else if (factories.size()==1) {
            AnnotationComponentFactory factory = factories.iterator().next();
            PDFAnnotation annot = factory.createNewAnnotation(x1, y1, x2, y2);
            annot.setAuthor(root.getCurrentUser());
            JComponent editcomp = factory.createEditComponent(annot, false, true);
            if (editcomp!=null) {
                body.add(editcomp, BorderLayout.CENTER, 0);
                annotationholder[0] = annot;
                factoryholder[0] = factory;
            } else {
                factory = null;
            }
        } else {
            final JSplitPane splitpane = new JSplitPane();
            Vector<AnnotationComponentFactory> factorylist = new Vector<AnnotationComponentFactory>(factories);

            final CardLayout cards = new CardLayout();
            final JPanel cardPanel = new JPanel();
            cardPanel.setLayout(cards);
            final PDFAnnotation[] annots = new PDFAnnotation[factorylist.size()];
            for (int i = 0; i < factorylist.size(); i++) {
                AnnotationComponentFactory factory = factorylist.get(i);
                annots[i] = factory.createNewAnnotation(x1, y1, x2, y2);
                annots[i].setAuthor(root.getCurrentUser());
                JComponent editcomp = factory.createEditComponent(annots[i], false, true);
                if (editcomp != null) {
                    cardPanel.add(editcomp, factory.getName());
                } else {
                    factorylist.remove(i--);
                }
            }
            splitpane.setBottomComponent(cardPanel);

            final JList<AnnotationComponentFactory> list = new JList<AnnotationComponentFactory>(factorylist);
            list.setCellRenderer(new DefaultListCellRenderer() {
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean focus) {
                    return super.getListCellRendererComponent(list, ((AnnotationComponentFactory)value).getAnnotationType()+"      ", index, isSelected, focus);
                }
            });
            splitpane.setTopComponent(list);

            list.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    int index = list.getSelectedIndex();
                    if (index >= 0) {
                        AnnotationComponentFactory factory = (AnnotationComponentFactory) list.getSelectedValue();
                        String name = factory.getName();
                        cards.show(cardPanel, name);
                        annotationholder[0] = annots[index];
                        factoryholder[0] = factory;
                        Preferences preferences = root.getPreferences();
                        if (preferences!=null) {
                            preferences.put("feature.AnnotationAdd.defaultType", name);
                        }
                    } else {
                        factoryholder[0] = null;
                        annotationholder[0] = null;
                        splitpane.setBottomComponent(new JPanel());
                    }
                }
            });
            Preferences preferences = root.getPreferences();
            String deffactory = preferences==null ? null : preferences.get("feature.AnnotationAdd.defaultType", null);
            if (deffactory==null) {
                list.setSelectedIndex(0);
            } else {
                for (int i=0;i<factorylist.size();i++) {
                    if (factorylist.get(i).getName().equals(deffactory)) {
                        list.setSelectedIndex(i);
                    }
                }
            }
            body.add(splitpane, BorderLayout.CENTER, 0);
        }

        final DialogPanel dialog = new DialogPanel(true);
        dialog.addComponent(body);
        if (!dialog.showDialog(root, UIManager.getString("PDFViewer.Annotation"))) {
            annotationholder[0] = null;
        }
        return new Object[] { annotationholder[0], factoryholder[0] };
    }

}
