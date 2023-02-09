// $Id: Concatenate.java 21903 2015-09-08 17:44:23Z mike $

import org.faceless.pdf2.*;
import java.io.*;
import java.util.*;

/**
 * One of the most common requests we get is "How do I join two
 * documents together". This example shows just that, in the simplest
 * correct way possible.
 *
 * The "getPages()" method here is only being used to add pages to the
 * end, but as it returns a java.util.List it's easy to manipulate the
 * list as necessary. The same applies to Bookmarks and Fields.
 */
public class Concatenate {

    public static void main(String[] args) throws IOException {
        boolean merge = false;
        boolean bmtree = false;
        PDF pdf = null;
        Map<String,FormElement> elements = null;

        // Merge two PDF's by joining:
        // * The Pages list
        // * The Bookmarks list
        // * The Form Fields
        // * Named actions and Embedded files (less commonly used)
        //
        // This doesn't merge JavaScript - if the PDF(s) contain JavaScript then
        // you will need to create a combined JavaScript by hand.
        for (int i=0;i<args.length;i++) {
            if (args[i].equals("--merge")) {
                merge = true;
            } else if (args[i].equals("--bmtree")) {
                // Bookmark Trees - nest the bookmarks for each section into it's own tree.
                bmtree = true;
            } else if (pdf == null) {
                pdf = new PDF(new PDFReader(new File(args[i])));
                OutputProfile profile = pdf.getBasicOutputProfile();
                if (profile.isSet(OutputProfile.Feature.XFAForm)) {
                    System.out.println("WARNING: Can't merge XFA Forms - removing XFA components");
                    pdf.getForm().removeXFA();
                }
                elements = pdf.getForm().getElements();
                if (merge) {
                    profile = new OutputProfile(OutputProfile.Default);
                    profile.setRequired(OutputProfile.Feature.MergeResources);
                    new OutputProfiler(pdf).apply(profile);
                }
                if (bmtree) {
                    PDFBookmark bm = new PDFBookmark(getBookmarkName(args[i]), PDFAction.goTo(pdf.getPage(0)));
                    bm.getBookmarks().addAll(pdf.getBookmarks());
                    pdf.getBookmarks().add(bm);
                }
            } else {
                PDF newpdf = new PDF(new PDFReader(new File(args[i])));
                if (bmtree) {
                    PDFBookmark bm = new PDFBookmark(getBookmarkName(args[i]), PDFAction.goTo(newpdf.getPage(0)));
                    bm.getBookmarks().addAll(newpdf.getBookmarks());
                    pdf.getBookmarks().add(bm);
                } else {
                    pdf.getBookmarks().addAll(newpdf.getBookmarks());
                }
                pdf.getPages().addAll(newpdf.getPages());
                pdf.getNamedActions().putAll(newpdf.getNamedActions());
                pdf.getEmbeddedFiles().putAll(newpdf.getEmbeddedFiles());

                Map<String,FormElement> newelements = newpdf.getForm().getElements();
                Set<String> duplicatenames = new HashSet<String>(elements.keySet());
                duplicatenames.retainAll(newelements.keySet());
                if (!duplicatenames.isEmpty()) {
                    //
                    // You can't merge two PDFs with duplicate field names. One option is
                    // to rename the field(s) - see the Form.renameElement() method, or the
                    // Form.renameAllElements() method. If you rename your fields, be careful
                    // if your PDF contains JavaScript that might reference those fields by
                    // name: if it does you'll need to update that by hand.
                    //
                    throw new IllegalStateException("Forms contain duplicate field names: "+duplicatenames);
                } else {
                    elements.putAll(newelements);
                }
            }
        }
        if (pdf == null) {
            System.out.println("Usage: java Concatenate [--merge] <file1> <file2> ... <filen>");
            System.out.println("       Creates file \"Concatenate.pdf\"");
            System.exit(0);
        }

        FileOutputStream out = new FileOutputStream("Concatenate.pdf");
        pdf.render(out);
        out.close();
    }

    // Convert a filename to a bookmark name when creating bookmark trees
    static String getBookmarkName(String name) {
        name = name.substring(name.lastIndexOf("/")+1);
        return name;
    }
}
