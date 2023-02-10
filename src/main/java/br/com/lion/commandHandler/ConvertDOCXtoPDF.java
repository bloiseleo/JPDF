package br.com.lion.commandHandler;

import br.com.lion.interpreter.CommandHandler;
import org.apache.poi.xwpf.converter.pdf.PdfConverter;
import org.apache.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;


import java.io.*;
import java.util.HashMap;

public class ConvertDOCXtoPDF extends CommandHandler {

    private String result;
    private String docxFile;

    public ConvertDOCXtoPDF() {
        super("convert_docx_to_pdf");
    }

    public ConvertDOCXtoPDF(HashMap<String, String> params) {
        super("convert_docx_to_pdf", params);
        this.docxFile = this.getParam("docxPath");
        this.result = this.getParam("result");
    }

    private String getPathToResultFile() {
        String separator = System.getProperty("file.separator");
        String directory = System.getProperty("user.dir");
        return directory.concat(separator).concat(this.result);
    }
    private String getPathToDOCX() {
        File docx = new File(this.docxFile);
        if(docx.isAbsolute()) {
            return docx.getPath();
        }
        String separator = System.getProperty("file.separator");
        String directory = System.getProperty("user.dir");
        return directory
                .concat(separator).concat(this.docxFile);
    }
    @Override
    public boolean exec() {
        File pdfFile = new File(this.getPathToDOCX());
        try (InputStream is = new FileInputStream(pdfFile)) {
            OutputStream out = new FileOutputStream(this.getPathToResultFile());
            XWPFDocument document = new XWPFDocument(is);
            PdfOptions pdfOptions = PdfOptions.create();
            PdfConverter.getInstance().convert(document, out, pdfOptions);
            return true;
        } catch (IOException exception) {
            System.out.println("[-] Something went wrong. See below:");
            exception.printStackTrace();
            return false;
        }

    }
}
