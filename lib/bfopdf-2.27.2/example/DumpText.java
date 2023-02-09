// $Id: DumpText.java 19735 2014-07-22 12:07:13Z mike $

import java.io.*;
import java.util.*;
import java.text.*;
import org.faceless.pdf2.*;

/**
 * This example dumps the raw text contained in the document, including it's
 * position on the page. It's mainly useful to see exactly how a PDF is constructed
 * internally to help determine why text extraction is inaccurate
 *
 * Usage: java DumpText <filename.pdf>
 */
public class DumpText
{
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF(new PDFReader(new File(args[0])));
        PDFParser parser = new PDFParser(pdf);
        for (int i=0;i<pdf.getNumberOfPages();i++) {
            System.out.println("\n\nPage "+(i+1)+"/"+pdf.getNumberOfPages());
            PDFPage page = pdf.getPage(i);
            int numd = Math.max(page.getWidth(), page.getHeight()) > 999 ? 4 : 3; // To nicely format large pages
            PageExtractor extractor = parser.getPageExtractor(page);
//            extractor.setSpaceTolerance(-0.5f, 0.3f, 1.5f);   // Tinker with this to alter word spacing

            Collection<PageExtractor.Text> c = extractor.getTextUnordered();
            for (Iterator<PageExtractor.Text> j = c.iterator();j.hasNext();) {
                PageExtractor.Text text = j.next();

                String val = text.getText();
                float[] corners = text.getCorners();

                System.out.println(fmt(corners[0], numd)+","+fmt(corners[1], numd)+
                               " "+fmt(corners[2], numd)+","+fmt(corners[3], numd)+
                               " "+fmt(corners[4], numd)+","+fmt(corners[5], numd)+
                               " "+fmt(corners[6], numd)+","+fmt(corners[7], numd)+
                               "    \""+val+"\"");
            }
        }
    }

    private static final DecimalFormat f = new DecimalFormat("#0");

    private static final String fmt(float val, int numd) {
        String s = f.format(val);
        while (s.length()<numd) s = " "+s;
        return s;
    }
}
