// $Id: Annotations.java 10479 2009-07-10 09:51:07Z chris $

import java.util.*;
import java.io.*;
import java.awt.Color;
import java.net.URL;
import org.faceless.pdf2.*;

/**
 * A simple demo showing adding annotations (popup notes and
 * hyperlinks) to the PDF document.
 *
 * Also sets the access level on the document to allow printing
 * but nothing else.
 */
public class Annotations
{
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");

        // Load the resources we need for this demo
        URL url = new URL("http://big.faceless.org/products/pdf");
        PDFImage image = new PDFImage(new FileInputStream("resources/images/logo.png"));
        PDFSound sound = new PDFSound(new FileInputStream("resources/test.wav"));

        // Create a style
        PDFStyle mystyle = new PDFStyle();
        mystyle.setFont(new StandardFont(StandardFont.HELVETICA), 24);
        mystyle.setFillColor(Color.black);
        page.setStyle(mystyle);

        // Add a text hyperlink to a URL
        page.beginText(100, 100, page.getWidth()-100, page.getHeight()-200);
        page.drawText("This is a ");
        page.beginTextLink(PDFAction.goToURL(url), PDFStyle.LINKSTYLE);
        page.drawText("hyperlink");
        page.endTextLink();
        page.drawText(" which links to a website.");
        page.endText(false);

        // Add an image and make it a link to play a sound.
        PDFStyle linkstyle = new PDFStyle();
        linkstyle.setLineColor(Color.blue);
        linkstyle.setFillColor(Color.blue);
        page.drawImage(image, 100, 400, 300, 480);
        AnnotationLink link = new AnnotationLink();
        link.setAction(PDFAction.playSound(sound));
        link.setRectangle(100, 400, 300, 480);
        page.getAnnotations().add(link);

        // Add a text annotation popup to the page
        AnnotationNote note = new AnnotationNote();
        note.setContents("This is a popup note");
        note.setAuthor("A Popup");
        note.setType("Note", Color.blue);
        note.setRectangle(100, 100, 350, 150);
        page.getAnnotations().add(note);

        // Add a rubber stamp annotation
        AnnotationStamp topsecret = new AnnotationStamp("stamp.stencil.TopSecret", 50);
        topsecret.setRectangle(300, 600, 500, 650);
        page.getAnnotations().add(topsecret);

        // Finally, add a named action to print the page (in Acrobat only)
        page.drawTextLink("Click here to print the document", 100, 250, PDFAction.named("Print"));

        // Create the document
        pdf.setAction(Event.OPEN, PDFAction.goToFit(page));
        pdf.setInfo("Title", "Annotation Demonstration");

        OutputStream out = new FileOutputStream("Annotations.pdf");
        pdf.render(out);
        out.close();
    }
}
