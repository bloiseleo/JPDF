// $Id: StructuredPDF.java 25211 2017-04-04 10:47:41Z mike $

import java.util.*;
import java.io.*;
import java.awt.color.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * A worked example showing how to use the beginTag/endTag methods
 * to add structure to a PDF.
 *
 * This example was revised in 2.20 and the resulting file is now
 * both PDF/A-3a and PDF/UA compliant.
 */
public class StructuredPDF {

    public static void main(String[] args) throws IOException {

        // Here's how we create a PDF that meets the requirments of both
        // PDF/A *and* PDF/UA - use the OutputProfile.merge() method.
        ICC_Profile icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        OutputProfile profile = new OutputProfile(OutputProfile.PDFA3a, "sRGB", null, "http://www.color.org", null, icc);
        profile.merge(OutputProfile.PDFUA1);
        PDF pdf = new PDF(profile);

        pdf.setInfo("Title", "An Accessible PDF");              // Title must be set
        pdf.setLocale(Locale.ENGLISH);                          // Locale too - if not we will set a default

        PDFPage page = pdf.newPage("Letter");
        PDFCanvas canvas = new PDFCanvas(612, 792);
        int margin = 36;

        // PDF/A and PDF/UA both require embedded fonts.
        PDFStyle style = new PDFStyle();
        OpenTypeFont font = new OpenTypeFont(new FileInputStream("resources/fonts/bluehigh.ttf"), 2);
        style.setFont(font, 24);
        style.setFillColor(Color.black);

        PDFStyle linkstyle = new PDFStyle(style);
        linkstyle.setFillColor(Color.blue);
        linkstyle.setTextUnderline(true);

        Locale spainlocale = new Locale("es", "ES");
        LayoutBox box = new LayoutBox(page.getWidth() - margin - margin);
        Map<String,Object> atts = new HashMap<String,Object>();

        //--------------------------------------------------------------
        // Here we create the tagged content. The document we're trying to
        // create will have the following psuedo-structure
        //
        // <article>
        //  <p id="para1">
        //   Hello
        //   <span id="span1" class="username">Mr Smith</span>
        //  </p>
        //  <p id="para2" xml:lang="es_ES">
        //   Hola
        //   <span id="span2" class="username">
        //    <a href="http://bfo.com" alt="Página principal de BFO">Señor Smith</a>
        //   </span>
        //  </p>
        // </article>
        //
        // On the left we have the commands for         Here on the right we have
        // the PDF API                                  the analagous HTML-style
        //                                              tags.
        //
        // Of course the HTML equivalents aren't exact - PDF structure tags are
        // not a direct match, but they should help to explain the structure
        //
        page.beginTag("Art", null);                     // <article>

        atts.clear();
        atts.put("id", "para1");
        box.beginTag("P", atts);                        // <p id="para1">

        box.addText("Hello, ", style, null);            // hello

        atts.clear();
        atts.put("id", "span1");
        atts.put("class", "username");
        box.beginTag("Span", atts);                     // <span id="span1" class="username">

        box.addText("Mr Smith", style, null);           // Mr Smith

        box.addLineBreak(style);                        // <br/>
        box.endTag();                                   // </span>
        box.endTag();                                   // </p>

        atts.clear();
        atts.put("id", "para2");
        atts.put("lang", spainlocale);
        box.beginTag("P", atts);                        // <p id="para2" lang="en-ES">

        box.addText("Hola, ", style, null);             // Hola

        atts.clear();
        atts.put("id", "span2");
        atts.put("class", "username");
        box.beginTag("Span", atts);                     // <span id="span2" class="username">

        AnnotationLink link = new AnnotationLink();
        link.setContents("P\u00e1gina principal de BFO");    // Description is required
        link.setAction(PDFAction.goToURL("http://bfo.com"));
        atts.clear();
        atts.put("annotation", link);
        box.beginTag("Link", atts);                     // <a href="http://bfo.com" alt="Página principal de BFO">

        LayoutBox.Text text = box.addText("Se\u00f1or Smith", linkstyle, null);  // Señor Smith

        box.endTag();                                   // </a>
        box.endTag();                                   // </span>
        box.endTag();                                   // </p>
        box.flush();

        page.drawLayoutBox(box, margin, page.getHeight() - margin);

        // After we've drawn the box on the page, set the link annotation position
        // to match our hyperlink
        link.setCorners(text.getCorners(margin, page.getHeight() - margin));
        link.setPage(page);

        // Add an "artifact" - in this case, a page number.
        //
        // Artifacts are parts of the document  that have no meaningful content -
        // this includes incidental text like page numbers, but also graphics: for
        // example, the box drawn around a table.
        //
        // PDF/UA requires that *everything* in a PDF is either actual content marked
        // with tags, or an Artifact. To help you along, we will automatically tag
        // anything not inside a beginTag/endTag as an artifact; but when you want
        // to add an Artifact to the page and you're already inside an existing
        // beginTag/endTag pair, you need to explicitly mark the Artifact, as below.
        //
        // See http://www.w3.org/TR/2014/NOTE-WCAG20-TECHS-20140408/pdf.html#PDF4
        //
        page.beginTag("Artifact", null);                // <artifact>
        box = new LayoutBox(100);
        style.setFont(font, 12);
        box.addText("Page 1", style, null);
        style.setFont(font, 24);
        page.drawLayoutBox(box, page.getWidth() / 2, page.getHeight() - 20);
        page.endTag();                                  // </artifact>

        page.endTag();                                  // </article>

        OutputStream fo = new FileOutputStream("StructuredPDF.pdf");
        pdf.render(fo);
        fo.close();
    }

}
