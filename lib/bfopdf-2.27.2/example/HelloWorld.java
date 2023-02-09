// $Id: HelloWorld.java 10479 2009-07-10 09:51:07Z chris $

import java.util.*;
import java.io.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * The obligatory Hello World example.
 */
public class HelloWorld
{
    public static void main(String[] args) {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");

        // Create a new "style" to write in - Black 24pt Helvetica.
        PDFStyle mystyle = new PDFStyle();
        PDFFont font = new StandardFont(StandardFont.HELVETICA);
        mystyle.setFont(font, 24);
        mystyle.setFillColor(Color.black);

        // Put something on the page.
        page.setStyle(mystyle);
        page.drawText("Hello, PDF-viewing World!", 100, page.getHeight()-100);

        // Add some meta-info and set a "fit page" action to run when opened.
        pdf.setInfo("Author", "Joe Bloggs");
        pdf.setInfo("Title", "My First Document");
        pdf.setAction(Event.OPEN, PDFAction.goToFit(page));

        // Write the document to a file
        try {
            OutputStream fo = new FileOutputStream("HelloWorld.pdf");
            pdf.render(fo);
            fo.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
