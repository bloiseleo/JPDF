// $Id: PathExample.java 10479 2009-07-10 09:51:07Z chris $

import java.util.*;
import java.io.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * An example showing some of the graphics operations, like save
 * and restore, bezier curves, line dashing and so on.
 */
public class PathExample
{
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");

        // Create a new style to demonstrate some unusual features
        // of PDFStyles. Draw the lines 2pt wide in yellow, and make
        // them dashed (draw five points, skip four points). Also,
        // make the ends of each dash rounded rather than square.
        PDFStyle linestyle = new PDFStyle();
        linestyle.setLineColor(Color.yellow);
        linestyle.setLineWeighting(2);
        linestyle.setLineDash(5, 4, 0);
        linestyle.setLineCap(PDFStyle.LINECAP_ROUND);

        // Create a style "bluefill", which has yellow 2pt wide
        // border and a blue fill.
        PDFStyle bluefill = new PDFStyle();
        bluefill.setFillColor(Color.blue);
        bluefill.setLineColor(Color.yellow);
        bluefill.setLineWeighting(2);

        // Create a style "yellowfill" which just fills the path
        // with yellow
        PDFStyle yellowfill = new PDFStyle();
        yellowfill.setFillColor(Color.yellow);

        // Next, draw a circle with several bezier curves inside, all
        // rotated around the center.

        // Save the state first so we can get back to it easily.
        page.save();
        int left = 300;
        int top = 500;

        // Draw the outer circle, fill it with blue
        page.setStyle(bluefill);
        page.drawCircle(left, top, 160);

        // Draw the "spokes", each one rotated by an extra 10 degrees.
        // We save and restore the state between each stroke
        page.setStyle(linestyle);
        for (int i=0;i<360;i+=10) {
            page.save();
            page.rotate(left, top, i);
            page.pathMove(left, top+10);
            page.pathBezier(left-100, top+60, left+100, top+110, left, top+159);
            page.pathPaint();
            page.restore();
        }

        // Draw a circle on the inside
        page.setStyle(yellowfill);
        page.drawCircle(left, top, 10);

        // Go back to our original co-ordinate system.
        page.restore();

        // Done. Write the document to a file
        OutputStream fo = new FileOutputStream("PathExample.pdf");
        pdf.render(fo);
        fo.close();
    }
}
