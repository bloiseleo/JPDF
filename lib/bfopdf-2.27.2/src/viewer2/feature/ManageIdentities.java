// $Id: ManageIdentities.java 41543 2021-11-03 13:07:28Z mike $

package org.faceless.pdf2.viewer2.feature;

import java.util.regex.Pattern;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.text.*;
import java.util.*;
import javax.security.auth.x500.*;
import javax.swing.*;
import javax.swing.event.*;
import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.*;
import org.faceless.pdf2.FormSignature;
import org.faceless.util.CombinedKeyStore;


/**
 * Create a button that opens a dialog to manage digital identities.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ManageIdentities</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class ManageIdentities extends ViewerWidget
{
    private static final Pattern KEYSTORE = Pattern.compile("\\.(keystore|jks|p12|pfx|pkcs12)$");
    private static final Pattern CERT = Pattern.compile("\\.(cer|crt|pem)$");
    private static final Pattern JKS = Pattern.compile("\\.(jks|keystore)$");
    private static final Pattern PKCS12 = Pattern.compile("\\.(p12|pfx|pkcs12)$");

    private KeyStoreManager manager;

    public ManageIdentities() {
        super("ManageIdentities");
    }

    public void initialize(PDFViewer viewer) {
        manager = viewer.getKeyStoreManager();
        setButton("Signatures", "PDFViewer.Feature.ManageIdentities.icon", "PDFViewer.tt.ManageIdentities");
        setMenu("Advanced\tManageIdentities...");
        setDocumentRequired(false);
        super.initialize(viewer);
    }

    public void action(ViewerEvent event) {
        showIdentityManagementDialog(event.getViewer());
    }

    private KeyStore getKeyStore(Component root) {
        try {
            return manager.getKeyStore();
        } catch (Exception e) {
            Util.displayThrowable(e, root);
            return null;
        }
    }

    /**
     * Show the "Identity Management" dialog, which allows keys and certificats
     * to be created/imported, viewed, exported or deleted.
     */
    private void showIdentityManagementDialog(final JComponent root) {
        try {
            try {
                manager.loadKeyStore();
            } catch (IOException e) {
                manager.createKeyStore();
            }

            DialogPanel dialog = new DialogPanel(manager.isCancellable()) {
                public void cancelDialog() {
                    super.cancelDialog();
                    manager.cancelKeyStore();
                }
                public void acceptDialog() {
                    try {
                        if (manager.isChanged()) {
                            try {
                                manager.saveKeyStore();
                            } catch (Exception e) {
                                Util.displayThrowable(e, root);
                                if (!manager.saveKeyStore(root)) {
                                    return;
                                }
                            }
                        }
                        super.acceptDialog();
                    } catch (Exception e) {
                        Util.displayThrowable(e, root);
                    }
                }
            };
            final JPanel body = new JPanel(new BorderLayout());
            body.add(getIdentityManagementPanel(root));

            dialog.setButtonText("ok", UIManager.getString("PDFViewer.Save"));
            dialog.addComponent(body);
            if (manager.isFileBased()) {
                dialog.addButton(UIManager.getString("PDFViewer.ReloadFile"), null, new AbstractAction() {
                    public void actionPerformed(ActionEvent evt) {
                        try {
                            if (manager.loadKeyStore(root)) {
                                body.remove(0);
                                body.add(getIdentityManagementPanel(root));
                                body.validate();
                            }
                        } catch (Exception e) {
                            Util.displayThrowable(e, root);
                        }
                    }
                });
            }
            dialog.showDialog(root, UIManager.getString("PDFViewer.ManageIdentities"));

        } catch (GeneralSecurityException e) {
            Util.displayThrowable(e, root);
        }
    }

    //---------------------------------------------------------------------------

    /**
     * Return a JPanel containing the Identity Management functions
     */
    private JComponent getIdentityManagementPanel(JComponent root) throws KeyStoreException {
        final JTabbedPane tabbedpane = new JTabbedPane();
        tabbedpane.setTabPlacement(JTabbedPane.TOP);
        tabbedpane.addTab(UIManager.getString("PDFViewer.Keys"), null, getEntrySelectionPanel(root, 0, true, true));
        tabbedpane.addTab(UIManager.getString("PDFViewer.Contacts"), null, getEntrySelectionPanel(root, 1, true, true));
        tabbedpane.addTab(UIManager.getString("PDFViewer.Authorities"), null, getEntrySelectionPanel(root, 2, true, true));
        return tabbedpane;
    }

    private void clearButtons(JComponent buttonpanel, int pos) {
    }

    private void addBButton(JComponent buttonpanel, int pos, JButton button) {
    }

    /**
     * Return a JPanel containing the widgets to select a Private Key or Certificate
     * from the KeyStore
     * @param key 0 to select Private Keys, 1 to select Certificates, 2 to select CA Roots
     * @param newentry whether to allow the "Create Key" or "Import" button
     * @param management whether to allow management functions on keys, eg delete
     */
    private JComponent getEntrySelectionPanel(final JComponent root, final int type, boolean newentry, final boolean management) throws KeyStoreException {
        final JSplitPane pane = new JSplitPane();

        final JPanel left = new JPanel(new BorderLayout());
        left.setMinimumSize(new Dimension(200, 200));
        pane.setTopComponent(left);
        final KeyStoreAliasList list = new KeyStoreAliasList(false, false) {
            public boolean isDisplayed(KeyStore keystore, String alias) {
                try {
                    if (type == 0) {
                        return keystore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class);
                    } else {
                        if (keystore.entryInstanceOf(alias, KeyStore.TrustedCertificateEntry.class)) {
                            X509Certificate cert = (X509Certificate)keystore.getCertificate(alias);
                            return (cert.getVersion()==3 && cert.getBasicConstraints()==-1) == (type==1);
                        } else {
                            return false;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        };
        list.setKeyStoreManager(manager);
        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                pane.setBottomComponent(getEntryPanel(root, list, management));
            }
        });
        JScrollPane scrollpane = new JScrollPane(list);
        left.add(scrollpane, BorderLayout.CENTER);
        if (newentry) {
            JPanel leftbuttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            if (type == 0) {
                if (manager.canCreateSelfSignedCertificate()) {
                    JButton button = new JButton(UIManager.getString("PDFViewer.New"));
                    button.setMnemonic(KeyEvent.VK_N);
                    button.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent evt) {
                            try {
                                createNewIdentity(root, list, null);
                            } catch (Exception e) {
                                Util.displayThrowable(e, root);
                            }
                        }
                    });
                    leftbuttons.add(button);
                    button = new JButton(UIManager.getString("PDFViewer.Import"));
                    button.setMnemonic(KeyEvent.VK_I);
                    button.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent evt) {
                            try {
                                importKey(root);
                            } catch (Exception e) {
                                Util.displayThrowable(e, root);
                            }
                        }
                    });
                    leftbuttons.add(button);
                }
            } else {
                if (Util.hasFilePermission()) {
                    JButton button = new JButton(UIManager.getString("PDFViewer.Import"));
                    button.setMnemonic(KeyEvent.VK_I);
                    button.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent evt) {
                            try {
                                importCertificate(root, list);
                            } catch (Exception e) {
                                Util.displayThrowable(e, root);
                            }
                        }
                    });
                    leftbuttons.add(button);
                }
            }
            left.add(leftbuttons, BorderLayout.SOUTH);
        }
        pane.setBottomComponent(getEntryPanel(root, list, management));
        return pane;
    }

    /**
     * Create and return a Panel containin information on a single KeyStore entry
     * @param list the List, the selected item of which is the alias we want to display
     * @param management whether to allow the management functions, eg delete/export
     */
    private JComponent getEntryPanel(final JComponent root, final JList<?> list, final boolean management) {
        final KeyStore keystore = getKeyStore(root);
        final String alias = list.getSelectedIndex()==-1 ? null : (String)list.getSelectedValue();
        final JPanel pane = new JPanel(new BorderLayout());

        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(Color.white);
        main.setOpaque(true);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        Certificate[] certs = null;
        JComponent sigpathpanel = null;
        if (alias != null) {
            try {
                keystore.getCertificateChain(alias);
                if (certs == null) {
                    certs = new Certificate[] { keystore.getCertificate(alias) };
                }
                if (certs == null || certs.length == 0 || !(certs[0] instanceof X509Certificate)) {
                    throw new GeneralSecurityException("No X.509 Certificate for Alias \""+alias+"\"");
                }
            } catch (GeneralSecurityException e) {
                Util.displayThrowable(e, root);
            }
            try {
                String pathalias = alias + SignatureCapture.PATHSUFFIX;
                if (keystore.containsAlias(pathalias)) {
                    SecretKey seckey = manager.getSecret(pathalias, SignatureCapture.KEYALGORITHM, SignatureCapture.SECRETKEYPASSWORD);
                    if (seckey != null) {
                        byte[] pathbytes = seckey.getEncoded();
                        if (pathbytes != null && pathbytes.length > 0) {
                            final GeneralPath sigpath = SignatureCapture.readPath(pathbytes);
                            if (sigpath != null) {
                                sigpathpanel = SignatureCapture.getPathComponent(sigpath);
                            }
                        }
                    }
                }
            } catch (GeneralSecurityException e) {
                Util.displayThrowable(e, root);
            }
        }
        final X509Certificate cert = certs == null ? null : (X509Certificate)certs[0];

        if (alias != null) {
            try {
                int row = 0;
                row = addEntryPanelEntry(main, "PDFViewer.Alias", alias, sigpathpanel, row);

                String name = null;

                // Name
                String field = FormSignature.getSubjectField(cert, "CN");
                if (field != null && field.length() > 0) {
                    row = addEntryPanelEntry(main, "PDFViewer.Name", field, sigpathpanel == null ? null : "RELATIVE", row);
                    if (name == null) {
                        name = field;
                    }
                }

                // Orgnization
                field = FormSignature.getSubjectField(cert, "O");
                if (field != null && field.length() > 0) {
                    row = addEntryPanelEntry(main, "PDFViewer.Organization", field, null, row);
                    if (name == null) {
                        name = field;
                    }
                }

                // Orgnization Unit
                field = FormSignature.getSubjectField(cert, "OU");
                if (field != null && field.length() > 0) {
                    row = addEntryPanelEntry(main, "PDFViewer.OrgUnit", field, null, row);
                    if (name == null) {
                        name = field;
                    }
                }

                // Location
                field = FormSignature.getSubjectField(cert, "L");
                if (field == null) {
                    field = "";
                }
                String state = FormSignature.getSubjectField(cert, "ST");
                if (state != null && state.length() > 0) {
                    if (field.length() > 0) {
                        field += ", ";
                    }
                    field += state;
                }
                state = FormSignature.getSubjectField(cert, "C");
                if (state != null && state.length() > 0) {
                    if (field.length() > 0) {
                        field += ", ";
                    }
                    field += state;
                }
                if (field.length() > 0) {
                    row = addEntryPanelEntry(main, "PDFViewer.Location", field, null, row);
                }

                // Validity
                field = formatDate(cert.getNotBefore());
                field += "  -  ";
                field += formatDate(cert.getNotAfter());
                try {
                    cert.checkValidity();
                    row = addEntryPanelEntry(main, "PDFViewer.Validity", field, null, row);
                } catch (Exception e) {
                    row = addEntryPanelEntry(main, "PDFViewer.Expired", field, "red", row);
                }

                // Issuer
                boolean first = true;
                for (int i=0;i<certs.length;i++) {
                    X509Certificate tempcert = (X509Certificate)certs[i];
                    if (!tempcert.getIssuerX500Principal().equals(tempcert.getSubjectX500Principal())) {
                        field = "";
                        String[] f = new String[] { "CN", "OU", "O", "L", "ST", "C" };
                        for (int j=0;j<f.length;j++) {
                            String bit = FormSignature.getIssuerField(tempcert, f[j]);
                            if (bit != null && bit.length() > 0) {
                                if (field.length() > 0) {
                                    field += ", ";
                                }
                                field += bit;
                            }
                        }
                        if (first) {
                            row = addEntryPanelEntry(main, "", "", null, row);
                            first = false;
                        }
                        row = addEntryPanelEntry(main, "PDFViewer.IssuedBy", field, null, row);
                    }
                }
 
                final int rrow = row;
                main.add(new JLabel(), new GridBagConstraints() { {     // To bottom align
                    weightx = weighty = 1;
                    gridy = rrow;
                    gridwidth = gridheight = GridBagConstraints.REMAINDER;
                } });

                if (management) {

                    // Following block to do with Signature Capture dialog
                    final ViewerFeature capturefeature = getViewer().getFeature("SignatureCapture");
                    if (capturefeature != null && manager.canStoreSecretKeysOnConversion()) {
                        JButton button = new JButton(UIManager.getString("PDFViewer.CaptureSignature"));
                        button.setMnemonic(KeyEvent.VK_A);
                        final String pathalias = alias + SignatureCapture.PATHSUFFIX;
                        button.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent evt) {
                                try {
                                    final int pwidth = 300;
                                    final int pheight = 50;
                                    GeneralPath path = ((SignatureCapture)capturefeature).capture(pwidth, pheight);

                                    if (path != null) {
                                        byte[] pathbytes = SignatureCapture.writePath(path);
                                        boolean deleted = pathbytes == null || pathbytes.length == 0;
                                        if (!deleted || keystore.containsAlias(pathalias)) {
                                            final JComponent pathpanel = deleted ? null : SignatureCapture.getPathComponent(path);

                                            DialogPanel panel = new DialogPanel();
                                            JPanel pp = new JPanel(new BorderLayout());
                                            pp.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
                                            JComponent prompt;
                                            if (deleted) {
                                                prompt = new JLabel("<html>"+UIManager.getString("PDFViewer.ConfirmSignaturePathDelete")+"</html>");
                                            } else {
                                                pathpanel.setBorder(BorderFactory.createEtchedBorder());
                                                pp.add(pathpanel);
                                                if (manager.canStoreSecretKeys()) {
                                                    prompt = new JLabel("<html>"+UIManager.getString("PDFViewer.ConfirmSignaturePathStore")+"</html>");
                                                } else {
                                                    prompt = new JLabel("<html>"+UIManager.getString("PDFViewer.ConfirmSignaturePathConvert")+"</html>");
                                                }
                                                panel.setPreferredSize(new Dimension(pwidth+20, 200));
                                            }
                                            pp.add(prompt, BorderLayout.SOUTH);
                                            panel.addComponent(pp);

                                            if (panel.showDialog(root, UIManager.getString("PDFViewer.CreateDigitalIdentity"))) {
                                                try {
                                                    if (deleted) {
                                                        manager.putSecret(pathalias, null, SignatureCapture.SECRETKEYPASSWORD);
                                                    } else {
                                                        manager.putSecret(pathalias, new SecretKeySpec(pathbytes, SignatureCapture.KEYALGORITHM), SignatureCapture.SECRETKEYPASSWORD);
                                                    }
                                                    ((JSplitPane)SwingUtilities.getAncestorOfClass(JSplitPane.class, pane)).setBottomComponent(getEntryPanel(root, list, management));
                                                } catch (Exception e) {
                                                    Util.displayThrowable(e, root);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Util.displayThrowable(e, root);
                                }
                            }
                        });
                        buttons.add(button);
                    }

                    final JButton delete = new JButton(UIManager.getString("PDFViewer.Delete"));
                    delete.setMnemonic(KeyEvent.VK_D);
                    delete.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent evt) {
                            if (JOptionPane.showConfirmDialog(root, UIManager.getString("PDFViewer.ConfirmDeleteText"), UIManager.getString("PDFViewer.Confirm"), JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) {
                                try {
                                    manager.deleteEntry(alias);
                                    list.clearSelection();
                                } catch (Exception e) {
                                    Util.displayThrowable(e, root);
                                }
                            }
                        }
                    });
                    buttons.add(delete);

                    if (keystore instanceof CombinedKeyStore) {
                        CombinedKeyStore cks = (CombinedKeyStore) keystore;
                        if (cks.isReadOnly(alias)) {
                            delete.setEnabled(false);
                        }
                    }

                    final JButton export = new JButton(UIManager.getString("PDFViewer.Export"));
                    export.setMnemonic(KeyEvent.VK_E);
                    final String fname = name;
                    export.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent evt) {
                            try {
                                exportCertificate(root, keystore, (String)list.getSelectedValue(), fname);
                            } catch (Exception e) {
                                Util.displayThrowable(e, root);
                            }
                        }
                    });
                    buttons.add(export);
                }

            } catch (GeneralSecurityException e) {
                Util.displayThrowable(e, root);
            }
        }

        final JButton show = new JButton(UIManager.getString("PDFViewer.ShowCertificate"));
        show.setMnemonic(KeyEvent.VK_S);
        show.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                final JDialog cdialog = Util.newJDialog(root, UIManager.getString("PDFViewer.DigitalIdentities"), true);
                JTextArea field = new JTextArea();
                field.setText(cert.toString());
                field.setEditable(false);
                field.setCaretPosition(0);
                JScrollPane pane = new JScrollPane(field);
                pane.setPreferredSize(new Dimension(500, 300));
                JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                final JButton ok = new JButton(UIManager.getString("PDFViewer.OK"));
                ok.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        cdialog.setVisible(false);
                        cdialog.dispose();
                    }
                });
                buttons.add(ok);
                JPanel body = new JPanel(new BorderLayout());
                body.add(pane, BorderLayout.CENTER);
                body.add(buttons, BorderLayout.SOUTH);
                cdialog.setContentPane(body);
                cdialog.setResizable(true);
                cdialog.pack();
                cdialog.setLocationRelativeTo(root);
                cdialog.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent evt) {
                        cdialog.dispose();
                    }
                });
                cdialog.setVisible(true);
            }
        });
        show.setEnabled(alias != null);
        buttons.add(show);

        JScrollPane scrollpane = new JScrollPane(main);
        scrollpane.setBorder(BorderFactory.createEtchedBorder());

        pane.setMinimumSize(new Dimension(400, 200));
        pane.setPreferredSize(new Dimension(400, 300));
        pane.add(scrollpane, BorderLayout.CENTER);
        pane.add(buttons, BorderLayout.SOUTH);


        return pane;
    }

    private int addEntryPanelEntry(JComponent comp, String key, String value, Object extra, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = gbc.NORTH;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;

        JLabel keylabel = new JLabel(UIManager.getString(key));
        comp.add(keylabel, gbc);
        keylabel.setOpaque(false);
        keylabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 8));

        if (extra instanceof JComponent) {
            gbc.gridx = 1;
            gbc.weightx = 1;
            JLabel valuelabel = new JLabel("<html>"+value+"</html>");
            valuelabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            valuelabel.setForeground(new Color(0x606060, false));
            comp.add(valuelabel, gbc);

            gbc.gridx = 2;
            gbc.weightx = 1;
            gbc.gridheight = 2;
            comp.add((JComponent)extra, gbc);

        } else {
            if ("RELATIVE".equals(extra)) {
                gbc.gridwidth = 1;
            } else {
                gbc.gridwidth = 2;
            }
            gbc.gridx = 1;
            gbc.weightx = 1;

            JLabel valuelabel = new JLabel("<html>"+value+"</html>");
            valuelabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            if ("red".equals(extra)) {
                valuelabel.setForeground(Color.red);
            } else {
                valuelabel.setForeground(new Color(0x606060, false));
            }
            comp.add(valuelabel, gbc);
        }
        return ++row;
    }

    private String formatDate(Date date) {
        DateFormat tdf = new SimpleDateFormat("dd MMM yyyy HH:mm");
        DateFormat ddf = new SimpleDateFormat("dd MMM yyyy");
        return (Math.abs(date.getTime()-System.currentTimeMillis()) > 1000*60*60*24*14 ? ddf : tdf).format(date);
    }

    void importKey(JComponent root) throws GeneralSecurityException, IOException {
        KeyStoreManager temp = new KeyStoreManager(getViewer());
        if (temp.loadKeyStore(root)) {
            boolean done = false;
            final KeyStoreAliasList list = new KeyStoreAliasList(temp, true, false);

            if (list.getModel().getSize() == 1) {
                try {
                    String pass = temp.getParameter("password");
                    manager.importPrivateKey(temp.getKeyStore(), (String)list.getModel().getElementAt(0), pass==null ? null : pass.toCharArray());
                    done = true;
                } catch (RuntimeException e) {
                    // Defaults didn't work, carry on
                }
            }

            if (!done) {
                final JPasswordField password = new JPasswordField();
                if (temp.getParameter("password") != null) {
                    password.setText(temp.getParameter("password"));
                }
                DialogPanel panel = new DialogPanel() {
                    public String validateDialog() {
                        try {
                            KeyStore keystore = list.getKeyStoreManager().getKeyStore();
                            keystore.getKey((String)list.getSelectedValue(), password.getPassword());
                            return null;
                        } catch (Exception e) {
                            return UIManager.getString("PDFViewer.WrongPassword");
                        }
                    }
                };
                JViewport vp = new JViewport();
                list.setSelectedIndex(0);
                vp.add(list);
                panel.addComponent(vp);
                panel.addComponent(UIManager.getString("PDFViewer.Password"), password);
                if (panel.showDialog(root)) {
                    try {
                        manager.importPrivateKey(temp.getKeyStore(), (String)list.getSelectedValue(), password.getPassword());
                    } catch (Exception e) {
                        Util.displayThrowable(e, root);
                    }
                }
            }
        }
    }

    /**
     * Bring up a dialog prompting for a filename, then import the certificate
     * from the specified certificate or KeyStore to this KeyStore.
     * @param root the Component to base the Dialog on
     * @param keystore the KeyStore
     * @param alias the alias of the entry to export
     */
    private void importCertificate(Component root, JList<?> list)
        throws GeneralSecurityException, IOException
    {
        JFileChooser filechooser = Util.fixFileChooser(new JFileChooser((File)null));
        filechooser.addChoosableFileFilter(KeyStoreManager.FILTER_CERTIFICATE);
        filechooser.addChoosableFileFilter(KeyStoreManager.FILTER_KEYSTORE);
        filechooser.setFileFilter(KeyStoreManager.FILTER_CERTIFICATE);

        if (filechooser.showOpenDialog(root)==JFileChooser.APPROVE_OPTION) {
            File file = filechooser.getSelectedFile();
            manager.importAllCertificates(file, file.getName());
        }
    }

    /**
     * Bring up a dialog prompting for a filename, and export the certificate
     * to the chosen file. If the "PKCS12" file filter is set then the private
     * key will be exported too.
     * @param root the Component to base the Dialog on
     * @param keystore the KeyStore
     * @param alias the alias of the entry to export
     * @param name the name of the certificate - to initialize the filename.
     */
    private void exportCertificate(JComponent root, KeyStore keystore, String alias, String name)
        throws GeneralSecurityException, IOException
    {
        JFileChooser filechooser = Util.fixFileChooser(new JFileChooser((File)null));
        if (name!=null) {
            name = name.replaceAll("[.,\"';:/\\ ]", "")+".cer";
            filechooser.setSelectedFile(new File(name));
        }
        filechooser.addChoosableFileFilter(KeyStoreManager.FILTER_CERTIFICATE);
        if (keystore.isKeyEntry(alias)) {
            filechooser.addChoosableFileFilter(KeyStoreManager.FILTER_KEYSTORE_PKCS12);
        }
        filechooser.setFileFilter(KeyStoreManager.FILTER_CERTIFICATE);
        if (filechooser.showSaveDialog(root)==JFileChooser.APPROVE_OPTION) {
            File file = filechooser.getSelectedFile();
            boolean pkcs12 = filechooser.getFileFilter().accept(new File("test.p12"));
            if (pkcs12) {
                JPasswordField password = new JPasswordField(10);
                if (manager.getParameter("password") != null) {
                    password.setText(manager.getParameter("password"));
                }
                JPanel panel = new JPanel();
                panel.add(new JLabel(UIManager.getString("PDFViewer.Password")));
                panel.add(password);
                int action = JOptionPane.showConfirmDialog(null, panel, UIManager.getString("PDFViewer.Password"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (action == JOptionPane.OK_OPTION) {
                    OutputStream stream = null;
                    try {
                        stream = new FileOutputStream(file);
                        manager.exportPKCS12Certificate(stream, alias, password.getPassword());
                    } catch (GeneralSecurityException e) {
                        JOptionPane.showMessageDialog(null, UIManager.getString("PDFViewer.WrongPassword"));
                    } finally {
                        if (stream!=null) stream.close();
                    }
                }
            } else {
                OutputStream stream = null;
                try {
                    stream = new FileOutputStream(file);
                    manager.exportCertificate(stream, alias);
                } finally {
                    if (stream!=null) stream.close();
                }
            }
        }
    }

    /**
     * Create a new Private Key entry
     * @param list the list to add the component to
     * @param alias the alias to use, or null to auto-generate one
     * @return an arry of [ the alias the key is stored, password ] as or null if no key was generated
     */
    private void createNewIdentity(Component root, JList<?> list, final String alias) throws GeneralSecurityException, IOException {
        String[] countries = { "AF", "AL", "DZ", "AS", "AD", "AO", "AI", "AG", "AR", "AM", "AW", "AU", "AT", "AZ", "BS", "BH", "BD", "BB", "BY", "BE", "BZ", "BJ", "BM", "BT", "BO", "BA", "BW", "BR", "VG", "BN", "BG", "BF", "BI", "KH", "CM", "CA", "CV", "KY", "CF", "TD", "CL", "CN", "CO", "KM", "CG", "CD", "CK", "CR", "CI", "HR", "CU", "CY", "CZ", "DK", "DJ", "DM", "DO", "EC", "EG", "SV", "GQ", "ER", "EE", "ET", "FK", "FJ", "FI", "FR", "GF", "PF", "GA", "GM", "GE", "DE", "GH", "GI", "GR", "GD", "GP", "GU", "GT", "GN", "GW", "GY", "HT", "HN", "HK", "HU", "IS", "IN", "ID", "IR", "IE", "IL", "IT", "JM", "JP", "JO", "KZ", "KE", "KI", "KP", "KR", "KW", "KG", "LA", "LV", "LB", "LS", "LR", "LY", "LI", "LT", "LU", "MO", "MK", "MG", "MW", "MY", "MV", "ML", "MT", "MH", "MQ", "MR", "MU", "YT", "MX", "FM", "MD", "MC", "MN", "ME", "MS", "MA", "MZ", "MM", "NA", "NR", "NP", "NL", "AN", "NC", "NZ", "NI", "NE", "NG", "NU", "NF", "MP", "NO", "OM", "PK", "PW", "PA", "PG", "PY", "PE", "PH", "PL", "PT", "PR", "QA", "RE", "RO", "RU", "RW", "WS", "SM", "ST", "SA", "SN", "RS", "SC", "SL", "SG", "SK", "SI", "SB", "SO", "ZA", "ES", "LK", "KN", "LC", "VC", "SD", "SR", "SZ", "SE", "CH", "SY", "TW", "TJ", "TZ", "TH", "TL", "TG", "TO", "TT", "TN", "TR", "TM", "TC", "TV", "VI", "UG", "UA", "AE", "GB", "US", "UY", "UZ", "VU", "VE", "VN", "YE", "ZM", "ZW" };
        final JComboBox<String> country = new JComboBox<String>(countries);
        country.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, UIManager.getString("iso3166."+value), index, isSelected, cellHasFocus);
            }
        });
        try {
            country.setSelectedItem(Locale.getDefault().getCountry().toUpperCase());
        } catch (Exception e) { }

        final JTextField name = new JTextField();
        final JTextField unit = new JTextField();
        final JTextField org = new JTextField();
        final JTextField city = new JTextField();
        final JTextField state = new JTextField();
        final JPasswordField password = manager.isKeyPasswordUsed() ? new JPasswordField() : null;
        final JPasswordField confirmpassword = manager.isKeyPasswordUsed() ? new JPasswordField() : null;

        DialogPanel panel = new DialogPanel() {
            public String validateDialog() {
                if ((name.getText().trim()+unit.getText().trim()+org.getText().trim()).length()==0) {
                    return UIManager.getString("PDFViewer.OneNameRequired");
                } else if ((name.getText()+unit.getText()+org.getText()+city.getText()+state.getText()).indexOf("\"")>=0) {
                    return Util.getUIString("PDFViewer.InvalidWhy", "No \" allowed in Name");
                } else if (password != null && !Arrays.equals(password.getPassword(), confirmpassword.getPassword())) {
                    return UIManager.getString("PDFViewer.PasswordMismatch");
                } else if (password != null && password.getPassword().length == 0) {
                    return UIManager.getString("PDFViewer.ChoosePassword");
                } else {
                    return null;
                }
            }
        };
        panel.addComponent(UIManager.getString("PDFViewer.Name"), name);
        panel.addComponent(UIManager.getString("PDFViewer.OrgUnit"), unit);
        panel.addComponent(UIManager.getString("PDFViewer.Organization"), org);
        panel.addComponent(UIManager.getString("PDFViewer.City"), city);
        panel.addComponent(UIManager.getString("PDFViewer.State"), state);
        panel.addComponent(UIManager.getString("PDFViewer.Country"), country);
        if (password != null) {
            panel.addComponent(UIManager.getString("PDFViewer.Password"), password);
            panel.addComponent(UIManager.getString("PDFViewer.ConfirmPassword"), confirmpassword);
        }
        if (panel.showDialog(root, UIManager.getString("PDFViewer.CreateDigitalIdentity"))) {
            // Run this as a task - creating keys on PKCS#11 tokens can be time consuming.
            // Creating key on token can't be interrupted so this is model and not cancellable.
            LongRunningTask task = new LongRunningTask() {
                public boolean isCancellable() {
                    return false;
                }
                public float getProgress() {
                    return 0.5f;
                }
                public void run() throws GeneralSecurityException {
                    manager.createSelfSignedKey(alias, name.getText(), unit.getText(), org.getText(), city.getText(), state.getText(), (String)country.getSelectedItem(), password == null ? null : password.getPassword(), 365*2);
                }
            };
            task.start((JComponent)root, UIManager.getString("PDFViewer.CreateDigitalIdentity"), true);
        }
    }
}
