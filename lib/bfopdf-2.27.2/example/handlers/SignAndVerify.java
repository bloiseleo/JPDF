// $Id: SignAndVerify.java 10479 2009-07-10 09:51:07Z chris $

import org.faceless.pdf2.*;
import java.io.*;
import java.security.GeneralSecurityException;

/**
 * This class creates an empty PDF, signs it with our DummyHandler and
 * then verifies it. Try tweaking the PDF file slightly between the sign
 * and verify stage - you'll find that the signature no longer verifies.
 */
public class SignAndVerify
{
    public static void main(String[] args) throws IOException, GeneralSecurityException {
        sign();
        verify();
    }

    private static void sign() throws IOException, GeneralSecurityException {
        PDF pdf = new PDF();
        pdf.setOutputProfile(OutputProfile.NoCompression);
	PDFPage page = pdf.newPage("A4");
	Form form = pdf.getForm();

	SignatureHandlerFactory factory = new DummySignatureHandlerFactory();
	FormSignature sig = new FormSignature(null, null, null, factory);
        sig.addAnnotation(page, 100, 600, 200, 700);
	form.getElements().put("DummySig", sig);

        FileOutputStream out = new FileOutputStream("Signed.pdf");
	pdf.render(out);
        out.close();
        System.out.println("Signed: Created file 'Signed.pdf'");
    }

    private static void verify() throws IOException, GeneralSecurityException {
        FormSignature.registerHandlerForVerification(new DummySignatureHandlerFactory());

        PDF pdf = new PDF(new PDFReader(new File("Signed.pdf")));
	FormSignature sig = (FormSignature)pdf.getForm().getElement("DummySig");
        System.out.println("Verified: valid="+sig.verify());
    }
}
