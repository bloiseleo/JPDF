package br.com.lion.commandHandler;

import org.faceless.pdf2.*;
import br.com.lion.interpreter.CommandHandler;
import java.awt.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CreatePDF extends CommandHandler {

    private String pdfName = "example.pdf";

    public CreatePDF() {
        super("create_pdf");
    }

    public CreatePDF(String pdfName) {
        super("create_pdf");
        this.pdfName = pdfName;
        this.treatName();
    }
    private void removeExtensionIfExists() {
        if(this.pdfName.contains(".pdf")) {
            System.out.println("[+] You do not need to add the extension in your files. Just name them and we will handle this for you");
            this.pdfName = this.pdfName.replace(".pdf", "");
        }
    }
    private String getPathToPDF() {
        String separator = System.getProperty("file.separator");
        String directory = System.getProperty("user.dir");
        return directory
                .concat(separator).concat(this.pdfName);
    }
    private void treatName() {
        this.removeExtensionIfExists();
        this.pdfName = this.pdfName.replace(" ", "_");
        this.pdfName = this.pdfName.toLowerCase();
        this.pdfName = this.pdfName.concat(".pdf");
    }
    @Override
    public boolean exec() {
        PDF pdf = new PDF();
        PDFPage page = pdf.newPage("A4");
        PDFStyle myStyle = new PDFStyle();
        myStyle.setFont(new StandardFont(StandardFont.TIMES), 24);
        myStyle.setFillColor(Color.black);
        page.setStyle(myStyle);
        page.drawText("Hello World!", 100, page.getWidth()-100);
        System.out.println(this.getPathToPDF());
        try (OutputStream out = new FileOutputStream(this.getPathToPDF())){
            pdf.render(out);
            return true;
        } catch (IOException exception) {
            System.out.println("[-] Check if the name provided to be a file is, actually, a directory.\n[-] Check if the file can be opened by the user executing this software.\n[-] Check if the file can be created.");
            exception.printStackTrace();
            return false;
        }
    }
}
