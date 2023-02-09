// $Id: FormProcess.java 19735 2014-07-22 12:07:13Z mike $

import org.faceless.pdf2.*;
import java.io.*;
import java.util.*;

/**
 * The third and final stage in the "Form" series of examples - take a form
 * which has been filled out and extract the results. A more sophisticated
 * example would cycle through the values returned from form.getValues()
 * and print them out depending on type.
 */
public class FormProcess
{
    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF(new PDFReader(new File(args[0])));
        Form form = pdf.getForm();

        // Get the values from the form. Although the method is always
        // called "getValue", different elements return different objects
        // so we need to cast each one separately. We replace the '\n' with
        // '\t' in the FormText one too, to make printing of multiline text
        // objects easier.
        //
        System.out.println("PROCESSING FORM:");
        Map<String,FormElement> m = form.getElements();
        for (Iterator<Map.Entry<String,FormElement>> i = m.entrySet().iterator();i.hasNext();) {
            Map.Entry<String,FormElement> e = i.next();
            FormElement elt = e.getValue();
            String val = null;
            if (elt instanceof FormText) {
                val = ((FormText)elt).getValue();
            } else if (elt instanceof FormChoice) {
                val = ((FormChoice)elt).getValue();
            } else if (elt instanceof FormRadioButton) {
                val = ((FormRadioButton)elt).getValue();
            } else if (elt instanceof FormCheckbox) {
                val = ((FormCheckbox)elt).getValue();
            }
            if (val != null) {
                String key = (String)e.getKey();
                val = val.replace('\n', '\t');          // Make printing Address easier
                System.out.println(key+"=\""+val+"\"");
            }
        }
    }
}
