// $Id: SignaturePanel.java 39859 2021-04-26 12:28:08Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.event.*;
import java.security.cert.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.text.*;
import java.util.*;
import java.util.List;

/**
 * Create a {@link SidePanel} that will display a list of Digital Signatures in the PDF.
 * Double clicking on a blank signature will select a {@link SignatureProvider} and
 * then call {@link SignatureProvider#showSignDialog SignatureProvider.showSignDialog()}.
 * Double clicking on a previously signed signature will select a {@link SignatureProvider} and
 * then call {@link SignatureProvider#showVerifyDialog SignatureProvider.showVerifyDialog()}.
 * <br><br>
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>VerifyAll.Hide</th><td><code>true</code> or <code>false</code>, to show or hide the "Verify All" button. The default is true</td></tr>
 * <tr><th>HideUntilKnown</th><td>When a linearized PDF is being loaded, the API cannot know for sure whether signatures exist in the PDF until the load is complete. By default the SignaturePanel will be shown in a "loading" state until the load is complete, and then closed if no signature exists, but setting this value to not-null will hide the panel until the load is complete, and if a signature is found will add the panel at that point.</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ShowHideSignatures</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class SignaturePanel extends SidePanelFactory {

    /**
     * Create a new SignaturePanel
     * @since 2.11
     */
    public SignaturePanel() {
        super("ShowHideSignatures");
    }

    public boolean isSidePanelRequired(DocumentPanel docpanel) {
        PDF pdf = docpanel.getPDF();
        // If we are still loading, assume that we are required
        // until all pages have arrived and we can safely say we are not.
        return pdf.getLoadState(-1) != null || !getSignatures(docpanel).isEmpty();
    }

    static Vector<FormSignature> getSignatures(DocumentPanel docpanel) {
        Vector<FormSignature> v = new Vector<FormSignature>();
        // This should not be called until all pages have been loaded,
        // since otherwise it will block until they are.
        for (PDF pdf : docpanel.getAllPDFs()) {
            for (Iterator<FormElement> i = pdf.getForm().getElements().values().iterator();i.hasNext();) {
                FormElement field = i.next();
                if (field instanceof FormSignature) {
                    v.add((FormSignature)field);
                }
            }
        }
        return v;
    }

    public SidePanel createSidePanel() {
        return new SignaturePanelImpl(this);
    }
}

/**
 * A {@link SidePanel} representing the "Signature" or "Bookmark" panel.
 *
 * <p><i>
 * This code is copyright the Big Faceless Organization. You're welcome to
 * use, modify and distribute it in any form in your own projects, provided
 * those projects continue to make use of the Big Faceless PDF library.
 * </i></p>
 */
class SignaturePanelImpl extends JPanel implements SidePanel, ListCellRenderer<FormSignature>, DocumentPanelListener, Runnable {

    private JScrollPane scrollpane;
    private DocumentPanel docpanel;
    private Icon icon;
    private SignaturePanel factory;
    private JList<FormSignature> list;
    private JLabel spinner;

    SignaturePanelImpl(SignaturePanel factory) {
        super(new BorderLayout());
        setOpaque(true);
        this.factory = factory;
        scrollpane = new JScrollPane();
        this.icon = UIManager.getIcon("PDFViewer.Feature.SignaturePanel.icon");
        Icon spinnerIcon = UIManager.getIcon("PDFViewer.Spinner.icon");
        spinner = new JLabel(spinnerIcon);
    }

    public Icon getIcon() {
        return icon;
    }

    public String getName() {
        return "Signatures";
    }

    public void panelVisible() {
    }

    public void panelHidden() {
    }

    public void run() {
        PDF pdf = docpanel.getPDF();
        Vector<FormSignature> signatures = SignaturePanel.getSignatures(docpanel);
        if (signatures.isEmpty()) {
            // All pages now loaded and there are no signatures
            docpanel.removeSidePanel(this);
        } else {
            setVisible(true);
            doInit(signatures);
            remove(spinner);
            add(scrollpane, BorderLayout.CENTER);
            docpanel.addSidePanel(this);
            doRevalidate();
        }
    }

    private void doRevalidate() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                revalidate();
            }
        });
    }

    public void setDocumentPanel(DocumentPanel docpanel) {
        if (docpanel == this.docpanel) {
            return;
        }
        this.docpanel = docpanel;
        PDF pdf = docpanel == null ? null : docpanel.getPDF();
        if (pdf != null) {
            if (pdf.getLoadState(-1) != null) {
                boolean show = factory.getFeatureProperty(docpanel.getViewer(), "HideUntilKnown") == null;
                if (show) {
                    remove(scrollpane);
                    add(spinner, BorderLayout.CENTER);
                    doRevalidate();
                } else {
                    setVisible(false);
                }
                docpanel.getLinearizedSupport().invokeOnDocumentLoad(this);
            } else {
                run();
            }
        }
    }

    void doInit(final Vector<FormSignature> signatures) {
        list = new JList<FormSignature>(signatures);
        list.setCellRenderer(this);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int ix = list.locationToIndex(event.getPoint());
                    if (ix >= 0) {
                        final FormSignature field = signatures.get(ix);
                        if (field.getState() == FormSignature.STATE_SIGNED) {
                            SignatureProvider.selectVerifyProvider(docpanel, field, list, event.getPoint(), new ActionListener() {
                                public void actionPerformed(ActionEvent event) {
                                    SignatureProvider ssp = (SignatureProvider)event.getSource();
                                    ssp.showVerifyDialog(docpanel, field);
                                }
                            });
                        } else {
                            SignatureProvider.selectSignProvider(docpanel, field, list, event.getPoint(), new ActionListener() {
                                public void actionPerformed(ActionEvent event) {
                                    SignatureProvider ssp = (SignatureProvider)event.getSource();
                                    try {
                                        ssp.showSignDialog(docpanel, field);
                                    } catch (Exception e) {
                                        Util.displayThrowable(e, docpanel);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIManager.getColor("List.background"));
        panel.setOpaque(true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = gbc.BOTH;
        gbc.weightx = 1;
        gbc.anchor = gbc.WEST;
        gbc.gridwidth = gbc.REMAINDER;
        panel.add(list, gbc);
        JButton verifyall = new JButton(Util.getUIString("PDFViewer.VerifyAll"));
        verifyall.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                new BackgroundSignatureVerifier().startVerification(docpanel, signatures);
            }
        });
        gbc.fill = gbc.NONE;
        panel.add(verifyall, gbc);

        if (factory.getFeatureProperty(docpanel.getViewer(), "VerifyAll.Hide") != null || factory.getFeatureProperty(docpanel.getViewer(), "verifyAll.hide") != null) {     // legacy
            verifyall.setVisible(false);
        }

        gbc.fill = gbc.BOTH;
        gbc.weighty = 1;
        JPanel fill = new JPanel();
        fill.setOpaque(false);
        panel.add(fill, gbc);
        Insets i = UIManager.getInsets("PDFViewer.Feature.SignaturePanel.margin");
        if (i != null) {
            panel.setBorder(BorderFactory.createEmptyBorder(i.top, i.left, i.bottom, i.right));
        }

        scrollpane.setVisible(true);
        scrollpane.setViewportView(panel);
    }

    public Component getListCellRendererComponent(JList<? extends FormSignature> list, final FormSignature field, int index, boolean isSelected, boolean hasFocus) {
        Icon icon;
        final StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        if (field.getState() == FormSignature.STATE_SIGNED) {
            icon = SignatureProvider.getIcon(docpanel, field);
            SignatureProvider.selectVerifyProvider(docpanel, field, list, null, new ActionListener() {
                public void actionPerformed(ActionEvent event) {
                    SignatureProvider ssp = (SignatureProvider)event.getSource();
                    for (Map.Entry<String,String> e : ssp.getSummaryText(field, docpanel)) {
                        sb.append("<b>");
                        sb.append(e.getKey());
                        sb.append("</b>: ");
                        sb.append(e.getValue());
                        sb.append("<br>");
                    }
                }
            });
        } else {
            icon = UIManager.getIcon("PDFViewer.Feature.SignaturePanel.unsigned.icon");
        }
        sb.append("</html>");

        JLabel comp = new JLabel(sb.toString());
        comp.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        comp.setIcon(icon);
        comp.setOpaque(true);
        comp.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        comp.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        comp.setVerticalTextPosition(SwingConstants.TOP);
        return comp;
    }

    private static String getSubject(X509Certificate cert) {
        return cert.getSubjectX500Principal().toString();
    }

    public void documentUpdated(DocumentPanelEvent event) {
        if (event.getType().equals("stateChanged") && list != null) {
            list.repaint();
        }
    }


}
