// $Id: DummySignatureHandler.java 10479 2009-07-10 09:51:07Z chris $

import org.faceless.pdf2.*;
import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

/**
 * This is a demonstration SignatureHandler that "signs" the PDF by applying
 * an MD5 checksum. This is just an example - the checksum isn't digitally
 * signed so this would be no good as a real document integrity checker - but
 * the design is the same.
 */
public class DummySignatureHandler extends SignatureHandler
{
    private boolean debug = false;              // Set to true to see method calls
    private MessageDigest digest;

    /**
     * Return the name of the Filter.
     * If you're creating PKCS#7 Signatures, typically you want to return
     * "Adobe.PPKLite" here.
     */
    public String getFilter() {
        return "Dummy.MD5";
    }

    /**
     * Overriding this method is optional - but in this example we know the "Contents"
     * string will always be 128 bits. Stored as a hex string this is 32 bytes and the
     * two PDF string markers "<" and ">" make it 34, so we create a placeholder for
     * that string in the map and return it.
     *
     * Comment this method out and the example will still work - the difference is the
     * "getMessageDigest" and "sign" method will be called twice, the first time just
     * to determine the size of the "Contents" string.
     *
     * This is OK unless there's a specific reason you don't want sign() called twice.
     * Usually this is because you're signing remotely. In that case you should take a
     * guess at the size of your byte array object, add a bit to be sure, add 2 and
     * double it.
     */
    public Map getVariables() {
        Map map = new HashMap();
        map.put("Contents", new byte[34]);
        return map;
    }

    protected void prepareToSign(KeyStore store, String alias, char[] password)
        throws GeneralSecurityException
    {
        super.prepareToSign(store, alias, password);    // Always call super
        putNumericValue("Version", 1);	// Add some values to the signature dictionary
        // Those creating a standard PKCS#7 signature will  need this line:
        // putNameValue("SubFilter", "adobe.pkcs7.detached");
    }

    public MessageDigest getMessageDigest() {
        if (debug) {
            System.out.println("-->getMessageDigest");
        }
        if (digest==null) {     // It doesn't matter if you return a new item or the
            try {               // same item each time - both will work.
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        return digest;
    }

    public byte[] sign() {
        byte[] value = digest.digest();
        if (debug) {
            System.out.println("-->sign: digest="+dump(value));
        }
        return value;
    }

    public boolean verify(InputStream in) throws IOException {
        getMessageDigest();
        int len;
        byte[] buf = new byte[8192];
        while ((len=in.read(buf))>=0) {
            digest.update(buf, 0, len);
        }
        byte[] calcedvalue = digest.digest();
        byte[] storedvalue = getStringValue("Contents");
        if (debug) {
            System.out.println("-->verify: stored="+dump(storedvalue)+" calculated="+dump(calcedvalue));
        }
        return Arrays.equals(calcedvalue, storedvalue);
    }

    public String[] getLayerNames() {
        return new String[] { "n0", "n2" }; // Despite what the spec says, these two seem to be the standard
    }

    public PDFCanvas getLayerAppearance(String name, PDFStyle style) {
        PDFCanvas canvas = new PDFCanvas(100, 100);
	if (name.equals("n2")) {
            style.setFont(style.getFont(), 40);  // By default, font size is 0
	    LayoutBox box = new LayoutBox(100);
	    box.addText("MD5\nMD5", style, null);
	    canvas.drawLayoutBox(box, 0, 100);
	}
        return canvas;
    }

    private static String dump(byte[] value) {  // For debugging
        return new java.math.BigInteger(1, value).toString(16);
    }
}
