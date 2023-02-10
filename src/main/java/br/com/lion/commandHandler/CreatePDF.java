package br.com.lion.commandHandler;

import br.com.lion.interpreter.CommandHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.util.HashMap;

public class CreatePDF extends CommandHandler {

    private String pdfName;

    public CreatePDF() {
        super("create_pdf");
    }

    public CreatePDF(HashMap<String, String> params) {
        super("create_pdf", params);
        this.pdfName = this.getParam("pdfName");
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
    public boolean exec()  {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        PDFont pdFont = PDType1Font.HELVETICA_BOLD;
        try{
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            // Define a text content stream using the selected font, moving the cursor and drawing the text "Hello World"
            contentStream.beginText();
            contentStream.setFont( pdFont, 12 );
            contentStream.moveTextPositionByAmount( 100, 700 );
            contentStream.drawString( "Hello World" );
            contentStream.endText();

            // Make sure that the content stream is closed:
            contentStream.close();
            document.save( this.getPathToPDF());
            document.close();
            return true;
        } catch (IOException exception) {
            System.out.println("[-] Check if the name provided to be a file is, actually, a directory.\n[-] Check if the file can be opened by the user executing this software.\n[-] Check if the file can be created.");
            exception.printStackTrace();
            return false;
        }
    }
}
