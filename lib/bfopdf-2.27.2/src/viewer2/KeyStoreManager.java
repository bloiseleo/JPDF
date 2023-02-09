// $Id: KeyStoreManager.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2;

import java.beans.*;
import java.io.*;
import java.security.cert.*;
import java.util.*;
import java.util.List;
import java.lang.reflect.*;
import java.util.regex.Pattern;
import java.math.BigInteger;
import java.security.*;
import javax.crypto.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import javax.security.auth.x500.*;
import javax.security.auth.callback.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileFilter;
import org.faceless.pdf2.FormSignature;
import org.faceless.pdf2.PropertyManager;
import org.faceless.util.Base64;
import org.faceless.util.CombinedKeyStore;
import org.faceless.util.WeakPropertyChangeSupport;
import org.faceless.util.asn1.X509CertificateBuilder;
import org.faceless.pdf2.viewer2.util.DialogPanel;
import org.faceless.pdf2.viewer2.feature.ManageIdentities;
import org.faceless.pdf2.viewer2.feature.KeyStoreSignatureProvider;

/**
 * <p>
 * This class is a wrapper around a {@link KeyStore}, providing high-level management
 * functions and the ability to load and save the KeyStore, selecting the file via
 * a Swing dialog.
 * </p><p>
 * Each {@link PDFViewer} should have a KeyStoreManager if it needs to work with
 * digital identities, which in practice means PDFs containing digital signatures (or
 * that will have signatures added) or those encrypted with a
 * {@link PublicKeyPromptEncryptionHandler public key}. By default this class works
 * with KeyStores stored in a file, although subclasses aren't bound by this.
 * </p>
 * <h3>File-based KeyStores - JKS, JCEKS and PKCS#12</h3>
 * <p>If nothing else is specified, the default {@link KeyStore} used is a JKS KeyStore
 * loaded from the file <code><i>${user.home}</i>/.keystore</code>, which is the default
 * for the <code>keytool</code> supplied with the JDK. The path to the file can be changed
 * by setting the <code>file</code> parameter, and the password with the "password"
 * parameter. The KeyStore <code>type</code> parameter can be <code>jks</code> (the
 * default, and used for <code>jceks</code> keystores as well) or <code>pkcs12</code> if
 * the KeyStore is in PKCS#12 format. Here's how to do this for an application
 * </p>
 * <pre class="brush:plain">
 * java -Dorg.faceless.pdf2.viewer2.KeyStoreManager.params="type=pkcs12;file=/path/to/file.p12" org.faceless.pdf2.viewer2.PDFViewer
 * </pre>
 * <h3>Using the OS X KeyChain as a KeyStore</h3>
 * <p>Certificates and keys stored in the Apple OS X "KeyChain" can be accessed by setting the
 * <code>provider</code> parameter to "Apple" and the <code>type</code> parameter to "KeychainStore". No
 * other parameters are required. There are some issues with this KeyStore - in particular, at least until
 * OS X 10.6 only the first private key in the KeyStore can be accessed. Here's an example.</p>
 * <pre class="brush:plain">
 * java -Dorg.faceless.pdf2.viewer2.KeyStoreManager.params=type=KeychainStore\;provider=Apple org.faceless.pdf2.viewer2.PDFViewer
 * </pre>
 * <h3>PKCS#11 based Keystores</h3>
 * <p>
 * Since 2.11.14 it's possible to use a PKCS#11 based Hardware Security Module (HSM) as a KeyStore. The
 * attributes supplied to the <code>sun.security.pkcs11.SunPKCS11</code> Provider (specified
 * <a target="_top" href="http://download.oracle.com/javase/7/docs/technotes/guides/security/p11guide.html#Config">here</a>)
 * can be supplied directly as parameters, and the <code>type</code> parameter must be set to "pkcs11".
 * For example, here's how to use a
 * <a target="_new" href="http://www.safenet-inc.com/aladdin-content/etoken/devices/pro-usb/">Safenet eToken Pro</a>
 * on Windows as a KeyStore: for an explanation of how to parse the parameters see the {@link #setParameters} method.
 * </p>
 * <pre class="brush:plain">
 * java -Dorg.faceless.pdf2.viewer2.KeyStoreManager.params="type=pkcs11;name=eToken;library='c:\\WINDOWS\\system32\\eTPKCS11.dll';password=1234" org.faceless.pdf2.viewer2.PDFViewer
 * </pre>
 * <p>
 * Alternatively, if you prefer to read the PKCS#11 parameters from a file, specify the file using the "configfile" parameter:
 * </p>
 * <pre class="brush:plain">
 * java -Dorg.faceless.pdf2.viewer2.KeyStoreManager.params="type=pkcs11;configfile=pkcs11.cfg" org.faceless.pdf2.viewer2.PDFViewer
 * </pre>
 * <p>
 * The {@link ManageIdentities} feature is a useful companion to this class and can be used to
 * maintain the {@link KeyStore}, but it's not necessary for this class's operation.
 * </p>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.3, but a major rewrite in 2.11
 * @see ManageIdentities
 * @see KeyStoreSignatureProvider
 * @see PublicKeyPromptEncryptionHandler
 * @see PDFViewer#getKeyStoreManager
 */
public class KeyStoreManager {

    /**
     * A {@link FileFilter} that selects any {@link KeyStore} file
     * @since 2.11
     */
    public static final FileFilter FILTER_KEYSTORE = createFileFilter("PDFViewer.FilesKeyStore", Pattern.compile(".*\\.(keystore|jks|p12|pfx|pkcs12)"));

    /**
     * A {@link FileFilter} that selects any JKS/JCEKS format {@link KeyStore} file
     * @since 2.11
     */
    public static final FileFilter FILTER_KEYSTORE_JKS = createFileFilter("PDFViewer.FilesJKS", Pattern.compile(".*\\.(keystore|jks|jceks)"));

    /**
     * A {@link FileFilter} that selects any PKCS#12 format {@link KeyStore} file
     * @since 2.11
     */
    public static final FileFilter FILTER_KEYSTORE_PKCS12 = createFileFilter("PDFViewer.FilesPKCS12", Pattern.compile(".*\\.(p12|pfx|pkcs12)"));

    /**
     * A {@link FileFilter} that selects any {@link X509Certificate X.509 Certificate} file.
     * @since 2.11
     */
    public static final FileFilter FILTER_CERTIFICATE = createFileFilter("PDFViewer.FilesCertificate", Pattern.compile(".*\\.(cer|crt|pem)"));


    protected WeakPropertyChangeSupport propertyChangeSupport;
    protected KeyStore keystore;
    private CallbackHandler callbackHandler;
    protected Map<String,String> params;
    protected final PropertyManager propertymanager;
    private PDFViewer viewer;
    protected boolean changed;

    /**
     * Create a new KeyStoreManager
     * @param viewer the Viewer - must not be null
     */
    public KeyStoreManager(PDFViewer viewer) {
        this(viewer.getPropertyManager());
        this.viewer = viewer;
    }

    /**
     * Create a new KeyStoreManager. This constructor can be used when no PDFViewer is instantiated.
     * @param propertymanager the PropertyManager to load properties from - must not be null
     * @since 2.16
     */
    public KeyStoreManager(PropertyManager propertymanager) {
        propertyChangeSupport = new WeakPropertyChangeSupport(this);
        params = new LinkedHashMap<String,String>();
        this.propertymanager = propertymanager;
        setCallbackHandler(new DefaultCallbackHandler());
    }

    /**
     * Return the CallbackHandler used by this object to authenticate
     * access to the KeyStore. The default implementation will use the "password"
     * {@link #getParameter parameter} if specified, otherwise it will prompt with
     * a password dialog.
     * @since 2.23.5
     */
    public CallbackHandler getCallbackHandler() {
        return callbackHandler;
    }

    /**
     * Set the CallbackHandler used by this object to authenticate
     * access to the KeyStore.
     * @since 2.23.5
     */
    public void setCallbackHandler(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    /**
     * Load the {@link KeyStore} this manager is supposed to work on, based on the
     * {@link #setParameter parameters} specified. These will typically include the
     * <code>file</code> and <code>password</code> parameters. If the KeyStore cannot
     * be loaded, this method will throw an Exception
     * Subclasses that need to manage their own KeyStore will typically override this
     * method and {@link #saveKeyStore}.
     *
     * @throws IOException if the KeyStore file cannot be accessed
     * @throws GeneralSecurityException if the KeyStore cannot be parsed or decoded
     */
    public void loadKeyStore() throws IOException, GeneralSecurityException {
        String type = getParameter("type");
        String provider = getParameter("provider");
        if ("pkcs11".equalsIgnoreCase(type)) {
            StringBuilder sb = new StringBuilder();
            String configfilename = params.get("configfile");
            byte[] config = null;
            if (configfilename == null) {
                for (Iterator<Map.Entry<String,String>> i = params.entrySet().iterator();i.hasNext();) {
                    Map.Entry<String,String> e = i.next();
                    String key = e.getKey();
                    if (key.equals("library") || key.equals("name") || key.equals("description") ||
                      key.equals("slot") || key.equals("slotListIndex") ||
                      key.equals("enabledMechanisms") || key.equals("disabledMechanisms") ||
                      (key.startsWith("attributes(")) && key.charAt(key.length()-1)==')') {
                        sb.append(key+"="+e.getValue()+"\n");
                    }
                }
                config = sb.toString().getBytes("ISO-8859-1");
            }
            Provider prov = null;
            try {
                try {
                    // Jump through hoops to try four possibles
                    // 1. Java 6/7/8 call to new sun.security.pkcs11.SunPKCS11(inputstream) with byte stream
                    // 2. Java 6/7/8 call to new sun.security.pkcs11.SunPKCS11(inputstream) with config file stream
                    // 3. Java 9+ call to new provider.configure("--" + configstream) to load config from a string
                    // 4. Java 9+ call to new provider.configure(filename)) with config file name
                    if (prov == null) {
                        // Create a PKCS11 provider the Java 6/7/8 way
                        // This class (may) not on Windows 64-bit! Thanks, Sun.
                        Class<?> c = Class.forName("sun.security.pkcs11.SunPKCS11");
                        InputStream in = null;
                        try {
                            Constructor<?> constructor = c.getConstructor(InputStream.class);
                            if (configfilename != null) {
                                in = new FileInputStream(configfilename);
                            } else {
                                in = new ByteArrayInputStream(config);
                            }
                            prov = (Provider)constructor.newInstance(in);
                        } catch (NoSuchMethodException e) {
                        } finally {
                            if (in != null) in.close();
                        }

                    }
                    if (prov == null) {
                        prov = Security.getProvider("SunPKCS11");
                        if (prov != null) {
                            // Create a PCKS11 provider the Java 9 way
                            if (configfilename == null) {
                                // Undocumented trick to read config from String! But why not make it take a Stream??
                                configfilename = "--" + sb;
                            }
                            try {
                                prov = (Provider)Provider.class.getMethod("configure", String.class).invoke(prov, configfilename);
                            } catch (NoSuchMethodException e) { }
                        } 
                    }
                    if (prov == null) {
                        throw new NoSuchMethodException("Can't initialize SunPKCS11 provider with either Java 8 or 9 API");
                    }
                    ((AuthProvider)prov).setCallbackHandler(getCallbackHandler());
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof GeneralSecurityException) {
                        throw (GeneralSecurityException)cause;
                    } else if (cause instanceof IOException) {
                        throw (IOException)cause;
                    } else if (cause instanceof RuntimeException) {
                        throw (RuntimeException)cause;
                    } else if (cause instanceof Error) {
                        throw (Error)cause;
                    }
                }
            } catch (Throwable e) {
                IllegalArgumentException e2 = new IllegalArgumentException("Unable to initialize sun.security.pkcs11.SunPKCS11 implementation");
                e2.initCause(e);
                throw e2;
            }
            KeyStore.Builder builder = KeyStore.Builder.newInstance("pkcs11", prov, new KeyStore.CallbackHandlerProtection(getCallbackHandler()));
            keystore = builder.getKeyStore();
        } else if ("keychainstore".equalsIgnoreCase(type) && "apple".equalsIgnoreCase(provider)) {
            keystore = KeyStore.getInstance("KeychainStore", "Apple");
            keystore.load(null, null);
        } else {
            String password = getParameter("password");
            String filename = getParameter("file");
            if (filename == null && (type == null || "JCEKS".equals(filename) || "JKS".equals(filename))) {
                filename = ".keystore";
            }
            InputStream in = null;
            try {
                File file = null;
                // Note that not every keystore type must have a file; we could be here
                // for a custom keystore type that we hae no knowledge of, that may (or may
                // not) be loaded from a file. So make no presumptions.
                if (filename != null) {
                    in = getClass().getResourceAsStream(filename);
                    if (in == null ) {
                        file = getKeyStoreFile();
                        if (!file.canRead()) {
                            throw new IOException("File \""+file+"\" cannot be read");
                        }
                        in = new FileInputStream(file);
                    }
                    if (!in.markSupported()) {
                        in = new BufferedInputStream(in);
                    }
                    if (type == null || "JCEKS".equals(filename) || "JKS".equals(filename)) {
                        type = guessStoreType(in);
                    }
                }
                if (provider == null) {
                    keystore = KeyStore.getInstance(type);
                } else {
                    keystore = KeyStore.getInstance(type, provider);
                }
                CombinedKeyStore cks = CombinedKeyStore.newInstance(keystore);
                if (getParameter("complete") == null) {
                    cks.addReadStore(FormSignature.loadDefaultKeyStore());
                }
                keystore = cks;
                keystore.load(in, password == null ? null : password.toCharArray());
            } finally {
                if (in != null) in.close();
            }
            this.changed = false;
            params.put("type", type);
        }
    }

    /**
     * Save the {@link KeyStore} using the {@link #setParameter} parameters specified for
     * this class - for file-based KeyStores like JKS, JCEKS and PKCS#12, this requires the <code>file</code>
     * parameter and optionally the <code>password</code> parameter too (if no password is specified,
     * the empty string is used).
     * @throws IOException if the KeyStore file cannot be saved.
     * @throws GeneralSecurityException if the KeyStore cannot be saved for a cryptographic-related reason.
     */
    public void saveKeyStore() throws IOException, GeneralSecurityException {
        String type = getParameter("type");
        String provider = getParameter("provider");
        String password = getParameter("password");

        if ("pkcs11".equalsIgnoreCase(type)) {
            keystore.store(null, password==null ? new char[0] : password.toCharArray());
        } else if ("keychainstore".equalsIgnoreCase(type) && "apple".equalsIgnoreCase(provider)) {
            keystore.store(null, null);
        } else {
            OutputStream out = null;
            try {
                out = new FileOutputStream(getKeyStoreFile());
                keystore.store(out, password==null ? new char[0] : password.toCharArray());
            } finally {
                if (out!=null) {
                    out.close();
                }
            }
        }
    }

    /**
     * Set the store parameters. The supplied string is a semi-colon
     * delimitered String which contains the parameters that could
     * also be specified individually in calls to {@link #setParameter}.
     * For instance, the following method calls are the same:
     * <pre class="brush:java">
     * setParameters("type=pkcs11;name=eToken;library='/usr/lib/libeTPkcs11.so';enabledMechanisms='{CKM_RSA_PKCS CKM_RSA_PKCS_KEY_PAIR_GEN};attributes(*,CKO_PRIVATE_KEY,*)='{\\nCKA_SIGN=true\\nCKA_DECRYPT=true\\n}'");
     *
     * setParameter("type", "pkcs11");
     * setParameter("name", "eToken");
     * setParameter("library", "/usr/lib/libeTPkcs11.so");
     * setParameter("enabledMechanisms", "{CKM_RSA_PKCS CKM_RSA_PKCS_KEY_PAIR_GEN}");
     * setParameter("attribute(*,CKO_PRIVATE_KEY,*)", "{\nCKA_SIGN=true\nCKA_DECRYPT=true\n}");
     * </pre>
     * <p>
     * Characters can be quoted or preceded with a backslash to treat them as literals.
     * </p><p>
     * If one of the parameters is "configfile", parameters will also be loaded from that file. For example:
     * </p>
     * <pre class="brush:java">
     * setParameters("type=pkcs11;configfile=pkcs11.cfg");
     * </pre>
     * @since 2.11.14
     */
    public void setParameters(String in) {
        this.params = new LinkedHashMap<String,String>();
        if (!in.endsWith(";")) {
            in += ';';
        }
        char quote = 0;
        int eqix = -2, scix = 0;
        for (int i=0;i<in.length();i++) {
            char c = in.charAt(i);
            if (c=='\\' && i < in.length() - 1) {
                c = in.charAt(++i);
                switch(c) {
                    case 'n': c = '\n'; break;
                    case 'r': c = '\r'; break;
                    case 't': c = '\t'; break;
                }
            } else if ((c == '\'' || c == '\"') && (quote == 0 || quote == c)) {
                quote = quote == 0 ? c : 0;
            } else if (c == '=' && quote == 0) {
                eqix = i;
            } else if (c == ';' && quote == 0) {
                String key = null, val = null;
                if (eqix == -2) {
                    key = "";
                    val = in.substring(scix, i);
                } else if (eqix > 0) {
                    key = in.substring(scix, eqix);
                    val = in.substring(eqix+1, i);
                }
                if (key != null) {
                    params.put(key, val);
                    if (key.equals("configfile")) {
                        File configfile = new File(val);
                        if (configfile.canRead()) {
                            // If PKCS11 we will only use the __file, but as this method could
                            // be used with other types as well, read the configuration keys
                            // from that file just in case. File probably has them one-per-line,
                            // convert to semicolon seperated to match our expectations.
                            try {
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                FileInputStream fin = new FileInputStream(in);
                                int d;
                                while ((d=fin.read())>=0) {
                                    if (d == '\n') {
                                        d = ';';
                                    }
                                    if (d != '\r') {
                                        out.write((byte)d);
                                    }
                                }
                                fin.close();
                                out.close();
                                String s = new String(out.toByteArray(), "UTF-8");
                                if (!s.endsWith(";")) {
                                    s = s + ";";
                                }
                                in = in + s;
                            } catch (IOException e) {
                                // tried our best
                            }
                        }
                    }
                }
                eqix = -1;
                scix = i + 1;
            }
        }
    }

    /**
     * Set a parameter to be used when loading or saving the KeyStore.
     * Typical parameters include
     * <table summary="" class="defntable">
     * <tr><th>type</th><td>The KeyStore type: one of "jks", "jceks", "pkcs12", "pkcs11" or "keychainstore"</td></tr>
     * <tr><th>provider</th><td>The KeyStore provider</td></tr>
     * <tr><th>file</th><td>For jks, jceks and pkcs12 KeyStores, the file to save the KeyStore in</td></tr>
     * <tr><th>password</th><td>The store password for the KeyStore</td></tr>
     * <tr><th>keylength</th><td>When creating new key pairs, the number of bits to use for the key (default is 2048)</td></tr>
     * <tr><th>sigalg</th><td>When creating new key pairs, the algorith, to use (default is SHA1withRSA)</td></tr>
     * <tr><th>complete</th><td>If not-null, the current KeyStore defines the complete list of certificates - the system keystore supplied with Java will not be used to supplement the list of certificates</td></tr>
     * </table>
     * For PKCS#11 KeyStores, any of the attributes specified in the
     * <a href="http://download.oracle.com/javase/7/docs/technotes/guides/security/p11guide.html#Config">Java PKCS#11 Reference Guide</a>
     * may be specified as well.
     * @since 2.11.14
     */
    public void setParameter(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("Parameter is null");
        }
        if (value == null) {
            params.remove(key);
        } else {
            params.put(key, value);
        }
    }

    /**
     * Return a parameter set by {@link #setParameter}
     * @since 2.11.14
     */
    public String getParameter(String key) {
        return params.get(key);
    }

    private File getKeyStoreFile() {
        String param = getParameter("file");
        if (param == null) {
            param = ".keystore";
        }
        File file = new File(param);
        if (!file.isAbsolute()) {
            file = new File(propertymanager.getProperty("user.home"), param);
        }
        return file;
    }

    /**
     * Get the KeyStore type
     * @since 2.11.21
     */
    public String getStoreType() {
        return getParameter("type");
    }

    /**
     * Cancel any changes to the current keystore.
     */
    public void cancelKeyStore() {
        keystore = null;
    }

    private static String guessStoreType(InputStream in) throws IOException {
        in.mark(4);
        int v = (in.read()<<24) | (in.read()<<16) | (in.read()<<8) | in.read();
        in.reset();
        if (v == 0xFEEDFEED) {
            return "JKS";
        } else if (v == 0xCECECECE) {
            return "JCEKS";
        } else {
            return "PKCS12";
        }
    }

    /**
     * Initialize a new {@link KeyStore} for this object to manage. The KeyStore
     * will use the <code>provider</code> and <code>type</code> parameters set by
     * {@link #setParameter setParameter()}, or the system defaults, to determine
     * the type of store to create.
     * @throws GeneralSecurityException if the KeyStore cannot be created.
     */
    public void createKeyStore() throws GeneralSecurityException {
        try {
            String type = getParameter("type");
            String provider = getParameter("provider");
            if (type == null) {
                type = KeyStore.getDefaultType();
            } else if (type.equalsIgnoreCase("pkcs11") || type.equalsIgnoreCase("keychainstore")) {
                throw new IllegalStateException("Cannot create new \""+type+"\" KeyStore");
            }

            KeyStore keystore;
            if (provider == null) {
                keystore = KeyStore.getInstance(type);
            } else {
                keystore = KeyStore.getInstance(type, provider);
            }
            CombinedKeyStore cks = CombinedKeyStore.newInstance(keystore);
            if (Util.hasFilePermission() && getParameter("complete") == null) {
                cks.addReadStore(FormSignature.loadDefaultKeyStore());
            }
            this.keystore = cks;
            this.keystore.load(null, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return true if the KeyStore is file based, false otherwise.
     * @since 2.11.14
     */
    public boolean isFileBased() {
        String type = getParameter("type");
        return type == null || type.equalsIgnoreCase("jks") || type.equalsIgnoreCase("pkcs12");
    }

    /**
     * Return true if changes to the KeyStore can be cancelled, or false if they're committed
     * immediately
     * @since 2.11.14
     */
    public boolean isCancellable() {
        String type = getParameter("type");
        return !"pkcs11".equals(type);
    }

    /**
     * Return true if this type of KeyStore uses a per-key password.
     * It is for file-based stores, but isn't for PKCS#11.
     * @since 2.23.5
     */
    public boolean isKeyPasswordUsed() {
        String type = getParameter("type");
        return !"pkcs11".equals(type);
    }

    /**
     * Create a Swing component prompting the user to load the {@link KeyStore}.
     * The initially selected file is the KeyStore
     * managed by this object, if specified.
     * @param root the JComponent to position the dialog relative too.
     * @return true if a KeyStore was loaded, false otherwise
     */
    public boolean loadKeyStore(JComponent root) {
        do {
            Map<String,Object> choice = chooseFile(root, true);
            if (choice == null) {
                return false;
            } else {
                File file = (File)choice.get("file");
                char[] password = (char[])choice.get("password");
                setParameter("file", file.getPath());
                setParameter("password", password == null ? null : new String(password));
                try {
                    loadKeyStore();
                    return true;
                } catch (Exception e) {
                    Util.displayThrowable(e, root);
                }
            }
        } while (true);
    }

    /**
     * Create a Swing compoment prompting the user to save the {@link KeyStore}.
     * The initially selected file is the KeyStore managed by this object, if specified.
     * @param root the JComponent to position the dialog relative too.
     * @return true if the KeyStore was saved, false otherwise
     */
    public boolean saveKeyStore(JComponent root) {
        do {
            Map<String,Object> choice = chooseFile(root, false);
            if (choice == null) {
                return false;
            } else {
                File file = (File)choice.get("file");
                char[] password = (char[])choice.get("password");
                setParameter("file", file.getPath());
                setParameter("password", password == null ? null : new String(password));
                try {
                    saveKeyStore();
                    return true;
                } catch (Exception e) {
                    Util.displayThrowable(e, root);
                }
            }
        } while (true);
    }

    //---------------------------------------------------------------------------------

    /**
     * Given a Certificate object, return a suitable alias to store the
     * Certificate under. The alias will be unique.
     * @param keystore the KeyStore
     * @param cert the Certificate to store
     * @param alias the default name to use - may be <code>null</code>
     */
    private String getNewAlias(X509Certificate cert, String alias)
        throws CertificateException, KeyStoreException
    {
        if (alias == null) {
            alias = FormSignature.getSubjectField(cert, "CN");
            if (alias==null) {
                alias = FormSignature.getSubjectField(cert, "O");
            }
            if (alias == null) {
                alias = cert.getSerialNumber().toString(16);
            }
        }
        String talias = alias;
        int count = 0;
        while (keystore.containsAlias(talias)) {
            talias = alias+"-"+(++count);
        }
        return talias;
    }

    /**
     * Get the {@link KeyStore} managed by this object - will call {@link #loadKeyStore} if it's
     * not already been called.
     */
    public KeyStore getKeyStore() throws GeneralSecurityException, IOException {
        if (keystore == null) {
            try {
                loadKeyStore();
            } catch (IOException e) {
                if ("pkcs11".equalsIgnoreCase(getParameter("type"))) {
                    throw e;            // Can't create PKCs#11.
                } else {
                    createKeyStore();
                }
            }
        }
        return keystore;
    }

    /**
     * Return true if this {@link KeyStore} is "dirty" and needs to be saved to commit
     * any changes, or false if no changes have been made.
     */
    public boolean isChanged() {
        return changed;
    }

    //---------------------------------------------------------------------------

    /**
     * Add the specified X.509 Certificate to the list of trusted root certificates.
     * @param alias the alias to store it under, or <code>null</code> to choose one
     * @param cert the X.509 Certificate to store
     * @return the name the Certificate was stored under, or <code>null</code> if
     * the certificate already existed
     * @throws GeneralSecurityException if the Certificate can not be imported
     */
    public String importCertificate(String alias, X509Certificate cert)
        throws GeneralSecurityException
    {
        if (keystore.getCertificateAlias(cert) == null) {
            alias = getNewAlias(cert, alias);
            keystore.setCertificateEntry(alias, cert);
            propertyChangeSupport.firePropertyChange("alias", null, alias);
            changed = true;
            return alias;
        } else {
            return null;
        }
    }

    /**
     * Import all the X.509 Certificates from the specified file into this {@link KeyStore}.
     * The File may be a {@link KeyStore} file or a file that can be parsed by an X.509
     * {@link CertificateFactory}.
     * @param file the File containing the X.509 Certificates
     * @param alias the initial alias for the imports (may be <code>null</code>)
     * @return a list of the aliases the Certificates were stored under
     * @throws IOException if the Certificates can not be read due to File I/O reasons
     * @throws GeneralSecurityException if the Certificates can not be read for a cryptographic reason
     */
    public String[] importAllCertificates(File file, String alias) throws GeneralSecurityException, IOException {
        String[] added;
        InputStream in = null;
        String format = null;
        try {
            in = new FileInputStream(file);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            cf.generateCertificate(in);
            format = "X.509";
        } catch (IOException e) {
        } catch (CertificateException e) {
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException e2) {}
            }
        }

        if (format == null) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".pkcs12") || name.endsWith(".pfx") || name.endsWith(".p12")) {
                format = "pkcs12";
            } else {
                format = "JKS";
            }
        }

        try {
            in = new FileInputStream(file);
            added = importAllCertificates(in, null, format);
        } finally {
            try { in.close(); } catch (IOException e2) {}
        }
        return added;
    }

    /**
     * Import all the X.509 Certificates from an {@link InputStream} into the {@link KeyStore}.
     * The InputStream is closed on completion.
     *
     * @param in the InputStream to read the X.509 Certificates from
     *
     * @param alias if importing from a list of X.509 Certificates the alias
     * to store the Certificate against, or <code>null</code> to pick one
     *
     * @param format one of "X.509", "JKS" or "pkcs12" to specify the format of
     * <code>in</code> - a list of X.509 certificates, a JKS/JCEKS KeyStore or a PKCS#12
     * KeyStore
     *
     * @return a list of aliases added to the KeyStore
     * @throws IOException if the Certificates can not be read due to File I/O reasons
     * @throws GeneralSecurityException if the Certificates can not be read for a cryptographic reason
     */
    public String[] importAllCertificates(InputStream in, String alias, String format)
        throws GeneralSecurityException, IOException
    {
        try {
            List<String> added  = new ArrayList<String>();
            if (!in.markSupported()) {
                in = new BufferedInputStream(in);
            }
            if ("X.509".equalsIgnoreCase(format)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                while (in.available() > 0) {
                    X509Certificate cert = (X509Certificate)cf.generateCertificate(in);
                    String newalias = importCertificate(alias, cert);
                    if (newalias != null) {
                        added.add(newalias);
                    }
                }
            } else {
                format = guessStoreType(in);
                KeyStore tempkeystore = KeyStore.getInstance(format);
                try {
                    tempkeystore.load(in, null);
                } finally {
                    in.close();
                }
                for (Enumeration<String> i = tempkeystore.aliases();i.hasMoreElements();) {
                    alias = i.nextElement();
                    Certificate cert;
                    if (tempkeystore.isKeyEntry(alias)) {
                        Certificate[] chain = tempkeystore.getCertificateChain(alias);
                        cert = chain == null ? null : chain[chain.length-1];
                    } else {
                        cert = tempkeystore.getCertificate(alias);
                    }
                    if (cert instanceof X509Certificate) {
                        alias = importCertificate(alias, (X509Certificate)cert);
                        if (alias!=null) {
                            added.add(alias);
                        }
                    }
                }
            }
            return (String[])added.toArray(new String[0]);
        } finally {
            in.close();
        }
    }

    /**
     * Add a {@link PrivateKey} to the {@link KeyStore}. The Key is loaded from the specified KeyStore
     * @param store the KeyStore to load the private key from
     * @param alias the name the private key is stored under
     * @param password the password to access the private key
     * @throws GeneralSecurityException if the Key could not be extracted or stored
     */
    public String importPrivateKey(KeyStore store, String alias, char[] password)
        throws GeneralSecurityException
    {
        PrivateKey key = (PrivateKey)store.getKey(alias, password);
        Certificate[] certs = store.getCertificateChain(alias);
        if (certs == null) {
            throw new GeneralSecurityException("No entry for '"+alias+"'");
        } else {
            return importPrivateKey(key, certs, alias, password);
        }
    }

    /**
     * Add a {@link PrivateKey} to the {@link KeyStore}.
     * @param key the Key
     * @param certs the Certificate chain
     * @param alias a suggestion for the name the private key should be stored under
     * @param password the password to encrypt the private key with
     * @throws GeneralSecurityException if the Key could not be stored
     */
    public String importPrivateKey(PrivateKey key, Certificate[] certs, String alias, final char[] password)
        throws GeneralSecurityException
    {
        alias = getNewAlias((X509Certificate)certs[0], alias);
        KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(key, certs);
        KeyStore.PasswordProtection protection = password == null ? null : new KeyStore.PasswordProtection(password);
        keystore.setEntry(alias, entry, protection);
        propertyChangeSupport.firePropertyChange("alias", null, alias);
        changed = true;
        return alias;
    }

    /**
     * Export a {@link PrivateKey} and associated {@link X509Certificate Certificate Chain}
     * from the {@link KeyStore} to a PKCS#12 object. The PKCS#12 object is written to the
     * {@link OutputStream}, and the stream is left open on completion of this method.
     * @param out the OutputStream
     * @param alias the alias of the entry to export
     * @param password the password used to access the private key
     * @throws IOException if an I/O exception occurs while writing
     * @throws GeneralSecurityException if the PrivateKey cannot be extracted from the KeyStore
     */
    public void exportPKCS12Certificate(OutputStream out, String alias, char[] password)
        throws GeneralSecurityException, IOException
    {
        Key key = keystore.getKey(alias, password);
        KeyStore store = KeyStore.getInstance("pkcs12");
        store.load(null, null);
        Certificate[] chain = keystore.getCertificateChain(alias);
        store.setKeyEntry(alias, key, password, chain);
        store.store(out, password);
    }

    /**
     * Indicates whether this key store contains the specified certificate.
     * @param cert the certificate to test
     */
    public boolean contains(Certificate cert) throws GeneralSecurityException, IOException {
        return getKeyStore().getCertificateAlias(cert) != null;
    }

    /**
     * Export a public {@link X509Certificate} from the {@link KeyStore} to a
     * DES-encoded Certificate file. The file is written
     * to the specified OutputStream, and the stream is left open on completion of this method.
     * @param out the OutputStream
     * @param alias the alias of the entry to export
     * @throws IOException if an I/O exception occurs while writing
     * @throws GeneralSecurityException if the Certificate cannot be extracted from the KeyStore
     */
    public void exportCertificate(OutputStream out, String alias)
        throws GeneralSecurityException, IOException
    {
        out.write("-----BEGIN X509 CERTIFICATE-----\n".getBytes("ISO-8859-1"));
        out.flush();
        OutputStream bout = Base64.getEncoder(out, false);
        bout.write(keystore.getCertificate(alias).getEncoded());
        bout.flush();
        out.write("\n-----END X509 CERTIFICATE-----\n".getBytes("ISO-8859-1"));
    }

    /**
     * Delete the specified entry ({@link PrivateKey} or {@link X509Certificate}) from the
     * {@link KeyStore}
     * @param alias the entry to delete
     * @throws GeneralSecurityException if the entry cannot be deleted from the KeyStore
     */
    public void deleteEntry(String alias) throws GeneralSecurityException {
        if (keystore.containsAlias(alias)) {
            keystore.deleteEntry(alias);
            propertyChangeSupport.firePropertyChange("alias", alias, null);
            changed = true;
        }
    }

    /**
     * Return true if this KeyStore can store Secret (symmetric) key information,
     * or can be converted to one that can.
     * @see #canStoreSecretKeys
     * @since 2.11.22
     */
    public boolean canStoreSecretKeysOnConversion() {
        String st = getStoreType();
        return st == null || st.equalsIgnoreCase("jks") || canStoreSecretKeys();
    }

    /**
     * Return true if this KeyStore can store Secret (symmetric) key information.
     * @since 2.11.21
     * @see #canStoreSecretKeysOnConversion
     */
    public boolean canStoreSecretKeys() {
        String st = getStoreType();
        return st == null || st.equalsIgnoreCase("jceks") || st.equalsIgnoreCase("pkcs11");
    }

    /**
     * Store a secret value in the KeyStore - any data which needs to be
     * password protected.
     * @param alias the alias
     * @param key the key to store, or null to delete any secret key with this alias
     * @param password the password that will be used to encrypt this data.
     * if null, the KeyStore password is tried.
     * @since 2.11.21
     */
    public void putSecret(String alias, SecretKey key, char[] password) throws GeneralSecurityException, IOException {
        if (key == null) {
            keystore.deleteEntry(alias);
        } else {
            if (!canStoreSecretKeys()) {
                convertJKStoJCEKS();
            }
            if (password == null) {
                String pw = getParameter("password");
                if (pw != null) {
                    password = pw.toCharArray();
                }
            }
            keystore.setKeyEntry(alias, key, password, null);
        }
        changed = true;
    }

    /**
     * Get a secret value from the KeyStore, as set by {@link #putSecret putSecret()}
     * @param alias the alias
     * @param password the password - if null, the KeyStore password is tried.
     * @since 2.11.21
     */
    public SecretKey getSecret(String alias, String type, char[] password) throws GeneralSecurityException {
        if (password == null) {
            String pw = getParameter("password");
            if (pw != null) {
                password = pw.toCharArray();
            }
        }
        Key key = keystore.getKey(alias, password);
        if (key instanceof SecretKey) {
            return (SecretKey)key;
        }
        return null;
    }

    private void convertJKStoJCEKS() throws GeneralSecurityException, IOException {
        if (isChanged()) {
            saveKeyStore();
        }
        setParameter("type", "JCEKS");
        loadKeyStore();
    }

    /**
     * Create a new 2048-bit RSA {@link PrivateKey} with self-signed {@link X509Certificate},
     * and add it to the {@link KeyStore}.
     * @param alias the alias to store it as
     * @param name the CN of the X.509 certificate DN
     * @param unit the OU of the X.509 certificate DN
     * @param organization the O of the X.509 certificate DN
     * @param city the L of the X.509 certificate DN
     * @param country the C of the X.509 certificate DN
     * @param password the password to store the key with
     * @param days the number of days the Key is valid for from now.
     * @return the alias the new Key is stored under in the KeyStore
     * @throws GeneralSecurityException if something goes wrong
     */
    public String createSelfSignedKey(String alias, String name, String unit, String organization, String city, String state, String country, char[] password, int days) throws GeneralSecurityException {
        String algorithm = getParameter("sigalg");
        if (algorithm == null) {
            algorithm = "SHA1withRSA";
        }
        int keylength = 2048;
        try {
            keylength = Integer.parseInt(getParameter("keylength"));
        } catch (Exception e) { 
            // Ignore
        }
        return createSelfSignedKey(alias, name, unit, organization, city, state, country, password, days, algorithm, keylength);
    }

    /**
     * Create a new {@link PrivateKey} of the specified algorithm, with self-signed {@link X509Certificate},
     * and add it to the {@link KeyStore}.
     * @param alias the alias to store it as
     * @param name the CN of the X.509 certificate DN
     * @param unit the OU of the X.509 certificate DN
     * @param organization the O of the X.509 certificate DN
     * @param city the L of the X.509 certificate DN
     * @param country the C of the X.509 certificate DN
     * @param password the password to store the key with
     * @param days the number of days the Key is valid for from now.
     * @param algorithm the Signature algorithm, eg "SHA1withRSA", "SHA256withRSA", "SHA256withDSA"
     * @param keylength the length of the key in bits, eg 1024, 2048, 4096
     * @return the alias the new Key is stored under in the KeyStore
     * @throws GeneralSecurityException if something goes wrong
     * @since 2.11.14
     */
    public String createSelfSignedKey(String alias, String name, String unit, String organization, String city, String state, String country, char[] password, int days, String algorithm, int keylength) throws GeneralSecurityException {
        String dn = "";
        if (name != null && name.trim().length() > 0) {
            dn += "CN=" + name.replaceAll(",", "\\\\,") + ", ";
        }
        if (unit != null && unit.trim().length() > 0) {
            dn += "OU=" + unit.replaceAll(",", "\\\\,") + ", ";
        }
        if (organization != null && organization.trim().length() > 0) {
            dn += "O=" + organization.replaceAll(",", "\\\\,") + ", ";
        }
        if (city != null && city.trim().length() > 0) {
            dn += "L=" + city.replaceAll(",", "\\\\,") + ", ";
        }
        if (state != null && state.trim().length() > 0) {
            dn += "ST=" + state.replaceAll(",", "\\\\,") + ", ";
        }
        if (country != null && country.trim().length() > 0) {
            dn += "C=" + country.replaceAll(",", "\\\\,") + ", ";
        }
        if (dn.length() == 0) {
            throw new IllegalArgumentException("No DN keys specified");
        } else {
            dn = dn.substring(0, dn.length() - 2);
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm.endsWith("DSA") ? "DSA" : "RSA");
        generator.initialize(keylength, SecureRandom.getInstance("SHA1PRNG"));
        KeyPair pair = generator.generateKeyPair();

        X509CertificateBuilder builder = new X509CertificateBuilder();
        builder.setKeyPair(pair);
        builder.setValidity(days);
        builder.setAlgorithm(algorithm, null);
        builder.setIssuer(dn);
        X509Certificate cert = builder.build(null);
        return importPrivateKey(pair.getPrivate(), new X509Certificate[] { cert }, alias, password);
    }

    /**
     * Return true if this KeyStoreManager has permission to create a new self-signed certificate.
     */
    public boolean canCreateSelfSignedCertificate() {
        return true;
    }

    private Map<String,Object> chooseFile(JComponent root, final boolean open) {
        String keystoretype = getParameter("type");
        String keystorepassword = getParameter("password");
        File keystorefile = getKeyStoreFile();

        final JFileChooser filechooser = Util.fixFileChooser(new JFileChooser(keystorefile));
        if (keystorefile.exists()) {
            filechooser.ensureFileIsVisible(keystorefile);
        }
        filechooser.setControlButtonsAreShown(false);
        if (keystorefile.exists() || !open) {
            filechooser.setSelectedFile(keystorefile);
        }
        if (!open) {
            filechooser.setDialogType(JFileChooser.SAVE_DIALOG);
        }
        filechooser.setFileHidingEnabled(false);

        if ("JKS".equals(keystoretype) || "JCEKS".equals(keystoretype)) {
            filechooser.addChoosableFileFilter(FILTER_KEYSTORE_JKS);
            filechooser.setFileFilter(FILTER_KEYSTORE_JKS);
        } else if ("pkcs12".equals(keystoretype)) {
            filechooser.addChoosableFileFilter(FILTER_KEYSTORE_PKCS12);
            filechooser.setFileFilter(FILTER_KEYSTORE_PKCS12);
        } else {
            filechooser.addChoosableFileFilter(FILTER_KEYSTORE);
            filechooser.setFileFilter(FILTER_KEYSTORE);
        }

        final JPasswordField passwordfield;
        if (keystorepassword == null) {
            passwordfield = new JPasswordField();
        } else {
            passwordfield = new JPasswordField(keystorepassword);
        }

        DialogPanel panel = new DialogPanel() {
            public String validateDialog() {
                if (!open && passwordfield.getPassword().length==0) {
                    return UIManager.getString("PDFViewer.ChoosePassword");
                } else if (filechooser.getSelectedFile()==null) {
                    return UIManager.getString("PDFViewer.ChooseFile");
                } else {
                    return null;
                }
            }
        };
        panel.addComponent(filechooser);
        panel.addComponent(UIManager.getString("PDFViewer.Password"), passwordfield);
        if (panel.showDialog(root)) {
            Map<String,Object> m = new LinkedHashMap<String,Object>();
            m.put("file", filechooser.getSelectedFile());
            if (passwordfield.getPassword().length > 0) {
                m.put("password", passwordfield.getPassword());
            }
            return m;
        } else {
            return null;
        }
    }

    /**
     * Add a Listener to changes to this {@link KeyStore}. A {@link PropertyChangeEvent}
     * occurs when a new entry is added or removed from the {@link KeyStore} managed
     * by this KeyStoreManager. Duplicate PropertyChangeListeners are ignored and
     * listeners are held in this class with a weak-reference and so will be removed
     * automatically on garbage collection.
     * @param listener the Listener.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove a Listener that was previously added in {@link #addPropertyChangeListener}
     * @param listener a listener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    private static FileFilter createFileFilter(final String description, final Pattern pattern) {
        return new FileFilter() {
            public String getDescription() {
                return UIManager.getString(description);
            }
            public boolean accept(File f) {
                return f.isDirectory() || pattern.matcher(f.getName().toLowerCase()).matches();
            }
        };
    }

    private class DefaultCallbackHandler implements CallbackHandler {
        public void handle(Callback[] list) {
            for (Callback cb : list) {
                if (cb instanceof PasswordCallback) {
                    final PasswordCallback pc = (PasswordCallback)cb;
                    String password = getParameter("password");
                    if (password != null) {
                        pc.setPassword(password.toCharArray());
                    } else {
                        Runnable runnable = new Runnable() {
                            public void run() {
                                Frame frame = JOptionPane.getFrameForComponent(viewer);
                                JPasswordField field = new JPasswordField(16);
                                JPanel panel = new JPanel(new BorderLayout());
                                panel.add(new JLabel(UIManager.getString("PDFViewer.Password")), BorderLayout.WEST);
                                panel.add(field, BorderLayout.CENTER);
                                if (JOptionPane.showConfirmDialog(frame, panel, UIManager.getString("PDFViewer.Password"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == 0) {
                                    pc.setPassword(field.getPassword());
                                }
                            }
                        };
                        if (SwingUtilities.isEventDispatchThread()) {
                            runnable.run();
                        } else {
                            try {
                                SwingUtilities.invokeAndWait(runnable);
                            } catch (InvocationTargetException e) {
                                Throwable e2 = e.getCause();
                                if (e2 instanceof RuntimeException) {
                                    throw (RuntimeException)e2;
                                } else {
                                    throw new RuntimeException(e);
                                }
                                } catch (InterruptedException e) {
                            }
                        }
                    }
                }
            }
        }
    }
}
