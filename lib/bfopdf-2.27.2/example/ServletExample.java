// $Id: ServletExample.java 10479 2009-07-10 09:51:07Z chris $

import java.util.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * A Hello World Servlet. This is identical to HelloWorld.java
 * except for the method names and the last 3 lines.
 */
public class ServletExample extends HttpServlet
{
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException
    {
        doGet(req, res);
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException
    {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");

        // Create a new "style" to write in - Black 24pt Helvetica.
        PDFStyle mystyle = new PDFStyle();
        mystyle.setFont(new StandardFont(StandardFont.HELVETICA), 24);
        mystyle.setFillColor(Color.black);

        // Put something on the page.
        page.setStyle(mystyle);
        page.drawText("Hello, PDF-viewing World!", 100, page.getHeight()-100);

        // Add some meta-info and set a "fit page" action to run when opened.
        pdf.setInfo("Author", "Joe Bloggs");
        pdf.setInfo("Title", "My First Document");
        pdf.setAction(Event.OPEN, PDFAction.goToFit(page));

        // Write the PDF to the servlet output stream. Set the Content-Type
        // header, and also Content-Length - required by some version of
        // Internet Explorer to correctly display a PDF with the plugin.
        //
        // To get the Content-Length, first render to a byte array then write
        // it to the servlet response.
        //
        ByteArrayOutputStream tempout = new ByteArrayOutputStream();
        pdf.render(tempout);
        res.setContentType("application/pdf");
        res.setContentLength(tempout.size());
        tempout.writeTo(res.getOutputStream());
    }
}
