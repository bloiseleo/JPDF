// $Id: HelloUnicodeWorld.java 10479 2009-07-10 09:51:07Z chris $

import java.util.*;
import java.io.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * A more international Hello World example. Unicode text is
 * no more difficult than standard Latin text!
 *
 * A more complete example of internationalization is in
 * the "Unicode.java" example
 */
public class HelloUnicodeWorld
{
    public static void main(String[] args) throws IOException {
        if (args.length==0) {
            System.err.println("Usage: java HelloUnicodeWorld <truetype-font-file>\n\nThis program needs a TrueType font containing Arabic and Cyrllic\ncharacters, like \"times.ttf\" (the Times-Roman supplied with\nMicrosoft Windows 2000.\n\n");
            System.exit(1);
        }

        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");

        PDFStyle mystyle = new PDFStyle();
        OpenTypeFont otf = new OpenTypeFont(new FileInputStream(args[0]), 2);
        mystyle.setFont(otf, 24);
        mystyle.setFillColor(Color.black);

        // Write Hello World in:
        //
        // * English
        // * Russian
        // * Arabic
        // * Czech
        //
        // Notice that the arabic letters are all the "nominal" forms, from
        // the U+0600 table. From version 1.0.1 and above, these will be
        // automatically substituted with the appropriate ligature.
        //
        // Feel free to mail corrections, or different languages (greek, thai
        // or hebrew anyone?) to info@big.faceless.org
        //
        // We're trying to demonstrate is that you can mix and match
        // any unicode character in the same String, let alone the same
        // document - which is why this is all on one line and hard to read.
        //
        //
        page.setStyle(mystyle);
        page.drawText("Hello, World\n\u0417\u0434\u0430\u0432\u0441\u0442\u0432\u0443\u0439\u0442\u0435 \u043C\u0438\u0440\n\u010Cau sv\u011bte\n\u0633\u0644\u0627\u0645 \u0627\u0644\u0639\u0627\u0644\u0645\n", 100, page.getHeight()-100);

        OutputStream fo = new FileOutputStream("HelloUnicodeWorld.pdf");
        pdf.render(fo);
        fo.close();
    }
}
