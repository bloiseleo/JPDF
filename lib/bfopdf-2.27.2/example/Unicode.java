// $Id: Unicode.java 18790 2013-12-18 12:57:55Z mike $

import java.util.*;
import java.io.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * An example showing off the Unicode capabilities of the library. Loads
 * and displays a number of files in UTF-8 format.
 *
 * The program requires a font, which can be passed in on the Command line.
 * If it's not specified, it defaults to a standard place to find it under
 * Windows 2K/NT.
 */
public class Unicode {
    // Which font to use to display the bulk of the text. We've opted for the Times-Roman
    // that comes with Windows 98 and later, which has the extended latin, arabic, hebrew
    // and cyrillic glyphs needed for this demonstration.
    //
    private static String NORMALFONT = "C:\\Windows\\Fonts\\times.ttf";


    // The list of languages to use and their names. Note that Georgian (ISO code "ka") and
    // Thai (ISO code "th") are not included in this list, even though we have the translation, 
    // because we haven't got a font with the required glyphs
    //
    private static String[] langs = { "ar", "ca", "da", "de", "el", "en", "eo", "es", "fi", "fr", "ga", "he", "hu", "it", "ja", /*"ka", */ "ko", "nl", "no-bok", "no-nyn", "oc", "pt-br", "pt-pt", "ro", "ru", "sl", "sv", /*"th", */ "yi", "zh-cn", "zh-tw" };
    private static String[] names = { "Arabic", "Catalan", "Danish", "German", "Greek", "English", "Esperanto", "Spanish", "Finnish", "French", "Irish Gaelic", "Hebrew", "Hungarian", "Italian", "Japanese", /*"Georgian", */ "Korean", "Dutch", "Norwegian (Bokmal)", "Norwegian (Nynorsk)", "Occitan", "Portuguese (Brazil)", "Portuguese (Portugal)", "Romanian", "Russian", "Slovakian", "Swedish", /*"Thai", */ "Yiddish", "Chinese (Simplified)", "Chinese (Traditional)" };

    private static PDFFont opentypefont, builtinfont;

    public static void main(String[] args) throws IOException {
        if (args.length>0) NORMALFONT = args[0];
        init();

        PDF pdf = new PDF();
        addPage(pdf, "Intro", "resources/utf8/intro.txt", getFont("intro"), 16, Locale.ENGLISH);

        // Add the language pages
        for (int i=0;i<langs.length;i++) {
            String file = "resources/utf8/"+langs[i]+".txt";
            Locale locale;
            if (langs[i].indexOf('-')>=0) {
                locale = new Locale(langs[i].substring(0, langs[i].indexOf('-')), langs[i].substring(langs[i].indexOf('-')+1));
            } else {
                locale = new Locale(langs[i]);
            }
            addPage(pdf, names[i], file, getFont(langs[i]), 24, locale);
        }

        pdf.setOption("pagelayout", "OneColumn");
        pdf.setOption("pagemode", "UseOutlines");
        pdf.setInfo("Subject", "Demonstration of Unicode functionality\nin the Big Faceless PDF Library");

        // Create the file
        OutputStream out = new FileOutputStream("Unicode.pdf");
        pdf.render(out);
        out.close();
    }

    /**
     * This routine adds the specified file to the specified PDF, in the specified
     * font and size.
     */
    public static void addPage(PDF pdf, String title, String file, PDFFont font, float size, Locale l) throws IOException
    {
        // Create a new page, and add a bookmark to it
        PDFPage page = pdf.newPage(PDF.PAGESIZE_A4);
        pdf.getBookmarks().add(new PDFBookmark(title, PDFAction.goTo(page)));

        // Create the header text
        pdf.setLocale(Locale.ENGLISH);
        PDFStyle style = new PDFStyle();
        style.setFillColor(Color.black);
        style.setFont(new StandardFont(StandardFont.HELVETICAOBLIQUE), 18);
        page.setStyle(style);
        page.drawText(title, 50, page.getHeight()-50);

        // Add gratuitous hyperlink back to our website
        style.setFont(new StandardFont(StandardFont.HELVETICAOBLIQUE), 10);
        page.setStyle(style);
        page.beginText(50, 50, page.getWidth()-50, 70);
        page.drawText("Created with the ");
        page.beginTextLink(PDFAction.goToURL(new java.net.URL("http://bfo.com/products/pdf")), PDFStyle.LINKSTYLE);
        page.drawText("Big Faceless PDF Library");
        page.endTextLink();
        page.endText(false);

        // Create the body of the text. Be sure to set the working locale of the PDF
        // to the correct locale for the language. This changes various things, most of
        // them fairly subtle changes to do with default text-direction, line wrapping
        // styles etc.
        //
        // After we set the locale, we create a brand new style so it will pick up any
        // text-direction changes implied by the locale. If we reused the same style we
        // defined above, all the text would be left-aligned (or whatever the default is
        // for Locale.ENGLISH), even hebrew and arabic!
        pdf.setLocale(l);

        // Create a new style, in the specified font
        style = new PDFStyle();
        style.setFillColor(Color.black);
        style.setFont(font, size);
        page.setStyle(style);
        style.setFontFeature("requote", true);

        // Display the text on the page. First jump through the hoops Java sets us to
        // load files in UTF-8
        page.beginText(50, 50, page.getWidth()-50, page.getHeight()-100);
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String s;
        while ((s=r.readLine())!=null) {
            page.drawText(s + "\n");
        }
        r.close();
        page.endText(false);
    }


    //----------------------- UTILITY METHODS FROM HERE ON ----------------------------


    /**
     * Get the font to be used for the specified language
     */
    private static PDFFont getFont(String lang) {
        if (lang.equals("intro")) return builtinfont;

        // The following languages can use the built in fonts, as all the
        // characters they require are provided. Pretty much, anyway -
        // take a look at the Internationalization section of the userguide
        // for more info.
        String[] builtinfontok = { "en", "de", "fr", "es", "no", "sv", "da", "fi", "it", "pt", "ca", "eu", "nl", "sq", "et", "rm", "fo", "is", "ga", "gd", "af", "sw", "fy", "gl", "id", "in", "tl" };

        for (int i=0;i<builtinfontok.length;i++) {
            if (lang.startsWith(builtinfontok[i])) {
                return builtinfont;
            }
        }
        if (lang.startsWith("ja")) {
            return new StandardCJKFont(StandardCJKFont.HEISEIMIN, StandardCJKFont.REGULAR);
        } else if (lang.startsWith("ko")) {
            return new StandardCJKFont(StandardCJKFont.HYSMYEONGJO, StandardCJKFont.REGULAR);
        } else if (lang.equals("zh-tw") || lang.equals("zh-hk")) {
            return new StandardCJKFont(StandardCJKFont.MSUNG, StandardCJKFont.REGULAR);
        } else if (lang.equals("zh-cn") || lang.equals("zh-sg")) {
            return new StandardCJKFont(StandardCJKFont.STSONG, StandardCJKFont.REGULAR);
        } else {
            return opentypefont;
        }
    }

    /**
     * Initialize the fonts to be used
     */
    private static void init() throws IOException {
        // Load the Font to be used for most of the examples. If you get an error
        // here, you probably haven't changed the NORMALFONT String to point to
        // the TrueType font file you want to use (we don't supply one with this library).
        //
        InputStream in = new FileInputStream(NORMALFONT);
        opentypefont = new OpenTypeFont(in, 2);
        in.close();
        builtinfont = new StandardFont(StandardFont.TIMES);
    }
}
