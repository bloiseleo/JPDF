import org.faceless.pdf2.*;
import org.faceless.util.*;
import java.io.*;
import java.awt.color.ColorSpace;
import java.awt.Color;
import java.util.*;
import java.net.URL;
import java.awt.geom.*;
import java.awt.Shape;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.sax.*;
import javax.xml.transform.dom.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import java.text.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.logging.*;

/**
 * Usage: java Dump [--out <filename>] [--html] [--full] [--keystore <keystore>]
 *                  [--password <password>] [--provider <providername>] [--laxssl] <input>...
 *
 * This example takes a PDF file and dumps out as much information as possible
 * about it. It's very complete so may display a lot of information!
* <input> may be a File, a URL, or "-" to read the PDF from stdin.
 *
 * The command line options are:
 * --out <filename>             Redirects output to a file. The file will be in UTF-8
 *                              format, unlike output to System.out which is escaped.
 * --full                       Extract the Full OutputProfile rather than the basic one.
 * --html                       Dump the output in HTML format rather than plain text.
 * --keystore <keystore>        Specify the keystore, for use with public key encrypted docs,
 *                              or for verifying signatures against a list of trusted roots.
 * --password <password>        Specify the open password for the document.
 * --provider <provider>        Use the specified Security Provider. Typical values for
 *                              this would be "BC" to use the BouncyCastle provider, "IAIK"
 *                              to use the IAIK provider, "SUN" for the default provider
 *                              supplied with Java, or the class name of a Provider.
 * --laxssl                     Don't check certificates when loading a PDF from an HTTPS URL
 */
public class Dump
{
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("USAGE: java Dump [--out <filename>] [--keystore keystore] [--full] [--html] [--password <password>] [--provider <providername>] [--laxssl] <file|url|-> ...\n");
            System.err.println("       Output filename will have $dir replaced with path to input file, $name replaced with filename (less any .pdf suffix)");
            System.exit(1);
        }

        PrintStream SYSTEMOUT = new PrintStream(System.out, true, "UTF-8");
        PrintStream out = SYSTEMOUT;
        String outputFileName = null, nextOutputFileName = null;
        String password = null, format = "text";
        KeyStore keystore = null;
        boolean fullprofile = false;
        boolean redirlogging = true;    // Do we presume java.util.logging and try to redir logs to the same output?

        for (int i=0;i<args.length;i++) {
            if (args[i].equals("--password")) {
                password = args[++i];
            } else if (args[i].equals("--keystore")) {
                // This is pretty basic and assumes JKS not PKCS12
                keystore = KeyStore.getInstance("JKS");
                FileInputStream kin = new FileInputStream(args[++i]);
                keystore.load(kin, null);
                kin.close();
            } else if (args[i].equals("--laxssl")) {
                laxssl();
            } else if (args[i].equals("--full")) {
                fullprofile = true;
            } else if (args[i].equals("--html")) {
                format = "html";
            } else if (args[i].equals("--text")) {
                format = "text";
            } else if (args[i].equals("--trusted")) {
                keystore = FormSignature.loadTrustedKeyStore();
            } else if (args[i].equals("--provider")) {
                String provname = args[++i];
                if (provname.equalsIgnoreCase("BC")) {
                    provname = "org.bouncycastle.jce.provider.BouncyCastleProvider";
                } else if (provname.equalsIgnoreCase("IAIK")) {
                    provname = "iaik.security.provider.IAIK";
                }
                Provider provider = Security.getProvider(provname);
                if (provider == null) {
                    Class cl = null;
                    try {
                        cl = Class.forName(provname);
                    } catch (ClassNotFoundException e) {}
                    if (cl != null && Provider.class.isAssignableFrom(cl)) {
                        provider = (Provider)cl.newInstance();
                    } else {
                        throw new IllegalArgumentException("No Security Provider \"" + provname + "\"");
                    }
                }
                Security.insertProviderAt(provider, 1);
            } else if (args[i].equals("--out")) {
                nextOutputFileName = args[++i];
            } else {
                String filename = args[i];
                String workOutputFileName = nextOutputFileName;
                if (workOutputFileName != null) {
                    if (workOutputFileName.contains("$name")) {
                        String base = new File(filename).getName();
                        if (base.toLowerCase().endsWith(".pdf")) {
                            base = base.substring(0, base.length() - 4);
                        }
                        workOutputFileName = workOutputFileName.replaceAll("\\$name", base);
                    }
                    if (workOutputFileName.contains("$dir")) {
                        workOutputFileName = workOutputFileName.replaceAll("\\$dir", new File(filename).getParent());
                    }
                }
                if (workOutputFileName != null && !workOutputFileName.equals(outputFileName)) {
                    if (outputFileName != null) {
                        out.close();
                    }
                    outputFileName = workOutputFileName;
                    out = new PrintStream(new FileOutputStream(outputFileName), true, "UTF-8");
                    System.err.println("Creating \"" + outputFileName + "\"");
                    if (redirlogging) {
                        // This nasty looking block is just to redirect logging to the same output
                        // file. It presumes a default java.util.logging setup.
                        Logger logger = Logger.getLogger("org.faceless.pdf2");
                        logger.setUseParentHandlers(false);
                        while (logger.getHandlers().length > 0) {
                            logger.getHandlers()[0].flush();
                            logger.removeHandler(logger.getHandlers()[0]);
                        }
                        logger.addHandler(new StreamHandler(out, new SimpleFormatter() {
                            public String format(LogRecord record) {
                                StringWriter sb = new StringWriter();
                                sb.append(record.getLevel().toString());
                                sb.append(' ');
                                sb.append(record.getMessage());
                                sb.append('\n');
                                if (record.getThrown() != null) {
                                    record.getThrown().printStackTrace(new PrintWriter(sb));
                                }
                                return sb.toString();
                            }
                        }) {
                            @Override public synchronized void publish(final LogRecord record) {
                                super.publish(record);
                                flush();
                            }
                        });
                    }
                }
                try {
                    EncryptionHandler handler = null;
                    if (keystore != null && password != null) {
                        handler = new PublicKeyEncryptionHandler(keystore, null, password.toCharArray());
                    } else if (password != null) {
                        handler = new StandardEncryptionHandler();
                        ((StandardEncryptionHandler)handler).setUserPassword(password);
                    }
                    Dump dump = new Dump();
                    dump.setFullProfile(fullprofile);
                    dump.setStructure(fullprofile);
                    dump.setKeyStore(keystore);
                    dump.setOutput(format.equals("text") && outputFileName == null ? "console" : format, out);
                    dump.go(filename, handler);
                } catch (Throwable e) {
                    if (redirlogging) {
                        Logger.getLogger("org.faceless.pdf2").log(Level.SEVERE, "Load failed", e);
                    } else {
                        e.printStackTrace();
                    }
                } finally {
                    out.flush();
                }
            }
        }
        if (outputFileName != null) {
            out.close();
        }
    }

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


    //--------------------------------------------------------------

    private Printer printer;
    private boolean unicode, fullprofile, structure;
    private Appendable out;
    private PDF pdf;
    private PDFParser pdfparser;
    private KeyStore keystore;

    public Dump() {
    }

    public void setOutput(String format, Appendable out) throws IOException, PasswordException {
        if (format.equals("html")) {
            printer = new HtmlPrinter(out);
        } else {
            printer = new TextPrinter(out);
            unicode = !format.equals("console");
        }
    }

    public void setFullProfile(boolean fullprofile) {
        this.fullprofile = fullprofile;
    }

    public void setStructure(boolean structure) {
        this.structure = structure;
    }

    public void setKeyStore(KeyStore keystore) {
        this.keystore = keystore;
    }

    KeyStore getKeyStore() throws GeneralSecurityException {
        if (keystore == null) {
            keystore = FormSignature.loadDefaultKeyStore();
        }
        return keystore;
    }

    public void go(String filename, EncryptionHandler handler) throws IOException, GeneralSecurityException {
        if (printer == null) {
            throw new IllegalStateException("Output not set");
        }
        printer.initialize(filename);
        PDFReader reader = new PDFReader();
        reader.addEncryptionHandler(handler);
        try {
            if (filename.equals("-")) {
                reader.setSource(System.in);
            } else if (filename.startsWith("http:") || filename.startsWith("https:")) {
                reader.setSource(new URL(filename));
            } else {
                reader.setSource(new File(filename));
            }
        } catch (IOException e) {
            if (e.getCause() instanceof PasswordException) {
                throw (PasswordException)e.getCause();
            } else {
                throw e;
            }
        }
        reader.load();
        this.pdf = new PDF(reader);
        this.pdfparser = fullprofile ? new PDFParser(pdf) : null;
        
        // PDF loaded, off we go

        getInfoDictionary(printer);
        getOptions(printer);
        getArlington(printer);
        OutputProfile profile = getOutputProfile(printer);
        for (OutputIntent intent: profile.getOutputIntents()) {
            getOutputIntent(printer, intent);
        }
        if (pdfparser != null) {
            Collection<OutputProfile> targets = profile.getClaimedTargetProfiles();
            for (OutputProfile target : targets) {
                getProfileValidation(printer, target, target.getProfileName(), profile);
            }
        }
        boolean extract = getEncryption(printer);
        getJavaScript(printer);
        getDocumentActions(printer);
        getMetaData(pdf.getXMP(), printer);
        getBookmarks(printer);
        getNamedActions(printer);
        getEmbeddedFiles(printer);
        getOptionalContent(printer);
        if (structure) {
            getStructure(printer, extract);
        }
        getPages(printer);
        getXFA(printer);
        getForm(printer);
        printer.close();
        if (out instanceof Flushable) {
            ((Flushable)out).flush();
        }
    }

    void getProfileValidation(Printer out, OutputProfile target, String name, OutputProfile profile) throws IOException {
        out.startSection(name + " Compliance");
        OutputProfile.Feature[] mismatch = profile.isCompatibleWith(target);
        if (mismatch == null) {
            out.printKeyValue("Status", "Passed");
        } else {
            out.printKeyValue("Status", "Failed");
            for (int i=0;i<mismatch.length;i++) {
                if (profile.isSet(mismatch[i])) {
                    out.printKeyValue("Reason", "\"" + mismatch[i] + "\" is set but disallowed");
                } else {
                    out.printKeyValue("Reason", "\"" + mismatch[i] + "\" is required but missing");
                }
            }
        }
        out.endSection();
    }

    @SuppressWarnings("unchecked")
    void getStructure(Printer out, boolean extract) throws IOException {
        Document doc = extract ? pdfparser.getStructureTree() : pdf.getStructureTree();
        if (doc.getDocumentElement() != null && doc.getDocumentElement().getFirstChild() != null) {
            Map<String,String> rm = (Map<String,String>)doc.getDomConfig().getParameter("role-map");
            if (!rm.isEmpty()) {
                out.startSection("Structure Role-Map");
                for (Map.Entry<String,String> e : rm.entrySet()) {
                    out.printKeyValue(e.getKey().replaceAll("\n", " "), e.getValue().replaceAll("\n", " "));
                }
                out.endSection();
            }
            out.startSection("Structure");
            out.print(new DOMSource(doc));
            out.endSection();
        }
    }

    @SuppressWarnings("unchecked")
    void getOptions(Printer out) throws IOException {
        final String[] keys = { "view.fullscreen", "view.displaydoctitle", "view.hidetoolbar", "view.hidemenubar", "view.hidewindowui", "view.fitwindow", "view.centerwindow", "pagelayout", "pagemode", "view.area", "view.clip", "print.area", "print.clip", "print.scaling", "print.duplex", "print.matchtraysize", "print.numcopies", "print.pagerange" };

        out.startSection("Options");
        for (int i=0;i<keys.length;i++) {
            String key = keys[i];
            Object value = pdf.getOption(key);
            if (value != null) {
                if (key == "print.pagerange") {
                    List pagelist = (List)value;                // a List of PDFPages
                    for (int j=0;j<pagelist.size();j++) {
                        PDFPage page = (PDFPage)pagelist.get(j);
                        pagelist.set(j, Integer.valueOf(page.getPageNumber()));
                    }
                }
                out.printKeyValue(keys[i], value);
            }
        }
        out.endSection();
    }

    @SuppressWarnings("unchecked")
    void getArlington(Printer out) throws IOException {

        out.startSection("Arlington Model Issues");
        List<ArlingtonModelIssue> list = new OutputProfiler(pdf).getArlingtonModelIssues();
        if (list.isEmpty()) {
            out.println("No issues");
        } else {
            Map<String,Integer> m = new LinkedHashMap<String,Integer>();
            out.startSection("Repairable");
            for (ArlingtonModelIssue i : list) {
                if (i.getRepairType() != null) {
                    Integer v = m.get(i.getRepairType());
                    m.put(i.getRepairType(), Integer.valueOf(v == null ? 1 : v.intValue() + 1));
                }
            }
            for (Map.Entry<String,Integer> e : m.entrySet()) {
                out.printKeyValue(e.getKey(), e.getValue() + " occurance");
            }
            out.endSection();

            m.clear();
            out.startSection("Non-Repairable");
            for (ArlingtonModelIssue i : list) {
                if (i.getRepairType() == null) {
                    Integer v = m.get(i.toString());
                    m.put(i.toString(), Integer.valueOf(v == null ? 1 : v.intValue() + 1));
                }
            }
            for (Map.Entry<String,Integer> e : m.entrySet()) {
                out.printKeyValue(e.getKey(), e.getValue() + " occurance");
            }
            out.endSection();
        }
        out.endSection();
    }

    boolean getEncryption(Printer out) throws IOException {
        out.startSection("Encryption");
        EncryptionHandler handler = pdf.getEncryptionHandler();
        int extract = StandardEncryptionHandler.EXTRACT_ALL;
        if (handler != null) {
            if (handler instanceof StandardEncryptionHandler || handler instanceof PublicKeyEncryptionHandler) {
                int change, print;
                if (handler instanceof StandardEncryptionHandler) {
                    int version = ((StandardEncryptionHandler)handler).getVersion();
                    out.printKeyValue("Type", "Password");
                    out.printKeyValue("Version", version + ": " + ((StandardEncryptionHandler)handler).getDescription());
                    change = ((StandardEncryptionHandler)handler).getChange();
                    print = ((StandardEncryptionHandler)handler).getPrint();
                    extract = ((StandardEncryptionHandler)handler).getExtract();
                } else {
                    out.printKeyValue("Type", "Public-Key");
                    change = ((PublicKeyEncryptionHandler)handler).getChange();
                    print = ((PublicKeyEncryptionHandler)handler).getPrint();
                    extract = ((PublicKeyEncryptionHandler)handler).getExtract();
                }
                boolean change_forms = (change & StandardEncryptionHandler.CHANGE_FORMS) == StandardEncryptionHandler.CHANGE_FORMS;
                boolean change_layout = (change & StandardEncryptionHandler.CHANGE_LAYOUT) == StandardEncryptionHandler.CHANGE_LAYOUT;
                boolean change_annots = (change & StandardEncryptionHandler.CHANGE_ANNOTATIONS) == StandardEncryptionHandler.CHANGE_ANNOTATIONS;
                boolean change_none = !(change_forms | change_layout | change_annots);
                out.printKeyValue("Can Change", (change_none?"None ":"")+(change_layout?"Assembly ":"")+(change_forms?"Forms ":"")+(change_annots?"Comments ":"") + " " + change);
                out.printKeyValue("Can Print", (print == StandardEncryptionHandler.PRINT_NONE ? "None" : print == StandardEncryptionHandler.PRINT_LOWRES ? "Low-res" : "High-res") + " " + print);
                out.printKeyValue("Can Extract", (extract == StandardEncryptionHandler.EXTRACT_NONE ? "None" : extract == StandardEncryptionHandler.EXTRACT_ACCESSIBILITY ? "Accessibility" : "All") + " " + extract);
            } else {
                out.printKeyValue("Type", "Non-Standard ("+handler.getFilterName()+")");
            }
        }
        out.endSection();
        return extract != StandardEncryptionHandler.EXTRACT_NONE;
    }

    OutputProfile getOutputProfile(Printer out) throws IOException {
        out.startSection("Output Profile");
        long time1 = System.currentTimeMillis();
        OutputProfiler profiler;
        if (pdfparser != null) {
            profiler = new OutputProfiler(pdfparser);
        } else {
            profiler = new OutputProfiler(pdf);
        }
        OutputProfile profile = profiler.getProfile();
        long time2 = System.currentTimeMillis();
        for (int i=0;i<OutputProfile.Feature.ALL.length;i++) {
            OutputProfile.Feature feature = OutputProfile.Feature.ALL[i];
            if (profile.isSet(feature)) {
                out.printKeyValue(feature.toString(), null, feature.getDescription());
            }
        }

        boolean open = false;

        // Report on all the Fonts found in the profile
        for (OutputProfile.FontInfo fontinfo : profile.getFontInfo()) {
            if (!open) {
                out.startSection("Fonts");
                open = true;
            }
            out.printKeyValue(fontinfo.getBaseName(), (fontinfo.isCIDFont() ? "CID ":" Simple ")+fontinfo.getType()+(fontinfo.isEmbedded() ? " (Embedded)" : ""));
        }
        if (open) {
            out.endSection();
            open = false;
        }

        // Report on all the Color Separations found in the profile
        for (OutputProfile.Separation sep : profile.getColorSeparations()) {
            if (!open) {
                out.startSection("Separations");
                open = true;
            }
            out.startSection(sep.getName());
            out.printKeyValue("Name", sep.getName());
            Color c = sep.getColor();
            if (c != null) {
                out.printKeyValue("Color", fmt(c));
            }
            out.endSection();
        }
        if (open) {
            out.endSection();
            open = false;
        }

        // Report on all the ICC ColorSpaces found in the profile
        for (ICCColorSpace icc : profile.getICCColorSpaces()) {
            if (!open) {
                out.startSection("ICC Profiles");
                open = true;
            }
            out.printKeyValue("Profile", "ICC \"" + icc.getDescription() + "\" (" + icc.getNumComponents() + " components)");
        }
        if (open) {
            out.endSection();
            open = false;
        }

        // Report on all the Embedded Files found in the profile
        // Note this will skip over any EmbeddedFile objects owned
        // by the PDF or by an Annotation, as those are reported
        // on in the PDF and Annotation sections - we don't want to
        // list them twice.
        for (EmbeddedFile ef : profile.getAssociatedFiles()) {
            boolean skip = false;
            for (Object o : ef.getOwners()) {
                skip |= o instanceof PDFAnnotation || o instanceof PDF;
            }
            if (!skip) {
                if (!open) {
                    out.startSection("Embedded Files (other than those associated with a PDF or Annotation)");
                    open = true;
                }
                getEmbeddedFile("File", ef, out);
            }
        }
        if (open) {
            out.endSection();
            open = false;
        }

        if (pdfparser != null) {
            out.println("Output profiling took " + (time2-time1) + "ms");
        }
        out.endSection();
        return profile;
    }

    private static String fmt(Color c) {
        ColorSpace cs = c.getColorSpace();
        StringBuilder sb = new StringBuilder();
        float[] comps = c.getColorComponents(null);
        boolean round = false;
        if (cs.isCS_sRGB()) {
            sb.append("rgb(");
            round = true;
        } else if (cs == CMYKColorSpace.getInstance()) {
            sb.append("cmyk(");
        } else if (cs instanceof ICCColorSpace) {
            sb.append("icc(\"");
            sb.append(((ICCColorSpace)cs).getDescription());
            sb.append("\" ");
        } else if (cs.getType() == ColorSpace.TYPE_RGB) {
            sb.append("calrgb(");
        } else if (cs.getType() == ColorSpace.TYPE_GRAY) {
            sb.append("gray(");
        } else if (cs.getType() == ColorSpace.TYPE_Lab) {
            sb.append("lab(");
        } else if (cs instanceof SpotColorSpace) {
            sb.append("spot(");
            sb.append("\"" + cs.getName(0) + "\"="+comps[0]);
            sb.append(" ");
            sb.append(fmt(new org.faceless.util.FixedColor(((SpotColorSpace)cs).getFallbackColorSpace(), ((SpotColorSpace)cs).toFallback(new float[] { 1 }), 1)));      // FixedColor: identical to Color but correctly handles Min/MaxRange for each component
            sb.append(')');
            return sb.toString();
        } else if (cs instanceof DeviceNColorSpace) {
            sb.append("devicen(");
            for (int i=0;i<cs.getNumComponents();i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append("\"" + cs.getName(i) + "\"="+comps[i]);
            }
            sb.append(')');
            return sb.toString();
        }
        for (int i=0;i<comps.length;i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(round ? fmt(Math.round(comps[i] * 255)) : fmt(comps[i]));
        }
        if (c.getAlpha() != 255) {
            sb.append(" / ");
            sb.append(c.getAlpha() / 255f);
        }
        sb.append(')');
        return sb.toString();
    }

    void getOutputIntent(Printer out, OutputIntent intent) throws IOException {
        String type = intent.getType();
        out.startSection(type + " Output Intent");
        if (intent.getIdentifier() != null) {
            out.printKeyValue("Identifier", intent.getIdentifier());
        }
        if (intent.getRegistry() != null) {
            out.printKeyValue("Registry", intent.getRegistry());
        }
        if (intent.getInfo() != null) {
            out.printKeyValue("Info", intent.getInfo());
        }
        ICCColorSpace icc = intent.getColorSpace();
        if (icc == null) {
            out.printKeyValue("Profile", "Not Embedded");
        } else {
            out.printKeyValue("Profile", "ICC \"" + icc.getDescription() + "\" (" + icc.getNumComponents() + " components)");
        }
        out.endSection();
    }

    void getJavaScript(Printer out) throws IOException {
        out.startSection("JavaScript");
        String js = pdf.getJavaScript();
        if (js != null) {
            out.println(js);
        }
        out.endSection();
    }

    void getBookmarks(Printer out) throws IOException {
        out.startSection("Bookmarks");
        getBookmarks(out, pdf.getBookmarks());
        out.endSection();
    }

    private void getBookmarks(Printer out, List<PDFBookmark> l) throws IOException {
        for (int i=0;l != null && i<l.size();i++) {
            PDFBookmark b = l.get(i);
            if (b != null) {
                out.printKeyValue(b.getName(), getAction(b.getAction(), null, out.getSubPrinter()));
                if (b.getBookmarks() != null) {
                    out.startSection(null);
                    getBookmarks(out, b.getBookmarks());
                    out.endSection();
                }
            }
        }
    }

    private Printer getAction(PDFAction action, String name, Printer out) throws IOException {
        if (action != null) {
            while (action != null) {
                out.startSection(name);
                String type = action.getType();
                if (type.equals("FormJavaScript")) {
                    out.startSection("JavaScript");
                    out.println(action.getJavaScript());
                    out.endSection();
                } else if (type.equals("GoToR")) {
                    out.printKeyValue("Remote", action.getRemoteFilename() + " page " + action.getRemotePageNumber());
                } else if (type.equals("GoToE")) {
                    if (action.getRemotePageNumber() < 0) {
                        out.printKeyValue("Embedded", action.getRemoteFilename() + " element " + action.getStructureElementId());
                    } else {
                        out.printKeyValue("Embedded", action.getRemoteFilename() + " page " + action.getRemotePageNumber());
                    }
                } else if (type.equals("FormReset") || type.equals("FormSubmit")) {
                    out.printKeyValue("Type", type);
                    String desc = type + ":";
                    if (type.equals("FormSubmit")) {
                        out.printKeyValue("URL", action.getURL());
                        int method = action.getFormSubmitMethod();
                        if (method == PDFAction.METHOD_HTTP_POST) {
                            out.printKeyValue("Method", "POST");
                        } else if (method == PDFAction.METHOD_HTTP_IMAGEMAP_POST) {
                            out.printKeyValue("Method", "POST + imageMap");
                        } else if (method == PDFAction.METHOD_FDF) {
                            out.printKeyValue("Method", "FDF");
                        } else if (method == PDFAction.METHOD_XML) {
                            out.printKeyValue("Method", "XML");
                        } else if (method == PDFAction.METHOD_FDF_WITH_ALL_ANNOTATIONS) {
                            out.printKeyValue("Method", "FDF + all annotations");
                        } else if (method == PDFAction.METHOD_XML_WITH_ALL_ANNOTATIONS) {
                            out.printKeyValue("Method", "XML + all annotations");
                        } else if (method == PDFAction.METHOD_FDF_WITH_MY_ANNOTATIONS) {
                            out.printKeyValue("Method", "FDF + my annotations");
                        } else if (method == PDFAction.METHOD_PDF) {
                            out.printKeyValue("Method", "PDF");
                        } else {
                            out.printKeyValue("Method", "Unknown " + method);
                        }
                        int flags = action.getFormSubmitFlags();
                        if ((flags & PDFAction.METHOD_FLAG_EMPTYFIELDS) != 0) {
                            out.printKeyValue("Empty Fields", "true");
                        }
                        if ((flags & PDFAction.METHOD_FLAG_CANONICALDATES) != 0) {
                            out.printKeyValue("Canonical Dates", "true");
                        }
                        out.printKeyValue("Flags", "0x" + Integer.toHexString(flags));
                    }
                    Collection<FormElement> c = action.getFormSubmitFields();
                    String fieldnames;
                    if (c.size() == pdf.getForm().getElements().size()) {
                        out.printKeyValue("Fields", "all");
                    } else {
                        List<String> l = new ArrayList<String>();
                        for (Iterator<FormElement> i = c.iterator();i.hasNext();) {
                            FormElement e = i.next();
                            l.add(e.getForm().getName(e));
                        }
                        out.printKeyValue("Fields", l);
                    }
                } else if (type.startsWith("Named")) {
                    out.printKeyValue("Type", type);
                } else if (type.equals("URL")) {
                    out.printKeyValue("URL", action.getURL());
                } else if (type.equals("Launch")) {
                    out.printKeyValue("Launch", action.getRemoteFilename());
                } else if (type.equals("ShowWidget") || type.equals("HideWidget")) {
                    WidgetAnnotation annot = action.getAnnotation();
                    FormElement field = annot == null ? null : annot.getField();
                    String fieldname = field == null ? null : field.getForm().getName(field);
                    out.printKeyValue("Type", fieldname + ":" + (field == null?0:field.getAnnotations().indexOf(annot)));
                } else if (type.startsWith("GoTo") || type.startsWith("Structure-GoTo")) {
                    Element elt = type.startsWith("Structure") ? action.getStructureElement() : null;
                    int pagenumber = action.getPageNumber();
                    // To better test linearization, optionally don't get the coordinates of
                    // the action on the page unless the page is already loaded.
                    out.printKeyValue("Type", type);
                    if (elt != null && elt.getAttribute("id") != null) {
                        out.printKeyValue("Element", elt.getAttribute("id"));
                    }
                    if (pagenumber >= 0) {
                        out.printKeyValue("Page", Integer.toString(pagenumber + 1));
                        float[] coords = action.getGoToCoordinates();
                        if (coords != null && coords.length > 0) {
                            StringBuilder sb = new StringBuilder();
                            sb.append('[');
                            for (int i=0;i<coords.length;i++) {
                                if (i > 0) {
                                    sb.append(' ');
                                }
                                sb.append(fmt(coords[i]));
                            }
                            sb.append(']');
                            out.printKeyValue("Coordinates", sb);
                        }
                    } else {
                        out.printKeyValue("Page", "missing");
                    }
                } else {
                    out.printKeyValue("Type", type);
                }
                action = action.getNext();
                out.endSection();
            }
        }
        return out;
    }

    void getAnnotation(PDFAnnotation annot, String title, Printer out) throws IOException {
        out.startSection(title);
        if (annot instanceof AnnotationStamp) {
            out.printKeyValue("Type", "Stamp: " + annot.getType());
        } else {
            out.printKeyValue("Type", annot.getType());
        }
        float[] r = annot.getRectangle();
        if (r != null) {
            out.printKeyValue("Rect", fmt(r[0]) + ", " + fmt(r[1]) + " - " + fmt(r[2]) + ", " + fmt(r[3]));
        }
        if (annot.getColor() != null) {
            out.printKeyValue("Color", annot.getColor());
        }
        if (annot.getUniqueID() != null) {
            out.printKeyValue("Unique ID", annot.getUniqueID());
        }
        if (annot.getModifyDate() != null) {
            out.printKeyValue("Modified", fmt(annot.getModifyDate()));
        }
        if (annot.getCreationDate() != null) {
            out.printKeyValue("Created", fmt(annot.getCreationDate()));
        }
        if (annot.getOptionalContentDescriptor() != null) {
            out.printKeyValue("Optional Content", annot.getOptionalContentDescriptor().getName());
        }
        if (annot.isReadOnly()) {
            out.printKeyValue("Read-Only", "true");
        }
        if (annot.isPositionLocked() || annot.isContentLocked()) {
            out.printKeyValue("Locked", (annot.isPositionLocked() ? "position ":"")+(annot.isContentLocked()?"content":""));
        }
        if (!annot.isVisible()) {
            out.printKeyValue("Invisible", "true");
        }
        if (!annot.isPrintable()) {
            out.printKeyValue("Printable", "false");
        }
        if (annot.getSubject() != null) {
            out.printKeyValue("Subject", annot.getSubject());
        }
        if (annot.getAuthor() != null) {
            out.printKeyValue("Author", annot.getAuthor());
        }
        if (annot.getLocale() != Locale.ROOT) {
            out.printKeyValue("Lang", annot.getLocale());
        }
        for (EmbeddedFile ef : annot.getAssociatedFiles()) {
            getEmbeddedFile("File", ef, out);
        }
        if (annot instanceof AnnotationFile) {
            getEmbeddedFile("File", ((AnnotationFile)annot).getFile(), out);
        } else if (annot instanceof WidgetAnnotation) {
            WidgetAnnotation widget = (WidgetAnnotation)annot;
            out.printKeyValue("Field",  pdf.getForm().getName(widget.getField()));
            out.printKeyValue("Text-Style", widget.getTextStyle());
            out.printKeyValue("Background-Style", widget.getBackgroundStyle());
        } else if (annot instanceof AnnotationLink) {
            float[] corners = ((AnnotationLink)annot).getCorners();
            if (corners != null) {
                StringBuffer sb = new StringBuffer();
                for (int i=0;i<corners.length;i+=2) {
                    sb.append(fmt(corners[i]) + "," + fmt(corners[i+1]) + " ");
                    if (i%8 == 6) {
                        sb.append("  ");
                    }
                }
                out.printKeyValue("Corners", sb);
            }
            getAction(((AnnotationLink)annot).getAction(), "Action", out);
        } else if (annot instanceof AnnotationMarkup) {
            float[] corners = ((AnnotationMarkup)annot).getCorners();
            if (corners != null) {
                StringBuffer sb = new StringBuffer();
                for (int i=0;i<corners.length;i+=2) {
                    sb.append(fmt(corners[i]) + "," + fmt(corners[i+1]) + " ");
                    if (i%8 == 6) {
                        sb.append("  ");
                    }
                }
                out.printKeyValue("Corners", sb);
            }
        } else if (annot instanceof AnnotationShape) {
            Shape shape = ((AnnotationShape)annot).getShape();
            out.printKeyValue("Style", ((AnnotationShape)annot).getStyle());
            out.printKeyValue("Shape", fmt(shape));
        }
        for (int i=0;i<Event.ALL.length;i++) {
            PDFAction act = annot.getAction(Event.ALL[i]);
            getAction(act, Event.ALL[i].toString(), out);
        }
        getMetaData(annot.getXMP(), out);
        if (annot.getContents() != null) {
            out.startSection("Contents");
            out.println(fmt(annot.getContents().trim()));
            out.endSection();
        }
        List<PDFAnnotation> l = annot.getReviews();
        if (l.size() > 0) {
            out.startSection("Reviews");
            for (int i=0;i<l.size();i++) {
                PDFAnnotation subannot = l.get(i);
                if (subannot instanceof AnnotationNote) {
                    AnnotationNote note = (AnnotationNote)subannot;
                    out.printKeyValue(note.getStatus() + " set by " + note.getAuthor() + " at " + fmt(note.getModifyDate()), null);
                } else {
                    getAnnotation(subannot, "Review " + (i+1) + "/" + l.size(), out);
                }
            }
            out.endSection();
        }
        l = annot.getReplies();
        if (l.size() > 0) {
            out.startSection("Reviews");
            for (int i=0;i<l.size();i++) {
                getAnnotation((PDFAnnotation)l.get(i), "Reply " + (i+1) + "/" + l.size(), out);
            }
            out.endSection();
        }
        out.endSection();
    }

    void getInfoDictionary(Printer out) throws IOException {
        out.startSection("Info Dictionary");
        out.printKeyValue("Document ID", pdf.getDocumentID(true) + " " + pdf.getDocumentID(false));
        out.printKeyValue("Revisions", ""+pdf.getNumberOfRevisions());
        Map<String,Object> info = pdf.getInfo();
        for (Iterator<Map.Entry<String,Object>> i = info.entrySet().iterator();i.hasNext();) {
            Map.Entry<String,Object> e = i.next();
            String key = e.getKey();
            Object val = e.getValue();
            // pdf.getInfo() includes two versions of each date - a Date ("ModDate") and a Calendar ("_ModDate")
            // which we added as a bit of hack when we found we needed the Timezones.
            // Skip the Date and display only the Calendar
            if (val instanceof Calendar && key.charAt(0) == '_' && info.get(key.substring(1)) instanceof Date) {
                key = key.substring(1);
                val = fmt((Calendar)val);
            } else if (val instanceof Date && info.get("_" + key) instanceof Calendar) {
                continue;
            } else {
                val = val.toString();
            }
            out.printKeyValue(key, (String)val);
        }
        if (pdf.getLocale() != null) {
            out.printKeyValue("Locale", pdf.getLocale().toString());
        }
        for (OutputProfile.Extension ext : pdf.getBasicOutputProfile().getExtensions()) {
            if (ext.getPrefix().equals("ADBE") && "1.7".equals(ext.getBaseVersion())) {
                continue;       // Already reported this as part of the version number
            }
            out.printKeyValue("Extension", ext.toString());
        }
        out.endSection();
    }

    void getDocumentActions(Printer out) throws IOException {
        out.startSection("Document Actions");
        for (int i=0;i<Event.ALL.length;i++) {
            PDFAction act = pdf.getAction(Event.ALL[i]);
            getAction(act, Event.ALL[i].toString(), out);
        }
        out.endSection();
    }

    void getMetaData(XMP xmp, Printer out) throws IOException {
        if (!xmp.isValid()) {
            out.startSection("MetaData (Invalid)");
            out.println(xmp.toString());
            out.endSection();
        } else if (!xmp.isEmpty()) {
            out.startSection("MetaData");
            xmp.set("indent", 1);   // Pretty print our output
            out.println(xmp.toString());
            out.endSection();
        }
    }

    void getOptionalContent(Printer out) throws IOException {
        out.startSection("Optional Content");
        getOptionalContent(out, pdf.getOptionalContentLayers());
        out.endSection();
    }

    private void getOptionalContent(Printer out, List<OptionalContentLayer> layers) throws IOException {
        for (int i=0;i<layers.size();i++) {
            OptionalContentLayer layer = layers.get(i);
            out.startSection(layer.getName());
            out.printKeyValue("Name", layer.getName());
            if (!layer.isEnabled()) {
                out.printKeyValue("Enabled", "false");
            }
            if (layer.isLocked()) {
                out.printKeyValue("Locked", "true");
            }
            if (!layer.isDisclosed()) {
                out.printKeyValue("Disclosed", "false");
            }
            if (layer.getView() != OptionalContentLayer.When.Enabled) {
                out.printKeyValue("View", layer.getView());
            }
            if (layer.getPrint() != OptionalContentLayer.When.Enabled) {
                out.printKeyValue("Print", layer.getPrint());
            }
            if (layer.getExport() != OptionalContentLayer.When.Enabled) {
                out.printKeyValue("Export", layer.getExport());
            }
            if (layer.getProcessingStep() != null) {
                out.printKeyValue("Processing Step", layer.getProcessingStep());
            }
            if (!layer.getExclusions().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (OptionalContentLayer l : layer.getExclusions()) {
                    sb.append(l.getName());
                    sb.append(", ");
                }
                sb.setLength(sb.length() - 2);
                out.printKeyValue("Exclusions", sb);
            }
            if (!layer.getOptionalContentLayers().isEmpty()) {
                getOptionalContent(out, layer.getOptionalContentLayers());
            }
            out.endSection();
        }
    }

    void getNamedActions(Printer out) throws IOException {
        out.startSection("Named Actions");
        Map<String,PDFAction> actions = pdf.getNamedActions();
        for (Iterator<Map.Entry<String,PDFAction>> i = actions.entrySet().iterator();i.hasNext();) {
            Map.Entry<String,PDFAction> e = i.next();
            String name = e.getKey();
            PDFAction action = e.getValue();
            getAction(action, name, out);
        }
        out.endSection();
    }

    void getEmbeddedFiles(Printer out) throws IOException {
        out.startSection("Embedded Files");
        Map<String,EmbeddedFile> files = pdf.getEmbeddedFiles();
        for (Iterator<Map.Entry<String,EmbeddedFile>> i = files.entrySet().iterator();i.hasNext();) {
            Map.Entry<String,EmbeddedFile> e = i.next();
            String name = e.getKey();
            EmbeddedFile value = e.getValue();
            getEmbeddedFile(name, value, out);
        }
        out.endSection();
    }

    void getEmbeddedFile(String handle, EmbeddedFile file, Printer out) throws IOException {
        out.startSection(handle);
        if (file != null) {
            out.printKeyValue("Name", file.getName());
            if (file.getType() != null) {
                out.printKeyValue("Type", file.getType());
            }
            if (file.getModDate() != null) {
                out.printKeyValue("Modified", fmt(file.getModDateAsCalendar()));
            }
            if (file.getCreationDate() != null) {
                out.printKeyValue("Created", fmt(file.getCreationDateAsCalendar()));
            }
            if (file.getSize() != -1) {
                out.printKeyValue("Size", file.getSize());
            }
            if (file.getDescription() != null) {
                out.printKeyValue("Description", file.getDescription());
            }
            if (file.getRelationship() != null) {
                out.printKeyValue("Relationship", file.getRelationship());
            }
            if (!file.getProperties().isEmpty()) {
                out.printKeyValue("Properties", file.getProperties());
            }
            if (file.getPortfolioFolder() != null && !file.getPortfolioFolder().equals("/")) {
                out.printKeyValue("Portfolio Folder", file.getPortfolioFolder());
            }
            out.printKeyValue("Checksum", (file.isValid() ? "Probably OK" : "Definitely corrupt"));
            for (Map.Entry<String,Object> e : file.getProperties().entrySet()) {
                out.printKeyValue("* " + e.getKey(), e.getValue());
            }
        }
        out.endSection();
    }

    void getPages(Printer out) throws IOException {
        out.startSection("Pages");
        List<PDFPage> l = pdf.getPages();
        for (int i=0;i<l.size();i++) {
            PDFPage page = l.get(i);
            getPage(page, out);
        }
        out.endSection();
    }

    void getPage(PDFPage page, Printer out) throws IOException {
        String label = pdf.getPageLabel(page.getPageNumber() - 1);
        out.startSection("Page " + page.getPageNumber() + "/" + pdf.getNumberOfPages());
        if (label != null) {
            out.printKeyValue("Label", "\"" + label + "\"");
        }
        out.printKeyValue("Size", page.getWidth() + " x " + page.getHeight());
        String[] boxes = { "CropBox", "ArtBox", "BleedBox", "TrimBox" };
        for (int i=0;i<boxes.length;i++) {
            float[] box = page.getBox(boxes[i]);
            if (box != null) {
                out.printKeyValue(boxes[i], fmt(box[0]) + ", " + fmt(box[1]) + " - " + fmt(box[2]) + ", " + fmt(box[3]));
            }
        }
        int rotate = page.getPageOrientation();
        if (rotate != 0) {
            out.printKeyValue("Orientation", Integer.toString(rotate));
        }
        for (int i=0;i<Event.ALL.length;i++) {
            PDFAction act = page.getAction(Event.ALL[i]);
            getAction(act, Event.ALL[i].toString(), out);
        }

        List<PDFAnnotation> annots = page.getAnnotations();
        for (int j=0;j<annots.size();j++) {
            PDFAnnotation annot = annots.get(j);
            if (annot.getInReplyTo() == null && !annot.getType().equals("Popup")) {
                getAnnotation(annot, "Annotation " + (j+1) + "/" + annots.size(), out);
            }
        }
        getMetaData(page.getXMP(), out);
        out.endSection();
    }

    /**
     * Dump information on the XFA form. This method requires Java 1.4,
     * specifically the javax.xml.transform package. If you're still
     * running Java 1.3 you can comment it out and the call to it.
     */
    void getXFA(Printer out) throws IOException {
        InputStream in = pdf.getForm().getXFAElement(null);
        if (in != null) {
            out.startSection("XFA Form");
            out.print(new StreamSource(in));
            out.endSection();
        }
    }

    void getForm(Printer out) throws IOException, GeneralSecurityException {
        out.startSection("Form");
        Map<String,FormElement> form = new TreeMap<String,FormElement>(pdf.getForm().getElements());

        for (Iterator<Map.Entry<String,FormElement>> i=form.entrySet().iterator(); i.hasNext();) {
            Map.Entry<String,FormElement> e = i.next();
            String name = e.getKey();
            FormElement el = e.getValue();
            if (el instanceof FormSignature) {
                getFormSignature((FormSignature)el, name, out);
            } else if (el instanceof FormText) {
                getFormText((FormText)el, name, out);
            } else if (el instanceof FormButton) {
                getFormButton((FormButton)el, name, out);
            } else if (el instanceof FormCheckbox || el instanceof FormRadioButton) {
                getFormRadioBox(el, name, out);
            } else if (el instanceof FormChoice) {
                getFormChoice((FormChoice)el, name, out);
            } else if (el instanceof FormBarCode) {
                getFormBarCode((FormBarCode)el, name, out);
            }
        }
        out.endSection();
    }

    void getFormElement(FormElement el, Printer out) throws IOException {
        if (el.getDescription() != null) {
            out.printKeyValue("Description", fmt(el.getDescription()));
        }
        if (el.isReadOnly()) {
            out.printKeyValue("Read-Only", "true");
        }
        if (el.isRequired()) {
            out.printKeyValue("Required", "true");
        }
        if (!el.isSubmitted()) {
            out.printKeyValue("Submitted", "false");
        }
        for (int j=0;j<Event.ALL.length;j++) {
            PDFAction act = el.getAction(Event.ALL[j]);
            getAction(act, Event.ALL[j].toString(), out);
        }
    }

    void getFormSignature(FormSignature sig, String name, Printer out) throws IOException, GeneralSecurityException {
        String[] certifications = { "No", "Yes: No Changes", "Yes: Modify Forms", "Yes: Modify Comments" };
        out.startSection("Signature \"" + name + "\"");
        if (sig.getState() == sig.STATE_BLANK) {
            out.printKeyValue("State", "Blank");
        } else {
            Calendar when = sig.getSignDate();
            out.printKeyValue("Type", sig.getFilter()+(sig.getSignatureHandler() instanceof PKCS7SignatureHandler ? " / "+((PKCS7SignatureHandler)sig.getSignatureHandler()).getSubFilter() : ""));
            out.printKeyValue("Handler", sig.getSignatureHandler().getHandlerName());
            KeyStore keystore = getKeyStore();
            Object[] o = getSignatureValidity(sig, keystore, out);
            String validity = (String)o[0];
            X509Certificate cert = (X509Certificate)o[1];
            if (when != null) { // Only null for non-standard SignatureHandlers
                String timestampvalidity = getSignatureTimeStampValidity(sig, keystore, out);
                out.printKeyValue("Signed", fmt(when) + " (" + timestampvalidity + ")");
            }
            out.printKeyValue("Certified", certifications[sig.getCertificationType()]);
            if (sig.getName() != null) {
                out.printKeyValue("Signed by", sig.getName());
            }
            if (sig.getReason() != null) {
                out.printKeyValue("Reason", sig.getReason());
            }
            if (sig.getLocation() != null) {
                out.printKeyValue("Location", sig.getLocation());
            }
            out.printKeyValue("Validity", validity);
            getFormElement(sig, out);
            if (sig.getSignatureHandler() instanceof PKCS7SignatureHandler) {
                PKCS7SignatureHandler pkcs7 = (PKCS7SignatureHandler)sig.getSignatureHandler();
                out.printKeyValue("Digest Algo", pkcs7.getHashAlgorithm());
                List<PKCS7SignatureHandler.ValidationInformation> ltv = pkcs7.getValidationInformation();
                if (ltv != null) {
                    for (PKCS7SignatureHandler.ValidationInformation vi : ltv) {
                        out.startSection("Long Term Validation");
                        if (vi.getTime() != null) {
                            out.printKeyValue("When", fmt(vi.getTime()));
                        }
                        out.printKeyValue("Original", vi.isInitial());
                        out.printKeyValue("Complete", vi.isComplete(null));
                        out.endSection();
                    }
                }
            }
            if (cert != null) {
                out.startSection("Unverifiable Certificate");
                out.println(cert.toString());
                out.endSection();
            }
        }
        out.endSection();
    }

    /**
     * Verify the signature.
     *
     * Unfortunately we want to return two things here - the "validity" string
     * and the unverifiable X.509 certificate. We return them in an array...
     */
    private Object[] getSignatureValidity(FormSignature sig, KeyStore keystore, Printer out) throws IOException {
        boolean verified = false, verifiable = false, certsok = false;
        X509Certificate cert = null;
        Calendar when = sig.getSignDate();
        try {
            verified = sig.verify();
            verifiable = true;
            if (sig.getSignatureHandler() instanceof PKCS7SignatureHandler) {
                PKCS7SignatureHandler handler = (PKCS7SignatureHandler)sig.getSignatureHandler();
                X509Certificate[] certs = handler.getCertificates();
                cert = FormSignature.verifyCertificates(certs, keystore, null, when);
                certsok = cert == null;
            } else {
            }
        } catch (Exception e) {
            out.print(e);
        }
        String validity="";
        if (sig.getNumberOfRevisionsCovered() != pdf.getNumberOfRevisions()) {
            if (sig.getNumberOfRevisionsCovered() == 0) {
                validity = "Bad - signature doesn't cover an entire revision. The section that is covered is ";
            } else {
                validity = "Partial - covers the first " + sig.getNumberOfRevisionsCovered() + " of " + pdf.getNumberOfRevisions() + " revisions. Those covered are ";
            }
        }
        if (verified && verifiable && certsok) {
            validity += "Perfect (signature and certificates verified)";
        } else if (verified && verifiable && !certsok) {
            validity += "Good (signature verified, but not certificates)";
        } else if (verifiable) {
            validity += "Bad (signature not verified - document has been altered)";
        } else {
            validity += "Unknown (not a PKCS#7 Signature, unable to verify)";
        }
        return new Object[] { validity, cert };
    }

    /**
     * Verify the RFC3161 TimeStamp on the signature
     * if there is one (only on PKCS#7 sigs).
     */
    private String getSignatureTimeStampValidity(FormSignature sig, KeyStore keystore, Printer out) throws IOException {
        X509Certificate[] certs = null;
        if (sig.getSignatureHandler() instanceof PKCS7SignatureHandler) {
            try {
                certs = ((PKCS7SignatureHandler)sig.getSignatureHandler()).getTimeStampCertificates();
            } catch (Exception e) {
                out.print(e);
            }
        }

        String validity;
        if (certs != null) {
            Calendar when = sig.getSignDate();
            String issuer = null;
            X509Certificate badcert = null;
            try {
                issuer = FormSignature.getSubjectField(certs[0], "CN");
                badcert = FormSignature.verifyCertificates(certs, keystore, null, when);
            } catch (Exception e) {
                out.print(e);
            }
            if (badcert == null) {
                validity = "guaranteed by verified timestamp from \"" + issuer + "\")";
            } else {
                validity = "guaranteed by unknown timestamp from \"" + issuer + "\")";
            }
        } else {
            validity = "from computer clock";
        }
        return validity;
    }

    void getFormText(FormText text, String name, Printer out) throws IOException {
        out.startSection("Text \"" + name + "\"");
        if (text.getValue() != null) {
            out.printKeyValue("Value", "\"" + fmt(text.getValue())+ "\"");
        }
        if (text.getDefaultValue() != null) {
            out.printKeyValue("Default Value", "\"" + fmt(text.getDefaultValue())+ "\"");
        }
        if (text.getType() == FormText.TYPE_PASSWORD) {
            out.printKeyValue("Type", "Password");
        } else if (text.getType() == FormText.TYPE_BARCODE) {
            out.printKeyValue("Type", "XFA Barcode");
        } else if (text.getType() == FormText.TYPE_MULTILINE) {
            out.printKeyValue("Type", "Multi-line");
        }
        if (text.getMaxLength() != 0) {
            out.printKeyValue("Max-Length", text.getMaxLength());
        }
        if (text.getNumberOfCombs() != 0) {
            out.printKeyValue("Combs", text.getNumberOfCombs());
        }
        if (text.isSpellCheck()) {
            out.printKeyValue("SpellCheck", "true");
        }
        if (text.isScrollable()) {
            out.printKeyValue("Scrollable", "true");
        }
        if (text.isRTL()) {
            out.printKeyValue("RTL", "true");
        }
        getFormElement(text, out);
        out.endSection();
    }

    void getFormButton(FormButton button, String name, Printer out) throws IOException {
        out.startSection("Button \"" + name + "\"");
        getFormElement(button, out);
        out.endSection();
    }

    void getFormRadioBox(FormElement box, String name, Printer out) throws IOException {
        out.startSection((box instanceof FormRadioButton ? "RadioButton" : "Checkbox") + " \"" + name + "\"");
        if (box.getValue() != null) {
            out.printKeyValue("Value", "\"" + fmt(box.getValue())+ "\"");
        }
        List<WidgetAnnotation> annots = box.getAnnotations();
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<annots.size();i++) {
            WidgetAnnotation w = annots.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"' + fmt(w.getValue()) + '"');
        }
        getFormElement(box, out);
        out.printKeyValue("Options", sb);
        out.endSection();
    }

    void getFormChoice(FormChoice choice, String name, Printer out) throws IOException {
        out.startSection("Choice \"" + name + "\"");
        int type = choice.getType();
        out.printKeyValue("Type", (type == FormChoice.TYPE_COMBO ? "Combo" : type == FormChoice.TYPE_SCROLLABLE ? "Scrollable" : type == FormChoice.TYPE_MULTISCROLLABLE ? "Multi-choice Scrollable" : "DropDown"));
        if (choice.getValue() != null) {
            out.printKeyValue("Value", "\"" + fmt(choice.getValue())+ "\"");
        }
        if (choice.isRTL()) {
            out.printKeyValue("RTL", "true");
        }
        getFormElement(choice, out);

        Map<String,String> opts = choice.getOptions();
        out.startSection("Options");
        for (Iterator<Map.Entry<String,String>> j=opts.entrySet().iterator();j.hasNext();) {
            Map.Entry<String,String> e = j.next();
            String key = e.getKey();
            String val = e.getValue();
            out.printKeyValue(key, val);
        }
        out.endSection();
    }
    
    void getFormBarCode(FormBarCode barcode, String name, Printer out) throws IOException {
        out.startSection("Barcode \"" + name + "\"");
        out.printKeyValue("Symbology", barcode.getSymbology() + " (with ECC=" + barcode.getECC() + " and X-unit of " + fmt.format(barcode.getSymbolSize()) + "mm" + (barcode.getCompression() != 0?", flate compressed":"") + ")");
        if (barcode.getValue() != null) {
            out.printKeyValue("Value", "\"" + fmt(barcode.getValue())+ "\"");
        }
        getFormElement(barcode, out);
        out.endSection();
    }

    //-------------------------------------------------------------------------------------
    //
    // Remainder are utility methods and a subclass (Printer) for formatting the output
    //
    //-------------------------------------------------------------------------------------

    private static final DecimalFormat fmt = new DecimalFormat("#0.00");

    private static String fmt(Calendar c) {
        DateFormat dateformat;
        // We use a Timezone with an ID of "Floating" when a PDF date has no timezone specified
        if (c.getTimeZone().getID().equals("Floating")) {
            dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        } else {
            dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZZ");
        }
        dateformat.setTimeZone(c.getTimeZone());
        return dateformat.format(c.getTime());
    }

    private static String fmt(double f) {
        if (f != f) {
            return "NaN";
        } else if (f == Double.POSITIVE_INFINITY) {
            return " + Inf";
        } else if (f == Double.POSITIVE_INFINITY) {
            return "-Inf";
        }  else {
            String s = fmt.format(f);
            while (s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        }
    }

    private String fmt(String in) {
        if (in == null) {
            return null;
        }
        StringBuffer st = new StringBuffer(in.length());
        for (int i=0;i<in.length();i++) {
            char c = in.charAt(i);
            if (c == '\n') {
                st.append("\\n");
            } else if (c == '\r') {
                st.append("\\r");
            } else if (c == '\t') {
                st.append("\\t");
            } else if ((c>=32 && c<=255) || unicode) {
                st.append(c);
            } else {
                String z = "0000" + Integer.toHexString(c);
                st.append("\\u" + z.substring(z.length()-4, z.length()));
            }
        }
        return st.toString();
    }

    private static String fmt(Shape s) {
        if (s instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D)s;
            return "<rect x=" + fmt(r.getMinX()) + " y=" + fmt(r.getMinY()) + " w=" + fmt(r.getWidth()) + " h=" + fmt(r.getHeight()) + ">";
        } else if (s instanceof Ellipse2D) {
            Ellipse2D r = (Ellipse2D)s;
            return "<ellipse x=" + fmt(r.getMinX()) + " y=" + fmt(r.getMinY()) + " w=" + fmt(r.getWidth()) + " h=" + fmt(r.getHeight()) + ">";
        } else if (s instanceof Line2D) {
            Line2D l = (Line2D)s;
            return "<line x1=" + fmt(l.getX1()) + " y1=" + fmt(l.getY1()) + " x2=" + fmt(l.getX2()) + " y2=" + fmt(l.getY2()) + ">";
        } else {
            float[] f = new float[6];
            StringBuffer sb = new StringBuffer();
            for (PathIterator i = s.getPathIterator(null);!i.isDone();i.next()) {
                int t = i.currentSegment(f);
                if (t == i.SEG_MOVETO) {
                    sb.append(fmt(f[0]) + " " + fmt(f[1]) + " m ");
                } else if (t == i.SEG_LINETO) {
                    sb.append(fmt(f[0]) + " " + fmt(f[1]) + " l ");
                } else if (t == i.SEG_QUADTO) {
                    sb.append(fmt(f[0]) + " " + fmt(f[1]) + " " + fmt(f[2]) + " " + fmt(f[3]) + " q ");
                } else if (t == i.SEG_CUBICTO) {
                    sb.append(fmt(f[0]) + " " + fmt(f[1]) + " " + fmt(f[2]) + " " + fmt(f[3]) + " " + fmt(f[4]) + " " + fmt(f[5]) + " c ");
                } else {
                    sb.append(" h ");
                }
            }
            return sb.toString().trim();
        }
    }

    static interface Printer {
        void initialize(String filename) throws IOException;
        void startSection(String header) throws IOException;
        void endSection() throws IOException;
        void printKeyValue(String key, Object value) throws IOException;
        void printKeyValue(String key, Object value, String description) throws IOException;
        void println(CharSequence s) throws IOException;
        void print(Exception e) throws IOException;
        void print(Source source) throws IOException;
        Printer getSubPrinter();
        void close() throws IOException;
    }

    /** 
     * Utility class to format output nicely
     */
    private static class TextPrinter implements Printer {
        final Appendable out;
        private int printdepth, endprintdepth;
        private List<String> headers;
        private List<String[]> props;
        private boolean nl = true;
        private String prefix = "";
        static final String LINE = "---------------------------------------------------------------";

        TextPrinter(Appendable out) {
            this.out = out;
            this.headers = new ArrayList<String>();
        }

        public void initialize(String filename) throws IOException {
            out.append("DUMPING \"" + filename + "\" with PDF Library " + PDF.VERSION + "/" + System.getProperty("java.vendor") + " Java " + System.getProperty("java.version") + "/" + System.getProperty("os.name")+"\n");
        }

        public void startSection(String header) throws IOException {
            flushProps();
            headers.add(header);
        }

        public void endSection() throws IOException {
            flushProps();
            if (printdepth == headers.size()) {
                if (printdepth == 1) {
                    out.append("\n");
                    endprintdepth = 0;
                } else {
                    String header = headers.get(headers.size() - 1);
                    prefix = prefix.substring(0, prefix.length() - 2);
                    if (header != null) {
                        out.append(prefix + "+" + LINE + "\n");
                    }
                    endprintdepth = printdepth;
                }
                printdepth--;
            }
            String s = headers.remove(headers.size()-1);
            if (out instanceof Flushable) {
                ((Flushable)out).flush();
            }
        }

        public void printKeyValue(String key, Object value) throws IOException {
            printKeyValue(key, value, null);
        }

        public void printKeyValue(String key, Object value, String description) throws IOException {
            if (props == null) {
                props = new ArrayList<String[]>();
            }
            props.add(new String[] { key, value == null ? null : value.toString(), description });
        }

        public void println(CharSequence s) throws IOException {
            flushProps();
            print(s + "\n");
        }

        public void print(Exception e) throws IOException {
            flushProps();
            print("");
            StringWriter sb = new StringWriter();
            e.printStackTrace(new PrintWriter(sb));
            out.append(sb.toString());
        }

        public void print(Source src) throws IOException {
            flushProps();
            try {
                StringWriter w = new StringWriter();
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
                transformer.transform(src, new StreamResult(w));
                print("");
                String s = w.toString();
                s.replaceAll("\n *\n", "\n");
                out.append(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public Printer getSubPrinter() {
            return new SingleLinePrinter();
        }

        public void close() throws IOException {
            out.append(LINE);
            out.append("\n");
        }

        private void flushProps() throws IOException {
            List<String[]> props = this.props;
            this.props = null;
            if (props != null) {
                int maxlen = 0;
                for (String[] s : props) {
                    maxlen = Math.max(maxlen, s[0] == null ? 0 : s[0].length());
                }
                maxlen += 5;
                for (String[] s : props) {
                    String key = s[0];
                    String val = s[1];
                    String desc = s[2];
                    StringBuilder sb = new StringBuilder();
                    sb.append(key);
                    if (val != null) {
                        sb.append(':');
                        while (sb.length() < maxlen) {
                            sb.append(' ');
                        }
                        sb.append(val);
                    }
                    println(sb);
                }
            }
        }

        private void print(String s) throws IOException {
            while (printdepth < headers.size()) {
                String header = headers.get(printdepth);
                if (printdepth == 0) {
                    if (header != null) {
                        out.append("=> " + header + "\n");
                        out.append("-" + LINE + "\n");
                    }
                } else {
                    if (header != null) {
                        if (printdepth + 1 != endprintdepth) {
                            out.append(prefix + "+" + LINE + "\n");
                        }
                        prefix += "| ";
                        out.append(prefix + "=> " + header + "\n");
                    } else {
                        prefix += "  ";
                    }
                }
                printdepth++;
                nl = true;
            }
            if (nl) {
                out.append(prefix);
            }
            out.append(s);
            nl = s.endsWith("\n");
        }
    }

    private static class SingleLinePrinter implements Printer {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        public void initialize(String s) {
        }

        public void startSection(String header) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('{');
            first = true;
        }

        public void endSection() {
           sb.append('}');
        }

        public void printKeyValue(String key, Object value) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(key+"="+value);
            first = false;
        }

        public void printKeyValue(String key, Object value, String description) {
            printKeyValue(key, value);
        }

        public void println(CharSequence s) {
            s = s.toString().replaceAll("\n", "\\n");
            if (s.length() > 80) {
                sb.append(sb.length()+" characters");
            } else {
                sb.append("\"" + s + "\"");
            }
        }

        public void print(Exception e) {
        }
        public void print(Source source) {
        }

        public Printer getSubPrinter() {
            return new SingleLinePrinter();
        }

        public void close() {
        }

        public String toString() {
            return sb.toString();
        }
    }

    private static class HtmlPrinter implements Printer {
        final Appendable out;
        final StringBuilder hold;
        private String indent = "";
        private int h = 1;
        private List<String[]> props;

        HtmlPrinter(Appendable out) {
            this.out = out;
            hold = new StringBuilder();
        }

        public void initialize(String filename) throws IOException {
            out.append("<!DOCTYPE html>\n<html>\n<head>\n<style>\n");
            out.append(".pdf-info { font-family: sans-serif }\n.pdf-info ul, .pdf-info .xml > .xml { display: none }\n.pdf-info .expanded > :nth-child(n + 2), .pdf-info > ul { display: block; }\n.pdf-info .xml .tag { display: inline; color: #006 }\n.pdf-info .xml.expanded > .tag { display: block }\n.pdf-info .xml > .tag.close::before { content: \" ... </\"; }\n.pdf-info .expand, .pdf-info .tag.open { cursor: pointer; }\n.pdf-info .xml { margin-left: 1em; font-family: monospace }\n.pdf-info .xml > .tag.open, .pdf-info .xml.expanded > .tag.close { margin-left: -1em }\n.pdf-info .xml .tag.open::before { content: \"<\" }\n.pdf-info .xml.expanded > .tag.close::before { content: \"</\"; }\n.pdf-info .xml .tag::after { content: \">\" }\n.pdf-info .xml .tag .key { color: #000 }\n.pdf-info .xml .tag .val { color: #060; }\n.pdf-info .xml.text { color: #600; margin-left: 0em }\n.pdf-info .xml .tag.open .key::before { content: \" \" }\n.pdf-info .xml .tag.open .key::after { content: \"=\" }\n.pdf-info .xml .tag.open .val::before, .xml .tag.open .val::after { content: \"\\\"\"; }\n.pdf-info h3 { font-size: 1em }\n.pdf-info ul { list-style-type: none }\n.pdf-info dt { display: inline-block; white-space: pre; min-width: 1in; font-style: italic }\n.pdf-info dd { display: inline }\n.pdf-info ul, .pdf-info h3 { margin: 0; padding: 0 }\n.pdf-info li { margin: 4px 0 }\n.pdf-info ul ul  { margin-left: 0.2em; border-left: 0.5em solid #ccc; padding-left: 1.25em }\n.pdf-info .expanded > h3::before { content: \"\\2bc6\"; padding-right: 1em; color: #aaa; }\n.pdf-info li > h3::before { content: \"\\2bc8\"; padding-right: 1em; color: #aaa; }\n.pdf-info li:nth-child(2n) { background: #00000008 }\n");
            out.append("</style>\n<script>\n");
            out.append("function loader() {\n  document.querySelectorAll(\".pdf-info .expand, .pdf-info .tag.open\").forEach(a => {\n    a.addEventListener(\"click\", function() {\n      a.parentNode.classList.toggle(\"expanded\");\n    });\n  });\n}");
            out.append("\n</script>\n</head>\n<body class=\"pdf-info\" onload=\"loader()\">\n<ul class=\"expanded\">\n");
            indent += " ";
        }

        public void startSection(String header) throws IOException {
            flushHold();
            flushProps();
            hold.append(indent);
            hold.append("<li>\n");
            indent += " ";
            if (header != null) {
                hold.append(indent);
                hold.append("<h3 class=\"expand\">");
                hold.append(safe(header, false));
                hold.append("</h3>\n");
            }
            hold.append(indent);
            hold.append("<ul>\n");
            indent += " ";
            h++;
        }

        private void flushHold() throws IOException {
            if (hold.length() > 0) {
                out.append(hold.toString());
                hold.setLength(0);
            }
        }

        public void endSection() throws IOException {
            flushProps();
            indent = indent.substring(0, indent.length() - 1);
            if (hold.length() == 0) {
                out.append(indent);
                out.append("</ul>\n");
            }
            indent = indent.substring(0, indent.length() - 1);
            if (hold.length() == 0) {
                out.append(indent);
                out.append("</li>\n");
            }
            hold.setLength(0);
            h--;
        }

        public void close() throws IOException {
            out.append("</body>\n</html>\n");
        }

        public void printKeyValue(String key, Object value) throws IOException {
            printKeyValue(key, value, null);
        }

        public void printKeyValue(String key, Object value, String description) throws IOException {
            flushHold();
            if (props == null) {
                props = new ArrayList<String[]>();
            }
            if (value instanceof Color) {
                value = Dump.fmt((Color)value);
            }
            props.add(new String[] { key, value == null ? null : value.toString(), description });
        }

        public void println(CharSequence s) throws IOException {
            flushHold();
            flushProps();
            out.append(indent);
            out.append("<pre>");
            out.append(s.toString());
            out.append("</pre>\n");
        }

        public void print(Exception e) throws IOException {
            flushHold();
            flushProps();
        }

        public void print(Source src) throws IOException {
            flushHold();
            flushProps();
            try {
                StringWriter w = new StringWriter();
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transformer.transform(src, new SAXResult(new DefaultHandler() {
                    StringBuilder sb = new StringBuilder();
                    public void processingInstruction(String type, String data) throws SAXException {
                        try {
                            flush();
                            out.append(indent);
                            out.append("<div class=\"xml pi\"><span class=\"name\">");
                            out.append(safe(type, false));
                            out.append("</span>");
                            out.append("<span class=\"key\">");
                            out.append(safe(data, false));
                            out.append("</span></div>\n");
                        } catch (IOException e) {
                            throw new SAXException(e);
                        }
                    }
                    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                        try {
                            flush();
                            out.append(indent);
                            out.append("<div class=\"xml\">\n");
                            indent += " ";
                            out.append(indent);
                            out.append("<div class=\"tag open\"><span class=\"name\">");
                            out.append(safe(qName, false));
                            out.append("</span>");
                            for (int i=0;i<atts.getLength();i++) {
                                out.append("<span class=\"key\">");
                                out.append(safe(atts.getQName(i), false));
                                out.append("</span>");
                                out.append("<span class=\"val\">");
                                out.append(safe(atts.getValue(i), false));
                                out.append("</span>");
                            }
                            out.append("</div>\n");
                            indent += " ";
                        } catch (IOException e) {
                            throw new SAXException(e);
                        }
                    }
                    public void characters(char[] buf, int off, int len) {
                        sb.append(buf, off, len);
                    }
                    private void flush() throws IOException {
                        if (sb.length() > 0) {
                            out.append(indent);
                            out.append("<div class=\"xml text\">\n");
                            out.append(sb.toString().trim());
                            out.append("</div>\n");
                            sb.setLength(0);
                        }
                    }
                    public void endElement(String uri, String localName, String qName) throws SAXException {
                        try {
                            flush();
                            indent = indent.substring(0, indent.length() - 1);
                            out.append(indent);
                            out.append("<div class=\"tag close\"><span class=\"name\">");
                            out.append(safe(qName, false));
                            out.append("</span></div>\n");
                            indent = indent.substring(0, indent.length() - 1);
                            out.append(indent);
                            out.append("</div>\n");
                        } catch (IOException e) {
                            throw new SAXException(e);
                        }
                    }
                }));
                out.append(w.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public Printer getSubPrinter() {
            return new SingleLinePrinter();
        }

        private void flushProps() throws IOException {
            List<String[]> props = this.props;
            this.props = null;
            if (props != null) {
                int maxlen = 0;
                for (String[] s : props) {
                    maxlen = Math.max(maxlen, s[0].length());
                }
                maxlen += 5;
                for (String[] s : props) {
                    String key = s[0];
                    String val = s[1];
                    String desc = s[2];
                    out.append(indent);
                    out.append("<li><dt");
                    if (desc != null) {
                        out.append(" title=\"");
                        out.append(safe(desc, true));
                        out.append('"');
                    }
                    out.append('>');
                    out.append(key);
                    out.append("</dt>");
                    if (val != null) {
                        out.append("<dd>");
                        out.append(val);
                        out.append("</dd>");
                    }
                    out.append("</li>\n");
                }
            }
        }

        public static String safe(CharSequence s, boolean attribute) {
            if (s == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            int len = s.length();
            for (int i=0;i<len;i++) {
                int c = s.charAt(i);
                if (c >= 0xd800 && c <= 0xdbff && i + 1 < len) {
                    c = ((c-0xd7c0)<<10) | (s.charAt(++i)&0x3ff);    // UTF16 decode
                }
                if (c < 0x80) {      // ASCII range: test most common case first
                    if (c < 0x20 && (c != '\t' && c != '\r' && c != '\n')) {
                        // Illegal XML character, even encoded. Skip or substitute
                        sb.append("&#xfffd;");   // Unicode replacement character
                    } else {
                        switch(c) {
                          case '&':  sb.append("&amp;"); break;
                          case '>':  sb.append("&gt;"); break;
                          case '<':  sb.append("&lt;"); break;
                          case '\'':  if (attribute) { sb.append("&apos;"); break; }
                          case '\"':  if (attribute) { sb.append("&quot;"); break; }
                          default:   sb.append((char)c);
                        }
                    }
                } else if ((c >= 0xd800 && c <= 0xdfff) || c == 0xfffe || c == 0xffff) {
                    // Illegal XML character, even encoded. Skip or substitute
                    sb.append("&#xfffd;");   // Unicode replacement character
                } else {
                    sb.append("&#x");
                    sb.append(Integer.toHexString(c));
                    sb.append(';');
                }
            }
            return sb.toString();
        }

    }

}
