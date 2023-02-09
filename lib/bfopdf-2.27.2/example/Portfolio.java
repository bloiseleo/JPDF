import org.faceless.pdf2.*;
import java.io.*;
import java.util.*;

/**
 * Demonstrates creating a PDF Portfolio, for use with Acrobat 9 or later.
 */
public class Portfolio {

    public static void main(String[] args) throws IOException {
        PDF pdf = new PDF();
        pdf.getPortfolio().setSchema("{\"Size\":{\"type\":\"size\"}, \"Name:\":{}, \"Index\":{\"type\":\"number\"}, \"Now\":{\"type\":\"date\"}}");
        Map<String,EmbeddedFile> files = pdf.getEmbeddedFiles();

        String foldername = null;
        EmbeddedFile folder = null;
        for (int i=0;i<args.length;i++) {
            File f = new File(args[i]);
            System.out.println("Adding \""+f.getName()+"\"");
            EmbeddedFile ef = new EmbeddedFile(f);
            if (args[i].toLowerCase().endsWith(".pdf")) {
                ef.setType("application/pdf");
            }
            ef.getProperties().put("Index", i);
            ef.getProperties().put("Now", new Date());

            if (foldername != null) {
                // If you wanted to place the file in a subfolder in the PDF,
                // this is how you would do it. Acrobat 9 will ignore the folders,
                // but Acrobat X and later will honour them.
                if (folder == null) {
                    folder = EmbeddedFile.createFolder("folder");
                    pdf.getPortfolio().getRoot().getChildren().add(folder);
                }
                folder.getChildren().add(ef);
            } else {
                // Alternatively, just add the files to the EmbeddedFiles map.
                files.put(f.getName(), ef);
            }
        }

        // You have to have at least one page in a PDF, although it will only
        // be visible to PDF viewers that don't support portfolios. Blank is OK.
        PDFPage page = pdf.newPage("A4");

        // Next line is not really required but helpful if you're targetting Acrobat 8.
        pdf.setOption("pagemode", "UseAttachments");

        pdf.render(new FileOutputStream("Portfolio.pdf"));
    }

}
