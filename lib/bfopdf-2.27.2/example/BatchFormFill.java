// $Id: BatchFormFill.java 10479 2009-07-10 09:51:07Z chris $

import org.faceless.pdf2.*;
import java.io.*;

/**
 * This demo is a mockup of a fairly typical case - a template is loaded
 * and multiple copies are created with different data. Typically this
 * would be run from a Servlet or similar.
 */
public class BatchFormFill
{
    /**
     * A simple "Person" object, which we will use to populate the template
     */
    private static class Person {
        public final String name, country, phone;

        public Person(String name, String country, String phone) {
            this.name = name;
            this.country = country;
            this.phone = phone;
        }
    }

    /**
     * The main program creates some people as test data, loads the
     * template and the loops round each person, creating a form
     * for them.
     */
    public static void main(String[] args) throws IOException {
        Person[] people = { new Person("Gordon Brown", "UK", "+44 20 78868199"), 
                            new Person("George Bush", "US", "+1 212 5551234"), 
                            new Person("Angela Merkel", "DE", "+49 30 890 81911"), 
                            new Person("Nikolas Sarkozy", "FR", "+33 01 40 20 52 06"),
                            new Person("Kostas Karamanlis", "GR", "+30 2860 92 448"),
                          };

        // For efficiency, load the template once and clone it as we need it
        FileInputStream in = new FileInputStream("resources/fw8eci.pdf");
        PDF template = new PDF(new PDFReader(in));
        in.close();

        for (int i=0;i<people.length;i++) {
            createPDF(i, people[i], template);
        }
    }

    /**
     * Create a form for the person from the supplied template,
     * and write it to a file called "BatchForm-i.pdf", where "i"
     * is the persons record number.
     *
     * First we clone the PDF, so we're not writing to the
     * original template. In this fairly simple example this is
     * not really necessary, as all we're doing it filling out
     * form fields. More complex example might add bookmarks,
     * add or remove pages and so on, in which case leaving an
     * unaltered original is a good idea.
     *
     * Flattening the form shaves about 28k off the document size
     * in this case, although it does mean the values are permanently
     * part of the PDF.
     */
    static final void createPDF(int i, Person person, PDF template) throws IOException {
        PDF pdf = new PDF(template);
        Form form = pdf.getForm();
        ((FormText)form.getElement("f1-1")).setValue(person.name);
        ((FormText)form.getElement("f1-2")).setValue(person.country);
        ((FormText)form.getElement("f1-3")).setValue(person.phone);

        form.flatten();

        System.out.println("Writing \"BatchForm-"+i+".pdf\"");
        FileOutputStream out = new FileOutputStream("BatchForm-"+i+".pdf");
        pdf.render(out);
        out.close();
    }
}
