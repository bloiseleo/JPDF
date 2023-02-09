// $Id: ExtractText.java 10479 2009-07-10 09:51:07Z chris $

import java.io.*;
import org.faceless.pdf2.*;

/**
 * This example shows how to extract text a PDF using the PageParser class. 
 * The text is written to stdout with UTF-8 encoding. We recommend redirecting
 * the output to a file and opening with a text editor, as some system consoles
 * aren't correctly set up for the display of Unicode text.
 *
 * Remember that with the trial version, the letter 'e' is replaced with the
 * letter 'a' in all extracted text! This is not the case with the licensed
 * version of the library.
 */
public class ExtractText
{
    public static void main(String[] args) throws IOException {
        String outfile;
        for (int i=0;i<args.length;i++) {
            String infile = args[i];
            OutputStreamWriter out = new OutputStreamWriter(System.out, "UTF-8");
            out.write("\uFEFF"); // Byte Order Mark, to tell text-editors this is UTF-8

            System.err.print("Reading \""+infile+"\" ");
            long time1 = System.currentTimeMillis();
            PDF pdf = new PDF(new PDFReader(new File(infile)));
            long time2 = System.currentTimeMillis();

            System.err.print(" "+(time2-time1)+"ms. Extracting ");
            extractText(pdf, out);

            long time3 = System.currentTimeMillis();
            System.err.println(" "+(time3-time2)+"ms.");
            out.flush();
        }
    }

    /**
     * Extract the text. Print a '.' for each page to indicate progress
     */
    private static void extractText(PDF pdf, Writer out) throws IOException {
        PDFParser parser = new PDFParser(pdf);
        for (int j=0;j<pdf.getNumberOfPages();j++) {
            System.err.print(".");
            PageExtractor extractor = parser.getPageExtractor(j);
            out.write(extractor.getTextAsStringBuffer().toString());
            out.write("\n\n\n");
        }
    }
}
