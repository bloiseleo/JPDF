// $Id: FormCreation.java 24966 2017-03-06 16:03:50Z mike $

import org.faceless.pdf2.*;
import java.io.*;
import java.awt.Color;
import java.util.Arrays;

/**
 * The first in a series of three examples demonstrating how to
 * create, manipulate and parse PDF Forms or "AcroForms", which
 * are a feature of the Extended Edition of the library since
 * 1.1.23
 *
 * This example shows how to create a new form. This is filled
 * out in the next example (FormFill.java) and processed in the
 * last example (FormProcess.java)
 */
public class FormCreation
{
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");

        // Create the page labels - nothing to do with forms, 
        // but included to make the document more complete.
        PDFStyle label = new PDFStyle();
        label.setFillColor(Color.black);
        label.setFont(new StandardFont(StandardFont.HELVETICA), 11);
        page.setStyle(label);
        page.drawText("Name:", 100, 708);
        page.drawText("Year of birth:", 100, 668);
        page.drawText("Address:", 330, 708);
        page.drawText("Rating:", 100, 628);
        page.drawText("1", 155, 628);
        page.drawText("5", 255, 628);
        page.drawText("Yes, send me spam!:", 330, 628);

        // Now we begin the forms specific bits - we get the form from the
        // PDF, and set a default style for any new elements that we create.
        // The background style can have a fill color, line color, line
        // weighting, line dash pattern and form style set.

        Form form = pdf.getForm();
        PDFStyle background = new PDFStyle();
        background.setFillColor(new Color(240, 240, 255));
        background.setLineColor(Color.blue);
        background.setFormStyle(PDFStyle.FORMSTYLE_BEVEL);
        form.setBackgroundStyle(background);

        // Create the first field - a text field called "name"
        FormText name = new FormText(page, 170, 700, 300, 720);
        form.addElement("Name", name);

        // Create the second field - a multiline text box for the Address
        FormText address = new FormText(page, 400, 660, 550, 720);
        address.setType(FormText.TYPE_MULTILINE);
        form.addElement("Address", address);

        // Create the third field - a pull-down menu of months
        FormChoice month = new FormChoice(FormChoice.TYPE_DROPDOWN, page , 170, 660, 230, 680);
        month.getOptions().put("Jan", null);
        month.getOptions().put("Feb", null);
        month.getOptions().put("Mar", null);
        month.getOptions().put("Apr", null);
        month.getOptions().put("May", null);
        month.getOptions().put("Jun", null);
        month.getOptions().put("Jul", null);
        month.getOptions().put("Aug", null);
        month.getOptions().put("Sep", null);
        month.getOptions().put("Oct", null);
        month.getOptions().put("Nov", null);
        month.getOptions().put("Dec", null);
        form.addElement("Month", month);

        // Create the fourth field - a text field containing a year.
        // Add some JavaScript constraints which limit this field to
        // numeric values between 1900 and 2000
        FormText year = new FormText(page, 240, 660, 300, 680);
        year.setMaxLength(4);
        WidgetAnnotation annot = year.getAnnotation(0);
        year.setAction(Event.KEYPRESS, PDFAction.formJavaScript("AFNumber_Keystroke(0, 1, 1, 0, '', true);"));
        year.setAction(Event.CHANGE, PDFAction.formJavaScript("AFRange_Validate(true, 1900, true, 2000);"));
        form.addElement("Year", year);

        // Create a set of five radio buttons, the fifth field.
        FormRadioButton rating = new FormRadioButton();

        rating.addAnnotation("1",  page, 170, 627, 180, 637);
        rating.addAnnotation("2",  page, 185, 627, 195, 637);
        rating.addAnnotation("3",  page, 200, 627, 210, 637);
        rating.addAnnotation("4",  page, 215, 627, 225, 637);
        rating.addAnnotation("5",  page, 230, 627, 245, 637);
        form.addElement("Rating",  rating);

        // Create the sixth field, a single checkbox.
        FormCheckbox spam = new FormCheckbox(page, 450, 627, 460, 637);
        form.addElement("Spam", spam);

        // Create the seventh and eighth fields - two buttons to reset
        // and submit the form. The Submit option won't work unless the
        // document is being viewed in a web browser, but it demonstrates
        // how you'd go about it.
        FormButton reset = new FormButton(page, 200, 580, 300, 600);
        WidgetAnnotation widget = reset.getAnnotation(0);
        widget.setValue("Reset Form");
        widget.setAction(Event.MOUSEUP, PDFAction.formReset());
        form.addElement("Reset", reset);

        FormButton submit = new FormButton(page, 320, 580, 420, 600);
        widget = submit.getAnnotation(0);
        widget.setValue("Submit Form");
        widget.setAction(Event.MOUSEUP, PDFAction.formSubmit("http://localhost:2345", PDFAction.METHOD_HTTP_POST));
        form.addElement("Submit", submit);

        // Add a BarCode field. This will work in our viewer and in Acrobat
        // but is unlikely to in other PDF viewers.
        FormBarCode barcode = new FormBarCode();
        barcode.setSymbology("QRCode");
        barcode.setSymbolSize(0.5f);
        barcode.setECC(1);
        String js = FormBarCode.getTabDelimiteredJavaScript(Arrays.asList(new String[] { "Name", "Address", "Month", "Year", "Rating", "Spam" }), true);
        barcode.setAction(Event.OTHERCHANGE, PDFAction.formJavaScript(js));
        barcode.addAnnotation(page, 470, 550, 550, 630);
        js = pdf.getJavaScript();
        if (js == null) {
            js = "";
        }
        pdf.setJavaScript(js + FormBarCode.getTabDelimiteredJavaScriptSetup());
        form.addElement("BarCode", barcode);

        OutputStream out = new FileOutputStream("FormCreation.pdf");
        pdf.render(out);
        out.close();
    }
}
