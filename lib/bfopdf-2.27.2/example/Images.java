// $Id: Images.java 10479 2009-07-10 09:51:07Z chris $

import java.util.*;
import java.io.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * The Images example demonstrates inserting images into a document.
 * We insert a GIF image, two JPEG's and a PNG onto the page.
 */
public class Images
{
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4-landscape");

        // To make layout easier, store the width and height of the page in
        // a couple of variables, and have the whole page and measured in
        // points from the top-left of the page.
        //
        float width = page.getWidth();
        float height = page.getHeight();
        page.setUnits(PDFPage.UNITS_POINTS, PDFPage.ORIGIN_PAGETOP);

        // Draw a light blue rectangle filling most of the page.
        PDFStyle background = new PDFStyle();
        background.setFillColor(new Color(208, 231, 240));
        page.setStyle(background);
        page.drawRectangle(100, 100, width-100, height-100);


        // Load the images.
        //
        // We're loading the images from files here, but there is no
        // reason whey they couldn't be loaded from a ByteArrayInputStream
        // or any other stream.
        PDFImage map = new PDFImage(new FileInputStream("resources/images/africa.gif"));
        PDFImage sand = new PDFImage(new FileInputStream("resources/images/sanddune.jpg"));
        PDFImage canoe = new PDFImage(new FileInputStream("resources/images/canoe.jpg"));
        PDFImage logo = new PDFImage(new FileInputStream("resources/images/logo.png"));


        // Draw the images.
        //
        // Although we are specifying the upper-left and lower-right hand
        // corners for each images' rectangle, any two opposite corners
        // would do.
        //
        // We're blatently ignoring the image aspect ratios.

        // Draw the first image (the map, a GIF image) with the top right
        // corner 100 pixels in from the top-right hand corner of the page
        page.drawImage(map, width-430, 100, width-100, height-100);

        // Draw the next two images, both JPEGs. Put them above eachother
        // 120 pixels in from the left of the page (just inside the blue
        // rectangle).
        page.drawImage(canoe, 120, 120, 350, 260);
        page.drawImage(sand, 120, 315, 350, 455);

        // Draw the last image - our logo, a PNG image - near the
        // bottom of the page. Although this appears as the smallest
        // image on the page, it's actually the highest resolution - you
        // will notice the difference when you zoom in on the images.
        page.drawImage(logo, width-200, height-70, width-100, height-30);

        // Draw a black box around the blue rectangle. Do this after
        // the images. If we did it before we drew the imagse,  they
        // would overwrite part of the line.
        PDFStyle borderStyle = new PDFStyle();
        borderStyle.setLineColor(Color.black);
        page.setStyle(borderStyle);
        page.drawRectangle(100, 100, width-100, height-100);



        // Add some text to complete the document.
        //
        // We're not doing anything clever with fonts in this
        // example, just using the built in Times-Roman and
        // Helvetica-Oblique

        // Create three new styles to write in.
        //
        // 1. "footer" - 8 point Helvetica Oblique, Right aligned
        // 2. "header" - 24 point Times Roman
        // 3. "caption" - 12 point Times Roman
        //
        PDFStyle footer = new PDFStyle();
        footer.setFont(new StandardFont(StandardFont.HELVETICAOBLIQUE), 8);
        footer.setFillColor(Color.black);
        footer.setTextAlign(PDFStyle.TEXTALIGN_RIGHT);

        PDFStyle caption = new PDFStyle();
        caption.setFont(new StandardFont(StandardFont.TIMES), 12);
        caption.setFillColor(Color.black);

        PDFStyle heading = new PDFStyle();
        heading.setFont(new StandardFont(StandardFont.TIMES), 24);
        heading.setFillColor(Color.black);

        // Now we can actually draw the text
        //
        page.setStyle(heading);
        page.drawText("Some Photos from Africa", 100, 70);

        page.setStyle(caption);
        page.drawText("An example showing two JPEG images, a GIF and a PNG image", 100, 90);
        page.drawText("A fishing boat on the beach - Western Ghana", 120, 280);
        page.drawText("The largest sand dunes in the world - Namibia", 120, 475);

        page.setStyle(footer);
        page.drawText("Document created using the Big Faceless PDF Library - http://bfo.co.uk/products/pdf", width-240, height-45);


        //----------------------------------------------------------------
        //
        // That's it! We've created the contents of the document. Now
        // to finish off we'll add some document meta-information and
        // set the page to resize when the document is opened.
        //
        //----------------------------------------------------------------


        // Add some document info.
        //
        pdf.setInfo("Author", "Big Faceless Organization, Inc.");
        pdf.setInfo("Title", "Some Photos from Africa");
        pdf.setAction(Event.OPEN, PDFAction.goToFit(page));

        // Write the document to a file
        //
        OutputStream fo = new FileOutputStream("Images.pdf");
        pdf.render(fo);
        fo.close();
    }
}
