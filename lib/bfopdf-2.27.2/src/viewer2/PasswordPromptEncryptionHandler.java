// $Id: PasswordPromptEncryptionHandler.java 19846 2014-07-30 11:19:13Z mike $

package org.faceless.pdf2.viewer2;

import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;

/**
 * An extension of the {@link StandardEncryptionHandler} that will pop up a
 * password dialog to request the password if necessary.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class PasswordPromptEncryptionHandler extends StandardEncryptionHandler {

    private Component parent;

    public PasswordPromptEncryptionHandler(Component parent) {
        this.parent = parent;
    }

    public boolean equals(Object o) {
        return o instanceof PasswordPromptEncryptionHandler && ((PasswordPromptEncryptionHandler)o).parent == parent && super.equals(o);
    }

    public void prepareToDecrypt() throws IOException {
        try {
            super.prepareToDecrypt();
        } catch (PasswordException e) {
            boolean ok = false;
            do {
                Frame frame = JOptionPane.getFrameForComponent(parent);
                JPasswordField field = new JPasswordField(16);
                JPanel panel = new JPanel(new BorderLayout());
                panel.add(new JLabel(UIManager.getString("PDFViewer.Password")), BorderLayout.WEST);
                panel.add(field, BorderLayout.CENTER);
                if (JOptionPane.showConfirmDialog(frame, panel, UIManager.getString("PDFViewer.Password"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != 0) {
                    throw new PasswordException(UIManager.getString("PDFViewer.WrongPassword"));
                } else {
                    setUserPassword(new String(field.getPassword()));
                    try {
                        super.prepareToDecrypt();
                        ok = true;
                    } catch (PasswordException e2) { }
                }
            } while(!ok);
        }
    }
}
