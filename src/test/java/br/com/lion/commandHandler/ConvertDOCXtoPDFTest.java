package br.com.lion.commandHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ConvertDOCXtoPDFTest {

    private final String DATA_DIR = "C:\\Users\\leona\\IdeaProjects\\JPDF_v2\\src\\test\\data";
    private final String SEPARATOR =  System.getProperty("file.separator");

    private HashMap<String, String> buildInput() {
        HashMap<String, String> commandMap = new HashMap<String, String>();
        commandMap.put("result", "./result_conversion.pdf");
        commandMap.put("docxPath", DATA_DIR.concat(SEPARATOR).concat("teste.docx"));
        return commandMap;
    }
    @Test
    public void ConvertDOCxToPDF() {
       HashMap<String, String> commandMap = this.buildInput();
       ConvertDOCXtoPDF convertDOCXtoPDF = new ConvertDOCXtoPDF(commandMap);
       assertEquals(true, convertDOCXtoPDF.exec());
       File pdf = new File(commandMap.get("result"));
       assertEquals(true, pdf.exists());
    }

    @AfterEach
    public void deleteResult() {
        File pdf = new File(this.buildInput().get("result"));
        pdf.delete();
    }
}