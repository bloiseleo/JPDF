// $Id: Stamp.java 10479 2009-07-10 09:51:07Z chris $

import org.faceless.pdf2.*;
import java.io.*;
import java.awt.Color;
import java.util.List;

/**
 * An example which adds a "Confidential" rubber stamp to each page.
 * Takes a filename as an argument, and creates "Stamp.pdf" as output.
 *
 * We demonstrate 4 different ways to do this, each with it's own pros and cons:
 *
 * Method 1: Adding a read-only annotation to the page
 * Method 2: Adding some text "under" the page contents
 * Method 3: Adding some outlined text over the page contents
 * Method 4: Adding some translucent text over the page contents
 *
 * Each method can be tried by changing the value of the METHOD variable.
 */
public class Stamp
{
    // "METHOD" can be 1, 2, 3, or 4 to use the various different
    // styles of stamping.
    //
    static int METHOD = 1;

    static PDFStyle stampstyle;

    public static void main(String[] args) throws IOException {
        if (args.length==3 && args[0].equals("--method")) {
            METHOD = Integer.parseInt(args[1]);
            args = new String[] { args[2] };
        }
        if (args.length==0) {
            System.err.println("Usage: java Stamp [--method N] <filename.pdf>");
            System.err.println("       Creates the file \"Stamp.pdf\"");
            System.exit(0);
        }

        // Initialize stampstyle
        if (METHOD==1) {
            initstamp1();
        } else if (METHOD==2) {
            initstamp2();
        } else if (METHOD==3) {
            initstamp3();
        } else if (METHOD==4) {
            initstamp4();
        }

        // Load the PDF
        PDF pdf = new PDF(new PDFReader(new File(args[0])));

        // For each page...
        List pages = pdf.getPages();
        for (int i=0;i<pages.size();i++) {
            PDFPage page = (PDFPage)pages.get(i);

            float[] box = page.getBox("CropBox");
            if (box==null) {
                box = page.getBox("MediaBox");
            }
            float midx = box[0] + (box[2] - box[0]) / 2;     // Position stamp just up
            float midy = box[1] + (box[3] - box[1]) / 1.5f;  // from the page center

            // Pick an arbitrary font size
            float fontsize = Math.min((box[2] - box[0]) / 15, (box[3] - box[1]) / 10);

            // Add the stamp.
            if (METHOD==1) {
                stamp1(page, midx, midy, fontsize);
            } else if (METHOD==2) {
                stamp2(page, midx, midy, fontsize);
            } else if (METHOD==3) {
                stamp3(page, midx, midy, fontsize);
            } else if (METHOD==4) {
                stamp4(page, midx, midy, fontsize);
            }
        }

        OutputStream out = new FileOutputStream("Stamp.pdf");
        pdf.render(out);
        out.close();
    }

    //------------------------------------------------------------------------
    // The first type of stamp - adds a translucent annotation to the page and
    // set it to read-only, preventing it from being moved or deleted.
    // 
    // Pros: * A number of predefined stamps to choose from, or create your
    //         own without much difficulty
    //       * Guaranteed to be visible - appears above any other annotations
    //         except form fields.
    //       * Stamp can optionally be displayed only when the document is
    //         printed (or only when it's on screen). This is only possible with
    //         annotations.
    //       * If all you're doing is reading, stamping and writing a document,
    //         then each page won't have to be decompressed/recompressed. Which
    //         makes this method potentially the fastest.
    //
    // Cons: * Translucency works only in Acrobat 5 or later.
    //       * With a knowledge of the PDF spec, the stamp could be removed by
    //         someone with a PDF API (like this one) or a hex editor. Certainly
    //         not something an average user could do.
    //
    static void initstamp1() {
    }

    static void stamp1(PDFPage page, float midx, float midy, float fontsize) {
        AnnotationStamp stamp = new AnnotationStamp("stamp.stencil.Confidential", 0.35f);
        stamp.setReadOnly(true);
        float w = stamp.getRecommendedWidth();
        float h = stamp.getRecommendedHeight();
        stamp.setRectangle(midx-(w/2), midy-(h/2), midx+(w/2), midy+(h/2));
        page.getAnnotations().add(stamp);
    }


    //------------------------------------------------------------------------
    // The second type of stamp - adds some regular text to the page "under"
    // the current page content. 
    //
    // Pros: * Difficult to remove even with an API and a knowledge of PDF.
    //       * Works in Acrobat 4 and earlier
    //       * Can use any text or image you like, in any layout.
    //
    // Cons: * Will be overwritten by page content, so solid blocks (images,
    //         filled rectangles etc.) will obscure it.
    //
    static void initstamp2() {
        stampstyle = new PDFStyle();
        stampstyle.setTextAlign(PDFStyle.TEXTALIGN_CENTER);
        stampstyle.setFillColor(new Color(255, 210, 210));
    }

    static void stamp2(PDFPage page, float midx, float midy, float fontsize) {
        stampstyle.setFont(new StandardFont(StandardFont.COURIERBOLD), fontsize);
        page.seekStart();                    // Move to start of page content
        page.setStyle(stampstyle);
        page.rotate(midx, midy, -20);
        page.drawText("Confidential", midx, midy);
    }


    //------------------------------------------------------------------------
    // The third type of stamp - again, adds text to the page but this time
    // above the page body. We avoid obscuring the current content of the
    // page by making the text outlined (the same way we do the "DEMO" stamp
    // in the trial version).
    //
    // Pros: * Difficult to remove even with an API and a knowledge of PDF.
    //       * Works in Acrobat 4 and earlier.
    //       * Can use any text you like, in any layout or font.
    //
    // Cons: * The text has to be outlined.
    //       * Can be obscured by an annotation displayed above it.
    //
    static void initstamp3() {
        stampstyle = new PDFStyle();
        stampstyle.setTextAlign(PDFStyle.TEXTALIGN_CENTER);
        stampstyle.setLineColor(new Color(0x00FF0000));
        stampstyle.setFontStyle(PDFStyle.FONTSTYLE_OUTLINE); // Set outlining
    }

    static void stamp3(PDFPage page, float midx, float midy, float fontsize) {
        stampstyle.setFont(new StandardFont(StandardFont.COURIERBOLD), fontsize);
        page.save();
        page.setStyle(stampstyle);
        page.rotate(midx, midy, -20);
        page.drawText("Confidential", midx, midy);
        page.restore();
    }


    //------------------------------------------------------------------------
    // The fourth type of stamp - like stamp3 but instead of outlined text, we
    // add translucent text.
    //
    // Pros: * Difficult to remove even with an API and a knowledge of PDF.
    //       * Can use any text you like, in any layout or font.
    //
    // Cons: * Translucency only works in Acrobat 5 or later
    //       * Can be obscured by an annotations displayed above it.
    //
    static void initstamp4() {
        stampstyle = new PDFStyle();
        stampstyle.setTextAlign(PDFStyle.TEXTALIGN_CENTER);
        stampstyle.setFillColor(new Color(0x80FF0000, true)); // Set alpha value
    }

    static void stamp4(PDFPage page, float midx, float midy, float fontsize) {
        stampstyle.setFont(new StandardFont(StandardFont.COURIERBOLD), fontsize);
        page.save();
        page.setStyle(stampstyle);
        page.rotate(midx, midy, -20);
        page.drawText("Confidential", midx, midy);
        page.restore();
    }
}
