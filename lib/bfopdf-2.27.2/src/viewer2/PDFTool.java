// $Id: PDFTool.java 39840 2021-04-24 10:11:23Z mike $

package org.faceless.pdf2.viewer2;

import org.faceless.pdf2.*;
import org.xml.sax.SAXException;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.RenderingHints;
import java.awt.Dimension;
import java.security.*;
import javax.swing.UIManager;
import javax.swing.JFrame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * <p>
 * This class is a utility class which does many of the common tasks required on PDFs -
 * joining them, completing forms, converting to bitmap images or viewing them.
 * It's the default action when the "bfopdf.jar" file is run. Try
 * <code>java -jar bfopdf.jar --help</code> for a list of options.
 * </p>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.7.1
 */
public class PDFTool
{
    private static final String APPNAME = "PDFTool";
    static String filename;
    public static void main(String[] args) throws Exception {
        Object o = PDFViewer.class;             // Ensure class is loaded
        try {
            // Set nice title for Mac OS X. Ignored for anything else
            if (System.getProperty("os.name").startsWith("Mac OS X")) {
                if (System.getProperty("com.apple.mrj.application.apple.menu.about.name")==null) {
                    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "BFO PDF Viewer");
                }
            }
        } catch (Throwable e) {}
        setLicense();
        PDF pdf = null;
        OutputStream out = System.out;
        OutputProfile profile = null;

        try {
            ListIterator<String> i = Arrays.asList(args).listIterator();
            while (i.hasNext()) {
                String cmd = i.next();
                if (cmd.equals("--join")) {
                    pdf = join(i, pdf);
                } else if (cmd.startsWith("--output=")) {
                    out = new FileOutputStream(cmd.substring(9));
                } else if (cmd.startsWith("--profile=")) {
                    cmd = cmd.substring(10);
                    profile = new OutputProfile(OutputProfile.Default);
                    int lastj = 0;
                    for (int j=0;j<=cmd.length();j++) {
                        char c = j==cmd.length() ? '\0' : cmd.charAt(j);
                        if (c=='+' || c=='-' || c==0) {
                            String subcmd = cmd.substring(lastj, j);
                            if (subcmd.equals("nocompression")) {
                                profile = new OutputProfile(OutputProfile.NoCompression);
                            } else if (subcmd.equals("recompression")) {
                                profile = new OutputProfile(OutputProfile.Default);
                                profile.setRequired(OutputProfile.Feature.RegularCompression);
                            } else if (subcmd.equals("acrobat4")) {
                                profile = new OutputProfile(OutputProfile.Acrobat4Compatible);
                            } else if (subcmd.equals("acrobat5")) {
                                profile = new OutputProfile(OutputProfile.Acrobat5Compatible);
                            } else if (subcmd.equals("acrobat6")) {
                                profile = new OutputProfile(OutputProfile.Acrobat6Compatible);
                            } else if (subcmd.equals("acrobat7")) {
                                profile = new OutputProfile(OutputProfile.Acrobat7Compatible);
                            } else if (subcmd.length()>1 && (subcmd.charAt(0)=='+' || subcmd.charAt(0)=='-')) {
                                OutputProfile.Feature[] l = OutputProfile.Feature.ALL;
                                boolean found = false;
                                for (int k=0;k<l.length;k++) {
                                    if (l[k].toString().equals(subcmd.substring(1))) {
                                        found = true;
                                        if (subcmd.charAt(0)=='+') {
                                            profile.setRequired(l[k]);
                                        } else {
                                            profile.setDenied(l[k]);
                                        }
                                    }
                                }
                                if (!found) throw new IllegalArgumentException("Unknown OutputProfile feature \""+subcmd.substring(1)+"\"");
                            } else if (subcmd.length()>0) {
                                throw new IllegalArgumentException("Unknown OutputProfile \""+subcmd+"\"");
                            }
                            lastj = j;
                        }
                    }
                } else if (cmd.equals("--form")) {
                    pdf = form(i, pdf);
                } else if (cmd.equals("--view")) {
                    view(i, pdf, filename);
                    pdf = null;
                    if (i.hasNext()) throw new IllegalArgumentException("--view must be the last action");
                } else if (cmd.equals("--sign")) {
                    pdf = sign(i, pdf);
                } else if (cmd.equals("--version")) {
                    usage();
                    System.exit(0);
                } else if (cmd.equals("--help")) {
                    help();
                    System.exit(0);
                } else if (cmd.equals("--toimage")) {
                    toimage(i, pdf, out);
                    pdf = null;
                    if (i.hasNext()) throw new IllegalArgumentException("--toimage must be the last action");
                } else if (!cmd.startsWith("--")) {
                    pdf = load(cmd);
                } else {
                    throw new IllegalArgumentException("Unknown argument \""+cmd+"\"");
                }
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.err.println(APPNAME+" "+PDF.VERSION+": Error: "+e.getMessage());
            System.err.println("  Run "+APPNAME+" to open the viewer");
            System.err.println("  Run "+APPNAME+" --help to display options");
            System.exit(1);
        }
        if (args.length == 0 || args.length == 1 && !args[0].startsWith("--")) {
            view(new ArrayList<String>().listIterator(), pdf, filename);
        } else {
            if (pdf != null) {
                if (profile != null) {
                    new OutputProfiler(pdf).apply(profile);
                }
                pdf.render(out);
                out.close();
            }
        }
    }

    private static PDF load(String file) throws IOException {
        PDFReader reader = new PDFReader();
        if (file.equals("-")) {
            reader.setSource(System.in);
            reader.load();
        } else if (file.startsWith("http://") || file.startsWith("https://")) {
            URL url = new URL(file);
            filename = url.getFile();
            filename = filename.substring(filename.lastIndexOf("/") + 1);
            reader.setSource(url);
            reader.load();
        } else {
            filename = file;
            reader.setSource(new FileInputStream(filename));
            reader.load();
        }
        return new PDF(reader);
    }

    private static final String getValue(String in) {
        return in.substring(in.indexOf("=")+1);
    }

    protected static void setLicense() {
        // To set your license key, insert it here, recompile the class
        // and insert it back into the JAR.
        //
        // PDF.setLicenseKey(...);
    }

    static PDF join(ListIterator<String> i, PDF pdf) throws IOException {
        double dpi = 0;
        boolean flatten = true;
        String pages = null, pagesize = null;
        if (pdf==null) pdf = new PDF();
        List<PDFPage> outpages = pdf.getPages();

        while (i.hasNext()) {
            String opt = i.next();
            if (opt.startsWith("--dpi=")) {
                dpi = Double.parseDouble(getValue(opt));
                if (dpi<0 || dpi>600) throw new IllegalArgumentException("DPI must be betweeen 0 and 600");
            } else if (opt.startsWith("--pages=")) {
                pages = getValue(opt);
            } else if (opt.startsWith("--pagesize=")) {
                pagesize = getValue(opt);
                if (pagesize.equals("auto")) pagesize=null;
            } else if (opt.startsWith("--")) {
                i.previous();
                return pdf;
            } else {
                byte[] buf = null;
                if (opt.equals("-")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    buf = new byte[8192];
                    for (int l=System.in.read(buf);l>=0;l=System.in.read(buf)) {
                        out.write(buf, 0, l);
                    }
                    buf = out.toByteArray();
                }
                InputStream in = null;
                try {
                    in = buf==null ? (InputStream)new FileInputStream(opt) : (InputStream)new ByteArrayInputStream(buf);
                    PDF pdf2 = new PDF(new PDFReader(in));
                    in.close();
                    if (flatten) pdf2.getForm().flatten();
                    PDFPage outpage = pagesize==null ? null : new PDFPage(pagesize);
                    List<PDFPage> inpages = new ArrayList<PDFPage>(pdf2.getPages());
                    int[] pagelist = parsePages(pages, inpages.size());
                    for (int j=0;j<pagelist.length;j++) {
                        PDFPage inpage = inpages.get(pagelist[j]);
                        outpage = pagesize==null ? null : new PDFPage(pagesize);
                        if (outpage!=null && (inpage.getWidth() != outpage.getWidth() || inpage.getHeight()!=outpage.getHeight())) {
                            PDFCanvas can = new PDFCanvas(inpage);
                            outpage.drawCanvas(can, 0, 0, outpage.getWidth(), outpage.getHeight());
                            outpages.add(outpage);
                            outpage = new PDFPage(pagesize);
                        } else {
                            outpages.add(inpage);
                        }
                    }
                } catch (IOException e1) {
                    try {
                        try { if (in!=null) in.close(); } catch (Exception e) {}
                        in = buf==null ? (InputStream)new FileInputStream(opt) : (InputStream)new ByteArrayInputStream(buf);
                        PDFImageSet images = new PDFImageSet(in);
                        in.close();
                        int[] pagelist = parsePages(pages, images.getNumImages());
                        for (int j=0;j<pagelist.length;j++) {
                            PDFImage image = images.getImage(pagelist[j]);
                            PDFPage page;
                            if (pagesize!=null) {
                                page = new PDFPage(pagesize);
                            } else {
                                double iw = dpi==0 ? image.getWidth() : image.getWidth()*image.getDPIX()/dpi;
                                double ih = dpi==0 ? image.getHeight() : image.getHeight()*image.getDPIY()/dpi;
                                page = new PDFPage((int)iw, (int)ih);
                            }
                            page.drawImage(image, 0, 0, page.getWidth(), page.getHeight());
                            outpages.add(page);
                        }
                    } catch (IOException e2) {
                        throw e2;
                    }
                } finally {
                    try { if (in!=null) in.close(); } catch (Exception e) {}
                }
                pages=null;
            }
        }
        return pdf;
    }

    /**
     * Given a page range, eg "1-4,6,7-end" or "reverse" or "all", return an integer
     * list of the pages to use
     * @param pages the page list as a string
     * @param num the number of pages available
     */
    private static int[] parsePages(String pages, int num) {
        int[] out;
        if (pages==null || pages.equals("all")) {
            out = new int[num];
            for (int i=0;i<out.length;i++) out[i] = i;
        } else if (pages.equals("reverse")) {
            out = new int[num];
            for (int i=0;i<out.length;i++) out[i] = num-i-1;
        } else {
            if (pages.charAt(0)=='-') {
                pages = "1" + pages;
            }
            if (pages.charAt(pages.length()-1)=='-') {
                pages += (num+1);
            }
            List<Integer> t = new ArrayList<Integer>();
            for (StringTokenizer st = new StringTokenizer(pages, ","); st.hasMoreTokens();) {
                String s1 = st.nextToken();
                int k = s1.indexOf('-');
                if (k < 0) {
                    t.add(Integer.valueOf(getPage(s1, num)-1));
                } else {
                    int start = getPage(s1.substring(0, k), num);
                    int end = getPage(s1.substring(k+1), num);
                    if (start < end) {
                        for (int i=start;i<=end;i++) {
                            t.add(Integer.valueOf(i-1));
                        }
                    } else {
                        for (int i=end;i>=start;i--) {
                            t.add(Integer.valueOf(i-1));
                        }
                    }
                }
            }
            out = new int[t.size()];
            for (int i=0;i<out.length;i++) {
                out[i] = t.get(i).intValue();
            }
        }
        return out;
    }

    private static int getPage(String val, int num) {
        if (val.equals("end")) {
            return num;
        } else {
            try {
                int i = Integer.parseInt(val);
                if (i<1 || i>num) {
                    throw new IllegalArgumentException("Page "+i+" outside range 1-"+num);
                }
                return i;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid page '"+val+"'");
            }
        }
    }

    private static PDF form(ListIterator<String> i, PDF pdf) throws IOException, SAXException {
        Map<String,String> fields = new HashMap<String,String>();
        boolean flatten = false;
        String xfa = null, fdf = null;
        while (i.hasNext()) {
            String opt = i.next();
            if (opt.startsWith("--properties=")) {
                String filename = getValue(opt);
                Properties p = new Properties();
                p.load(new FileInputStream(filename));
                for (Enumeration<?> e = p.propertyNames();e.hasMoreElements();) {
                    String key = (String)e.nextElement();
                    String value = p.getProperty(key);
                    fields.put(key, value);
                }
            } else if (opt.startsWith("--fdf=")) {
                fdf = getValue(opt);
            } else if (opt.startsWith("--xfa=")) {
                String filename = getValue(opt);
                Reader in = new InputStreamReader(new FileInputStream(filename), "UTF-8");
                StringWriter w = new StringWriter();
                int c;
                while ((c=in.read())>=0) {
                    w.write(c);
                }
                xfa = w.toString();
            } else if (opt.startsWith("--field=")) {
                String val = getValue(opt);
                int x = val.indexOf('=');
                if (x < 0) {
                    throw new IllegalArgumentException("\""+opt+"\" is invalid");
                } else {
                    fields.put(val.substring(0, x), val.substring(x+1));
                }
            } else if (opt.equals("--flatten") || opt.equals("--flatten=true")) {
                flatten = true;
            } else if (opt.equals("--flatten=false")) {
                flatten = false;
            } else if (opt.startsWith("--")) {
                i.previous();
                break;
            } else {
                pdf = load(opt);
            }
        }
        if (pdf != null) {
            Form form = pdf.getForm();
            if (xfa != null) {
                form.setXFADatasets(xfa);
            } else if (fdf != null) {
                FDF fdfobject = new FDF(new FileInputStream(fdf));
                pdf.importFDF(fdfobject);
            } else {
                for (Iterator<Map.Entry<String,String>> j = fields.entrySet().iterator();j.hasNext();) {
                    Map.Entry<String,String> e = j.next();
                    String key = e.getKey();
                    String val = e.getValue();
                    FormElement elt = form.getElement(key);
                    if (elt == null) {
                        System.err.println("Unknown field: \""+key+"\"");
                    } else if (elt instanceof FormText) {
                        ((FormText)elt).setValue(val);
                    } else if (elt instanceof FormRadioButton) {
                        ((FormRadioButton)elt).setValue(val);
                    } else if (elt instanceof FormCheckbox) {
                        ((FormCheckbox)elt).setValue(val);
                    } else if (elt instanceof FormChoice) {
                        ((FormChoice)elt).setValue(val);
                    } else {
                        System.err.println("Unable to set "+elt.getClass()+" element");
                    }
                }
                fields.clear();
            }
            if (flatten) form.flatten();
        }
        return pdf;
    }

    private static PDF sign(ListIterator<String> i, PDF pdf) throws IOException, GeneralSecurityException {
        String keypassword=null, password=null, reason=null, location=null, alias=null, keystorefile=null, field=null, name=null, keystoretype="JKS";
        int left=0, top=0, right=0, bottom=0, page=0;
        while (i.hasNext()) {
            String opt = i.next();
            if (opt.startsWith("--keypassword=")) {
                keypassword = getValue(opt);
            } else if (opt.startsWith("--password=")) {
                password = getValue(opt);
            } else if (opt.startsWith("--reason=")) {
                reason = getValue(opt);
            } else if (opt.startsWith("--location=")) {
                location = getValue(opt);
            } else if (opt.startsWith("--alias=")) {
                alias = getValue(opt);
            } else if (opt.startsWith("--name=")) {
                name = getValue(opt);
            } else if (opt.startsWith("--keystore=")) {
                keystorefile = getValue(opt);
            } else if (opt.startsWith("--keystoretype=")) {
                keystoretype = getValue(opt);
            } else if (opt.startsWith("--field=")) {
                field = getValue(opt);
            } else if (opt.startsWith("--left=")) {
                left = Integer.parseInt(getValue(opt));
            } else if (opt.startsWith("--top=")) {
                top = Integer.parseInt(getValue(opt));
            } else if (opt.startsWith("--right=")) {
                right = Integer.parseInt(getValue(opt));
            } else if (opt.startsWith("--bottom=")) {
                bottom = Integer.parseInt(getValue(opt));
            } else if (opt.startsWith("--page=")) {
                page = Integer.parseInt(getValue(opt)) - 1;
            } else if (opt.startsWith("--")) {
                i.previous();
                break;
            } else {
                pdf = load(opt);
            }
        }
        if (pdf!=null) {
            Form form = pdf.getForm();
            FormSignature sig = null;
            if (field != null) {
                sig = (FormSignature)form.getElement(field);
            }
            if (sig == null) {
                if (field == null) {
                    field = "Signature";
                }
                sig = new FormSignature();
                if (left != right && top != bottom) {
                    sig.addAnnotation(pdf.getPage(page), left, top, right, bottom);
                }
                pdf.getForm().addElement(field, sig);
            }
            if (keypassword==null) keypassword=password;
            KeyStore keystore = KeyStore.getInstance(keystoretype);
            InputStream in = new FileInputStream(keystorefile);
            keystore.load(in, password.toCharArray());
            in.close();
            sig.sign(keystore, alias, keypassword.toCharArray(), FormSignature.HANDLER_ACROBATSIX);

            sig.setReason(reason);
            sig.setLocation(location);
            if (name==null) {
                PKCS7SignatureHandler pkcs7 =  (PKCS7SignatureHandler)sig.getSignatureHandler();
                name = FormSignature.getSubjectField(pkcs7.getCertificates()[0], "CN");
            }
            sig.setName(name);
        }
        return pdf;
    }

    private static void toimage(ListIterator<String> i, PDF pdf, OutputStream out) throws IOException {
        java.awt.image.ColorModel cm = PDFParser.getBlackAndWhiteColorModel(128);
        double dpi = 200;
        String pages = null;
        String format = "tiff";
        String outfile = null;
        while (i.hasNext()) {
            String opt = i.next();
            if (opt.startsWith("--dpi=")) {
                dpi = Double.parseDouble(getValue(opt));
                if (dpi<=0 || dpi>600) throw new IllegalArgumentException("DPI must be betweeen 1 and 600");
            } else if (opt.equals("--model=bw")) {
                cm = PDFParser.BLACKANDWHITE;
            } else if (opt.startsWith("--model=bw")) {
                try {
                    int threshold=Integer.parseInt(opt.substring(10));
                    cm = PDFParser.getBlackAndWhiteColorModel(threshold);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Black and white threshold \""+opt.substring(10)+"\" not between 1 and 255");
                }
            } else if (opt.equals("--model=rgb")) {
                cm = PDFParser.RGB;
            } else if (opt.equals("--model=direct")) {
                cm = java.awt.image.ColorModel.getRGBdefault();
            } else if (opt.equals("--model=rgba")) {
                cm = PDFParser.RGBA;
            } else if (opt.equals("--model=cmyk")) {
                cm = PDFParser.CMYK;
            } else if (opt.equals("--model=gray")) {
                cm = PDFParser.GRAYSCALE;
            } else if (opt.equals("--format=tiff") || opt.equals("--format=tif")) {
                format="tiff";
            } else if (opt.equals("--format=png")) {
                format="PNG";
            } else if (opt.equals("--format=gif")) {
                format="gif";
            } else if (opt.equals("--format=jpeg")) {
                format="JPEG";
            } else if (opt.startsWith("--pages=")) {
                pages = getValue(opt);
            } else if (opt.startsWith("--output=")) {
                outfile = getValue(opt);
            } else if (opt.startsWith("--")) {
                i.previous();
                return;
            } else {
                pdf = load(opt);
            }
        }
        if (pdf != null) {
            try {
                RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                List<PDFPage> inpages = pdf.getPages();
                int[] pagelist = parsePages(pages, inpages.size());
                PDFParser parser = new PDFParser(pdf);
                if ("tiff".equals(format)) {
                    if (outfile != null) {
                        out = new FileOutputStream(outfile);
                    }
                    List<PDFPage> copy = new ArrayList<PDFPage>(inpages);
                    inpages.clear();
                    for (int j=0;j<pagelist.length;j++) {
                        inpages.add(copy.get(pagelist[j]));
                    }
                    parser.writeAsTIFF(out, (int)dpi, cm, hints);
                    if (out != null) {
                        out.close();
                    }
                    inpages.clear();
                    inpages.addAll(copy);
                } else {
                    if (outfile!=null && pdf.getNumberOfPages() > 1) {
                        DecimalFormat df = new DecimalFormat("0");
                        int np = pagelist[pagelist.length-1];
                        if (np>=10000) df = new DecimalFormat("00000");
                        if (np>=1000)  df = new DecimalFormat("0000");
                        if (np>=100)   df = new DecimalFormat("000");
                        if (np>=10)    df = new DecimalFormat("00");
                        for (int j=0;j<pagelist.length;j++) {
                            int pagenumber = pagelist[j];
                            PagePainter painter = parser.getPagePainter(pagenumber);
                            painter.setRenderingHints(hints);
                            java.awt.image.BufferedImage image = painter.getImage((float)dpi, cm);
                            String filename = outfile.substring(0, outfile.lastIndexOf("."));
                            filename += "-" + df.format(pagenumber) + outfile.substring(outfile.lastIndexOf("."));
                            javax.imageio.ImageIO.write(image, format, new FileOutputStream(filename));
                        }
                    } else {
                        if (outfile!=null) out = new FileOutputStream(outfile);
                        PagePainter painter = parser.getPagePainter(0);
                        painter.setRenderingHints(hints);
                        java.awt.image.BufferedImage image = painter.getImage((float)dpi, cm);
                        javax.imageio.ImageIO.write(image, format, out);
                    }
                }
            } finally {
                if (out != null) try { out.close(); } catch (IOException e) {}
            }
        }
    }

    private static void view(ListIterator<String> i, PDF pdf, String filename) throws IOException {
        List<String> l = new ArrayList<String>();
        while (i.hasNext()) {
            l.add(i.next());
        }
        if (filename != null) {
            l.add(filename);
        }
        String[] args = new String[l.size()];
        for (int j=0;j<args.length;j++) {
            args[j] = l.get(j);
        }
        PDFViewer.main(args);
    }

    private static void usage() {
        System.err.println(APPNAME+" "+PDF.VERSION);
        System.err.println("  Run "+APPNAME+" to open the viewer");
        System.err.println("  Run "+APPNAME+" --help for more information");
        System.err.println();
    }

    private static void help() {
        System.out.println("Usage: "+APPNAME+" [ --output=<outfile> | <infile> | <command> ] *");
        System.out.println();
        System.out.println("  This program runs a pipeline of one or more operations on a PDF. The initial");
        System.out.println("  PDF can be loaded (<infile> in the argument list above) and operations");
        System.out.println("  performed on it, after which it is written to standard out or <outfile> if");
        System.out.println("  it's specified.");
        System.out.println();
        System.out.println("  General arguments are:");
        System.out.println("     --output=<outfile>");
        System.out.println("       Set the file to write to. If not specified the default is standard out");
        System.out.println();
        System.out.println("     --profile=<profile>");
        System.out.println("       Set the output profile to use when rendering the PDF. Valid values are");
        System.out.println("       'nocompression', 'acrobat4', 'acrobat5', 'acrobat6', 'acrobat7'");
        System.out.println("       or 'recompression' (which recompresses uncompressed files). Individual");
        System.out.println("       features can be required or denied by adding a '+' or '-': eg.");
        System.out.println("       \"--profile=nocompression-'XMP Metadata'\" or \"--profile=+Linearized\"");
        System.out.println();
        System.out.println("     <infile>");
        System.out.println("       The PDF to work on. Must be specified somewhere in the parameter list");
        System.out.println("       unless the <filename> parameter is used with the join command.");
        System.out.println();
        System.out.println("  Command sequences can be one of the following:");
        System.out.println();
        System.out.println("     --version");
        System.out.println();
        System.out.println("       Display a short message then exit");
        System.out.println();
        System.out.println("     --help");
        System.out.println();
        System.out.println("       Display this message then exit");
        System.out.println();
        System.out.println("     --join [ [--pages=<list>] [--pagesize=<size>] [--dpi=<dpi>] <filename> ] *");
        System.out.println();
        System.out.println("       To concatenate a sequence of PDFs: Arguments are:");
        System.out.println();
        System.out.println("         --pagesize=<size>");
        System.out.println("           Set the size of the output pages. Valid values include A4, letter");
        System.out.println("           A4-landscape, 8.5x11in, 210x297mm and so on. The pagesize values is");
        System.out.println("           kept across input documents");
        System.out.println();
        System.out.println("         --dpi=<dpi>");
        System.out.println("           For input files that are bitmap images, this is an alternative way");
        System.out.println("           to set the output page size - by setting the DPI of the input image.");
        System.out.println("           Valid values are 1 to 600 (or 0 to use the default)");
        System.out.println();
        System.out.println("         --pages=<list>");
        System.out.println("           Set which pages from the next input file to use. List may be the");
        System.out.println("           value 'all' for all pages, 'reverse' to reverse the list or a comma-");
        System.out.println("           separated list of values, eg '1,2-5'. The value 'end' means the last");
        System.out.println("           page, and sequences may be backwards - eg. 'reverse' is equivalent");
        System.out.println("           to 'end-1'.");
        System.out.println();
        System.out.println("         <filename>");
        System.out.println("           The file to join to the existing PDF. May be a PDF, TIFF, JPEG, GIF");
        System.out.println("           PNG or PNM file");
        System.out.println();
        System.out.println("    --form [--flatten] [--xfa=<xfafile>] [--fdf=<fdffile>]");
        System.out.println("             [ [--properties=<propfile>] [--field=key=value] ] *");
        System.out.println();
        System.out.println("       To complete a PDF form: Arguments are:");
        System.out.println();
        System.out.println("         --properties=<propfile>");
        System.out.println("           Load the field values from the specified properties file. The keys");
        System.out.println("           in the file are the field names, the values the value to use");
        System.out.println();
        System.out.println("         --field=<key>=<value>");
        System.out.println("           Set the specified field to the specified value.");
        System.out.println();
        System.out.println("         --xfa=<xfafile>");
        System.out.println("           For a document containing an XFA form, set the form using the");
        System.out.println("           \"datasets\" object from the specified file. If this parameter is");
        System.out.println("           used any \"fields\" or \"properties\" parameters will be ignored.");
        System.out.println();
        System.out.println("         --fdf=<fdffile>");
        System.out.println("           Used to import an FDF or XFDF file into a PDF. If this parameter is");
        System.out.println("           used any \"fields\" or \"properties\" parameters will be ignored.");
        System.out.println();
        System.out.println("         --flatten");
        System.out.println("           Flatten the form after completion");
        System.out.println("");
        System.out.println("    --sign --keystore=<keystore> --alias=<alias> --password=<password>");
        System.out.println("             [--keypassword=<keypassword>] [--keystoretype=<keystoretype>]");
        System.out.println("             [--location=<location>] [--name=<name>] [--reason=<reason>]");
        System.out.println("             [--bottom=<bottom>] [--left=<left>] [--right=<right>] [--top=<top>]");
        System.out.println("             [--page=<page>] [--field=<field>");
        System.out.println();
        System.out.println("       Digitally sign a PDF file: Arguments are:");
        System.out.println();
        System.out.println("         --keystore=<keystore>");
        System.out.println("           The filename to load the keystore from. Must be specified.");
        System.out.println();
        System.out.println("         --alias=<alias>");
        System.out.println("           The alias to use from the keystore. Must be specified.");
        System.out.println();
        System.out.println("         --password=<password>");
        System.out.println("           The password to unlock the keystore. Must be specified.");
        System.out.println();
        System.out.println("         --keystoretype=<keystoretype>");
        System.out.println("           The type of keystore. The default is 'JKS', 'pkcs7' is also common");
        System.out.println();
        System.out.println("         --reason=<reason>");
        System.out.println("           The reason for signing");
        System.out.println();
        System.out.println("         --location=<location>");
        System.out.println("           The location the document is signed at");
        System.out.println();
        System.out.println("         --name=<name>");
        System.out.println("           The name of the person who is signing the document");
        System.out.println();
        System.out.println("         --field=<field>");
        System.out.println("           The name of the signature field. If it doesn't already exist in");
        System.out.println("           the PDF a new field is created with this name");
        System.out.println();
        System.out.println("         --page=<page>");
        System.out.println("           The page number to put the signature appearance on, if required.");
        System.out.println();
        System.out.println("         --left, top, right, bottom");
        System.out.println("           The location on the page to add the new field. The default is not");
        System.out.println("           to create a visible appearance for the signature");
        System.out.println();
        System.out.println("    --toimage [--format=<format>} [--model=<model>] [--dpi=<dpi>]");
        System.out.println();
        System.out.println("       To convert a PDF to a bitmap image - this must be the last command in");
        System.out.println("       the sequence (once a PDF is a TIFF there's nothing more you can do with");
        System.out.println("       it). Arguments are:");
        System.out.println();
        System.out.println("         --format=<format>");
        System.out.println("           Set the output file format. Values are 'tiff', 'png', 'jpeg'. The");
        System.out.println("           default is 'tiff', which will produce a multi-page document. 'png'");
        System.out.println("           and 'jpeg' will result in a single page document only");
        System.out.println();
        System.out.println("         --model=<model>");
        System.out.println("           Set the ColorModel. Values are 'bw', 'gray', 'rgb', 'rgba', 'cmyk'");
        System.out.println("           or 'bwNNN', where NNN is a value betwen 1 and 255 and sets how dark");
        System.out.println("           a color must be to be converted to black. The default model is 'bw'");
        System.out.println();
        System.out.println("         --dpi=<dpi>");
        System.out.println("           Set the DPI of the final file. Valid values are 1 to 600. The");
        System.out.println("           default is 200dpi");
        System.out.println();
        System.out.println("    --view");
        System.out.println("         --page=<page>");
        System.out.println("           Optionally set the initial page to open.");
        System.out.println();
        System.out.println("         --geometry=<width>x<height>[+x+y]");
        System.out.println("           Set the geometry of the Window - format is the same as X windows");
        System.out.println();
        System.out.println("         --features=[+name,-name,...]");
        System.out.println("           Add or remove features (specified by name) to the list of features");
        System.out.println("           included in the viewer");
        System.out.println();
        System.out.println("       Open the viewer. If a PDF has been specified on the command line, it");
        System.out.println("       will be opened. This must be the last command in the sequence.");
        System.out.println();
        System.out.println();
        System.out.println("  Some examples:");
        System.out.println("  1. To join two files then save them uncompressed");
        System.out.println("     "+APPNAME+" --profile=nocompression --join file1.pdf file2.tif > output.pdf");
        System.out.println();
        System.out.println("  2. To complete a form then display the result");
        System.out.println("     "+APPNAME+" in.pdf --form --field=name=John --field=date='20 May' --view");
        System.out.println();
        System.out.println("  3. To reverse a PDF then convert it to a TIFF");
        System.out.println("     "+APPNAME+" --join --pages=reverse file.pdf --output=out.tif --toimage");
        System.out.println();
        System.out.println("  4. To use only some pages from a source PDF");
        System.out.println("     "+APPNAME+" --join --pages=1,3-7,10-end file1.pdf > out.pdf");
        System.out.println();
        System.out.println("  5. To open and display a PDF");
        System.out.println("     "+APPNAME+" file.pdf      (or, a more complex example)");
        System.out.println("     "+APPNAME+" --view --page=3 --features=-Menus,-Toolbars \\");
        System.out.println("        --geometry=700x700+100-100 file.pdf");
    }
}
