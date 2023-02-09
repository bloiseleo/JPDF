// $Id: PDFToImage.java 25725 2017-07-12 10:39:28Z mike $

import java.awt.image.ColorModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.Transparency;
import java.awt.image.ComponentColorModel;
import java.awt.color.ICC_Profile;
import java.awt.color.ICC_ColorSpace;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.util.*;
import java.security.*;
import javax.imageio.*;
import org.faceless.pdf2.*;

/**
 * This example converts a PDF to a multi-page TIFF or a series of
 * bitmap images, using the PDF Viewer extension. 
 * 
 * Usage is:
 *
 *  java PDFToImage [--dpi <dpi>] [--pages <pagerange>] [--format <format>] [--model <model>]  file.pdf
 *
 * where "dpi" is the DPI of the final image (defaults to 200), "format" is the
 * file format - one of tiff, png or jpeg, and "model" is the color model to
 * use - one of bw, gray, rgb, rgba, cmyk or bwNNN, where "NNN" is a number between
 * 1 and 254 and determines the threshold where gray is considered to be black or
 * white. TIFF can use any of these models, jpeg and rgb will only use RGB or Gray.
 *
 * For TIFF images, the output filename is of the form "file.tif". For PNG or JPEG
 * images, the files are of the form "file-nnn.png", where "nnn" is the page number.
 *
 * @since 2.8.2
 */
public class PDFToImage {

    public static void main(String[] args) throws IOException {
        ColorModel cm = PDFParser.BLACKANDWHITE;
        float dpi = 200;
        String format = "tif";
        String output = null;
        String pagerange = null;
        boolean wrotesomething = false;

        for (int i=0;i<args.length;i++) {
            if (args[i].equals("--dpi")) {
                try {
                    dpi = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    usage();
                }
            } else if (args[i].equals("--laxssl")) {
                laxssl();
            } else if (args[i].equals("--model")) {
                String model = args[++i];
                if (model.equals("bw")) {
                    cm = PDFParser.BLACKANDWHITE;
                } else if (model.equals("gray")) {
                    cm = PDFParser.GRAYSCALE;
                } else if (model.equals("rgb")) {
                    cm = PDFParser.RGB;
                } else if (model.equals("cmyk")) {
                    cm = PDFParser.CMYK;
                } else if (model.equals("rgba")) {
                    cm = PDFParser.RGBA;
                } else if (model.equals("direct")) {
                    cm = ColorModel.getRGBdefault();
                } else if (model.equals("dither")) {
                    cm = PDFParser.getBlackAndWhiteDitheredColorModel();
                } else if (model.equals("intent")) {
                    cm = null;
                } else if (model.startsWith("bw")) {
                    try {
                        int threshold = Integer.parseInt(model.substring(2));
                        cm = PDFParser.getBlackAndWhiteColorModel(threshold);
                    } catch (NumberFormatException e) {
                        usage();
                    }
                } else {
                    usage();
                }
            } else if (args[i].equals("--pages")) {
                pagerange = args[++i];
            } else if (args[i].equals("--output")) {
                output = args[++i];
            } else if (args[i].equals("--format")) {
                format = args[++i];
                if (format.equals("tiff")) format = "tif";
                if (format.equals("jpeg")) format = "jpg";
                if (!format.equals("tif") && !format.equals("jpg") && !format.equals("png")) {
                    usage();
                }
            } else if (args[i].startsWith("--")) {
                usage();
            } else {                     // ---- Create the Image files here ----
                wrotesomething = true;
                String infile = args[i];
                String prefix = infile;
                if (infile.endsWith(".pdf") || infile.endsWith(".PDF")) {
                    prefix = infile.substring(0, infile.length()-4);
                    prefix = prefix.substring(prefix.lastIndexOf("/") + 1);
                }
                System.out.print("Reading \""+infile+"\"... ");
                ICC_Profile icc = null;
                PDFReader reader = new PDFReader();
                if (infile.startsWith("http://") || infile.startsWith("https://")) {
                    reader.setSource(new URL(infile));
                    prefix = infile.substring(infile.lastIndexOf("/") + 1);
                } else {
                    reader.setSource(new File(infile));
                }
                reader.load();
                PDF pdf = new PDF(reader);
                if (cm == null) {
                    // "intent" specified as the ColorModel: extract the model from
                    // the PDF OutputIntent and use that. Only works with TIFF!
                    OutputProfile profile = pdf.getBasicOutputProfile();
                    if (profile.isSet(OutputProfile.Feature.HasOutputIntentGTS_PDFX)) {
                        icc = profile.getOutputIntentDestinationProfile("GTS_PDFX");
                    } else if (profile.isSet(OutputProfile.Feature.HasOutputIntentGTS_PDFA1)) {
                        icc = profile.getOutputIntentDestinationProfile("GTS_PDFA1");
                    } else if (profile.isSet(OutputProfile.Feature.HasOutputIntentGTS_PDFA)) {
                        icc = profile.getOutputIntentDestinationProfile("GTS_PDFA");
                    }
                    if (icc == null) {
                        throw new IllegalArgumentException("No OutputIntent profile");
                    } else {
                        ICC_ColorSpace cs = new ICC_ColorSpace(icc);
                        cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
                    }
                }
                List<PDFPage> origpages = new ArrayList<PDFPage>(pdf.getPages());
                if (pagerange != null) {
                    limitPages(pdf, pagerange);
                    pagerange = null;
                }
                PDFParser parser = new PDFParser(pdf);

                if (format.equals("tif")) {
                    String outfile = output != null ? output : prefix+".tif";
                    System.out.print("Writing \""+outfile+"\"... ");
                    FileOutputStream out = new FileOutputStream(outfile);
                    parser.writeAsTIFF(out, (int)dpi, cm);
                    out.close();
                } else {
                    DecimalFormat pageformat = new DecimalFormat("000");
                    if (cm!=ColorModel.getRGBdefault() && cm!=PDFParser.RGB && cm!=PDFParser.GRAYSCALE && (!format.equals("png") || cm!=PDFParser.RGBA)) {
                        // ColorModel for PNG must be rgb, rgba or grayscale
                        // ColorModel for JPEG must be rgb or grayscale
                        cm = PDFParser.RGB;
                    }
                    for (int j=0;j<pdf.getNumberOfPages();j++) {
                        int origpagenumber = origpages.indexOf(pdf.getPage(j)) + 1;
                        String outfile = output != null ? output : prefix+"-"+pageformat.format(origpagenumber)+"."+format;
                        System.out.print("Writing \""+outfile+"\"...\n");
                        PagePainter painter = parser.getPagePainter(j);
                        BufferedImage image = painter.getImage(dpi, cm);
                        ImageIO.write(image, format, new File(outfile));
                    }
                }
                System.out.println();
            }
        }
        if (!wrotesomething) {
            usage();
        }
        System.exit(0);
    }

    private static void limitPages(PDF pdf, String pagerange) {
        List<PDFPage> pagelist = pdf.getPages();
        List<PDFPage> worklist = new ArrayList<PDFPage>(pagelist);
        pagelist.clear();
        for (StringTokenizer st = new StringTokenizer(pagerange, ",");st.hasMoreTokens();) {
            String token = st.nextToken();
            int ix = token.indexOf('-');
            int p1, p2;
            if (ix==-1) {
                p1 = p2 = Integer.parseInt(token);
            } else if (ix==0) {
                p1 = 0;
                p2 = Integer.parseInt(token.substring(1));
            } else if (ix==token.length()-1) {
                p1 = Integer.parseInt(token.substring(0, token.length()-1));
                p2 = worklist.size() - 1;
            } else {
                p1 = Integer.parseInt(token.substring(0, ix));
                p2 = Integer.parseInt(token.substring(ix+1));
            }
            pagelist.addAll(worklist.subList(p1-1, p2));
        }
    }

    private static void usage() {
        System.out.println("\nUsage: java PDFToImage [--pages <pagerange>] [--dpi <dpi>] [--output <file>]");
        System.out.println("                         [--format <format>] [--model <model>]  file.pdf\n");
        System.out.println("where \"dpi\" is the DPI of the final image (defaults to 200), \"format\" is the");
        System.out.println("file format - one of tiff, png or jpeg, and \"model\" is the color model to use;");
        System.out.println("one of direct, bw, gray, rgb, rgba, cmyk, dither or bwNNN, wher \"NNN\" is a");
        System.out.println("number between 1 and 254, and sets the threshold where gray is considered to be");
        System.out.println("black or white.");
        System.out.println("Alternatively, if the PDF has an Output Intent specified (eg PDF/X or PDF/A)");
        System.out.println("the value 'intent' can be used to create an image matching the specified intent");
        System.out.println("TIFF can use any of these models, JPEG and PNG will always be RGB or Grayscale.");
        System.out.println("Pagerange can be used to limit the pages converted - example values are \"1\",");
        System.out.println("\"2-5\", \"8,9,14-18,24\" (page numbers start at 1).\n");
        System.out.println("For TIFF images, the output filename is of the form \"file.tif\". For PNG or JPEG");
        System.out.println("images the files are of the form \"file-nnn.png\", where \"nnn\" is the pagenumber.");
        System.out.println("The output will be based on the input path unless 'output' is specified.");
        System.out.println();
        System.exit(-1);
    }

    private static void laxssl() {
        try {
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
