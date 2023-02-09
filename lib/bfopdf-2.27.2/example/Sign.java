// $Id: Sign.java 44689 2022-10-26 16:44:15Z mike $

import java.util.*;
import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import org.faceless.pdf2.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;


/**
 * An example showing how to digitally sign a PDF document.
 * Writes to "Sign.pdf"
 */
public class Sign
{
    private static String file;                         // Filename of PDF
    private static String keystorename;                 // Filename of KeyStore file
    private static char[] storepass;                    // Password for keystore
    private static String keyalias;                     // Alias of key in KeyStore (first if unspecified)
    private static String pdfpass;                      // Password to open the PDF
    private static char[] keypass;                      // Password for key
    private static String reason;                       // Reason for signing
    private static String location;                     // Location of signing
    private static String fieldname;                    // Field of signing
    private static List<URL> timestampurls = new ArrayList<URL>(); // URLs of timestamp servers
    private static String keystoretype = "JKS";         // KeyStore type
    private static Provider provider;                   // Provider
    private static String algorithm;                    // Digest algorithm
    private static boolean certify;                     // Whether to certify the PDF if possible
    private static boolean pades;                       // Whether to use PDF 2.0 PAdES signatures
    private static boolean ocsp;                        // Whether to include OCSP verification in signature
    private static SignatureHandlerFactory handler = FormSignature.HANDLER_ACROBATSIX;

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        getargs(args);
        if (keypass == null && storepass!=null) {
            keypass = storepass;
        }

        // 2. Load the keystore. If a provider was specified, use it.
        //
        KeyStore keystore;
        if (provider != null) {
            try {
                keystore = KeyStore.getInstance(keystoretype, provider);
            } catch (GeneralSecurityException e) {
                try {
                    keystore = KeyStore.getInstance(keystoretype);
                } catch (Exception e2) {
                    throw e;
                }
            }
        } else {
            keystore = KeyStore.getInstance(keystoretype);
        }
        keystore.load(keystorename == null ? null : new FileInputStream(keystorename), storepass);
        if (keystorename == null && (keystoretype.equals("JKS") || keystoretype.equals("PKCS12"))) {
            // No alias specified - create a new identity
            keyalias = "0";
            keypass = storepass = new char[0];
            createDummyIdentity(keystore, keyalias, keypass, "rsa");
        } else {
            if (keyalias == null) {
                for (Enumeration<String> i = keystore.aliases();keyalias == null && i.hasMoreElements();) {
                    String alias = i.nextElement();
                    if (keystore.isKeyEntry(alias)) {
                        keyalias = alias;
                    }
                }
            }
        }

        if (provider != null && handler instanceof AcrobatSignatureHandlerFactory) {
            ((AcrobatSignatureHandlerFactory)handler).setProvider(provider);
        }
        if (handler instanceof AcrobatSignatureHandlerFactory) {
            ((AcrobatSignatureHandlerFactory)handler).setTimeStampServers(timestampurls);
        }
        if (algorithm != null && handler instanceof AcrobatSignatureHandlerFactory) {
            ((AcrobatSignatureHandlerFactory)handler).setDigestAlgorithm(algorithm);
        }
        if (pades && handler instanceof AcrobatSignatureHandlerFactory) {
            ((AcrobatSignatureHandlerFactory)handler).setPAdES(true);
        }
        if (ocsp && handler instanceof AcrobatSignatureHandlerFactory) {
            ((AcrobatSignatureHandlerFactory)handler).setValidateCertificatesOnSigning(ocsp);
        }
        String name = null;
        if (handler instanceof PKCS7SignatureHandler) {
            // Get the name from the X.509 certificate we're using for signing.
            PKCS7SignatureHandler pkcs7 =  (PKCS7SignatureHandler)handler;
            name = FormSignature.getSubjectField(pkcs7.getCertificates()[0], "CN");
        }


        // 3. Load the PDF from the file, sign it and save it.
        //
        InputStream in = new FileInputStream(file);
        PDF pdf = new PDF(new PDFReader(in, pdfpass));
        in.close();
        sign(pdf, handler, keystore, keyalias, keypass, fieldname, name, reason, location, certify);
        OutputStream out = new FileOutputStream("Sign.pdf");
        pdf.render(out);
        out.close();
    }

    /**
     * Sign the PDF. A Certifying signature will be created if this is the first
     * signature in the file, otherwise the document will be signed normally.
     *
     * @param pdf the PDF
     * @param keystore the KeyStore to load the key from
     * @param handler the SignatureHandler to sign with
     * @param alias the name the signing key in the KeyStore
     * @param password the password to use to get the key from the KeyStore
     * @param fieldname the name of the signature field. If no such field exists it will be created.
     * @param name the name to put on the signature (may be null)
     * @param reason the reason to put on the signature (may be null)
     * @param location the location to put on the signature (may be null)
     */
    public static void sign(PDF pdf, SignatureHandlerFactory handler, KeyStore keystore, String alias, char[] password, String fieldname, String name, String reason, String location, boolean certify) throws GeneralSecurityException, IOException {
        Form form = pdf.getForm();
        FormSignature field = (FormSignature)form.getElement(fieldname);
//        PDFStyle style = new PDFStyle();
//        style.setFont(embeddedfont, 12);
//        style.setFillColor(java.awt.Color.black);
//        form.setTextStyle(style);
        if (field == null) {
            field = new FormSignature();
            if (fieldname == null) {      // Create new unique field name
                for (int i=0;form.getElements().containsKey(fieldname="Sig"+i);i++);
            }
            form.addElement(fieldname, field);
//            field.addAnnotation(pdf.getPage(0), 100, 500, 300, 600);
        }
        field.sign(keystore, alias, password, handler);

        field.setName(name);
        field.setReason(reason);
        field.setLocation(location);

        if (certify) {
            System.out.println("Not signed - certifying "+pdf.getNumberOfRevisions());
            field.setCertificationType(FormSignature.CERTIFICATION_NOCHANGES, null);
        } else if (pdf.getBasicOutputProfile().isSet(OutputProfile.Feature.DigitallySigned)) {
            System.out.println("Already signed - counter-signing");
        } else {
            System.out.println("Not signed - applying new signature");
        }
    }

    private static void getargs(String[] args) throws GeneralSecurityException, MalformedURLException {
        for (int i=0;i<args.length;i++) {
            if (args[i].startsWith("--pdfpassword")) {
                pdfpass = args[++i];
            } else if (args[i].equals("--password") || args[i].equals("--storepassword")) {
                storepass = args[++i].toCharArray();
            } else if (args[i].equals("--keypassword")) {
                keypass = args[++i].toCharArray();
            } else if (args[i].equals("--fieldname")) {
                fieldname = args[++i];
            } else if (args[i].equals("--algorithm")) {
                algorithm = args[++i];
            } else if (args[i].equals("--reason")) {
                reason = args[++i];
            } else if (args[i].equals("--location")) {
                location = args[++i];
            } else if (args[i].equals("--keyalias") || args[i].equals("--alias")) {
                keyalias = args[++i];
            } else if (args[i].equals("--keystore")) {
                keystorename = args[++i];
            } else if (args[i].equals("--keystoretype")) {
                keystoretype = args[++i];
            } else if (args[i].equals("--provider")) {
                String name = args[++i];
                if ("bc".equalsIgnoreCase(name)) {
                    name = "org.bouncycastle.jce.provider.BouncyCastleProvider";
                } else if ("iaik".equalsIgnoreCase(name)) {
                    name = "iaik.security.provider.IAIK";
                }
                try {
                    provider = (Provider)Class.forName(name).newInstance();;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            } else if (args[i].equals("--timestampserver")) {
                timestampurls.add(new URL(args[++i]));
            } else if (args[i].equals("--laxssl")) {
                laxssl();
            } else if (args[i].equals("--certify")) {
                certify = true;
            } else if (args[i].equals("--pades")) {
                pades = true;
            } else if (args[i].equals("--ocsp")) {
                ocsp = true;
            } else if (args[i].equals("--nocertify")) {
                certify = false;
            } else if (args[i].equals("--pkcs11")) {
                try {
                    byte[] config = args[++i].replace(';','\n').getBytes("ISO-8859-1");
                    // This class is not on Windows 64-bit! Thanks, Sun.
                    Class<?> c = Class.forName("sun.security.pkcs11.SunPKCS11");
                    provider = (Provider)c.getConstructor(new Class[] { InputStream.class }).newInstance(new Object[] { new ByteArrayInputStream(config) });
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
                keystoretype = "pkcs11";
            } else if (file == null) {
                file = args[i];
            } else {
                usage("Unknown argument \""+args[i]+"\"");
            }
        }
        if (file == null) {
            usage("No filename specified");
        }
    }

    private static void usage(String errorstring) {
        System.err.println("ERROR: "+errorstring);
        System.err.println("Usage: java Sign --keystore <file> --password <password> [--reason <text>]");
        System.err.println("            [--alias <alias>] [--keypassword <password>]");
        System.err.println("            [--pdfpassword <password>] [--keystoretype <jks|pkcs12>");
        System.err.println("            [--provider <provider>] [--timestampserver <url>]");
        System.err.println("            [--location <location comment>] [--fieldname <fieldname>]");
        System.err.println("            [--(no)certify] [--laxssl] [--pkcs11 <args>] input.pdf");
        System.err.println();
        System.err.println("--keystore         The KeyStore containing the private key to sign the PDF with");
        System.err.println("--password         The password required to open the keystore.");
        System.err.println("--alias            (opt) The name of the key in the keystore (default='mykey'");
        System.err.println("--keypassword      (opt) The password for the private key (default=password)");
        System.err.println("--pdfpassword      (opt) The password required to decrypt the PDF");
        System.err.println("--handler          (opt) The type of signature handler to use (default='acrobat6')");
        System.err.println("--timestampserver  (opt) The URL of an RFC3161 Timestamp server (may be specified multiple times)");
        System.err.println("--laxssl           (opt) If connecting to an HTTP Timestamp server, turn off");
        System.err.println("                         any form of certificate checking. Useful for testing");
        System.err.println("--keystoretype     (opt) The type of KeyStore. Typically \"JKS\" or \"PKCS12\"");
        System.err.println("--provider         (opt) Which JCE Provider to use for all crypto functions (\"bc\", \"iaik\" or classname)");
        System.err.println("--reason           (opt) The reason the document is being signed");
        System.err.println("--location         (opt) The location the document is being signed at");
        System.err.println("--fieldname        (opt) The name of the signature field");
        System.err.println("--algorithm        (opt) Digest algorithm: SHA1, SHA256, SHA384, SHA512, MD5");
        System.err.println("--(no)certify      (opt) Whether to certify the PDF if possible");
        System.err.println("--pades            (opt) If true, use the PDF 2.0 PAdES standard for signing");
        System.err.println("--ocsp             (opt) If true, verify certificates with OCSP before signing");
        System.err.println("--pkcs11           (opt) Additional arguments to use when signing with a PKCS#11 key,");
        System.err.println("                         e.g. \"library=/Library/OpenSC/lib/opensc-pkcs11.so;slot=1\"");
        System.err.println();
        System.err.println("Signs a PDF document and writes it to \"Sign.pdf\". To create a self-signed");
        System.err.println("keystore for testing, run the following;");
        System.err.println();
        System.err.println("  keytool -genkey -keyalg RSA -sigalg MD5withRSA -keystore testkeystorefile");
        System.exit(0);
    }

    /**
     * You can call this method to turn off all forms of SSL certificate checking:
     * useful if you're using a timestampserver on HTTPS with a self-signed certificate
     */
    private static void laxssl() throws GeneralSecurityException {
        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
        sc.init(null, new javax.net.ssl.TrustManager[] { new javax.net.ssl.X509TrustManager() {
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String auth) { }
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String auth) { }
            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
        } }, null);
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
            public boolean verify(String urlHostname, javax.net.ssl.SSLSession session) { return true; }
        });
    }

    /**
     * This is an unsupported, undocumented way of creating an identity for testing.
     * In general we'd recommend "keytool" or "openssl" for this, but we use this example
     * for testing so this is a useful method.
     * The "X509CertificateBuilder" class is one of our internal helpers - don't rely on it
     */
    private static void createDummyIdentity(KeyStore keystore, String keyalias, char[] keypass, String type) throws GeneralSecurityException {
        // This uses one of our undocumented helper classes, don't rely on it.
        KeyPairGenerator generator;
        String algo;
        if ("rsa".equalsIgnoreCase(type)) {
            algo = "SHA256withRSA";
            generator = provider != null ? KeyPairGenerator.getInstance("RSA", provider) : KeyPairGenerator.getInstance("RSA");
            RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
            generator.initialize(spec);
        } else if ("ecdsa".equalsIgnoreCase(type)) {
            algo = "SHA256withECDSA";
            generator = provider != null ? KeyPairGenerator.getInstance("EC", provider) : KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec spec = new ECGenParameterSpec("secp256r1");
//            ECGenParameterSpec spec = new ECGenParameterSpec("brainpoolP256r1");      // Requires BouncyCastle, and deprecated anyway.
            generator.initialize(spec);
        } else if ("ed25519".equalsIgnoreCase(type)) {
            generator = KeyPairGenerator.getInstance(algo = "Ed25519");        // This requires Java 15
        } else if ("ed448".equalsIgnoreCase(type)) {
            generator = KeyPairGenerator.getInstance(algo = "Ed448");          // Requires BouncyCastle - SHAKE256 digest not in default JCE
        } else {
            throw new IllegalArgumentException("Unknown type \"" + type + "\"");
        }
        KeyPair pair = generator.generateKeyPair();
        org.faceless.util.asn1.X509CertificateBuilder builder = new org.faceless.util.asn1.X509CertificateBuilder();
        builder.setIssuer("CN=test");
        builder.setKeyPair(pair);
        builder.setValidity(365);
        builder.setAlgorithm(algo, null);
        X509Certificate cert = builder.build(provider);
        KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(pair.getPrivate(), new X509Certificate[] { cert });
        keystore.setEntry(keyalias, entry, new KeyStore.PasswordProtection(keypass));
    }
}
