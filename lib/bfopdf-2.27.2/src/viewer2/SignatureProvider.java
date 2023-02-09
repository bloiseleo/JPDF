// $Id: SignatureProvider.java 44459 2022-09-30 08:50:43Z mike $

package org.faceless.pdf2.viewer2;

import javax.swing.*;
import java.awt.event.*;
import java.awt.Point;
import java.util.*;
import java.beans.*;
import java.text.*;
import java.io.IOException;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import org.faceless.pdf2.*;

/**
 * <p>
 * A ViewerFeature that acts as a service provider for applying Digital Signatures.
 * When a digital signature field is encounted by the PDF viewer, it will search
 * its list of features for an instance of this class that {@link #canSign can sign}
 * or {@link #canVerify can verify} the field as appropriate. A dialog will then
 * be presented.
 * </p><p>
 * Although each type of subclass will be different, there are several properties
 * that apply to any digital signature and so they can be specified here. Subclasses
 * implementing the {@link #showSignDialog showSignDialog()} method are expected to
 * use these values if specified, or prompt the user otherwise.
 * </p>
 * <div class="initparams">
 * The following <a href="doc-files/initparams.html">initialization parameters</a> can be specified to configure subclasses of this feature.
 * <table summary="">
 * <tr><th>name</th><td>The name of the entity signing the document - the default value of {@link #getDefaultName}</td></tr>
 * <tr><th>reason</th><td>The reason for the signature - the default value of {@link #getDefaultReason}.</td></tr>
 * <tr><th>location</th><td>The location the signature is being applied - the default value of {@link #getDefaultLocation}.</td></tr>
 * <tr><th>certification</th><td>The type of certification to use for the first signature applied to a PDF (the default value of {@link #getDefaultCertificationType}). Valid values are {@link FormSignature#CERTIFICATION_UNCERTIFIED none}, {@link FormSignature#CERTIFICATION_NOCHANGES nochanges}, {@link FormSignature#CERTIFICATION_ALLOWFORMS forms} or {@link FormSignature#CERTIFICATION_ALLOWCOMMENTS comments}. If the signature being applied is not the initial signature this is ignored</td></tr>
 * </table>
 * </div>
 *
 * @since 2.11
 */
public abstract class SignatureProvider extends ViewerFeature {

    private PDFViewer viewer;

    protected SignatureProvider(String name) {
        super(name);
    }

    public void initialize(PDFViewer viewer) {
        this.viewer = viewer;
        super.initialize(viewer);
    }

    /**
     * Return the {@link PDFViewer} set in {@link #initialize}
     */
    public final PDFViewer getViewer() {
        return viewer;
    }

    /**
     * Return the "user friendly" name of this SignatureProvider,
     * to use in dialogs and menus.
     */
    public abstract String getDisplayName();

    /**
     * Return the name of the entity signing the document
     * using the {@link SignatureProvider#showSignDialog showSignDialog()} method,
     * or <code>null</code> to not specify a default.
     */
    public String getDefaultName() {
        return getFeatureProperty(viewer, "name");
    }

    /**
     * Return the reason that the new signature is being applied
     * using the {@link SignatureProvider#showSignDialog showSignDialog()} method,
     * or <code>null</code> to not specify a default.
     */
    public String getDefaultReason() {
        return getFeatureProperty(viewer, "reason");
    }

    /**
     * Return the location of the new signature being applied
     * using the {@link SignatureProvider#showSignDialog showSignDialog()} method,
     * or <code>null</code> to not specify a default.
     */
    public String getDefaultLocation() {
        return getFeatureProperty(viewer, "location");
    }

    /**
     * Return the default type of certification for any new signatures
     * using the {@link SignatureProvider#showSignDialog showSignDialog()} method,
     * or -1 to not specify a default.
     * @return one of {@link FormSignature#CERTIFICATION_UNCERTIFIED}, {@link FormSignature#CERTIFICATION_NOCHANGES}, {@link FormSignature#CERTIFICATION_ALLOWFORMS}, {@link FormSignature#CERTIFICATION_ALLOWCOMMENTS}, or the value -1 to prompt the user (the default).
     */
    public int getDefaultCertificationType() {
        String certtype = getFeatureProperty(viewer, "certification");
        if ("none".equals(certtype)) {
            return FormSignature.CERTIFICATION_UNCERTIFIED;
        } else if ("nochanges".equals(certtype)) {
            return FormSignature.CERTIFICATION_NOCHANGES;
        } else if ("forms".equals(certtype)) {
            return FormSignature.CERTIFICATION_ALLOWFORMS;
        } else if ("annotations".equals(certtype)) {
            return FormSignature.CERTIFICATION_ALLOWCOMMENTS;
        } else {
            return -1;
        }
    }

    /**
     * Return true if this SignatureProvider can sign the specified field
     */
    public abstract boolean canSign(FormSignature field);

    /**
     * Return true if this SignatureProvider can verify the specified field
     */
    public abstract boolean canVerify(FormSignature field);

    /**
     * Display the signing dialog for the specified field, and assuming all goes well
     * sign the field at the end.
     * @param root the JCompoment the dialog should be relative to - typically this is the {@link DocumentPanel}
     * @param field the field to be signed
     */
    public abstract void showSignDialog(JComponent root, FormSignature field) throws IOException, GeneralSecurityException;

    /**
     * Show a dialog displaying information about the specified (signed) digital signature field.
     * The dialog should display the signatures verification state, which may be determined by this
     * method or retrieved from a previous verification
     * @param root the JCompoment the dialog should be relative to - typically this is the {@link DocumentPanel}
     * @param field the field to be verified
     */
    public abstract void showVerifyDialog(JComponent root, FormSignature field);

    /**
     * Verify the field. Must be overridden by any SignatureProvider that
     * returns true from {@link #canVerify canVerify()}. This method may
     * provide visual feedback to the user, but it's primary purpose is
     * to verify the field and return its state so it should not block
     * user progress unless it's unavoidable.
     *
     * @param root the component that should be used as a root for
     * @param field the signed field
     * @since 2.11.7
     */
    public SignatureState verify(JComponent root, FormSignature field) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get an Icon that can be used to describe the specified signature.
     */
    public static final ImageIcon getIcon(DocumentPanel docpanel, FormSignature field) {
        SignatureState state = getSignatureState(docpanel, field);
        if (state == null) {
            return (ImageIcon)UIManager.getIcon("PDFViewer.SignatureProvider.Unknown.icon");
        } else {
            return state.getIcon();
        }
    }

    private static final void selectProvider(final DocumentPanel docpanel, final FormSignature field, final JComponent comp, final Point point, final ActionListener listener, final boolean sign) {
        if (field.getState()==FormSignature.STATE_PENDING) {
            JOptionPane.showMessageDialog(docpanel, UIManager.getString("PDFViewer.PendingSignature"), UIManager.getString("PDFViewer.Alert"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JPopupMenu providermenu = new JPopupMenu();
        ViewerFeature[] features = docpanel.getViewer().getFeatures();
        int found = 0;
        SignatureProvider lastssp = null;
        for (int i = 0; i < features.length; i++) {
            Object feature = features[i];
            if (feature instanceof SignatureProvider) {
                final SignatureProvider ssp = (SignatureProvider)feature;
                if (sign ? ssp.canSign(field) : ssp.canVerify(field)) {
                    lastssp = ssp;
                    found++;
                    Action action = new AbstractAction() {
                        public void actionPerformed(ActionEvent e) {
                            listener.actionPerformed(new ActionEvent(ssp, e.getID(), sign ? "sign" : "verify"));
                        }
                    };
                    action.putValue(Action.NAME, ssp.getDisplayName());
                    providermenu.add(new JMenuItem(action));
                }
            }
        }
        if (found == 0) {
            JOptionPane.showMessageDialog(docpanel, UIManager.getString("PDFViewer.NoSignatureProvider"), UIManager.getString("PDFViewer.Alert"), JOptionPane.INFORMATION_MESSAGE);
        } else if (found == 1 || point == null) {
            listener.actionPerformed(new ActionEvent(lastssp, 0, sign ? "sign" : "verify"));
        } else {
            providermenu.show(comp, point.x, point.y);
        }
    }

    /**
     * <p>
     * Select a SignatureProvider that can be used to sign the specified signature field.
     * The <code>listener</code> parameter specifies an {@link ActionListener} which will be called with
     * the chosen provider - the <code>ActionEvent</code> it will be given will have the source set to
     * the chosen provider and the action type set to "sign".
     * </p><p>
     * If more than one SignatureProvider is available this method will show a dialog allowing the user to choose,
     * otherwise the the <code>listener</code> will be called without a dialog being displayed.
     * </p><p>
     * @param docpanel the DocumentPanel containing the PDF
     * @param field the field the user is requesting to sign
     * @param comp the Component the user has clicked on or selected to request the signing
     * @param point the position relative to <code>comp</code> that any dialog should be based around. If null, no dialog will be displayed, and hte first match will  be used
     * @param listener the ActionListener that should be called when the SignatureProvider is chosen
     */
    public static final void selectSignProvider(final DocumentPanel docpanel, final FormSignature field, final JComponent comp, final Point point, final ActionListener listener) {
        selectProvider(docpanel, field, comp, point, listener, true);
    }

    /**
     * <p>
     * Select a SignatureProvider that can be used to verify the specified signature field.
     * The <code>listener</code> parameter specifies an {@link ActionListener} which will be called with
     * the chosen provider - the {@link ActionEvent} it will be given will have the source set to the chosen
     * provider and the action type set to "verify".
     * </p><p>
     * If more than one SignatureProvider is available this method will show a dialog allowing the user to choose,
     * otherwise the the <code>listener</code> will be called without a dialog being displayed.
     * </p><p>
     * @param docpanel the DocumentPanel containing the PDF
     * @param field the field the user is requesting to verify
     * @param comp the Component the user has clicked on or selected to request the verification
     * @param point the position relative to <code>comp</code> that any dialog should be based around. If null, no dialog will be displayed, and hte first match will  be used
     * @param listener the ActionListener that should be called when the SignatureProvider is chosen
     */
    public static final void selectVerifyProvider(final DocumentPanel docpanel, final FormSignature field, final JComponent comp, final Point point, final ActionListener listener) {
        selectProvider(docpanel, field, comp, point, listener, false);
    }

    public List<Map.Entry<String,String>> getSummaryText(FormSignature field, DocumentPanel docpanel) {
        List<Map.Entry<String,String>> list = new ArrayList<Map.Entry<String,String>>();

        String fieldname = field.getDescription();
        if (fieldname == null) {
            fieldname = field.getForm().getName(field);
        }
        list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Field"), fieldname));

        String suffix = "1";
        if (field.getState() == FormSignature.STATE_SIGNED) {
            PKCS7SignatureHandler handler = null;
            try {
                handler = (PKCS7SignatureHandler)field.getSignatureHandler();
            } catch (Exception e) {};

            String revisionof = null;
            if (field.getForm().getPDF() != docpanel.getPDF()) {
                List<PDF> all = new ArrayList<PDF>();
                List<PDF> q = new ArrayList<PDF>();
                q.add(field.getForm().getPDF());
                while (q.size() > 0) {
                    PDF pdf = q.remove(q.size() - 1);
                    all.add(pdf);
                    for (EmbeddedFile ef : pdf.getEmbeddedFiles().values()) {
                        if (ef.hasPDF()) {
                            try {
                                q.add(ef.getPDF());
                            } catch (Exception e) {}    // Can't happen
                        }
                    }
                }
                int n = docpanel.getNumberOfPages();
                int start = 0, end = 0;
                for (int i=0;i<n;i++) {
                    if (all.contains(docpanel.getPage(i).getPDF())) {
                        if (start == 0) {
                            start = i + 1;
                        }
                        end = i + 1;
                    }
                }
                if (start == end) {
                    revisionof = Util.getUIString("PDFViewer.PageN", Integer.toString(start));
                } else {
                    revisionof = Util.getUIString("PDFViewer.PagesN", Integer.toString(start) + " - " + Integer.toString(end));
                    suffix = "N";
                }
            }
            int pdfc = field.getForm().getPDF().getNumberOfRevisions();
            int numc = field.getNumberOfRevisionsCovered();
            if (pdfc == numc) {
                if (revisionof == null) {
                    revisionof = Util.getUIString("PDFViewer.CurrentRevision");
                } else {
                    revisionof = Util.getUIString("PDFViewer.CurrentRevisionOf" + suffix, revisionof);
                }
            } else if (numc + 1 == pdfc) {
                if (revisionof == null) {
                    revisionof = Util.getUIString("PDFViewer.PreviousRevision");
                } else {
                    revisionof = Util.getUIString("PDFViewer.PreviousRevisionOf" + suffix, revisionof);
                }
            } else {
                if (revisionof == null) {
                    revisionof = Util.getUIString("PDFViewer.nRevisionsAgo", Integer.toString(numc), Integer.toString(pdfc));
                } else {
                    revisionof = Util.getUIString("PDFViewer.nRevisionsAgoOf" + suffix, Integer.toString(numc), Integer.toString(pdfc), revisionof);
                }
            }
            list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Signing"), revisionof));

            if (field.getSignDate() != null) {
                Date d = field.getSignDate().getTime();
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZZ"); // DateFormat.getDateTimeInstance();
                list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Date"), df.format(d)));
            }
            if (handler != null) {
                KeyStoreManager ksm = viewer == null ? null : viewer.getKeyStoreManager();
                try {
                    X509Certificate[] certs = handler.getTimeStampCertificates();
                    if (certs != null) {
                        for (int i=0;i<certs.length;i++) {
                            String sub = certs[i].getSubjectX500Principal().toString();
                            list.add(new AbstractMap.SimpleEntry<String,String>("• " + Util.getUIString("PDFViewer.VerifiedBy"), sub));
                            if (ksm != null && ksm.contains(certs[i])) {
                                list.add(new AbstractMap.SimpleEntry<String,String>("• " + Util.getUIString("PDFViewer.VerifiedBy"), Util.getUIString("PDFViewer.Keystore")));
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    list.add(new AbstractMap.SimpleEntry<String,String>("• " + Util.getUIString("PDFViewer.VerifiedBy"), Util.getUIString("PDFViewer.ssig.ErrorVerifyingTimestamp", e.getMessage())));
                }
                try {
                    X509Certificate[] certs = handler.getCertificates();
                    for (int i=0;i<certs.length;i++) {
                        String sub = certs[i].getSubjectX500Principal().toString();
                        if (i == 0) {
                            list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.SignedBy"), sub));
                        } else {
                            list.add(new AbstractMap.SimpleEntry<String,String>("• " + Util.getUIString("PDFViewer.VerifiedBy"), sub));
                        }
                        if (ksm != null && ksm.contains(certs[i])) {
                            list.add(new AbstractMap.SimpleEntry<String,String>("• " + Util.getUIString("PDFViewer.VerifiedBy"), Util.getUIString("PDFViewer.Keystore")));
                            break;
                        }
                    }
                } catch (Exception e) {
                }
            }
            if (field.getName() != null) {
                list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Name"), field.getName()));
            }
            if (field.getReason() != null) {
                list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Reason"), field.getReason()));
            }
            if (field.getLocation() != null) {
                list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Location"), field.getLocation()));
            }
            switch (field.getCertificationType()) {
                case FormSignature.CERTIFICATION_UNCERTIFIED:
//                    list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Certification"), Util.getUIString("PDFViewer.cert.Uncertified")));
                    break;
                case FormSignature.CERTIFICATION_NOCHANGES:
                    list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Certification"), Util.getUIString("PDFViewer.cert.NoChanges")));
                    break;
                case FormSignature.CERTIFICATION_ALLOWFORMS:
                    list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Certification"), Util.getUIString("PDFViewer.cert.ModifyForms")));
                    break;
                case FormSignature.CERTIFICATION_ALLOWCOMMENTS:
                    list.add(new AbstractMap.SimpleEntry<String,String>(Util.getUIString("PDFViewer.Certification"), Util.getUIString("PDFViewer.cert.ModifyComments")));
                    break;
            }
        }
        return list;
    }


    //--------------------------------------------------------------------------------------------

    /**
     * A SignatureState contains information about a {@link FormSignature} once it's been verified.
     * This is used to display information about the signatures in the dialog displayed
     * by {@link #showVerifyDialog showVerifyDialog()}, and to determine which Icon to display on
     * any visual representation of the Signature in the PDF (see {@link SignatureProvider#getIcon}).
     * Subclasses of SignatureState may extend this class to store additional information if necessary.
     */
    public class SignatureState {
        private final WeakReference<FormSignature> sig;
        private Boolean validity;
        private String reason;
        private boolean alteredsince;
        private Exception exception;

        /**
         * Create a new SignatureState
         * @param sig the signature
         * @param validity {@link Boolean#TRUE}, {@link Boolean#FALSE} or null to indicate the signature is valid, invalid or hasn't be validated
         * @param reason the reason for signing
         * @param alteredsince whether the PDF has been altered since the signature was applied
         * @param exception the exception encountered during validation, or null if it succeeded
         */
        public SignatureState(FormSignature sig, Boolean validity, String reason, boolean alteredsince, Exception exception) {
            this.validity = validity;
            this.reason = reason;
            this.alteredsince = alteredsince;
            this.exception = exception;
            this.sig = new WeakReference<FormSignature>(sig);
        }

        /**
         * Return the validity of the Signature. {@link Boolean#TRUE} for valid, {@link Boolean#FALSE} for invalid
         * or <code>null</code> for unknown validity.
         */
        public Boolean getValidity() {
            return validity;
        }

        /**
         * Return the descriptive text describing this state
         */
        public String getReason() {
            return reason;
        }

        /**
         * Return true of the PDF has been altered since the signature was applied.
         * Only useful if {@link #getValidity} returns True.
         */
        public boolean isAlteredSince() {
            return alteredsince;
        }

        /**
         * Return the Exception that occurred when trying to verify the signature or
         * certificate, or <code>null</code> if none was thrown.
         */
        public Exception getException() {
            return exception;
        }

        /**
         * Return the signature itself
         */
        public FormSignature getSignature() {
            return sig.get();
        }

        /**
         * Return the {@link SignatureProvider} that verified the Signature and
         * created this SignatureState object.
         */
        public SignatureProvider getSignatureProvider() {
            return SignatureProvider.this;
        }

        /**
         * Return an {@link Icon} that visually represents the state of the signature. This
         * will be displayed in the {@link DocumentPanel} and in the dialog displayed by
         * {@link #showVerifyDialog showVerifyDialog()}
         */
        public ImageIcon getIcon() {
            Icon icon;
            if (getValidity() == null) {
                icon = UIManager.getIcon("PDFViewer.SignatureProvider.Unknown.icon");
            } else if (getValidity().booleanValue() == false) {
                icon = UIManager.getIcon("PDFViewer.SignatureProvider.Invalid.icon");
            } else {
                int type = getSignature().getCertificationType();
                if (type == FormSignature.CERTIFICATION_UNCERTIFIED) {
                    icon = UIManager.getIcon("PDFViewer.SignatureProvider.Valid.icon");
                } else {
                    icon = UIManager.getIcon("PDFViewer.SignatureProvider.Certified.icon");
                }
            }
            return (ImageIcon)icon;
        }
    }

    /**
     * Get a previously determined {@link SignatureState} for the specified signature field, as set by
     * {@link #setSignatureState setSignatureState()}. If this method returns
     * <code>null</code> then the signature has not been verified yet.
     *
     * @param docpanel the DocumentPanel containing the signature
     * @param field the FormSignature whose state is being checked
     * @since 2.11.7
     */
    public static final SignatureState getSignatureState(final DocumentPanel docpanel, final FormSignature field) {
        final SignatureState[] stateholder = new SignatureState[1];
        Runnable r = new Runnable() {
            public void run() {
                @SuppressWarnings("unchecked") WeakHashMap<FormSignature,SignatureProvider.SignatureState> map = (WeakHashMap<FormSignature,SignatureProvider.SignatureState>)docpanel.getClientProperty("bfo.signatureState");
                if (map != null) {
                    stateholder[0] = map.get(field);
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (InvocationTargetException e) {
                throw (RuntimeException)e.getTargetException();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
        return stateholder[0];
    }

    /**
     * Set the {@link SignatureState} of this field - should be called by the {@link #showVerifyDialog showVerifyDialog()}
     * method after the field has been verified, to save the details of the verification.
     * This method may be called in any thread, but it will fire the "stateChanged"
     * {@link DocumentPanelEvent} on the Swing Event Dispatch Thread.
     *
     * @param docpanel the DocumentPanel containing the signature
     * @param field the FormSignature that was verified
     * @param state the state of the signature
     */
    public static final void setSignatureState(final DocumentPanel docpanel, final FormSignature field, final SignatureState state) {
        Runnable r = new Runnable() {
            public void run() {
                @SuppressWarnings("unchecked") WeakHashMap<FormSignature,SignatureProvider.SignatureState> map = (WeakHashMap<FormSignature,SignatureProvider.SignatureState>)docpanel.getClientProperty("bfo.signatureState");
                if (map == null) {
                    docpanel.putClientProperty("bfo.signatureState", map = new WeakHashMap<FormSignature,SignatureProvider.SignatureState>());
                }
                if (state == null) {
                    map.remove(field);
                } else {
                    map.put(field, state);
                }
                docpanel.raiseDocumentPanelEvent(DocumentPanelEvent.createStateChanged(docpanel, state));
                docpanel.repaint();
                // Add a listener to the dirty event for this PDF, reset the validity if we hear it.
                PropertyChangeListener pcl = new SigStateResetPropertyChangeListener(field);
                if (!Arrays.asList(docpanel.getPropertyChangeListeners()).contains(pcl)) {
                    docpanel.addPropertyChangeListener(pcl);
                }
                docpanel.addPropertyChangeListener(pcl);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (InvocationTargetException e) {
                throw (RuntimeException)e.getTargetException();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * If the PDF is changed in any way, we need to reset the validity of the signatures.
     */
    private static class SigStateResetPropertyChangeListener implements PropertyChangeListener {
        final FormSignature field;
        SigStateResetPropertyChangeListener(FormSignature field) {
            this.field = field;
        }
        public void propertyChange(PropertyChangeEvent e) {
            if (e.getPropertyName().equals("pdfContentChanged")) {
                DocumentPanel docpanel = (DocumentPanel)e.getSource();
                setSignatureState(docpanel, field, null);
            }
        }
        public int hashCode() {
            return field.hashCode();
        }
        public boolean equals(Object o) {
            return o instanceof SigStateResetPropertyChangeListener && ((SigStateResetPropertyChangeListener)o).field == field;
        }
    }
}
