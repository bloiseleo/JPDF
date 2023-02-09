// $Id: Join2Up.java 19735 2014-07-22 12:07:13Z mike $

import org.faceless.pdf2.*;
import java.io.*;
import java.util.*;

/**
 * An example which "stitches" pages together - displaying two pages
 * from the original documents on a single page in "2-up" format.
 *
 * Takes a list of filenames on the command line.
 */
public class Join2Up
{
    public static void main(String[] args) throws IOException {
        if (args.length==0) {
            System.err.println("Usage: java Join2Up <file1.pdf> [<file2.pdf> ...]");
            System.err.println("       Creates the file \"Join2Up.pdf\"\n");
            System.exit(0);
        }

        PDF dest = new PDF();
        PDFPage destpage = null;
        List<PDFPage> holdlist = new ArrayList<PDFPage>();

        // For each filename, add all the pages to the list "holdlist"
        for (int i=0;i<args.length;i++) {
            PDFReader reader = new PDFReader(new File(args[i]));
            PDF pdf = new PDF(reader);
            holdlist.addAll(pdf.getPages());
        }

        // Now add the pages from "holdlist" to the new PDF document
        //
        // The way this works is we create a PDFCanvas from each page,
        // which acts as a "stamp". We then create a new page and draw
        // this stamp onto the correct half of the page.
        //
        // NB: This takes no account of aspect ratio of the pages. For ISO
        // page sizes like A4, the ratio of width to height is sqrt(2) so
        // no distortion is introduced, but Letter or other US page formats
        // will be slightly stretched.
        //
        for (int i=0;i<holdlist.size();i++) {
            PDFPage srcpage = (PDFPage)holdlist.get(i);
            PDFCanvas canvas = new PDFCanvas(srcpage);

            // For even pages, create a new page and measure it in
            // percentage from the top-left.
            //
            if (i%2==0) {
                destpage = dest.newPage("A4 landscape");
                destpage.setUnits(PDFPage.UNITS_PERCENT, PDFPage.ORIGIN_PAGETOP);
            }
            destpage.drawCanvas(canvas, (i%2)*50, 0, ((i%2)+1)*50, 100);
        }

        OutputStream out = new FileOutputStream("Join2Up.pdf");
        dest.render(out);
        out.close();
    }
}
