// $Id: KeyStoreSignatureProvider.java 41702 2021-11-11 19:26:00Z mike $

package org.faceless.pdf2.viewer2.feature;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.net.URL;
import java.text.*;
import java.awt.event.*;
import javax.crypto.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.X509Certificate;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.DialogPanel;
import org.faceless.pdf2.viewer2.util.KeyStoreAliasList;


/**
 * <p>
 * A {@link SignatureProvider} that signs and verifies signatures from
 * a {@link KeyStoreManager} (ie from a local {@link KeyStore}.
 * It can verify signatures using any {@link PKCS7SignatureHandler}, and it
 * can sign PDFs using any {@link AcrobatSignatureHandlerFactory}.
 * </p><p>
 * By default this class will prompt the user for the name, reason etc., and
 * let the user choose a private key from the {@link KeyStore} to sign the PDF.
 * Developers who want to pre-set this information can do so by specifying the
 * values as <a href="../doc-files/initparams.html">initialization parameters</a>
 * for the PDFViewer.
 * </p>
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature, as well as those parameters specified in the {@link SignatureProvider} API documentation.
 * <table summary="">
 * <tr><th>alias</th><td>The default value returned by the {@link #getDefaultAlias} method</td></tr>
 * <tr><th>password</th><td>The default value returned by the {@link #getDefaultPassword} method</td></tr>
 * <tr><th>digestAlgorithm</th><td>The default value returned by the {@link #getDefaultDigestAlgorithm} method</td></tr>
 * <tr><th>timeStampServer</th><td>The default value returned by the {@link #getDefaultTimeStampServer} method</td></tr>
 * <tr><th>contentSize</th><td>The default value returned by the {@link #getDefaultContentSize} method</td></tr>
 * </table>
 * </div>
 *
 * <p>
 * As an example, when deploying the PDFViewer here's how to
 * ensure every signature applied with this class has the location specified is
 * cryptographically time-stamped using an RFC 3161 server. To save making two requests to the
 * time-stamp server, we're pre-allocating 8KB to store the PKCS#7 signature in the PDF. The
 * {@link AcrobatSignatureHandlerFactory} class has more information on these parameters.
 * </p>
 * <pre class="brush:shell">
 *  java -Dorg.faceless.pdf2.viewer2.feature.KeyStoreSignatureProvider.location="Signed using demo application" \
 *       -Dorg.faceless.pdf2.viewer2.feature.KeyStoreSignatureProvider.timeStampServer="https://tsa.aloaha.com/" \
 *       -Dorg.faceless.pdf2.viewer2.feature.KeyStoreSignatureProvider.contentSize=8192 \
 *       org.faceless.pdf2.viewer2.PDFViewer
 * </pre>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">KeyStoreSignatureProvider</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see AcrobatSignatureHandlerFactory
 * @see PKCS7SignatureHandler
 * @see KeyStoreManager
 *
 * @since 2.11
 */
public class KeyStoreSignatureProvider extends SignatureProvider {

    private KeyStoreManager keyStoreManager;
    private SignatureHandlerFactory factory;

    public KeyStoreSignatureProvider() {
        super("KeyStoreSignatureProvider");
    }

    public String getDisplayName() {
        return "Local KeyStore";
    }

    /**
     * Set the {@link SignatureHandlerFactory} used to sign fields using
     * this class.
     * @param factory the SignatureHandlerFactory to use when signing
     */
    public void setSignatureHandlerFactory(SignatureHandlerFactory factory) {
        this.factory = factory;
    }

    /**
     * Get the {@link SignatureHandlerFactory} used to sign fields using
     * this class. This is either set by {@link #setSignatureHandlerFactory setSignatureHandlerFactory()},
     * or a new {@link AcrobatSignatureHandlerFactory} which has
     * been initialized using the {@link #getDefaultContentSize},
     * {@link #getDefaultDigestAlgorithm} and {@link #getDefaultTimeStampServer}
     * methods.
     */
    protected SignatureHandlerFactory getSignatureHandlerFactory() {
        if (this.factory != null) {
            return this.factory;
        } else {
            AcrobatSignatureHandlerFactory factory = new AcrobatSignatureHandlerFactory();
            if (getDefaultContentSize() != 0) {
                factory.setContentSize(getDefaultContentSize());
            }
            if (getDefaultDigestAlgorithm() != null) {
                factory.setDigestAlgorithm(getDefaultDigestAlgorithm());
            }
            if (getDefaultTimeStampServer() != null) {
                factory.setTimeStampServer(getDefaultTimeStampServer());
            }
            return factory;
        }
    }

    /**
     * Return the {@link KeyStoreManager} used by this class - either the value returned by
     * {@link PDFViewer#getKeyStoreManager} (the default) or a value previously set by a call
     * to {@link #setKeyStoreManager setKeyStoreManager()}.
     */
    public KeyStoreManager getKeyStoreManager() {
        return keyStoreManager == null ? getViewer().getKeyStoreManager() : keyStoreManager;
    }

    /**
     * Set the {@link KeyStoreManager} used by this class, which will override the default.
     * @param keyStoreManager the KeyStoreManager to use, or <code>null</code> to use the default.
     */
    public void setKeyStoreManager(KeyStoreManager keyStoreManager) {
        this.keyStoreManager = keyStoreManager;
    }

    public boolean canSign(FormSignature field) {
        return true;
    }

    public boolean canVerify(FormSignature field) {
        return field.getSignatureHandler() instanceof PKCS7SignatureHandler;
    }

    /**
     * Return the KeyStore alias to use when signing a PDF using this SignatureProvider.
     * By default this method checks the "alias" {@link #getFeatureProperty feature property}
     * for this class - if specified, it must be a valid alias from the KeyStore,
     * and the user won't be prompted to select one from the list. You will almost
     * certainly want to specify the {@link #getDefaultPassword password} too.
     * @return the alias to use from the KeyStore, or <code>null</code> to let the user select one
     * from the KeyStore.
     */
    public String getDefaultAlias() {
        return getFeatureProperty(getViewer(), "alias");
    }

    /**
     * Return the password to use when signing a PDF using this SignatureProvider.
     * @see #getDefaultAlias
     * @return the password to use to unlock the alias returned by {@link #getDefaultAlias},
     * or <code>null</code> to let the user enter one.
     */
    public char[] getDefaultPassword() {
        String x = getFeatureProperty(getViewer(), "password");
        return x == null ? null : x.toCharArray();
    }

    /**
     * Return the URL of an RFC 3161 TimeStamp server to be used by the default {@link AcrobatSignatureHandlerFactory}.
     * See that class for more information.
     * @return the URL of an RFC 3161 TimeStamp server, or <code>null</code> not to specify one.
     */
    public URL getDefaultTimeStampServer() {
        String x = getFeatureProperty(getViewer(), "timeStampServer");
        try {
            return new URL(x);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Return the Digest Algorithm to be used by the default {@link AcrobatSignatureHandlerFactory}.
     * See that class for more information.
     * @return the digest algorithm to use, or <code>null</code> for the factory default.
     */
    public String getDefaultDigestAlgorithm() {
        return getFeatureProperty(getViewer(), "digestAlgorithm");
    }

    /**
     * Return the "Content Size" to be used by the default {@link AcrobatSignatureHandlerFactory}.
     * See that class for more information.
     * @return the contet size to pass to the factory, or <code>0</code> for the factory default.
     */
    public int getDefaultContentSize() {
        String x = getFeatureProperty(getViewer(), "contentSize");
        return x == null ? 0 : Integer.parseInt(x);
    }

    public void showSignDialog(JComponent root, FormSignature field) throws IOException, GeneralSecurityException {
        String alias = getDefaultAlias();
        char[] password = getDefaultPassword();
        String name = getDefaultName();
        String reason = getDefaultReason();
        String location = getDefaultLocation();
        int certificationType = getDefaultCertificationType();

        final KeyStoreAliasList aliaslist = alias == null ? new KeyStoreAliasList(getKeyStoreManager(), true, false) : null;
        DialogPanel panel = new DialogPanel() {
            public String validateDialog() {
                if (aliaslist != null && aliaslist.getSelectedValue() == null) {
                    return "Please choose a Key";
                } else {
                    return null;
                }
            }
        };


        JTextField namefield = new JTextField(name);
        JTextField reasonfield = new JTextField(reason);
        JTextField locationfield = new JTextField(location);
        JComboBox<String> certificationfield = null;
        JPasswordField passwordfield = null;

        if (password == null && getKeyStoreManager().isKeyPasswordUsed()) {
            passwordfield = new JPasswordField();
        }
        if (name != null) {
            namefield.setEditable(false);
        }
        if (reason != null) {
            reasonfield.setEditable(false);
        }
        if (location != null) {
            locationfield.setEditable(false);
        }

        if (field.getForm().getPDF().getBasicOutputProfile().isSet(OutputProfile.Feature.DigitallySigned)) {
            certificationType = FormSignature.CERTIFICATION_UNCERTIFIED;
        }
        if (certificationType == -1) {
            String[] values = {
                UIManager.getString("PDFViewer.cert.Uncertified"),
                UIManager.getString("PDFViewer.cert.NoChanges"),
                UIManager.getString("PDFViewer.cert.ModifyForms"),
                UIManager.getString("PDFViewer.cert.ModifyComments"),
            };
            certificationfield = new JComboBox<String>(values);
        }

        if (aliaslist != null) {
            JViewport vp = new JViewport();
            vp.add(aliaslist);
            panel.addComponent(vp);
        }
        if (passwordfield != null) {
            panel.addComponent("Password", passwordfield);
        }
        panel.addComponent("Name", namefield);
        panel.addComponent("Reason", reasonfield);
        panel.addComponent("Location", locationfield);
        if (certificationfield != null) {
            panel.addComponent("Certification", certificationfield);
        }

        if ((name != null && alias != null && reason != null && location != null && password != null && certificationType != -1) || panel.showDialog(root, UIManager.getString("PDFViewer.SignAndCertify"))) {
            if (aliaslist != null) {
                alias = (String)aliaslist.getSelectedValue();
            }
            if (passwordfield != null) {
                password = passwordfield.getPassword();
            }
            if (namefield != null) {
                name = namefield.getText();
            }
            if (reasonfield != null) {
                reason = reasonfield.getText();
            }
            if (locationfield != null) {
                location = locationfield.getText();
            }
            if (certificationfield != null) {
                certificationType = certificationfield.getSelectedIndex();
            }
            Rectangle2D.Float bounds = getAnnotationBounds(field);
            KeyStoreManager manager = getKeyStoreManager();
            KeyStore keystore = manager.getKeyStore();
            SignatureHandlerFactory factory = getSignatureHandlerFactory();
            if (bounds != null && factory instanceof AcrobatSignatureHandlerFactory) {
                PDFCanvas ap = getSignatureAppearance(manager, keystore, alias, bounds.width, bounds.height);
                if (ap != null) {
                    ((AcrobatSignatureHandlerFactory) factory).setCustomAppearance(ap, bounds.x, bounds.y, bounds.width, bounds.height);
                }
            }
            field.sign(keystore, alias, password, factory);
            field.setCertificationType(certificationType, null);

            if (reason.length() > 0) {
                field.setReason(reason);
            }
            if (location.length() > 0) {
                field.setLocation(location);
            }
            if (name.length() == 0) {
                SignatureHandler signatureHandler = field.getSignatureHandler();
                name = signatureHandler.getDefaultName();
            }
            if (name != null && name.length() > 0) {
                field.setName(name);
            }
        }
    }

    /**
     * Return the PDFCanvas to be used as a SignatureAppearance for this
     * signature, or null to use the default
     * @param manager the KeyStoreManager
     * @param keystore the KeyStore
     * @param alias the alias being used
     * @param width the width of the annotation
     * @param height the height of the annotation
     * @since 2.11.25
     */
    protected PDFCanvas getSignatureAppearance(KeyStoreManager manager, KeyStore keystore, String alias, float width, float height) {
        String pathalias = alias + SignatureCapture.PATHSUFFIX;
        try {
            if (keystore.containsAlias(pathalias)) {
                SecretKey seckey = manager.getSecret(pathalias, SignatureCapture.KEYALGORITHM, SignatureCapture.SECRETKEYPASSWORD);
                if (seckey != null) {
                    byte[] pathbytes = seckey.getEncoded();
                    if (pathbytes != null && pathbytes.length > 0) {
                        Shape sigpath = SignatureCapture.readPath(pathbytes);
                        if (sigpath != null) {
                            Rectangle2D sigbounds = sigpath.getBounds2D();
                            int border = 6;
                            PDFCanvas ap = new PDFCanvas((float)sigbounds.getWidth() + border + border, (float)sigbounds.getHeight() + border+border);
                            PDFStyle style = new PDFStyle();
                            style.setLineColor(Color.black);
                            ap.setStyle(style);
                            double x = -sigbounds.getMinX() + border;
                            double y = sigbounds.getHeight() + sigbounds.getMinY() + border;
                            sigpath = new AffineTransform(1, 0, 0, -1, x, y).createTransformedShape(sigpath);

                            ap.drawShape(sigpath);
                            ap.flush();
                            return ap;
                        }
                    }
                }
            }
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Rectangle2D.Float getAnnotationBounds(FormElement field) {
        Rectangle2D.Float ret = null;
        List<WidgetAnnotation> annots = field.getAnnotations();
        if (annots.size() > 0) {
            WidgetAnnotation annot = annots.get(0);
            float[] r = annot.getRectangle();
            if (r == null) {
                // Shouldn't happen, but have seen it
                r = new float[4];
            }
            ret = new Rectangle2D.Float(r[0], r[1], r[2]-r[0], r[3]-r[1]);
        }
        return ret;
    }

    public SignatureState verify(JComponent root, FormSignature field) {
        boolean verified = false;
        boolean alteredsince = false;
        X509Certificate invalidcert = null;
        X509Certificate signingcert = null;
        Exception exception = null;
        Calendar when = field.getSignDate();
        String reason = null;
        Boolean validity = null;

        try {
            verified = field.verify();
        } catch (Exception e) {
            validity = Boolean.FALSE;
            reason = Util.getUIString("PDFViewer.ssig.ErrorVerifyingSig", e.toString());
            exception = e;
        }

        if (field.getNumberOfRevisionsCovered() == 0) {
            validity = Boolean.FALSE;
            reason = UIManager.getString("PDFViewer.ssig.NotEntireRevision");
        } 
        try {
            X509Certificate[] certs = null;
            SignatureHandler handler = field.getSignatureHandler();
            if (handler instanceof PKCS7SignatureHandler) {
                certs = ((PKCS7SignatureHandler) handler).getCertificates();
            } else if (handler instanceof EGIZSignatureHandler) {
                certs = ((EGIZSignatureHandler) handler).getCertificates();
            }
            if (certs != null) {
                signingcert = certs[0];
                invalidcert = FormSignature.verifyCertificates(certs, getKeyStoreManager().getKeyStore(), null, when);
            }
            if (field.getNumberOfRevisionsCovered() != field.getForm().getPDF().getNumberOfRevisions()) {
                alteredsince = true;
            }
            if (validity == null) {
                if (verified && invalidcert == null) {
                    validity = Boolean.TRUE;
                    reason = UIManager.getString("PDFViewer.ssig.DocAndCertVerified");
                } else if (verified && invalidcert != null) {
                    validity = Boolean.TRUE;
                    reason = UIManager.getString("PDFViewer.ssig.UnableToVerifyCerts");
                } else {
                    validity = Boolean.FALSE;
                    reason = UIManager.getString("PDFViewer.ssig.AlteredSinceSigning");
                }
            }
        } catch (Exception e) {
            validity = Boolean.FALSE;
            reason = Util.getUIString("PDFViewer.ssig.ErrorVerifyingCerts", e.getMessage());
            exception = e;
        }
        return new X509SignatureState(field, validity, reason, alteredsince, exception, signingcert, invalidcert);
    }

    public void showVerifyDialog(JComponent jroot, FormSignature field) {
        DocumentPanel root = (DocumentPanel)jroot;
        X509SignatureState state = (X509SignatureState)getSignatureState(root, field);
        if (state == null) {
            state = (X509SignatureState)verify(jroot, field);
            setSignatureState(root, field, state);
        }

        JTabbedPane tabbedpane = new JTabbedPane(JTabbedPane.TOP);
        tabbedpane.addTab(UIManager.getString("PDFViewer.Summary"), getSignatureStatePanel(state, root));
        tabbedpane.addTab(UIManager.getString("PDFViewer.Timestamp"), getTimestampPanel(state, root));
        try {
            X509Certificate[] certs = null;
            SignatureHandler handler = field.getSignatureHandler();
            if (handler instanceof PKCS7SignatureHandler) {
                certs = ((PKCS7SignatureHandler) handler).getCertificates();
            } else if (handler instanceof EGIZSignatureHandler) {
                certs = ((EGIZSignatureHandler) handler).getCertificates();
            }
            if (certs != null) {
                tabbedpane.addTab(UIManager.getString("PDFViewer.Certificates"), getCertificatesPanel(state, root, certs, state.getCertificate(), tabbedpane));
            }
        } catch (Exception e) { }

        DialogPanel panel = new DialogPanel();
        panel.addButton("cancel", null, null, null);
        panel.addComponent(tabbedpane);
        String name = field.getDescription();
        if (name == null) {
            name = field.getForm().getName(field);
        }
        panel.showDialog(root, name);
    }

    /**
     * Return a JComponent that contains information about the SignatureState.
     * This method is used internally by the {@link #showVerifyDialog showVerifyDialog()}
     * method, and there's generally no reason to call it directly.
     * @param state the X509SignatureState to display
     * @param root the DocumentPanel containing the PDF
     * @return the panel to be added to the Signature Information dialog
     */
    protected JComponent getSignatureStatePanel(X509SignatureState state, DocumentPanel root) {
        String title, reason;
        FormSignature field = state.getSignature();

        if (state.getValidity() == null) {
            title = UIManager.getString("PDFViewer.ssig.UnknownValidity");
            reason = state.getReason();
        } else if (state.getValidity().equals(Boolean.FALSE)) {
            title = UIManager.getString("PDFViewer.ssig.InvalidSignature");
            reason = state.getReason();
        } else {
            int type = field.getCertificationType();
            if (type == FormSignature.CERTIFICATION_UNCERTIFIED) {
                if (state.isAlteredSince()) {
                    title = UIManager.getString("PDFViewer.ssig.ValidSignatureAlt");
                } else {
                    title = UIManager.getString("PDFViewer.ssig.ValidSignature");
                }
            } else {
                if (state.isAlteredSince()) {
                    title = UIManager.getString("PDFViewer.ssig.DocumentCertifiedAlt");
                } else {
                    title = UIManager.getString("PDFViewer.ssig.DocumentCertified");
                }
            }
            if (state.getCertificate() != null) {
                String name = "unknown";
                try {
                    String z = FormSignature.getSubjectField(state.getCertificate(), "CN");
                    if (z == null) {
                        z = FormSignature.getSubjectField(state.getCertificate(), "O");
                    }
                    name = "\""+z+"\"";
                } catch (Exception e) {
                    // Ignore
                }
                reason = Util.getUIString("PDFViewer.ssig.SigNotCertVerified", name);
            } else {
                reason = UIManager.getString("PDFViewer.ssig.SigCertBothVerified");
            }
        }

        GridBagConstraints all = new GridBagConstraints();
        GridBagConstraints key = new GridBagConstraints();
        GridBagConstraints val = new GridBagConstraints();
        JPanel body = new JPanel(new GridBagLayout());

        all.weightx = 0.5;
        all.fill = key.fill = val.fill = GridBagConstraints.HORIZONTAL;
        key.weighty = all.weighty = val.weighty = 0;
        key.gridx = all.gridx = 0;
        all.gridwidth = 3;
        key.gridwidth = 1;
        key.insets = new Insets(0, 0, 0, 20);
        val.gridx = 1;
        val.gridwidth = GridBagConstraints.REMAINDER;

        JLabel label = new JLabel(title);
        label.setIcon(state.getIcon());
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(new EmptyBorder(0,0,12,0));
        body.add(label, all);

        all.weighty = 1;
        label = new JLabel(reason);
        label.setBorder(new EmptyBorder(0,0,12,0));
        body.add(label, all);

        for (Map.Entry<String,String> e : getSummaryText(field, root)) {
            body.add(new JLabel(e.getKey()), key);
            body.add(new JLabel(e.getValue()), val);
        }
        Insets i = UIManager.getInsets("PDFViewer.Feature.KeyStoreSignatureProvider.margin");
        if (i != null) {
            body.setBorder(BorderFactory.createEmptyBorder(i.top, i.left, i.bottom, i.right));
        }
        return body;
    }

    /**
     * Return a JComponent that contains information about the X.509 certificates used in the signature.
     * This method is used internally by the {@link #getSignatureStatePanel getSignatureStatePanel()}
     * method, and there's generally no reason to call it directly.
     * @param state the X509SignatureState
     * @param root the DocumentPanel containing the PDF
     * @param certs the chain of X.509 Certificates that signed the PDF, for display
     * @param cert the X.509 certificate that signed the PDF, but is untrusted. If the certificate is trusted
     * this parameter should be null
     * @param tabbedpane the JTabbedPane to add the panel to.
     * @return the panel to be added to the Signature Information dialog
     */
    protected JComponent getCertificatesPanel(final X509SignatureState state, final DocumentPanel root, final X509Certificate[] certs, X509Certificate cert, final JTabbedPane tabbedpane)
        throws CertificateException
    {

        Vector<String> v = new Vector<String>();
        for (int i=0;i<certs.length;i++) {
            String name = FormSignature.getSubjectField(certs[i], "CN");
            if (name == null) {
                name = FormSignature.getSubjectField(certs[i], "O");
            }
            v.add(name);
        }

        final JList<String> list = new JList<String>(v);
        JScrollPane leftscroll = new JScrollPane(list);
        leftscroll.setPreferredSize(new Dimension(200, 200));

        final JPanel right = new JPanel(new BorderLayout());
        final JScrollPane rightscroll = new JScrollPane(new JPanel());
        rightscroll.setPreferredSize(new Dimension(250, 200));
        right.add(rightscroll, BorderLayout.CENTER);

        if (cert != null) {
            final JButton trustbutton = new JButton(UIManager.getString("PDFViewer.ssig.TrustCertificate"));
            trustbutton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    try {
                        KeyStoreManager ksm = getKeyStoreManager();
                        ksm.importCertificate(null, certs[list.getSelectedIndex()]);
                        ksm.saveKeyStore();
                        X509SignatureState newstate = (X509SignatureState)verify(root, state.getSignature());
                        setSignatureState(root, state.getSignature(), newstate);
                        tabbedpane.setComponentAt(0, getSignatureStatePanel(newstate, root));
                        trustbutton.setEnabled(false);
                    } catch (Exception e) {
                        Util.displayThrowable(e, root);
                    }
                }
            });
            right.add(trustbutton, BorderLayout.SOUTH);
        }

        JSplitPane splitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftscroll, right);
        list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent event) {
                rightscroll.setViewportView(getCertificatePanel(certs[list.getSelectedIndex()]));
            }
        });
        list.setSelectedIndex(0);
        return splitpane;
    }

    /**
     * Return a JComponent that contains information about a single X.509 certificate.
     * This method is used internally by the {@link #getSignatureStatePanel getSignatureStatePanel()}
     * method, and there's generally no reason to call it directly.
     * @param cert the Certificate to display
     * @return the component to be displayed
     */
    protected JComponent getCertificatePanel(X509Certificate cert) {
        JTextArea field = new JTextArea();
        field.setText(cert.toString());
        field.setCaretPosition(0);  // Scroll to top
        return field;
    }

    /**
     * Return a JComponent that contains information about the timestamp of the signature. For
     * Signatures not timestamped according to RFC3161, this panel will simply display the
     * signature time from the computer clock.
     * This method is used internally by the {@link #getSignatureStatePanel getSignatureStatePanel()}
     * method, and there's generally no reason to call it directly.
     * @param state the X509SignatureState
     * @param root the DocumentPanel containing the PDF
     */
    protected JComponent getTimestampPanel(X509SignatureState state, DocumentPanel root) {
        final FormSignature field = state.getSignature();
        JPanel panel = new JPanel(new BorderLayout());
        try {
            Calendar when = field.getSignDate();
            SignatureHandler handler = field.getSignatureHandler();
            X509Certificate[] certs = null;
            if (handler instanceof PKCS7SignatureHandler) {
                certs = ((PKCS7SignatureHandler)handler).getTimeStampCertificates();
            }
            if (certs == null) {
                panel.add(new JLabel(UIManager.getString("PDFViewer.ssig.DateFromComputer")), BorderLayout.NORTH);
            } else {
                String issuer = null;
                X509Certificate badcert = null;
                issuer = FormSignature.getSubjectField(certs[0], "CN");
                badcert = FormSignature.verifyCertificates(certs, getKeyStoreManager().getKeyStore(), null, when);
                String validity;
                if (badcert == null) {
                    validity = Util.getUIString("PDFViewer.ssig.DateGuaranteedKnown", issuer);
                } else {
                    validity = Util.getUIString("PDFViewer.ssig.DateGuaranteedUnknown", issuer);
                }
                panel.add(new JLabel(validity), BorderLayout.NORTH);
                try {
                    panel.add(getCertificatesPanel(state, root, certs, badcert, null), BorderLayout.CENTER);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            panel.add(new JLabel(Util.getUIString("PDFViewer.ssig.ErrorVerifyingTimestamp", e.getMessage())), BorderLayout.NORTH);
        }
        Insets i = UIManager.getInsets("PDFViewer.Feature.KeyStoreSignatureProvider.margin");
        if (i != null) {
            panel.setBorder(BorderFactory.createEmptyBorder(i.top, i.left, i.bottom, i.right));
        }
        return panel;
    }

    //--------------------------------------------------------------------------------------

    /**
     * A subclass of SignatureState that references an X.509 Certificate.
     * This class is used internally by the KeyStoreSignatureProvider and there's no need to
     * reference it directly unless you're extending it.
     */
    public class X509SignatureState extends SignatureState {
        private final X509Certificate signingcert, invalidcert;

        /**
         * Create a new X509SignatureState
         * @param sig the signature
         * @param validity {@link Boolean#TRUE}, {@link Boolean#FALSE} or null to indicate the signature is valid, invalid or hasn't be validated
         * @param reason the reason for signing
         * @param alteredsince whether the PDF has been altered since the signature was applied
         * @param exception the exception encountered during verification, or null if it succeeded
         * @param signingcert the X.509 Certificate from the signature used to sign
         * @param invalidcert the X.509 Certificate from the signature that failed to verify
         */
        public X509SignatureState(FormSignature sig, Boolean validity, String reason, boolean alteredsince, Exception exception, X509Certificate signingcert, X509Certificate invalidcert) {
            super(sig, validity, reason, alteredsince, exception);
            this.signingcert = signingcert;
            this.invalidcert = invalidcert;
        }

        /**
         * Calls {@link #getUnverifiableCertificate}
         * @deprecated call {@link #getUnverifiableCertificate} instead
         */
        public X509Certificate getCertificate() {
            return getUnverifiableCertificate();
        }

        /**
         * If the certificate chain could not be verified up to a trusted root in the KeyStore,
         * Return the Certificate that failed to verify. Otherwise, if everything was perfect, return null.
         * @since 2.26.2
         */
        public X509Certificate getUnverifiableCertificate() {
            return invalidcert;
        }

        /**
         * Return the signing certificate from the signature.
         * @since 2.26.2
         */
        public X509Certificate getSigningCertificate() {
            return signingcert;
        }
    }

}
