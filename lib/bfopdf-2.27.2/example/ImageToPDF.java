// $Id: ImageToPDF.java 15470 2012-04-17 17:37:42Z mike $

import java.util.*;
import java.io.*;
import org.faceless.pdf2.*;

/**
 * Takes a list of image files on the command line and creates a PDF with
 * each image, one per page. If an image has multiple pages (like some
 * TIFF images), include each one.
 *
 * Recognized file formats include most PNG, GIF, JPEG and TIFF images.
 * Unrecognized files will display an error and the process will continue.
 */
public class ImageToPDF
{
    public static void main(String[] args) throws IOException {
        if (args.length==0) {
            System.out.println("Usage: java ImageToPDF [--dpi NNN] <file1> [<file2> ... ]\n");
            System.out.println("       Creates the file \"ImageToPDF.pdf\"\n\n");
            System.exit(0);
        }

        PDF pdf = new PDF();

        // Load each image as a PDFImageSet - even if it only contains
        // one page, this keeps things simple.
        //
        int dpi = 0;
        boolean quantize = false;
        for (int i=0;i<args.length;i++) {
            if (args[i].equals("--dpi")) {
                dpi = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--quantize")) {
                quantize = true;
            } else {
                InputStream in = new FileInputStream(args[i]);
                PDFImageSet imgs = new PDFImageSet(in);
                for (int j=0;j<imgs.getNumImages();j++) {
                    // For each page in the image set, create a new page in the
                    // PDF that's the same size, and display the image.
                    PDFImage img = imgs.getImage(j);
                    if (quantize) {
                        img.quantize();
                    }
                    float w = img.getWidth();
                    float h = img.getHeight();
                    if (dpi != 0) {
                        w *= img.getDPIX()/dpi;
                        h *= img.getDPIY()/dpi;
                    }
                    PDFPage page = pdf.newPage((int)w, (int)h);
                    PDFStyle style = new PDFStyle();
                    page.drawImage(img, 0, 0, w, h);
                }
                in.close();
            }
        }

        OutputStream fo = new FileOutputStream("ImageToPDF.pdf");
        pdf.render(fo);
        fo.close();
    }
}
