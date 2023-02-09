// $Id: FormVoodoo.java 10479 2009-07-10 09:51:07Z chris $

import org.faceless.pdf2.*;
import java.io.*;
import java.awt.Color;

/**
 * This example shows some of the stranger and more interesting
 * things you can do with forms.
 */
public class FormVoodoo
{
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("Letter");

        Form form = pdf.getForm();
        PDFStyle background = new PDFStyle();
        background.setFillColor(new Color(240, 240, 255));
        background.setLineColor(Color.blue);
        background.setFormStyle(PDFStyle.FORMSTYLE_BEVEL);
        form.setBackgroundStyle(background);

        // First example - a popup help field.

        // Create the popup field and make it invisible
        FormText popup = new FormText(page, 170, 650, 350, 790);
        popup.setType(FormText.TYPE_MULTILINE);
        popup.setReadOnly(true);
        popup.setValue("This example shows some of the more unusual things you can do with forms.\n\nThis particular example shows how you could do a popup help box.\n\nThe example below shows using event handlers to do simple calculations on form fields.");
        PDFAnnotation popupannot = popup.getAnnotation(0);
        popupannot.setVisible(false);
        popupannot.setPrintable(false);
        form.addElement("Popup", popup);

        // Create a button which has MouseOver and MouseOut events
        // to show the popup help.
        FormButton help = new FormButton(page, 100, 700, 150, 720);
        help.getAnnotation(0).setValue("Help!");
        WidgetAnnotation helpannot = help.getAnnotation(0);
        helpannot.setAction(Event.MOUSEOVER, PDFAction.showWidget(popup.getAnnotation(0)));
        helpannot.setAction(Event.MOUSEOUT, PDFAction.hideWidget(popup.getAnnotation(0)));
        form.addElement("Help", help);


        // Second example - adding up totals

        // First create a JavaScript function in the document, which we
        // can call. You could do it inline, which is what most JavaScript
        // examples seem to do, but we feel this is much cleaner.
        String javascript = "function myTotal(fields)\n{\n  var tot=0;\n  for (var i=0;i<fields.length;i++) {\n    var f = this.getField(fields[i]);\n    if (f.value!=\"\") tot += parseFloat(f.value);\n  }\n  event.value=tot;\n}";
        pdf.setJavaScript(javascript);

        FormText field1 = new FormText(page, 100, 600, 150, 620);
        field1.setAction(Event.KEYPRESS, PDFAction.formJavaScript("AFNumber_Keystroke(2, 1, 1, 0, '', true);"));
        form.addElement("Field1", field1);

        FormText field2 = new FormText(page, 170, 600, 220, 620);
    field2.setAction(Event.KEYPRESS, PDFAction.formJavaScript("AFNumber_Keystroke(2, 1, 1, 0, '', true);"));
        form.addElement("Field2", field2);

        FormText field3 = new FormText(page, 250, 600, 300, 620);
        field3.setReadOnly(true);
    field3.setAction(Event.OTHERCHANGE, PDFAction.formJavaScript("myTotal(new Array(\"Field1\", \"Field2\"));"));
        form.addElement("Field3", field3);


        OutputStream out = new FileOutputStream("FormVoodoo.pdf");
        pdf.render(out);
        out.close();
    }
}
