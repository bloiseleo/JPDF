// $Id: PublicKeyPromptEncryptionHandler.java 14022 2011-10-13 11:57:48Z mike $

package org.faceless.pdf2.viewer2;

import org.faceless.pdf2.*;
import javax.swing.*;
import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.io.*;
import org.faceless.pdf2.viewer2.util.DialogPanel;
import org.faceless.pdf2.viewer2.util.KeyStoreAliasList;

/**
 * An extension of the {@link PublicKeyEncryptionHandler} that will pop up a
 * dialog allowing the user to select a KeyStore to select a private key from
 * if necessary.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.2
 */
public class PublicKeyPromptEncryptionHandler extends PublicKeyEncryptionHandler {

    private JComponent parent;
    private KeyStoreManager manager;
    private transient char[] password;

    public PublicKeyPromptEncryptionHandler(JComponent parent, KeyStoreManager manager) {
        this.parent = parent;
        this.manager = manager;
    }

    public void setAlias(String alias, char[] password) {
        // Alias not used
        this.password = password;
    }

    protected boolean chooseRecipient(final X500Principal[] issuers, final BigInteger[] serials) {
        final KeyStoreAliasList list = new KeyStoreAliasList(manager, true, false) {
            public boolean isEnabled(KeyStore keystore, String alias) {
                try {
                    Certificate[] certs = keystore.getCertificateChain(alias);
                    if (certs!=null && certs.length>0) {
                        X509Certificate cert = (X509Certificate)certs[0];
                        for (int i=0;i<issuers.length;i++) {
                            if (cert.getIssuerX500Principal().equals(issuers[i]) && cert.getSerialNumber().equals(serials[i])) {
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        };

        final JPasswordField passwordfield = new JPasswordField();
        if (password!=null) {
            passwordfield.setText(new String(password));
        }

        DialogPanel panel = new DialogPanel() {
            public String validateDialog() {
                try {
                    setDecryptionKey(list.getKeyStoreManager().getKeyStore(), (String)list.getSelectedValue(), passwordfield.getPassword());
                } catch (UnrecoverableKeyException e) {
                    return UIManager.getString("PDFViewer.WrongPassword");
                } catch (Exception e) {
                    return UIManager.getString("PDFViewer.Error")+": "+e;
                }
                return null;
            }
        };
        panel.addButton(UIManager.getString("PDFViewer.ReloadFile"), null, new AbstractAction() {
            public void actionPerformed(ActionEvent event) {
                PDFViewer viewer = (PDFViewer)SwingUtilities.getAncestorOfClass(PDFViewer.class, parent);
                KeyStoreManager newmanager = new KeyStoreManager(viewer);
                try {
                    if (newmanager.loadKeyStore(parent)) {
                        list.setKeyStoreManager(newmanager);
                    }
                } catch (Exception e) {}
            }
        });
        JViewport vp = new JViewport();
        vp.add(list);
        panel.addComponent(vp);
        panel.addComponent(UIManager.getString("PDFViewer.Password"), passwordfield);
        if (panel.showDialog(parent)) {
            try {
                setDecryptionKey(list.getKeyStoreManager().getKeyStore(), (String)list.getSelectedValue(), passwordfield.getPassword());
            } catch (Exception e) {
                Util.displayThrowable(e, panel);
                return false;
            }
            return true;
        } else {
            return false;
        }
    }
}
