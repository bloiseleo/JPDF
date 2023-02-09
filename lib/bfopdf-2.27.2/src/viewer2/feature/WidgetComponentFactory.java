// $Id: WidgetComponentFactory.java 35489 2020-03-06 11:57:14Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.Event;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.*;
import java.beans.*;
import java.util.*;

/**
 * A subclass of AnnotationComponentFactory for form fields. Package private as we may
 * be tinkering with this interface.
 * @since 2.11.2
 */
abstract class WidgetComponentFactory extends AnnotationComponentFactory
{
    private static final CancelAction CANCELACTION = new CancelAction();

    WidgetComponentFactory(String name) {
        super(name);
    }

    /**
     * Called by FormXXXWidgetFactory classes when the field is created, this keeps track
     * of which fields need to be recalculated.
     */
    @SuppressWarnings("unchecked") static void createOtherChange(DocumentPanel docpanel, FormElement field) {
        if (field.getAction(Event.OTHERCHANGE) != null) {
            Collection<FormElement> c = (Collection<FormElement>)docpanel.getClientProperty("field.otherchange");
            if (c == null) {
                c = new LinkedHashSet<FormElement>();
                docpanel.putClientProperty("field.otherchange", c);
                docpanel.addDocumentPanelListener(new DocumentPanelListener() {
                    public void documentUpdated(DocumentPanelEvent event) {
                        if (event.getType() == "closing") {
                            event.getDocumentPanel().putClientProperty("field.otherchange", null);
                            event.getDocumentPanel().removeDocumentPanelListener(this);
                        }
                    }
                });
            }
            c.add(field);
        }
    }

    /**
     * Called by FormXXXWidgetFactory classes after their value has been updated, this
     * runs any calculation scripts that need to be run
     */
    @SuppressWarnings("unchecked")
    static void runOtherChange(DocumentPanel docpanel, WidgetAnnotation annot) {
        Collection<FormElement> others = (Collection<FormElement>)docpanel.getClientProperty("field.otherchange");
        if (others != null) {
            JSManager console = docpanel.getJSManager();
            for (Iterator<FormElement> i = others.iterator();i.hasNext();) {
                FormElement other = i.next();
                console.runEventFieldCalculate(docpanel, other.getAnnotation(0), annot);
            }
        }
    }

    /**
     * A shorthand method to determine whether a Widget is read-only.
     * This is a property derived from both the Widget and the current
     * state of the DocumentPanel it's dispalyed in.
     */
    static boolean isWidgetReadOnly(WidgetAnnotation widget, DocumentPanel docpanel) {
        FormElement field = widget.getField();
        return (field != null && field.isReadOnly()) || (docpanel != null && !docpanel.hasPermission("FormFill"));
    }

    @SuppressWarnings("rawtypes")
    JComponent createComponent(final PagePanel pagepanel, PDFAnnotation annot, final Class<? extends JComponent> type) {
        JComponent comp;
        final JTextComponent textcomp;
        final WidgetAnnotation widget = (WidgetAnnotation)annot;
        final FormElement field = widget.getField();
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();
        final JSManager js = docpanel.getJSManager();

        if (type==null || type==JPanel.class || type==JComponent.class) {
            textcomp = null;
            comp = super.createComponent(pagepanel, annot);
            if (!isWidgetReadOnly(widget, docpanel)) {
                comp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        } else if (type==JTextField.class) {
            comp = textcomp = new JTextField() {
                public void paintComponent(Graphics g) {
                    AnnotationComponentFactory.paintComponent(this, this.ui, g);
                    paintComponentAnnotations(this, g);
                }
            };
        } else if (type==JPasswordField.class) {
            comp = textcomp = new JPasswordField() {
                public void paintComponent(Graphics g) {
                    AnnotationComponentFactory.paintComponent(this, this.ui, g);
                    paintComponentAnnotations(this, g);
                }
            };
        } else if (type==JTextArea.class) {
            // Note awful bodge. We are asked for a JTextArea, but in order to make
            // it scrollable (which is always required) we instead return a JScrollPane
            // which has been warped to display as a regular JComponent when the JTextArea
            // it contains does not have focus. This is thoroughly nasty and I expect to
            // burn for doing this.
            textcomp = new JTextArea();
            comp = new JScrollPane() {
                public void paint(Graphics g) {
                    if (textcomp.isFocusOwner()) {
                        super.paint(g);
                    } else {
                        paintComponent(g);
                    }
                }
                public void paintComponent(Graphics g) {
                    AnnotationComponentFactory.paintComponent(this, this.ui, g);
                    paintComponentAnnotations(this, g);
                }
            };
            ((JTextArea)textcomp).setLineWrap(true);
            ((JScrollPane)comp).setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            ((JScrollPane)comp).setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            ((JScrollPane)comp).setViewportView(textcomp);
        } else if (type==JList.class) {
            textcomp = null;
            comp = new JList() {
                public void paintComponent(Graphics g) {
                    AnnotationComponentFactory.paintComponent(this, this.ui, g);
                    paintComponentAnnotations(this, g);
                }
            };
        } else {
            throw new IllegalArgumentException("Unknown type "+type);
        }
        createOtherChange(docpanel, field);
        comp.setOpaque(false);
        comp.setBorder(AnnotationComponentFactory.FOCUSBORDER);
        comp.setFocusable(!isWidgetReadOnly(widget, docpanel));
        if (docpanel.getViewer() != null && !"false".equals(getFeatureProperty(docpanel.getViewer(), "toolTip"))) {
            comp.setToolTipText(field.getDescription()!=null ? field.getDescription() : field.getForm().getName(field));
        }

        comp.addMouseListener(new MouseListener() {
            public void mouseEntered(MouseEvent event) {
                if (!isWidgetReadOnly(widget, docpanel)) {
                    js.runEventFieldMouseEnter(docpanel, widget, event);
                }
            }
            public void mouseExited(MouseEvent event) {
                if (!isWidgetReadOnly(widget, docpanel)) {
                    js.runEventFieldMouseExit(docpanel, widget, event);
                }
            }
            public void mousePressed(MouseEvent event) {
                if (!isWidgetReadOnly(widget, docpanel)) {
                    js.runEventFieldMouseDown(docpanel, widget, event);
                }
            }
            public void mouseReleased(MouseEvent event) {
                if (!isWidgetReadOnly(widget, docpanel) && !(field instanceof FormRadioButton || field instanceof FormCheckbox)) {
                    js.runEventFieldMouseUp(docpanel, widget, event);
                }
            }
            public void mouseClicked(MouseEvent event) {
            }
        });

        final JComponent fcomp = comp;
        PropertyChangeListener pcl = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                if (e.getPropertyName().equals("readOnly")) {
                    fcomp.setFocusable(!isWidgetReadOnly(widget, docpanel));
                }
            }
        };
        field.addPropertyChangeListener(pcl);
        comp.putClientProperty("bfo.fieldPCL", pcl);

        docpanel.addDocumentPanelListener(new DocumentPanelListener() {
            public void documentUpdated(DocumentPanelEvent event) {
                if (event.getType().equals("closing")) {
                    docpanel.removeDocumentPanelListener(this);
                } else {
                    boolean readonly = isWidgetReadOnly(widget, docpanel);
                    if (type==null || type==JPanel.class || type==JComponent.class) {
                        fcomp.setCursor(Cursor.getPredefinedCursor(readonly ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                    }
                    fcomp.setFocusable(!readonly);
                }
            }
        });

        if (textcomp != null) {
            InputMap im = textcomp.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap am = textcomp.getActionMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
            am.put("cancel", CANCELACTION);
        }
        return comp;
    }

    private static class CancelAction extends AbstractAction {
        public void actionPerformed(ActionEvent event) {
            JTextComponent comp = (JTextComponent)event.getSource();
            WidgetAnnotation annot = (WidgetAnnotation)comp.getClientProperty("pdf.annotation");
            if (annot!=null) {
                AbstractDocument document = (AbstractDocument)comp.getDocument();
                DocumentFilter filter = document.getDocumentFilter();
                InputVerifier verifier = comp.getInputVerifier();
                if (comp.getClientProperty("bfo.HasChanged")!=null) {
                    comp.putClientProperty("bfo.HasChanged", null);
                }
                document.setDocumentFilter(null);
                String val = annot.getField().getValue();
                comp.setText(val==null ? "" : val);
                document.setDocumentFilter(filter);
                comp.setInputVerifier(verifier);
                comp.transferFocusUpCycle();
            }
        }
    }
}
