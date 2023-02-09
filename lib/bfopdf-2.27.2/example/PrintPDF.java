// $Id: PrintPDF.java 44744 2022-11-04 11:44:27Z mike $

import java.awt.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import javax.print.*;
import java.io.*;
import java.util.StringTokenizer;
import java.util.List;
import org.faceless.pdf2.*;

/**
 * Print a PDF to either the default printer or to a PostScript file called
 * "PrintPDF.ps". This uses the Java 1.4 print API in the javax.print package,
 * although the 1.2 API will work just as well.
 *
 * @since 2.5
 */
public class PrintPDF
{
    public static void main(String[] args) throws IOException, PrintException, PrinterException {
        boolean printtofile = false, list = false;
        int printernumber = 0;

        if (args.length == 0) {
            System.err.println("Usage: java PrintPDF [--list] [--file] [--printer <n>] [--pages <pages>] [--scale <x>] <filename>");
            System.err.println("       \"--list\" will list printers that can print the specified file");
            System.err.println("       \"--printer <n>\" will print to the numbered printer (run --list first)");
            System.err.println("       \"--file\" sends the PostScript output to \"PrintPDF.ps\"");
            System.err.println("       \"--printasimage <n>\" will print as bitmap at the specified resolution");
            System.err.println("       \"--pages\" sets the pages to print: <pages> is a comma-seperated");
            System.err.println("       \"--scale\" sets the scale to print at - value is specified as a percentage");
            System.err.println("       list of page ranges, which may be specified as follows:");
            System.err.println("         nnn            - single page");
            System.err.println("         -nnn           - from start to specified page");
            System.err.println("         nnn-           - from specified page to end");
            System.err.println("         nnn-mmm        - from page nnn to page mmm");
            System.err.println("       eg. java PrintPDF -p1,4-5,10- file.pdf");
            System.err.println();
            System.exit(-1);
        }

        // 1. Load the PDF
        // 2. Parse the arguments to set pageranges etc.
        // 3. Print the PDFParser to either a PostScript file or the default printer

        PDF pdf = new PDF(new PDFReader(new File(args[args.length-1])));
        float scale = 1;
        int printasimagedpi = 0;

        PrintRequestAttributeSet atts = new HashPrintRequestAttributeSet();
        DocAttributeSet docatts = new HashDocAttributeSet();
        initializePrintAttributes(atts, docatts, pdf);
        for (int i=0;i<args.length-1;i++) {
            if (args[i].equals("--printer")) {
                printernumber = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--pages")) {
                setPrintRange(args[++i], pdf.getNumberOfPages(), atts);
            } else if (args[i].equals("--printasimage")) {
                printasimagedpi = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--scale")) {
                scale = Integer.parseInt(args[++i]) / 100f;
            } else if (args[i].equals("--file")) {
                printtofile = true;
            } else if (args[i].equals("--list")) {
                list = true;
            }
        }
        if (scale != 1) {
            scalePDF(pdf, scale);
        }

        PDFParser parser = new PDFParser(pdf);
        parser.setPrintAsImageResolution(printasimagedpi);
        if (list) {
            listPrinters(atts);
        } else if (printtofile) {
            printToFile(parser, atts, docatts, "PrintPDF.ps");
            System.out.println("Created \"PrintPDF.ps\"");
        } else {
            PrintService svc = printToPrinter(parser, atts, docatts, printernumber);
            System.out.println("Spooled PDF to "+svc);
        }
    }

    /**
     * Initialize the PrintRequestAttributeSet based on any default print options
     * set in the PDF, such as duplex, number of copies etc.
     */
    public static void initializePrintAttributes(PrintRequestAttributeSet atts, DocAttributeSet docatts, PDF pdf) {
        String duplex = (String)pdf.getOption("print.duplex");
        if ("DuplexFlipShortEdge".equals(duplex)) {
            atts.add(Sides.TWO_SIDED_SHORT_EDGE);
        } else if ("DuplexFlipLongEdge".equals(duplex)) {
            atts.add(Sides.TWO_SIDED_LONG_EDGE);
        }

        Integer copies = (Integer)pdf.getOption("print.copies");
        if (copies != null) {
            atts.add(new Copies(copies.intValue()));
        }

        List pages = (List)pdf.getOption("print.pagerange");
        if (pages != null) {
            int[][] x = new int[pages.size()][];
            for (int i=0;i<pages.size();i++) {
                x[i] = new int[] { ((PDFPage)pages.get(i)).getPageNumber() };
            }
            atts.add(new PageRanges(x));
        }

        if (pdf.getInfo("Title") != null) {
            docatts.add(new DocumentName((String)pdf.getInfo("Title"), null));
        }
    }

    public static void listPrinters(final PrintRequestAttributeSet atts) throws PrinterException {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, atts);
        System.out.println("Found "+services.length+" printers");
        for (int i=0;i<services.length;i++) {
            System.out.println(i+": "+services[i]);
        }
    }

    /**
     * Print the Pageable object to the default printer. No PDF
     * specific code in here, this is all regular Java 1.4 printing
     * code.
     */
    public static PrintService printToPrinter(final Pageable pageable, final PrintRequestAttributeSet atts, final DocAttributeSet docatts, int printernumber)
        throws PrinterException
    {
        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PAGEABLE;
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, atts);
        if (services.length <= printernumber) {
            throw new IllegalStateException("Less than "+(printernumber+1)+" printers found");
        }

        final PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(services[printernumber]);

        // Why do we do this? It seems there are some issues with printing to
        // non-standard paper sizes on Windows, which can cause the edge of the
        // page to be chopped off. This wrapper around the "Pageable" object
        // means the paper size will always be set to match the paper size
        // available to the PrinterService. For other platforms, it's not
        // necessary but won't hurt.
        //
        job.setPageable(new Pageable() {
            public int getNumberOfPages() {
                return pageable.getNumberOfPages();
            }
            public Printable getPrintable(int pagenumber) {
                return pageable.getPrintable(pagenumber);
            }
            public PageFormat getPageFormat(int pagenumber) {
                PageFormat format = pageable.getPageFormat(pagenumber);
                Paper paper = job.defaultPage(format).getPaper();
                paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
                format.setPaper(paper);
                return format;
            }
        });
        job.print(atts);
        return services[printernumber];
    }

    /**
     * Print the Pageable object to a file called "PrintPDF.ps". No PDF
     * specific code in here, this is all regular Java 1.4 printing code.
     */
    public static void printToFile(Pageable pageable, PrintRequestAttributeSet atts, DocAttributeSet docatts, String filename)
        throws PrintException, IOException
    {
        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PAGEABLE;
        String psMimeType = DocFlavor.BYTE_ARRAY.POSTSCRIPT.getMimeType();
        StreamPrintServiceFactory[] factories = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(flavor, psMimeType);
        if (factories.length == 0) {
            throw new IllegalStateException("No PostScript Factory found");
        }

        FileOutputStream out = new FileOutputStream(filename);
        StreamPrintService sps = factories[0].getPrintService(out);
        DocPrintJob pj = sps.createPrintJob();
        Doc doc = new SimpleDoc(pageable, flavor, docatts);
        pj.print(doc, atts);
        out.close();
    }

    /**
     * Return the pages to dispay in a form suitable for adding
     * to the PrintRequestAttributes, or null for all pages
     */
    private static void setPrintRange(String arg, int maxpage, PrintRequestAttributeSet atts) {
        String pagerange="";
        for (StringTokenizer st = new StringTokenizer(arg, ",", true);st.hasMoreTokens();) {
            String token = st.nextToken();
            int ix = token.indexOf('-');
            if (ix==0) {
                token = "1"+token;
            } else if (ix==token.length()-1) {
                token = token+(maxpage-1);
            }
            pagerange += token;
        }
        atts.add(new PageRanges(pagerange));
    }

    /**
     * Scale the content on every page in the page, keeping the original
     * page size but adding a margin.
     * @param scale the scale value - 1 for full size, 0.9 for 90% etc.
     */
    private static void scalePDF(PDF pdf, float scale) {
        for (int i=0;i<pdf.getPages().size();i++) {
            PDFPage oldpage = pdf.getPage(i);
            List<PDFAnnotation> annots = oldpage.getAnnotations();
            while (!annots.isEmpty()) {
                annots.get(annots.size() - 1).flatten();
            }
            PDFCanvas c = new PDFCanvas(oldpage);
            float w = oldpage.getWidth();
            float h = oldpage.getHeight();
            float mx = w * (1-scale)/2; // margin - if scale=90%, margin is 5%
            float my = h * (1-scale)/2;
            PDFPage newpage = new PDFPage(w + "x" + h);
            newpage.drawCanvas(c, mx, my, w - mx, h - my);
            newpage.flush();
            pdf.getPages().set(i, newpage);
        }
    }
}
