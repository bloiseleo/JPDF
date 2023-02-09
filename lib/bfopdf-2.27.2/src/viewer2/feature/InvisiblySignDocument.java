// $Id: InvisiblySignDocument.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.Point;
import java.security.GeneralSecurityException;
import java.util.*;
import java.io.*;

/**
 * <p>
 * Creates a new, invisible {@link FormSignature} field and then sign it.
 * The field is created by the {@link #createSignature} method, a {@link SignatureProvider}
 * is chosen and then the {@link #sign sign()} method is called to apply
 * the signature.
 * </p><p>
 * As supplied this Widget only creates a menu item - the button is disabled by default.
 * </p>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">InvisiblySignDocument</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class InvisiblySignDocument extends ViewerWidget
{
    public InvisiblySignDocument() {
        super("InvisiblySignDocument");
        setButton("Signatures", "PDFViewer.Feature.InvisiblySignDocument.icon", "PDFViewer.InvisiblySignPDF");
    }

    public boolean isButtonEnabledByDefault() {
        return false;
    }

    public void initialize(final PDFViewer viewer) {
        // Build a list of all available SignatureProviders
        ViewerFeature[] features = viewer.getFeatures();
        List<SignatureProvider> providers = new ArrayList<SignatureProvider>();
        FormSignature sig = createSignature();
        for (int i = 0; i < features.length; i++) {
            Object o = features[i];
            if (o instanceof SignatureProvider && ((SignatureProvider)o).canSign(sig)) {
                providers.add((SignatureProvider)o);
            }
        }

        // If only one provider do things the normal way - the action() method below
        // won't create a popup. If more than one create submenus and handle the actions
        // manually.
        if (providers.size() == 1) {
            setMenu("Document\tSignAndCertify\tInvisiblySignPDF...");
            super.initialize(viewer);
        } else {
            super.initialize(viewer);
            for (Iterator<SignatureProvider> i = providers.iterator(); i.hasNext(); ) {
                final SignatureProvider provider = i.next();
                ActionListener listener = new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        DocumentPanel docpanel = viewer.getActiveDocumentPanel();
                        FormSignature field = addSignature(docpanel);
                        if (field!=null) {
                            try {
                                sign(field, docpanel, provider);
                            } catch (Exception e) {
                                Util.displayThrowable(e, docpanel);
                                field.getForm().getElements().values().remove(field);
                            }
                        }
                    }
                };
                viewer.setMenu("Document\tSignAndCertify\tInvisiblySignPDF\t"+provider.getDisplayName()+"...", (char)0, true, listener);
            }
        }
    }

    public void action(ViewerEvent event) {
        JComponent button = (JComponent)event.getComponent();
        Point point = new Point(button.getWidth()/2, button.getHeight()/2);
        PDFViewer viewer = event.getViewer();
        final DocumentPanel docpanel = viewer.getActiveDocumentPanel();
        final FormSignature field = addSignature(docpanel);

        SignatureProvider.selectSignProvider(docpanel, field, button, point, new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                SignatureProvider ssp = (SignatureProvider)event.getSource();
                try {
                    sign(field, docpanel, ssp);
                } catch (Exception e) {
                    Util.displayThrowable(e, docpanel);
                } finally {
                    if (field.getState() != FormSignature.STATE_PENDING) {
                        field.getForm().getElements().values().remove(field);
                    }
                }
            }
        });
    }

    /**
     * Create the Signature field that will be signed by this widget
     */
    protected FormSignature createSignature() {
        return new FormSignature();
    }

    private FormSignature addSignature(DocumentPanel panel) {
        PDF pdf = panel.getPDF();
        Form form = pdf.getForm();
        for (Iterator<FormElement> i = form.getElements().values().iterator();i.hasNext();) {
            FormElement e = i.next();
            if (e instanceof FormSignature && ((FormSignature)e).getState()==FormSignature.STATE_PENDING) {
                JOptionPane.showMessageDialog(panel, UIManager.getString("PDFViewer.PendingSignature"), UIManager.getString("PDFViewer.Alert"), JOptionPane.INFORMATION_MESSAGE);
                return null;
            }
        }
        int num = 1;
        while (form.getElements().containsKey("Sig"+num)) {
            num++;
        }
        FormSignature sig = createSignature();
        form.getElements().put("Sig"+num, sig);
        return sig;
    }

    /**
     * Sign the Signature field.
     */
    public void sign(FormSignature field, DocumentPanel docpanel, SignatureProvider provider)
        throws IOException, GeneralSecurityException
    {
        provider.showSignDialog(docpanel, field);
    }
}
