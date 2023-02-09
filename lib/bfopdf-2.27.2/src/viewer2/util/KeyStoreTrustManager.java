// $Id: KeyStoreTrustManager.java 22006 2015-09-23 14:57:21Z mike $

package org.faceless.pdf2.viewer2.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.*;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.faceless.pdf2.FormSignature;
import org.faceless.pdf2.viewer2.KeyStoreManager;
import org.faceless.pdf2.viewer2.PDFViewer;
import org.faceless.pdf2.viewer2.Util;

/**
 * <p>
 * This class makes working with SSL connections easier, by prompting the user
 * when an invalid certificate is encountered. The user will be given the option
 * of refuting or accepting the certificate, including the option of adding the
 * certificate to the PDF keystore.
 * </p><p>
 * Due to the way SSL is implemented in Java, in order to actually use this class
 * it needs to be installed statically. This can be done easily:
 * </p>
 * <pre class="brush:java">
 * PDFViewer viewer = newPDFViewer();   // Create it somehow
 * KeyStoreTrustManager.install(viewer);
 * </pre>
 * <p>
 * This will install an instance of this object as the default {@link X509TrustManager}
 * for any SSL connections made from Java. If an invalid certificate is encountered
 * a dialog will be displayed relative to the <code>PDFViewer</code> object, and if
 * the user chooses to accept the certificate permanently it will be added to the
 * {@link KeyStoreManager} returned by {@link PDFViewer#getKeyStoreManager}
 * </p><p>
 * If you have more than one <code>PDFViewer</code> on the screen at once, or you don't
 * want this class managing all SSL connections from the JVM, then you can create the
 * object and use it as a trust manager only on the connections you need.
 * </p>
 * @since 2.11
 */
public class KeyStoreTrustManager implements X509TrustManager, HostnameVerifier {

    private final KeyStoreManager ksm;
    private final Component root;
    private Map<String,Set<String>> hostnameAliases;

    private static final int ACCEPT_ONCE = 2;
    private static final int ACCEPT_ALWAYS = 1;
    private static final int ACCEPT_NONE = 0;

    /**
     * Create a new {@link KeyStoreTrustManager} and install it as part of the default
     * {@link SSLSocketFactory} and {@link HostnameVerifier} for all HTTTPS connections
     * made by the JVM.
     * @param viewer the PDFViewer to be used for the KeyStoreManager and dialog positioning.
     * May be <code>null</code>, in which case the dialog is not tied to any component and certificates
     * cannot be permanently added to a KeyStore.
     * @return true if the SecurityManager allowed this to be installed, false otherwise
     */
    public static boolean install(PDFViewer viewer) throws GeneralSecurityException {
        try {
            KeyStoreManager ksm = viewer==null ? null : viewer.getKeyStoreManager();
            KeyStoreTrustManager kstm = new KeyStoreTrustManager(ksm, viewer);
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[] { kstm }, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(kstm);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Create a new KeyStoreTrustManager.
     * @param ksm the KeyStoreManager to add any permanently trusted Certificates to. May
     * be <code>null</code>, in which case this option is not available.
     * @param root the object to position the dialog relative to. May be <code>null</code>
     * if this is not known.
     */
    public KeyStoreTrustManager(KeyStoreManager ksm, Component root) {
        this.ksm = ksm;
        this.root = root;
        hostnameAliases = new HashMap<String,Set<String>>();
    }

    public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException {
    }

    public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
        try {

            // Determine whether the key store contains any of the certificates
            // in the chain
            if (ksm!=null) {
                for (int i = 0; i < chain.length; i++) {
                    chain[i].checkValidity();
                    if (ksm.contains(chain[i])) {
                        return;
                    }
                }
            }

            // If not, prompt the user to add one or more of the certificates
            // in the chain
            String reason = UIManager.getString("PDFViewer.ssl.SecureHost");
            if (showDialog(ksm, chain, reason)) {
                return;
            }

            // Determine whether the key store contains any of the certificates
            // in the chain
            if (ksm!=null) {
                for (int i = 0; i < chain.length; i++) {
                    if (ksm.contains(chain[i])) {
                        return;
                    }
                }
            }

        } catch (CertificateException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            CertificateException e2 = new CertificateException();
            e2.initCause(e);
            throw e2;
        } catch (IOException e) {
            CertificateException e2 = new CertificateException();
            e2.initCause(e);
            throw e2;
        }

        throw new CertificateException("No trusted certificates found");
    }

    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }

    // From HostNameVerifier
    public boolean verify(String urlHostname, SSLSession session) {
        try {
            Certificate[] certs = session.getPeerCertificates();
            if (certs == null || certs.length < 1) {
                throw new SSLPeerUnverifiedException("No server certificate");
            }
            X509Certificate[] chain = new X509Certificate[certs.length];
            for (int i = 0; i < certs.length; i++) {
                chain[i] = (X509Certificate) certs[i];
            }

            String certHostname = FormSignature.getSubjectField(chain[0], "CN");
            if (certHostname == null || !certHostname.equals(urlHostname)) {
                Set<String> aliases = hostnameAliases.get(certHostname);
                if (aliases != null && aliases.contains(urlHostname)) {
                    return true;
                }

                String reasonPattern = UIManager.getString("PDFViewer.ssl.HostnameMismatch");
                MessageFormat mf = new MessageFormat(reasonPattern);
                String[] args = new String[] { certHostname, urlHostname };
                String reason = mf.format(args);

                if (showVerifyDialog(urlHostname, certHostname, reason)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Util.displayThrowable(e, root);
        }
        return false;
    }

    // Used to verify Certificates with an untrusted root
    private boolean showDialog(final KeyStoreManager ksm, final X509Certificate[] certs, final String reason)
        throws CertificateException
    {
        final int[] retval = new int[] { ACCEPT_NONE };
        final List<CertificateWrapper> outervalues = new ArrayList<CertificateWrapper>();
        final Throwable[] thrown = new Throwable[1];
        final Runnable runnable = new Runnable() {
            public void run() {
                try {
                    String title = UIManager.getString("PDFViewer.ssig.UnknownValidity");
                    final JDialog dialog = Util.newJDialog(root, title, true);
                    JButton acceptOnce = new JButton(UIManager.getString("PDFViewer.ssl.AcceptOnce"));
                    final JButton acceptAlways = new JButton(UIManager.getString("PDFViewer.ssl.AcceptAlways"));
                    JButton acceptNone = new JButton(UIManager.getString("PDFViewer.Cancel"));
                    acceptOnce.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent event) {
                            retval[0] = ACCEPT_ONCE;
                            dialog.dispose();
                        }
                    } );
                    acceptAlways.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent event) {
                            retval[0] = ACCEPT_ALWAYS;
                            dialog.dispose();
                        }
                    } );
                    acceptNone.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent event) {
                            retval[0] = ACCEPT_NONE;
                            dialog.dispose();
                        }
                    } );
                    JPanel buttonpane = new JPanel();
                    buttonpane.setLayout(new FlowLayout(FlowLayout.RIGHT));
                    buttonpane.add(acceptOnce);
                    if (ksm!=null) {
                        buttonpane.add(acceptAlways);
                    }
                    buttonpane.add(acceptNone);

                    final JTextArea detail = new JTextArea();
                    detail.setFont(new Font("Monospace", 0, 9));
                    final JScrollPane rightscroll = new JScrollPane(detail);
                    rightscroll.setPreferredSize(new Dimension(250, 200));

                    DefaultListModel<CertificateWrapper> model = new DefaultListModel<CertificateWrapper>();
                    for (int i = 0; i < certs.length; i++) {
                        model.addElement(new CertificateWrapper(certs[i]));
                    }

                    final JList<CertificateWrapper> list = new JList<CertificateWrapper>(model);
                    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                    list.addListSelectionListener(new ListSelectionListener() {
                        public void valueChanged(ListSelectionEvent event) {
                            StringBuilder text = new StringBuilder();
                            @SuppressWarnings("deprecation") Object[] values = list.getSelectedValues(); // Deprecated in Java 7
                            if (ksm != null) {
                                acceptAlways.setEnabled(values.length > 0);
                            }
                            outervalues.clear();
                            for (int i = 0; i < values.length; i++) {
                                CertificateWrapper w = (CertificateWrapper) values[i];
                                outervalues.add(w);
                                text.append(w.cert.toString());
                                text.append("\n-----\n");
                            }
                            detail.setText(text.toString());
                        }
                    });
                    list.setSelectedIndex(0);
                    JScrollPane leftscroll = new JScrollPane(list);
                    leftscroll.setPreferredSize(new Dimension(200, 200));

                    JSplitPane splitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftscroll, rightscroll);

                    JPanel main = new JPanel();
                    main.setLayout(new BorderLayout());
                    main.add(new JLabel(reason), BorderLayout.NORTH);
                    main.add(splitpane, BorderLayout.CENTER);
                    main.add(buttonpane, BorderLayout.SOUTH);

                    dialog.setContentPane(main);
                    dialog.pack();
                    if (root!=null) {
                        dialog.setLocationRelativeTo(root);
                    }
                    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    dialog.setVisible(true);
                } catch (Throwable e) {
                    thrown[0] = e;
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(runnable);
            } catch (Exception e) { }
        }
        if (thrown[0] instanceof CertificateException) {
            throw (CertificateException)thrown[0];
        } else if (thrown[0] instanceof RuntimeException) {
            throw (RuntimeException)thrown[0];
        } else if (thrown[0] instanceof Error) {
            throw (Error)thrown[0];
        }

        switch (retval[0]) {
            case ACCEPT_ONCE:
                return true;
            case ACCEPT_ALWAYS:
                try {
                    for (int i = 0; i < outervalues.size(); i++) {
                        CertificateWrapper w = outervalues.get(i);
                        ksm.importCertificate(null, w.cert);
                    }
                    ksm.saveKeyStore();
                } catch (Exception e) {
                    Util.displayThrowable(e, root);
                    return false;
                }
                return true;
            default:
                return false;
        }
    }

    // Used to verify Certificates where the hostname != certificate name. Part of HostnameVerifier.
    // No KeyStore required.
    private boolean showVerifyDialog(final String urlHostname, final String certHostname, final String reason) {
        final int[] retval = new int[] { ACCEPT_NONE };
        final Throwable[] thrown = new Throwable[1];
        final Runnable runnable = new Runnable() {
            public void run() {
                try {
                    String title = UIManager.getString("PDFViewer.ssig.UnknownValidity");
                    final JDialog dialog = Util.newJDialog(root, title, true);
                    JButton acceptOnce = new JButton(UIManager.getString("PDFViewer.ssl.AcceptOnce"));
                    final JButton acceptAlways = new JButton(UIManager.getString("PDFViewer.ssl.AcceptAlways"));
                    JButton acceptNone = new JButton(UIManager.getString("PDFViewer.Cancel"));
                    acceptOnce.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent event) {
                            retval[0] = ACCEPT_ONCE;
                            dialog.dispose();
                        }
                    } );
                    acceptAlways.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent event) {
                            retval[0] = ACCEPT_ALWAYS;
                            dialog.dispose();
                        }
                    } );
                    acceptNone.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent event) {
                            retval[0] = ACCEPT_NONE;
                            dialog.dispose();
                        }
                    } );
                    JPanel buttonpane = new JPanel();
                    buttonpane.setLayout(new FlowLayout(FlowLayout.RIGHT));
                    buttonpane.add(acceptOnce);
                    buttonpane.add(acceptAlways);
                    buttonpane.add(acceptNone);

                    JPanel main = new JPanel();
                    main.setLayout(new BorderLayout());
                    main.add(new JLabel(reason), BorderLayout.CENTER);
                    main.add(buttonpane, BorderLayout.SOUTH);

                    dialog.setContentPane(main);
                    dialog.pack();
                    if (root!=null) {
                        dialog.setLocationRelativeTo(root);
                    }
                    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    dialog.setVisible(true);
                } catch (Throwable e) {
                    thrown[0] = e;
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(runnable);
            } catch (Exception e) { }
        }

        if (thrown[0] instanceof RuntimeException) {
            throw (RuntimeException)thrown[0];
        } else if (thrown[0] instanceof Error) {
            throw (Error)thrown[0];
        }

        switch (retval[0]) {
            case ACCEPT_ONCE:
                return true;
            case ACCEPT_ALWAYS:
                Set<String> aliases = hostnameAliases.get(certHostname);
                if (aliases == null) {
                    aliases = new HashSet<String>();
                    hostnameAliases.put(certHostname, aliases);
                }
                aliases.add(urlHostname);
                return true;
            default:
                return false;
        }
    }

    static class CertificateWrapper {

        final X509Certificate cert;
        String name;

        CertificateWrapper(X509Certificate cert) throws CertificateException {
            this.cert = cert;
            name = FormSignature.getSubjectField(cert, "CN");
            if (name==null) {
                name = FormSignature.getSubjectField(cert, "O");
            }
        }

        public String toString() {
            return name;
        }

    }

}
