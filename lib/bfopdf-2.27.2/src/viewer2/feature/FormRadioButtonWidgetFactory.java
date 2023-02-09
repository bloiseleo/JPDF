// $Id: FormRadioButtonWidgetFactory.java 17105 2013-03-15 18:12:19Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.Event;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;

/**
 * Create annotations to handle {@link WidgetAnnotation} objects belonging to
 * {@link FormRadioButton} and {@link FormCheckbox} objects.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormRadioButtonWidgetFactory</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class FormRadioButtonWidgetFactory extends WidgetComponentFactory {

    /**
     * Create a new FormRadioButtonWidgetFactory.
     * @since 2.10.6
     */
    public FormRadioButtonWidgetFactory() {
        super("FormRadioButtonWidgetFactory");
    }

    public boolean matches(PDFAnnotation annot) {
        if (annot instanceof WidgetAnnotation) {
            FormElement field = ((WidgetAnnotation)annot).getField();
            return field instanceof FormRadioButton || field instanceof FormCheckbox;
        } else {
            return false;
        }
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation annot) {
        final WidgetAnnotation widget = (WidgetAnnotation)annot;
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();

        final JComponent comp = createComponent(pagepanel, annot, null);
        Listener listener = new Listener(comp, widget, docpanel);
        comp.addMouseListener(listener);
        comp.addFocusListener(listener);
        comp.addKeyListener(listener);
        return comp;
    }

    private static class Listener implements FocusListener, MouseListener, KeyListener {
        private final JComponent comp;
        private final FormElement field;
        private final WidgetAnnotation widget;
        private final DocumentPanel docpanel;

        Listener(JComponent comp, WidgetAnnotation widget, DocumentPanel docpanel) {
            this.comp = comp;
            this.widget = widget;
            this.field = widget.getField();
            this.docpanel = docpanel;
        }

        public void mouseEntered(MouseEvent event) { }
        public void mouseExited(MouseEvent event) { }
        public void mouseClicked(MouseEvent event) { }

        public void mousePressed(MouseEvent event) {
            if (!isWidgetReadOnly(widget, docpanel)) {
                comp.grabFocus();
            }
        }

        public void mouseReleased(MouseEvent event) {
            if (!isWidgetReadOnly(widget, docpanel)) {
                if (field instanceof FormCheckbox) {
                    widget.setSelected(!widget.isSelected());
                } else {
                    FormRadioButton button = (FormRadioButton)field;
                    boolean unison = button.isRadiosInUnison();
                    boolean offok = !button.isNoToggleToOff();
                    boolean selected = widget.isSelected();
                    if (!selected && unison) {
                        button.setValue(widget.getValue());
                    } else if (!selected) {
                        widget.setSelected(true);
                    } else if (selected && offok && !unison) {
                        widget.setSelected(false);
                    } else if (selected && offok) {
                        button.setValue(null);
                    }
                }
                docpanel.getJSManager().runEventFieldMouseUp(docpanel, widget, event);
                runOtherChange(docpanel, widget);
                comp.repaint();
            }
        }

        public void focusGained(FocusEvent event) {
            if (!isWidgetReadOnly(widget, docpanel)) {
                docpanel.getJSManager().runEventFieldFocus(docpanel, widget, false, false);
                comp.repaint();
            }
        }

        public void focusLost(FocusEvent event) {
            if (comp.isValid() && !isWidgetReadOnly(widget, docpanel)) {
                docpanel.getJSManager().runEventFieldBlur(docpanel, widget, false, false);
                comp.repaint();
            }
        }
        public void keyPressed(KeyEvent e) { }
        public void keyTyped(KeyEvent e) { }
        public void keyReleased(KeyEvent e) {
            if (!isWidgetReadOnly(widget, docpanel)) {
                int code = e.getKeyCode();
                if (code==e.VK_SPACE) {
                    mouseClicked(null);
                }
            }
        }
    }
}
