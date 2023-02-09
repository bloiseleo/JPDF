import java.io.*;
import java.util.*;
import org.faceless.pdf2.*;

/**
 * An example demonstrating column layout with a LayoutBox.
 * The idea is to make a LayoutBox the width of the column,
 * then split into multiple chunks and lay them out next
 * to eachother.
 */
public class ColumnLayout {

    static String TEXT = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. ";

    static final int GUTTER = 36;      // Margin around text and between columns
    static final int NUMCOLUMNS = 3;   // How many columns to create

    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");

        PDFStyle style = new PDFStyle();
        style.setTextAlign(PDFStyle.TEXTALIGN_JUSTIFY);
        style.setFont(new StandardFont(StandardFont.HELVETICA), 14);

        int colwidth = (page.getWidth() - GUTTER*(NUMCOLUMNS+1)) / NUMCOLUMNS;
        int colheight = page.getHeight() - GUTTER*2;
        LayoutBox box = new LayoutBox(colwidth);
        box.addText(TEXT, style, null);
        box.addText(TEXT, style, null);
        box.addText(TEXT, style, null);
        box.addText(TEXT, style, null);
        box.flush();

        int x = GUTTER;
        do {
            LayoutBox box2 = box.splitAt(colheight);
            page.drawLayoutBox(box, x, colheight + GUTTER);
            box = box2;
            x += colwidth + GUTTER;
        } while (box.getHeight() > 0);

        FileOutputStream out = new FileOutputStream("ColumnLayout.pdf");
        pdf.render(out);
        out.close();
    }
}
