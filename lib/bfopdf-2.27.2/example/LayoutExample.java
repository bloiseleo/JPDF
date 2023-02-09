// $Id: LayoutExample.java 10479 2009-07-10 09:51:07Z chris $

import org.faceless.pdf2.*;
import java.io.*;
import java.awt.Color;
import java.util.*;

/**
 * An example demonstrating how to use the LayoutBox class for
 * advanced combinations of text and graphics.
 */
public class LayoutExample
{
    static String text1 = "orem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.";

    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("Letter");

        // Create a style to draw in, and load an image to use in the demonstration.
        PDFStyle style = new PDFStyle();
        style.setTextAlign(PDFStyle.TEXTALIGN_JUSTIFY + PDFStyle.TEXTALIGN_BOTTOM);
        style.setFont(new StandardFont(StandardFont.TIMES), 9);
        style.setFillColor(Color.black);

        PDFImage image = new PDFImage(new FileInputStream("resources/L.gif"));
        LayoutBox.Box imagebox=null;

        // Create the LayoutBox - the width of the page less 200 points.
        LayoutBox flow = new LayoutBox(page.getWidth()-200);
        flow.setStyle(style);

        // Add the first block - create an image "floating" on the left, which
        // will cause all further text to wrap around the image.
        imagebox = flow.addBoxLeft(image.getWidth()/3, image.getHeight()/3, LayoutBox.CLEAR_LEFT);
        imagebox.setImage(image);
        flow.addText(text1, style, Locale.getDefault());
        flow.addLineBreak(style);
        flow.addLineBreak(style);

        // Add the second block. Place the first image "inline", so it's treated as part
        // of the text. Because the text style has the TEXTALIGN_BOTTOM alignment, the
        // text will be aligned so that the bottom of the text is flush with the bottom of
        // the line (and therefore, the image).
        imagebox = flow.addBoxInline(image.getWidth()/3, image.getHeight()/3, 0);
        imagebox.setImage(image);
        flow.addText(text1, style, Locale.getDefault());

        // Add another image inline so you can see more clearly how this works.
        imagebox = flow.addBoxInline(9, 9, 0);
        imagebox.setImage(image);
        flow.addText(text1, style, Locale.getDefault());

        // Draw the LayoutBox on the page, then draw a red outline around the whole box
        int top = (int)page.getHeight()-50;
        page.drawLayoutBox(flow, 100, top);
        style.setLineColor(Color.red);
        style.setFillColor(null);
        page.setStyle(style);
        page.drawRectangle(100, top, page.getWidth()-100, top-flow.getHeight());

        OutputStream out = new FileOutputStream("LayoutExample.pdf");
        pdf.render(out);
        out.close();
    }
}
