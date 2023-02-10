package br.com.lion.commandHandler;

import br.com.lion.interpreter.CommandHandler;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MergePDF extends CommandHandler {

    private String pdfName = null;
    private List<String> pdfsToMerge = null;
    public MergePDF() {
        super("merge_pdf");
    }
    public MergePDF(HashMap<String, String> params) {
        super("merge_pdf", params);
        this.pdfsToMerge = this.getAllParams("result");
        this.pdfName = this.getParam("result");
    }

    private String getPathToPDF() {
        String separator = System.getProperty("file.separator");
        String directory = System.getProperty("user.dir");
        return directory
                .concat(separator).concat(this.pdfName);
    }
    private List<File> processFiles() {
        List<File> result = new ArrayList<>();
        for(String file : this.pdfsToMerge) {
            result.add(
                    new File(file)
            );
        }
        return result;
    }

    @Override
    public boolean exec() {
        try {
            PDFMergerUtility pdfMerger = new PDFMergerUtility();
            pdfMerger.setDestinationFileName(this.getPathToPDF());
            List<File> filesToMerge = this.processFiles();
            for(File file: filesToMerge) {
                pdfMerger.addSource(file);
            }
            pdfMerger.mergeDocuments();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
