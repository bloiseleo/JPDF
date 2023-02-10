package br.com.lion.commandHandler;

import br.com.lion.interpreter.CommandHandler;
import org.apache.poi.xwpf.converter.pdf.PdfConverter;
import org.apache.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;


import java.io.*;
import java.util.HashMap;

public class ConvertDOCXtoPDF extends CommandHandler {

    private String result;
    private String pdfName;

    public ConvertDOCXtoPDF() {
        super("convert_docx_to_pdf");
    }

    public ConvertDOCXtoPDF(HashMap<String, String> params) {
        super("convert_docx_to_pdf", params);
        this.pdfName = this.getParam("pdfName");
        this.result = this.getParam("result");
        this.treatName();
    }

    private String getPathToResultFile() {
        String separator = System.getProperty("file.separator");
        String directory = System.getProperty("user.dir");
        return directory.concat(separator).concat(this.result);
    }

    private void treatName() {
        this.removeExtensionIfExists();
        this.pdfName = this.pdfName.replace(" ", "_");
        this.pdfName = this.pdfName.toLowerCase();
        this.pdfName = this.pdfName.concat(".pdf");
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
    @Override
    public boolean exec() {
        File pdfFile = new File(this.getPathToPDF());
        try (InputStream is = new FileInputStream(pdfFile)) {
            OutputStream out = new FileOutputStream(this.getPathToResultFile());
            XWPFDocument document = new XWPFDocument(is);
            PdfOptions pdfOptions = PdfOptions.create();
            PdfConverter.getInstance().convert(document, out, pdfOptions);
            return false;
        } catch (IOException exception) {
            System.out.println("[-] Something went wrong. See below:");
            exception.printStackTrace();
            return false;
        }

    }
}
