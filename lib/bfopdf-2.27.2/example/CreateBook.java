// $Id: CreateBook.java 31517 2019-03-07 19:24:33Z mike $

import org.faceless.pdf2.*;
import java.util.Locale;
import java.awt.Color;
import java.util.*;
import java.io.*;

/**
 * CreateBook - creates a "book" from a text file.
 *
 * This example tests the ability of the library to deal with massive
 * amounts of text. Given a plain text file as the argument, it turns it
 * into a "book" - basically renders the text on successive pages of
 * two-column A4.
 *
 * If no argument is supplied, it uses the supplied text file - part of
 * "Great Expectations" by Charles Dickens (modified from the version
 * available from the excellent "Project Gutenburg" website).
 */
public class CreateBook
{
    private static PDFStyle numstyle;
    private static int pagenum = 1;
    private static PDF pdf;
    private static final String PAGESIZE = "A4-Landscape";
    private static final float WIDTH, HEIGHT;
    static {
        PDFPage page = new PDFPage(PAGESIZE);
        WIDTH  = (page.getWidth()/2) - 100;
        HEIGHT = page.getHeight()    - 100;
    }


    public static void main(String args[]) throws IOException {
        String filename = args.length > 0 ? args[0] : "resources/GreatExpectations.txt";

        // Create a new PDF. For variety we've created this PDF with a "Compressed XRef Table",
        // which will make it marginally smaller but requires Acrobat 6 or later to open,
        // and created it as a Linearized PDF, which can make it faster to open the file when
        // it's served over HTTP. 
        // 
        OutputProfile profile = new OutputProfile(OutputProfile.Default);
        profile.setRequired(OutputProfile.Feature.CompressedXRef);
        profile.setRequired(OutputProfile.Feature.Linearized);
        pdf = new PDF(profile);

        // Set the locale of the PDF to english, which affects
        // the style of double-quote (") substitution in the requote() method.
        pdf.setLocale(Locale.ENGLISH);

        // Create a new style to render the text in. We use 11pt Times-Roman
        PDFStyle textstyle = new PDFStyle();
        PDFFont font = new StandardFont(StandardFont.TIMES);
        font.setFeature("requote", true); // For nicer looking quote characters
        textstyle.setFont(font, 11);
        textstyle.setFillColor(Color.black);
        textstyle.setTextAlign(PDFStyle.TEXTALIGN_JUSTIFY);

        // Create a new style for the page numbers
        numstyle = new PDFStyle();
        numstyle.setFont(new StandardFont(StandardFont.TIMES), 8);
        numstyle.setFillColor(Color.black);
        numstyle.setTextAlign(PDFStyle.TEXTALIGN_CENTER);

        LayoutBox chapter = new LayoutBox(WIDTH);
        int chapternumber = 0;
        BufferedReader in = new BufferedReader(new FileReader(filename));
        String line;

        long starttime = System.currentTimeMillis();
        System.out.println(new Date()+": Starting file");

        // Read the lines of the file, adding each line to a layout box
        // when we reach the end of a chapter split the layout box into pages
        // and draw it.
        //
        // An assumption has been made that each chapter of the book will start
        // with the word "Chapter".
        //
        while ((line=in.readLine())!=null) {
            line = line.trim();
            if (line.length() == 0) {
                line = "\n\n";
            } else {
                line += " ";
            }

            // When a chapter has been read the LayoutBox is sent off to be drawn
            if (line.startsWith("Chapter ")) {
                if (chapternumber > 0) {
                    System.out.println(new Date()+": Writing Chapter "+chapternumber);
                    writeChapter(chapter, chapternumber);
                }
                chapternumber++;
                chapter = new LayoutBox(WIDTH);
            }
            chapter.addText(line, textstyle, pdf.getLocale());
        }

        // Write the last chapter
        System.out.println(new Date()+": Writing Chapter "+chapternumber);
        writeChapter(chapter, chapternumber);

        System.out.println(new Date()+": Compressing and writing to file");
        OutputStream out = new FileOutputStream("CreateBook.pdf");
        pdf.render(out);
        out.close();
        System.out.println("Total time was "+(System.currentTimeMillis()-starttime)+"ms");
    }

    /**
     * Write the chapter, printing each page side by side. The layout box will
     * be broken up into page height chunks. Chapters always start on a new odd
     * numbered page.
     */
    private static void writeChapter(LayoutBox chapter, int chapternumber) {
        PDFPage page=null;
        boolean firstpage = true;
        float left;

        // Flush the layout box, this needs to be done before measuring the height.
        chapter.flush();

        while (chapter!=null) {
            // Split the layoutbox
            LayoutBox next=null;
            if (chapter.getHeight() > HEIGHT) {
                next = chapter.splitAt(HEIGHT);
            }

            if (pagenum%2 == 1) {
                page = pdf.newPage(PAGESIZE);
                left = 50;

                // Draw the page number
                page.setStyle(numstyle);
                page.drawText("Page "+ pagenum, page.getWidth()/4, 30);
                page.drawText("Page "+ (pagenum+1), 3*page.getWidth()/4, 30);
            } else {
                 left = (page.getWidth()/2)+50;
            }

            page.drawLayoutBox(chapter, left, page.getHeight()-50);
            chapter = next;
            pagenum++;

            // If its the first page of a new chapter add the bookmark
            if (firstpage) {
                pdf.getBookmarks().add(new PDFBookmark("Chapter "+chapternumber, PDFAction.goTo(page)));
                firstpage = false;
            }
        }

        // Make sure next chapter starts on an odd page
        pagenum |= 1;
    }
}
