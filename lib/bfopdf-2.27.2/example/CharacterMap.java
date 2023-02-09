// $Id: CharacterMap.java 37185 2020-07-13 14:53:38Z mike $

import java.util.*;
import java.io.*;
import java.awt.Color;
import org.faceless.pdf2.*;

/**
 * A more complex example which creates a Unicode Character Map, showing every
 * character in the font. This class will include characters in the U+10000
 * range if run under Java 1.5 or later, otherwise only U+0000 to U+FFFF are
 * supported (Search for NoSuchMethodError to see the differences).
 */
public class CharacterMap
{
    private static PDFStyle fontstyle, titlestyle, subtitlestyle, numberstyle, nonspacingstyle;
    private static PDFStyle boxglyph, boxmissing, boxundefined;

    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFFont font = loadFont(args);
        initStyles(font);
        List<PDFBookmark> bookmarks = pdf.getBookmarks();

        // Add some pages for each block to the document. There may
        // not be any characters defined for a block, in which case
        // it's skipped.
        //
        // We rely on our own block list rather than that from Java
        // here, so we can display characters defined in later versions
        // of unicode.
        //
        for (int i=0;i<BLOCKS.length - 1;i++) {
            int blockstart = BLOCKS[i];
            int blockend = BLOCKS[i + 1];
            String blockname = null;
            PDFPage blockpage = null;
            for (int j=blockstart;j<blockend;j+=128) {
                int pagestart = j;
                int pageend = Math.min(blockend, pagestart + 128);
                for (int k=pagestart;k<pageend;k++) {
                    if (font.isDefined(k)) {
                        if (blockname == null) {
                            try {
                                Character.UnicodeBlock block = Character.UnicodeBlock.of(k);
                                if (block != null) {
                                    blockname = toTitleCase(block.toString());
                                }
                            } catch (Exception e) { }
                            if (blockname == null) {
                                blockname = "Unknown U+" + Integer.toHexString(blockstart);
                            }
                            System.out.print(blockname);
                        }
                        PDFPage page = createPage(pdf, pagestart, pageend, blockname, font);
                        System.out.print(".");
                        if (blockpage == null) {
                            blockpage = page;
                            bookmarks.add(new PDFBookmark(blockname, PDFAction.goTo(page)));
                        }
                        break;
                    }
                }
            }
            if (blockname != null) {
                System.out.println();
            }
        }

        // Set some meta-information.
        pdf.setInfo("Title", "Character map for \""+font.getBaseName()+"\"");
        pdf.setInfo("Keywords", font.getBaseName()+", Unicode, character map, font");
        pdf.setOption("pagemode", "UseOutlines");

        // Write the document to a file
        OutputStream out = new FileOutputStream("CharacterMap.pdf");
        OutputProfile profile = new OutputProfile(OutputProfile.Default);
        profile.setRequired(OutputProfile.Feature.Linearized);
        new OutputProfiler(pdf).apply(profile);
        pdf.render(out);
        out.close();
    }

    /**
     * Create a page covering the specified range of characters.
     * @param pdf the PDF
     * @param start the first Unicode codepoint in the page
     * @param end the last Unicode codepoint in the page
     * @param name the name of the block
     * @param font the Font to use
     * @return the page
     */
    private static PDFPage createPage(PDF pdf, int start, int end, String name, PDFFont font) {
        PDFPage page = pdf.newPage("A4");
        page.setStyle(titlestyle);
        page.drawText(font.getBaseName(), page.getWidth()/2, page.getHeight()-50);
        page.setStyle(subtitlestyle);
        page.drawText(name+" ("+toHex(start)+" - "+toHex(end)+")", page.getWidth()/2, page.getHeight()-75);

        // Set the page to measure in percent, for simple layout
        page.setUnits(PDFPage.UNITS_PERCENT, PDFPage.ORIGIN_PAGETOP);
        int numrows = 16;
        float boxheight = 80 / numrows;         // 80%
        float boxwidth  = 80 / (128/numrows);

        for (int i=0; i<end-start; i++) {
            int row = i % numrows;
            int col = i / numrows;

            float left = 10 + (col*boxwidth);   // The 10 is 10% from the left/top
            float top =  10 + (row*boxheight);  

            int c = i+start;
            String text = c < 0x10000 ? ""+(char)c:""+(char)((c>>10)+0xD7C0)+(char)((c&0x3FF)+0xDC00);
            boolean infont = font.isDefined(c);
            boolean valid = infont || Character.getType(c) != Character.UNASSIGNED;

            boolean nonspacingmark = false;
            if (infont && valid && fontstyle.getTextLength(text)==0) {
                text = "\u00A0"+text+"\u00A0";
                nonspacingmark = true;
            }

            // If the character is defined, draw the glyph and draw a glyph box
            // otherwise set to gray box and draw the rectangle.
            if (infont) {
                page.setStyle(fontstyle);
                page.drawText(text, left+(boxwidth*0.5f), top+(boxheight*0.6f));
                page.setStyle(boxglyph);
            } else if (valid) {
                page.setStyle(boxmissing);
            } else {
                page.setStyle(boxundefined);
            }
            page.drawRectangle(left, top, left+boxwidth, top+boxheight);
            if (valid) {
                page.setStyle(numberstyle);         // Add the Unicode point to the box
                page.drawText(toHex(c), left+(boxwidth*0.9f), top+(boxheight*0.9f));
            }

            // Draw the dotted-circle to indicate where the character would be in a
            // diacritic or combining mark. Not 100% accurate, especially for
            // right-to-left combining marks (eg. arabic vowel sounds), but good enough.
            // 
            if (nonspacingmark && nonspacingstyle!=null) {
                page.setStyle(nonspacingstyle);
                page.drawCircle(left+(boxwidth*0.45f), top+(boxheight*0.52f), boxwidth/20);
            }
        }
        return page;
    }

    /**
     * Initialise the styles
     */
    private static void initStyles(PDFFont font) {
        // "fontstyle" is used to actually render the glyphs.
        fontstyle = new PDFStyle();
        fontstyle.setFillColor(Color.black);
        fontstyle.setFont(font, 12);
        fontstyle.setTextAlign(PDFStyle.TEXTALIGN_CENTER);

        // "titlestyle"
        titlestyle = new PDFStyle();
        titlestyle.setFont(new StandardFont(StandardFont.TIMES), 20);
        titlestyle.setFillColor(Color.black);
        titlestyle.setTextAlign(PDFStyle.TEXTALIGN_CENTER);
 
        // "subtitlestyle"
        subtitlestyle = new PDFStyle(titlestyle);
        subtitlestyle.setFont(new StandardFont(StandardFont.HELVETICA), 15);

        // "numberstyle" is for the "U+xxxx" notes in each character box
        numberstyle = new PDFStyle();
        numberstyle.setFont(new StandardFont(StandardFont.HELVETICAOBLIQUE), 6);
        numberstyle.setFillColor(Color.black);
        numberstyle.setTextAlign(PDFStyle.TEXTALIGN_RIGHT);

        // "boxglyph" is to surround glyphs that actually exist
        boxglyph = new PDFStyle();
        boxglyph.setLineColor(Color.black);

        // "boxmissing" is to surround glyphs that are valid but not in the font
        boxmissing = (PDFStyle)boxglyph.clone();
        boxmissing.setFillColor(new Color(220, 220, 220));

        // "boxinvalid" is to surround glyphs that aren't defined in Unicode.
        boxundefined = (PDFStyle)boxglyph.clone();
        boxundefined.setFillColor(Color.black);

        // "nonspacingstyle" is to draw the circle that indicates a non-spacing mark
        // or diacritic.
        //
        // Set this to null to not draw a circle - down to your preference really.
        //
        nonspacingstyle = new PDFStyle();
        nonspacingstyle.setLineColor(new Color(200, 200, 200));
        nonspacingstyle.setLineWeighting(0.5f);
        nonspacingstyle.setLineDash(1, 1, 0);
    }

    /**
     * Load the font.
     */
    private static PDFFont loadFont(String[] args) throws IOException {
        PDFFont font = null;
        String ttfname = null, afmname = null, pfbname = null, stdname = null;

        if (args.length>0) {
            String fontname = args[0];
            if (new File(fontname).canRead()) {
                if (fontname.endsWith(".afm")) {
                    InputStream afm = new FileInputStream(fontname);
                    InputStream pfb = args.length==1 ? null : new FileInputStream(args[1]);
                    font = new Type1Font(afm, pfb);
                } else {
                    font = new OpenTypeFont(new FileInputStream(fontname), 2);
                }
            } else {
                for (int i=0;font==null && i<STDFONT.length;i++) {
                    if (STDFONT[i].equalsIgnoreCase(fontname)) {
                        font = new StandardFont(i);
                    }
                }
                for (int i=0;font==null && i<STDCJKFONT.length;i++) {
                    if (STDCJKFONT[i].equalsIgnoreCase(fontname)) {
                        font = new StandardCJKFont(i, 0);
                    }
                }
            }
        }
        if (font==null) {
            System.out.println("Usage: CharacterMap [Times-Roman | Times-Bold | Times-Italic | Times-BoldItalic");
            System.out.println("                  Helvetica | Helvetica-Bold | Helvetica-Oblique | Helvetica-BoldOblique |");
            System.out.println("                  Courier | Courier-Bold | Courier-Oblique | Courier-BoldOblique |");
            System.out.println("                  Symbol | ZapfDingBats | STSong | MSung | MHei | HeiSeiMin |");
            System.out.println("                  HeiSeiKakuGo | HYGothic | HYSMyeongJo | <fontfile> ] [<fontfile.pfb>]");
            System.exit(0);
        }

        return font;
    }

    private static final int[] BLOCKS = new int[] { 0x0, 0x80, 0x100, 0x180, 0x250, 0x2b0, 0x300, 0x370, 0x400, 0x500, 0x530, 0x590, 0x600, 0x700, 0x750, 0x780, 0x7c0, 0x800, 0x840, 0x860, 0x870, 0x8a0, 0x900, 0x980, 0xa00, 0xa80, 0xb00, 0xb80, 0xc00, 0xc80, 0xd00, 0xd80, 0xe00, 0xe80, 0xf00, 0x1000, 0x10a0, 0x1100, 0x1200, 0x1380, 0x13a0, 0x1400, 0x1680, 0x16a0, 0x1700, 0x1720, 0x1740, 0x1760, 0x1780, 0x1800, 0x18b0, 0x1900, 0x1950, 0x1980, 0x19e0, 0x1a00, 0x1a20, 0x1ab0, 0x1b00, 0x1b80, 0x1bc0, 0x1c00, 0x1c50, 0x1c80, 0x1c90, 0x1cc0, 0x1cd0, 0x1d00, 0x1d80, 0x1dc0, 0x1e00, 0x1f00, 0x2000, 0x2070, 0x20a0, 0x20d0, 0x2100, 0x2150, 0x2190, 0x2200, 0x2300, 0x2400, 0x2440, 0x2460, 0x2500, 0x2580, 0x25a0, 0x2600, 0x2700, 0x27c0, 0x27f0, 0x2800, 0x2900, 0x2980, 0x2a00, 0x2b00, 0x2c00, 0x2c60, 0x2c80, 0x2d00, 0x2d30, 0x2d80, 0x2de0, 0x2e00, 0x2e80, 0x2f00, 0x2fe0, 0x2ff0, 0x3000, 0x3040, 0x30a0, 0x3100, 0x3130, 0x3190, 0x31a0, 0x31c0, 0x31f0, 0x3200, 0x3300, 0x3400, 0x4dc0, 0x4e00, 0xa000, 0xa490, 0xa4d0, 0xa500, 0xa640, 0xa6a0, 0xa700, 0xa720, 0xa800, 0xa830, 0xa840, 0xa880, 0xa8e0, 0xa900, 0xa930, 0xa960, 0xa980, 0xa9e0, 0xaa00, 0xaa60, 0xaa80, 0xaae0, 0xab00, 0xab30, 0xab70, 0xabc0, 0xac00, 0xd7b0, 0xd800, 0xdb80, 0xdc00, 0xe000, 0xf900, 0xfb00, 0xfb50, 0xfe00, 0xfe10, 0xfe20, 0xfe30, 0xfe50, 0xfe70, 0xff00, 0xfff0, 0x10000, 0x10080, 0x10100, 0x10140, 0x10190, 0x101d0, 0x10200, 0x10280, 0x102a0, 0x102e0, 0x10300, 0x10330, 0x10350, 0x10380, 0x103a0, 0x103e0, 0x10400, 0x10450, 0x10480, 0x104b0, 0x10500, 0x10530, 0x10570, 0x10600, 0x10780, 0x10800, 0x10840, 0x10860, 0x10880, 0x108b0, 0x108e0, 0x10900, 0x10920, 0x10940, 0x10980, 0x109a0, 0x10a00, 0x10a60, 0x10a80, 0x10aa0, 0x10ac0, 0x10b00, 0x10b40, 0x10b60, 0x10b80, 0x10bb0, 0x10c00, 0x10c50, 0x10c80, 0x10d00, 0x10d40, 0x10e60, 0x10e80, 0x10ec0, 0x10f00, 0x10f30, 0x10f70, 0x10fb0, 0x10fe0, 0x11000, 0x11080, 0x110d0, 0x11100, 0x11150, 0x11180, 0x111e0, 0x11200, 0x11250, 0x11280, 0x112b0, 0x11300, 0x11380, 0x11400, 0x11480, 0x114e0, 0x11580, 0x11600, 0x11660, 0x11680, 0x116d0, 0x11700, 0x11740, 0x11800, 0x11850, 0x118a0, 0x11900, 0x11960, 0x119a0, 0x11a00, 0x11a50, 0x11ab0, 0x11ac0, 0x11b00, 0x11c00, 0x11c70, 0x11cc0, 0x11d00, 0x11d60, 0x11db0, 0x11ee0, 0x11f00, 0x11fb0, 0x11fc0, 0x12000, 0x12400, 0x12480, 0x12550, 0x13000, 0x13430, 0x13440, 0x14400, 0x14680, 0x16800, 0x16a40, 0x16a70, 0x16ad0, 0x16b00, 0x16b90, 0x16e40, 0x16ea0, 0x16f00, 0x16fa0, 0x16fe0, 0x17000, 0x18800, 0x18b00, 0x18d00, 0x18d90, 0x1b000, 0x1b100, 0x1b130, 0x1b170, 0x1b300, 0x1bc00, 0x1bca0, 0x1bcb0, 0x1d000, 0x1d100, 0x1d200, 0x1d250, 0x1d2e0, 0x1d300, 0x1d360, 0x1d380, 0x1d400, 0x1d800, 0x1dab0, 0x1e000, 0x1e030, 0x1e100, 0x1e150, 0x1e2c0, 0x1e300, 0x1e800, 0x1e8e0, 0x1e900, 0x1e960, 0x1ec70, 0x1ecc0, 0x1ed00, 0x1ed50, 0x1ee00, 0x1ef00, 0x1f000, 0x1f030, 0x1f0a0, 0x1f100, 0x1f200, 0x1f300, 0x1f600, 0x1f650, 0x1f680, 0x1f700, 0x1f780, 0x1f800, 0x1f900, 0x1fa00, 0x1fa70, 0x1fb00, 0x1fc00, 0x20000, 0x2a6e0, 0x2a700, 0x2b740, 0x2b820, 0x2ceb0, 0x2ebf0, 0x2f800, 0x2fa20, 0x30000, 0x31350, 0xe0000, 0xe0080, 0xe0100, 0xe01f0, 0xf0000, 0x100000 }; // From Unicode 13

    static final String[] STDFONT = { "Times-Roman", "Times-Bold", "Times-Italic", "Times-BoldItalic", "Helvetica", "Helvetica-Bold", "Helvetica-Oblique", "Helvetica-BoldOblique", "Courier", "Courier-Bold", "Courier-Oblique", "Courier-BoldOblique", "Symbol", "ZapfDingbats" };
    static final String[] STDCJKFONT = { "STSong", "MSung", "MHei", "HeiSeiMin", "HeiseiKakuGo", "HYGothic", "HYSMyeongJo" };

    /**
     * Given a character, eg 'A', format it as "U+0041"
     */
    private static String toHex(int c) {
        String s = Integer.toHexString(c).toUpperCase();
        while (s.length()<4) s="0"+s;
        return "U+"+s;
    }

    /**
     * Take the block name and make it nicer
     */
    private static String toTitleCase(String s) {
        StringBuffer sb = new StringBuffer(s.length());
        boolean toupper = true;
        int i=0;
        if (s.startsWith("CJK_") || s.startsWith("IPA_")) {
            sb.append(s.substring(0, 3));
            i=sb.length();
        }
        while (i<s.length()) {
            char c = s.charAt(i);
            if (c=='_') c=' ';
            sb.append(toupper ? Character.toUpperCase(c) : Character.toLowerCase(c));
            toupper = !Character.isUpperCase(c);
            i++;
        }
        return sb.toString();
    }
}
