// $Id: FormFill.java 10479 2009-07-10 09:51:07Z chris $

import org.faceless.pdf2.*;
import java.io.*;
import java.awt.Color;

/**
 * The second stage in the "Form" series of examples - take a pre-existing
 * form (created by "FormCreation.java") and fill the fields in. This is
 * extremely easy, as you can see below. The resulting form is written to
 * "FormFill.pdf", which is then processed by "FormProcess.java".
 *
 * To make this example a little more interesting, we've also changed the
 * style of one of the existing buttons.
 */
public class FormFill
{
    /**
     * Load the existing PDF, extract the form then set the values of the fields.
     *
     * Then set the style of the "Submit" button. You wouldn't normally do this
     * if you were filling out a form obviously, but the capability is there to
     * completely restyle the form.
     */
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF(new PDFReader(new FileInputStream("FormCreation.pdf")));
        Form form = pdf.getForm();

        // Set the values of the form
        ((FormText)form.getElement("Name")).setValue("John Tester");
        ((FormText)form.getElement("Address")).setValue("12 Test St\nTestville\nTestLand");
        ((FormText)form.getElement("Year")).setValue("1960");
        ((FormChoice)form.getElement("Month")).setValue("May");
        ((FormRadioButton)form.getElement("Rating")).setValue("2");
        ((FormCheckbox)form.getElement("Spam")).setValue("Yes");

        PDFStyle bg = new PDFStyle();
        PDFStyle fg = new PDFStyle();
        bg.setFillColor(Color.yellow);
        bg.setLineColor(Color.red);
        bg.setFormStyle(PDFStyle.FORMSTYLE_SOLID);
        fg.setFont(new StandardFont(StandardFont.TIMES), 12);
        fg.setFillColor(Color.black);
        WidgetAnnotation widget =  form.getElement("Submit").getAnnotation(0);
        widget.setTextStyle(fg);
        widget.setBackgroundStyle(bg);

        OutputStream out = new FileOutputStream("FormFill.pdf");
        pdf.render(out);
        out.close();
    }
}
