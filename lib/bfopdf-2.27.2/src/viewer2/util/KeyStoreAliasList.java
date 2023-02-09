// $Id: KeyStoreAliasList.java 39857 2021-04-26 11:26:38Z mike $

package org.faceless.pdf2.viewer2.util;

import java.awt.*;
import javax.swing.*;
import java.beans.*;
import java.util.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import org.faceless.pdf2.viewer2.Util;
import org.faceless.pdf2.viewer2.KeyStoreManager;
import org.faceless.pdf2.FormSignature;
import org.faceless.util.CombinedKeyStore;
import org.faceless.util.SortedListModel;

/**
 * A {@link JList} that displays a list of aliases from a {@link KeyStore} managed by a {@link KeyStoreManager}
 */
public class KeyStoreAliasList extends JList<String> implements Comparator<String>, ListCellRenderer<String>, PropertyChangeListener {
    private KeyStoreManager ksm;
    private SortedListModel<String> model;
    private final boolean keys, certificates;
    private static final Color TRANSPARENT = new Color(0, true);

    /**
     * Create a new KeyStoreAliasList
     * @param keys whether to include keys in the list
     * @param certificates whether to include certificates in the list
     */
    public KeyStoreAliasList(boolean keys, boolean certificates) {
        this.keys = keys;
        this.certificates = certificates;
        this.model = new SortedListModel<String>(this);
        this.setVisibleRowCount(6);
        setModel(model);
//        addPropertyChangeListener(this);
        setCellRenderer(this);
    }


    /**
     * Create a new KeyStoreAliasList and set the KeyStoreManager
     * @param ksm the KeystoreManager
     * @param keys whether to include keys in the list
     * @param certificates whether to include certificates in the list
     */
    public KeyStoreAliasList(KeyStoreManager ksm, boolean keys, boolean certificates) {
        this(keys, certificates);
        setKeyStoreManager(ksm);
    }

    /**
     * Get the KeyStoreManager
     */
    public KeyStoreManager getKeyStoreManager() {
        return ksm;
    }

    /**
     * Set the KeyStoreManager
     * @param ksm the keystore manager
     */
    public void setKeyStoreManager(KeyStoreManager ksm) {
        try {
            if (this.ksm != null) {
                this.ksm.removePropertyChangeListener(this);
                model.clear();
            }
            this.ksm = ksm;
            KeyStore keystore;
            try {
                keystore = ksm.getKeyStore();
            } catch (Exception e) {
                keystore = null;
            }
            if (keystore != null) {
                for (Enumeration<String> e = keystore.aliases();e.hasMoreElements();) {
                    String alias = e.nextElement();
                    if (isDisplayed(keystore, alias)) {
                        model.add(alias);
                    }
                }
                ksm.addPropertyChangeListener(this);
            }
        } catch (RuntimeException e)  {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override public void propertyChange(PropertyChangeEvent event) {
        if (event.getPropertyName().equals("alias")) {
            try {
                if (event.getOldValue() != null) {
                    model.remove(event.getOldValue());
                }
                if (event.getNewValue() != null && isDisplayed(ksm.getKeyStore(), (String)event.getNewValue())) {
                    model.add((String)event.getNewValue());
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Return true if the specified alias from the specified keystore is displayed.
     * By default returns true for PrivateKeyEntry or TrustedCertificateEntry entries
     * (depending on constructor parameters), false otherwise
     * @param keystore the keystore
     * @param alias the keystore alias
     */
    public boolean isDisplayed(KeyStore keystore, String alias) {
        try {
            if (keys && keystore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
                return true;
            } else if (!keys && keystore.entryInstanceOf(alias, KeyStore.TrustedCertificateEntry.class)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Return true if the specified alias from the specified keystore is enabled
     * for selection. By default always returns true.
     * @param keystore the keystore
     * @param alias the keystore alias
     */
    public boolean isEnabled(KeyStore keystore, String alias) {
        return true;
    }

    /**
     * Return true if the specified alias from the specified keystore should be
     * a read-only entry. By default returns true for any entries from the system
     * keystore.
     * @param keystore the keystore
     * @param alias the keystore alias
     */
    public boolean isReadOnly(KeyStore keystore, String alias) {
        if (keystore instanceof CombinedKeyStore) {
            CombinedKeyStore cks = (CombinedKeyStore) keystore;
            return cks.isReadOnly(alias);
        }
        return false;
    }

    @Override public int compare(String o1, String o2) {
        int diff = 0;
        try {
            KeyStore keystore = ksm.getKeyStore();
            if (o1 == null ? o2 != null : !o1.equals(o2)) {
                X509Certificate c1 = (X509Certificate)keystore.getCertificate(o1);
                if (c1 == null) {
                    Certificate[] chain = keystore.getCertificateChain(o1);
                    if (chain != null) {
                        c1 = (X509Certificate)chain[0];
                    }
                }
                X509Certificate c2 = (X509Certificate)keystore.getCertificate(o2);
                if (c2 == null) {
                    Certificate[] chain = keystore.getCertificateChain(o2);
                    if (chain != null) {
                        c2 = (X509Certificate)chain[0];
                    }
                }
                if (c1 == null && c2 == null) {
                    return 0;
                } else if (c1 == null) {
                    return 1;
                } else if (c2 == null) {
                    return -1;
                } else {
                    String n1 = c1.getSubjectX500Principal().toString();
                    String n2 = c2.getSubjectX500Principal().toString();
                    diff = n1.compareTo(n2);
                    if (diff == 0) {
                        diff = c1.getNotAfter().compareTo(c2.getNotAfter());
                    }
                    if (diff == 0) {
                        diff = c1.getSerialNumber().compareTo(c2.getSerialNumber());
                    }
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            diff = o1.hashCode() - o2.hashCode();
        }
        return diff;
    }

    @Override public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean hasFocus) {
        try {
            KeyStore keystore = ksm.getKeyStore();
            String alias = (String)value;
            boolean enabled = isEnabled(keystore, alias);
            isSelected &= enabled;
            X509Certificate cert = (X509Certificate)keystore.getCertificate(alias);
            if (cert == null) {
                Certificate[] certs = keystore.getCertificateChain(alias);
                if (certs != null) {
                    cert = (X509Certificate)certs[0];
                }
            }
            String key = cert == null ? alias : FormSignature.getSubjectField(cert, "CN");
            if (key == null) {
                key = FormSignature.getSubjectField(cert, "O");
            }
            JLabel label = new JLabel(key);
            boolean labelEnabled = enabled && !isReadOnly(keystore, alias);
            label.setEnabled(labelEnabled);
            try {
                if (cert != null) {
                    cert.checkValidity();
                }
                label.setIcon(UIManager.getIcon("PDFViewer.KeyStore.Valid.icon"));
            } catch (Exception e) {
                label.setIcon(UIManager.getIcon("PDFViewer.KeyStore.Invalid.icon"));
                label.setToolTipText(UIManager.getString("PDFViewer.InvalidWhy").replaceAll("\\{1\\}", (e.getMessage() == null ? e.toString() : e.getMessage())));
            }

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(label);
            panel.setOpaque(true);
            panel.setBackground(isSelected ? list.getSelectionBackground() : TRANSPARENT);
            panel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            panel.setFont(list.getFont());
            panel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            return panel;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

