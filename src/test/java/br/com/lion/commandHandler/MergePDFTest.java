package br.com.lion.commandHandler;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MergePDFTest {

    public static int i = 0;
    private HashMap<String, String> buildInput() {
        HashMap<String, String> commandMap = new HashMap<String, String>();
        commandMap.put("result", "./result.pdf");
        commandMap.put("pdf1", "./result0.pdf");
        commandMap.put("pdf2", "./result1.pdf");
        commandMap.put("pdf3", "./result2.pdf");
        return commandMap;
    }

    private HashMap<String, String> buildInputToCreateData() {
        HashMap<String, String> commandMap = new HashMap<String, String>();
        commandMap.put("mainCommand", "create_pdf");
        return commandMap;
    }

    @Test
    public void MergeTest() {
        this.createDataToTest();
        this.createDataToTest();
        this.createDataToTest();
        HashMap<String, String> params  = this.buildInput();
        MergePDF mergePDF = new MergePDF(params);
        assertEquals(true, mergePDF.exec());
        File resultPdf = new File(
                params.get("result")
        );
        assertEquals(3, this.countPages(resultPdf));
    }

    @AfterEach
    public void deleteAllPdfs() {
        HashMap<String, String> commandMap = this.buildInput();
        Set<String> keys = commandMap.keySet();
        ArrayList<File> files = new ArrayList<>();
        for (String key : keys) {
            String path = commandMap.get(key);
            File file = new File(path);
            files.add(file);
        }
        for(File file : files) {
            file.delete();
        }
    }

    public int countPages(File pdf) {
        try {
            PDDocument doc = PDDocument.load(pdf);
            return doc.getNumberOfPages();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void createDataToTest() {
        HashMap<String, String> params = this.buildInputToCreateData();
        params.put("pdfName", "result" +i);
        CreatePDF createPDF = new CreatePDF(params);
        createPDF.exec();
        i++;
    }

}