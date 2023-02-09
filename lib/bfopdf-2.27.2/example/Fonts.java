// $Id: Fonts.java 10479 2009-07-10 09:51:07Z chris $

import java.io.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * This example shows how to mix and match the various types
 * of Fonts that can be used in a document.
 */
public class Fonts
{
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("Letter");

        // First style - "plain" - Helvetica 12 pt
        PDFStyle plain = new PDFStyle();
        plain.setFont(new StandardFont(StandardFont.HELVETICA), 12);
        plain.setFillColor(Color.black);

        // An italic version of "plain"
        PDFStyle italic = new PDFStyle(plain);
        italic.setFont(new StandardFont(StandardFont.HELVETICAOBLIQUE), 12);

        // A bold version of "plain"
        PDFStyle bold = new PDFStyle(plain);
        bold.setFont(new StandardFont(StandardFont.HELVETICABOLD), 12);

        // Create versions of "plain" that use a TrueType font and a "Type1" font.
        PDFStyle truetype = new PDFStyle(plain);
        PDFStyle type1 = new PDFStyle(plain);

        // A TrueType font - "Blue Highway", by Larabie fonts (www.larabiefonts.com)
        truetype.setFont(new OpenTypeFont(new FileInputStream("resources/fonts/bluehigh.ttf"), 1), 12);

        // A Type 1 font - "Bitstream Charter" by Bitstream Corporation.
        type1.setFont(new Type1Font(new FileInputStream("resources/fonts/bchr.afm"), new FileInputStream("resources/fonts/bchr.pfb")), 12);

        // Create a paragraph the size of the page, less a 50 point margin
        page.beginText(50, 50, page.getWidth()-100, page.getHeight()-100);

        // Draw the text
        page.setStyle(plain);
        page.drawText("This text is in ");
        page.setStyle(italic);
        page.drawText("Helvetica Oblique ");
        page.setStyle(plain);
        page.drawText("and this text is in ");
        page.setStyle(bold);
        page.drawText("Helvetica Bold. ");
        page.setStyle(plain);
        page.drawText("You can even mix TrueType fonts, ");
        page.setStyle(truetype);
        page.drawText("like \"Blue Highway\" from Larabie fonts, ");
        page.setStyle(plain);
        page.drawText("and Type 1 fonts ");
        page.setStyle(type1);
        page.drawText("like \"Bitstream Charter\" from Bitstream Corporation ");
        page.setStyle(plain);
        page.drawText("on the same line.\n\n");

        page.setStyle(type1);
        page.drawText("For an example of kerning, look closely at the spaces between the letters in the following phrase: \"AWAWAWA\". Notice how the top of the 'W' overlaps the Bottom of the 'A'. We also support ligatures, which you can see by carefully examining the letters 'f' and 'i' in the word \"difficult\".\n\n");

        page.setStyle(plain);
        page.drawText("Since version 1.1 of the library, we support TrueType subsetting, which results in much smaller files. For instance, this document is 60Kb without subsetting and 47k with subsetting, and the difference is even more noticable with larger fonts.\n\n");

        // Demonstrate track kerning (added in 1.1.14).
        //
        // Notice we clone the style before changing the tracking - this is
        // necessary because we are changing the style in the middle of a line.
        //
        page.drawText("We've also added \"Track Kerning\", for ");

        PDFStyle narrow = new PDFStyle(plain);
        narrow.setTrackKerning(-100);
        page.setStyle(narrow);
        page.drawText("cramped text");

        PDFStyle wide = new PDFStyle(plain);
        wide.setTrackKerning(200);
        page.setStyle(wide);
        page.drawText(" or spaced-out text.\n");

        page.endText(false);

        // Write the document to a file
        //
        OutputStream out = new FileOutputStream("Fonts.pdf");
        pdf.render(out);
        out.close();
    }
}
