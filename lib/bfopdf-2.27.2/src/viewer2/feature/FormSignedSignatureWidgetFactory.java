// $Id: FormSignedSignatureWidgetFactory.java 40618 2021-07-08 09:54:48Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.Event;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.awt.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.io.*;
import javax.swing.filechooser.FileFilter;
import java.util.*;
import java.text.*;
import java.awt.event.*;

/**
 * Create annotations to handle {@link WidgetAnnotation} objects belonging to signed
 * {@link FormSignature} fields. When an annotation created by this field is clicked on,
 * a {@link SignatureProvider} wil be chosen to verify the field and that objects
 * {@link SignatureProvider#showVerifyDialog showVerifyDialog()} method called.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature, as well as those parameters specified in the {@link SignatureProvider} API documentation.
 * <table summary="">
 * <tr><th>stateIcon</th><td>Whether to show the icon for the current signature state. Defaults to true</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">FormSignedSignatureWidgetFactory</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8, with much of the functionality moved to {@link SignatureProvider} in 2.11
 */
public class FormSignedSignatureWidgetFactory extends WidgetComponentFactory
{
    private boolean showStateIcon = true;

    /**
     * Create a new FormSignedSignatureWidgetFactory that verifies
     * against the default KeyStore.
     */
    public FormSignedSignatureWidgetFactory() {
        super("FormSignedSignatureWidgetFactory");
    }

    /**
     * Whether to show the icon for the current signature state on the signature
     * itself. Defaults to true
     * @param show the flag
     * @since 2.26
     */
    public void showStateIcon(boolean show) {
        this.showStateIcon = show;
    }

    public boolean matches(PDFAnnotation annot) {
        return annot instanceof WidgetAnnotation && ((WidgetAnnotation)annot).getField() instanceof FormSignature && ((FormSignature)((WidgetAnnotation)annot).getField()).getState()==FormSignature.STATE_SIGNED;
    }

    protected void paintComponentAnnotations(JComponent comp, Graphics g) {
         super.paintComponentAnnotations(comp, g);
         WidgetAnnotation annot = (WidgetAnnotation)comp.getClientProperty("pdf.annotation");
         DocumentPanel docpanel = (DocumentPanel)SwingUtilities.getAncestorOfClass(DocumentPanel.class, comp);
         if (docpanel != null && showStateIcon) {
             ImageIcon icon = SignatureProvider.getIcon(docpanel, (FormSignature)annot.getField());
             icon.paintIcon(comp, g, 0, 0);
         }
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation annot) {
        final WidgetAnnotation widget = (WidgetAnnotation)annot;
        final DocumentPanel docpanel = pagepanel.getDocumentPanel();
        final FormSignature field = (FormSignature)widget.getField();
        final JComponent comp = createComponent(pagepanel, annot, null);

        comp.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                SignatureProvider.selectVerifyProvider(docpanel, field, comp, event.getPoint(), new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        SignatureProvider ssp = (SignatureProvider)event.getSource();
                        verify(field, docpanel, ssp);
                        comp.repaint();
                    }
                });
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
     * Verify the signature field, by calling the
     * {@link SignatureProvider#showVerifyDialog showVerifyDialog()} method on the
     * specified SignatureProvider
     * @param field the signed Signature field to verify
     * @param docpanel the DocumentPanel
     * @param provider the SignatureProvider to use to verify the signature
     */
    public void verify(FormSignature field, DocumentPanel docpanel, SignatureProvider provider) {
        provider.showVerifyDialog(docpanel, field);
    }
}
