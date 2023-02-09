// $Id: FormBlankSignatureWidgetFactory.java 17105 2013-03-15 18:12:19Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.Event;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.beans.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.io.*;
import java.net.URL;
import javax.swing.filechooser.FileFilter;
import java.util.*;
import java.text.*;
import java.awt.event.*;

/**
 * Create annotations to handle {@link WidgetAnnotation} objects belonging to
 * unsigned {@link FormSignature} fields. When an annotation created by this
 * class is selected, a {@link SignatureProvider} will be chosen and its
 * {@link SignatureProvider#showSignDialog showSignDialog()} method called.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormBlankSignatureWidgetFactory</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8, with much of the functionality moved to {@link SignatureProvider} in 2.11
 */
public class FormBlankSignatureWidgetFactory extends WidgetComponentFactory
{
    /**
     * Create a new FormBlankSignatureWidgetFactory
     */
    public FormBlankSignatureWidgetFactory() {
        super("FormBlankSignatureWidgetFactory");
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof WidgetAnnotation && ((WidgetAnnotation)annot).getField() instanceof FormSignature && ((FormSignature)((WidgetAnnotation)annot).getField()).getState()==FormSignature.STATE_BLANK;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation annot) {
        final WidgetAnnotation widget = (WidgetAnnotation)annot;
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();
        final FormSignature field = (FormSignature)widget.getField();
        final JComponent comp = createComponent(pagepanel, annot, null);

        comp.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (!isWidgetReadOnly(widget, docpanel)) {
                    SignatureProvider.selectSignProvider(docpanel, field, comp, event.getPoint(), new ActionListener() {
                        public void actionPerformed(ActionEvent event) {
                            SignatureProvider ssp = (SignatureProvider)event.getSource();
                            try {
                                sign(field, docpanel, ssp);
                            } catch (Exception e) {
                                Util.displayThrowable(e, docpanel);
                            }
                        }
                    });
                }
            }
        });
        comp.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent event) {
                comp.repaint();
                docpanel.runAction(widget.getAction(Event.FOCUS));
            }
            public void focusLost(FocusEvent event) {
                if (comp.isValid()) {
                    comp.repaint();
                    docpanel.runAction(widget.getAction(Event.BLUR));
                }
            }
        });
        return comp;
    }

    /**
     * Sign the field, by calling the {@link SignatureProvider#showSignDialog showSignDialog()}
     * method on the specified SignatureServiceProvider.
     * @param field the blank Signature Field to sign
     * @param docpanel the DocumentPanel
     * @param provider the SignatureServiceProvider to use to sign the field.
     */
    public void sign(FormSignature field, final DocumentPanel docpanel, SignatureProvider provider)
        throws IOException, GeneralSecurityException
    {
        provider.showSignDialog(docpanel, field);
    }
}
