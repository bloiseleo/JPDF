// $Id: DummySignatureProvider.java 10479 2009-07-10 09:51:07Z chris $

import java.awt.*;
import java.util.*;
import javax.swing.*;
import java.security.GeneralSecurityException;
import java.io.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.DialogPanel;

/**
 * This is a feature for the viewer that verifies a signature created by
 * the "DummySignatureHandler" signature handler. It has a main method so
 * you can just run it as "java DummySignatureHandler". It will load the
 * PDF created by the "SignAndVerify.java" example.
 *
 * This is a very cut down example intended to show how to integrate a
 * custom provider, not necessarily the best way to write a custom provider.
 * For that we'd suggest looking at the providers supplied with the viewer,
 * KeyStoreSignatureProvider and/or RemoteSignatureProvider.
 */
public class DummySignatureProvider extends SignatureProvider {

    private DummySignatureHandlerFactory factory;

    public DummySignatureProvider() {
        super("DummySignatureProvider");
        factory = new DummySignatureHandlerFactory();
        FormSignature.registerHandlerForVerification(factory);  // Needed for verification
    }

    public String getDisplayName() {
        return "MD5 Checksum";
    }

    public boolean canSign(FormSignature field) {
        return true;
    }

    public boolean canVerify(FormSignature field) {
        return field.getSignatureHandler() instanceof DummySignatureHandler;
    }

    /**
     * Return a SignatureProvider.SignatureState object that can represents
     * the state of this signature. As our signatures are just an MD5 checksum
     * this is much simpler than it would usually be.
     */
    private SignatureState verify(FormSignature field) {
        try {
            boolean verified = field.verify();
            int revisionscovered = field.getNumberOfRevisionsCovered();
            int revisionsavailable = field.getForm().getPDF().getNumberOfRevisions();
            if (revisionscovered==0) {
                return new SignatureState(field, Boolean.FALSE, "Invalid range covered", false, null);
            } else if (revisionscovered!=revisionsavailable) {
                return new SignatureState(field, new Boolean(verified), "", true, null);
            } else {
                return new SignatureState(field, new Boolean(verified), "", false, null);
            }
        } catch (Exception e) {
            return new SignatureState(field, new Boolean(false), "Error verifying", false, e);
        }
    }

    public void showSignDialog(JComponent root, FormSignature field) throws GeneralSecurityException {
        DialogPanel panel = new DialogPanel();  // One of our convenience classes
        JTextField namefield = new JTextField();
        JTextField reasonfield = new JTextField();
        JTextField locationfield = new JTextField();
        panel.addComponent("Name", namefield);
        panel.addComponent("Reason", reasonfield);
        panel.addComponent("Location", locationfield);

        if (panel.showDialog(root, "MD5 Checksum")) {
            String name = namefield.getText();
            String reason = reasonfield.getText();
            String location = locationfield.getText();
            field.sign(null, null, null, factory);
            if (reason.length()>0) {
                field.setReason(reason);
            }
            if (location.length()>0) {
                field.setLocation(location);
            }
            if (name.length()>0) {
                field.setName(name);
            }
        }
    }

    public void showVerifyDialog(JComponent jroot, FormSignature field) {
        DocumentPanel root = (DocumentPanel)jroot;
        SignatureState state = getSignatureState(root, field);
        if (state==null) {
            state = verify(field);
            setSignatureState(root, field, state);
        }

        int type;
        String message;
        if (state.getValidity()==null || state.getValidity().booleanValue()==false) {
            type = JOptionPane.ERROR_MESSAGE;
            message = "Invalid MD5 checksum: "+state.getReason();
        } else {
            type = JOptionPane.INFORMATION_MESSAGE;
            message = "Valid MD5 checksum: "+state.getReason();
        }
        JOptionPane.showMessageDialog(jroot, message, message, type);
    }

    public static void main(String[] args) throws Exception {
        Collection features = new ArrayList(ViewerFeature.getAllFeatures());
        features.add(new DummySignatureProvider());
        PDFViewer viewer = PDFViewer.newPDFViewer(features);
        viewer.loadPDF(new File(args[0]));
    }
}
