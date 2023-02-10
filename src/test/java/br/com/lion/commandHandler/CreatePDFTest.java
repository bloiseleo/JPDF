package br.com.lion.commandHandler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class CreatePDFTest {

    private HashMap<String, String> buildInput() {
        HashMap<String, String> commandMap = new HashMap<String, String>();
        commandMap.put("mainCommand", "create_pdf");
        commandMap.put("pdfName", "./result.pdf");
        return commandMap;
    }

    @Test
    public void CreatePDFSuccessfullyTest() {
        HashMap<String, String> params = this.buildInput();
        CreatePDF createPDF = new CreatePDF(params);
        boolean result = createPDF.exec();
        assertEquals(true, result);
        File pdfPathToResultFile = new File(this.getPathToPDF(params.get("pdfName")));
        assertEquals(true, pdfPathToResultFile.exists());
    }
    @AfterEach
    public void DeleteCreatedPDF() {
        File pdfPathToResultFile = new File(this.getPathToPDF(this.buildInput().get("pdfName")));
        if(!pdfPathToResultFile.delete()) {
            throw new RuntimeException("File was not deleted");
        }
    }

    private String getPathToPDF(String pdfName) {
        String separator = System.getProperty("file.separator");
        String directory = System.getProperty("user.dir");
        return directory
                .concat(separator).concat(pdfName);
    }

}