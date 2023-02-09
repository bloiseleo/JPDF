// $Id: FormButtonWidgetFactory.java 17105 2013-03-15 18:12:19Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.Event;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Create annotations to handle {@link WidgetAnnotation} objects belonging to a {@link FormButton}.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormButtonWidgetFactory</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class FormButtonWidgetFactory extends WidgetComponentFactory {

    /**
     * Create a new FormButtonWidgetFactory.
     * @since 2.10.6
     */
    public FormButtonWidgetFactory() {
        super("FormButtonWidgetFactory");
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof WidgetAnnotation && ((WidgetAnnotation)annot).getField() instanceof FormButton;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation annot) {
        final WidgetAnnotation widget = (WidgetAnnotation)annot;
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();
        final JComponent comp = super.createComponent(pagepanel, annot, null);

        Listener listener = new Listener(widget, comp, docpanel);
        comp.addMouseListener(listener);
        comp.addFocusListener(listener);
        return comp;
    }

    private static class Listener implements MouseListener, FocusListener {
        private boolean down, over;
        private final JComponent comp;
        private final DocumentPanel docpanel;
        private final FormButton field;
        private final WidgetAnnotation widget;
        private final JSManager js;

        private Listener(WidgetAnnotation widget, JComponent comp, DocumentPanel docpanel) {
            this.comp = comp;
            this.widget = widget;
            this.field = (FormButton)widget.getField();
            this.docpanel = docpanel;
            this.js = docpanel.getJSManager();
        }

        private void update(boolean over, boolean down) {
            if (!isWidgetReadOnly(widget, docpanel)) {
                this.over = over;
                this.down = down;
                comp.putClientProperty("state", over ? down ? "D" : "R" : "N");
                comp.repaint();
            }
        }

        public void mouseEntered(MouseEvent event) {
            update(true, down);
        }

        public void mouseExited(MouseEvent event) {
            update(false, down);
        }

        public void mousePressed(MouseEvent event) {
            if (!isWidgetReadOnly(widget, docpanel)) {
                comp.grabFocus();
                update(over, true);
            }
        }

        public void mouseReleased(MouseEvent event) {
            update(over, false);
        }

        public void mouseClicked(MouseEvent event) {
        }

        public void focusGained(FocusEvent event) {
            if (!isWidgetReadOnly(widget, docpanel)) {
                comp.repaint();
                js.runEventFieldFocus(docpanel, widget, false, false);
            }
        }

        public void focusLost(FocusEvent event) {
            if (comp.isValid() && !isWidgetReadOnly(widget, docpanel)) {
                comp.repaint();
                js.runEventFieldBlur(docpanel, widget, false, false);
            }
        }
    }
}
