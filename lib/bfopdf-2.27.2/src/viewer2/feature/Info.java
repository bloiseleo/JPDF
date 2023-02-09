// $Id: Info.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.util.DialogPanel;
import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.text.*;

/**
 * Create a button and menu item to display information about the PDF.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">GeneralInfo</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class Info extends ViewerWidget {

    public Info() {
        super("GeneralInfo");
        setButton("Document", "PDFViewer.Feature.Info.icon", "PDFViewer.tt.GeneralInfo");
        setMenu("File\tDocumentProperties...", 'd');
    }

    public void action(ViewerEvent event) {
        PDF pdf = event.getPDF();
        if (pdf != null) {
            displayInfoPanel(pdf, event.getViewer());
        }
    }

    public void displayInfoPanel(PDF pdf, PDFViewer viewer) {
        DialogPanel dialog = new DialogPanel(false);
        dialog.setModal(false);

        DocumentPanel docpanel = viewer.getActiveDocumentPanel();
        JTabbedPane tabbed = new JTabbedPane(JTabbedPane.LEFT);
        tabbed.setTabPlacement(JTabbedPane.TOP);

        // Add Info panel and size main panel to be a bit bigger
        Component comp = getInfoPanel(pdf, docpanel.getPage(), docpanel.getAssociatedFile());
        tabbed.addTab(UIManager.getString("PDFViewer.Info"), new JScrollPane(comp));

        // Add Encryption panel if necessary
        if (pdf.getEncryptionHandler() instanceof StandardEncryptionHandler) {
            comp = getStandardEncryptionPanel(pdf);
            tabbed.addTab(UIManager.getString("PDFViewer.Encryption"), new JScrollPane(comp));
        } else if (pdf.getEncryptionHandler() instanceof PublicKeyEncryptionHandler) {
            comp = getPublicKeyEncryptionPanel(pdf);
            tabbed.addTab(UIManager.getString("PDFViewer.Encryption"), new JScrollPane(comp));
        }

        // Add OutputProfile panel
        comp = getProfilePanel(viewer.getActiveDocumentPanel().getParser());
        tabbed.addTab(UIManager.getString("PDFViewer.Features"), new JScrollPane(comp));

        tabbed.setSelectedIndex(0);
        dialog.addComponent(tabbed);
        dialog.showDialog(viewer, UIManager.getString("PDFViewer.About"));
    }

    private void addEntry(JPanel panel, String key, Object value, GridBagConstraints gbk, GridBagConstraints gbv) {
        if (value == null) {
            value = "";
        } else if (value instanceof Date) {
            value = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format((Date)value);
        } else {
            value = value.toString();
        }

        String text = UIManager.getString(key);
        if (text == null) {
            text = key;
        }
        JLabel label = new JLabel(text);
//        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbk);
        label = new JLabel((String)value);
        panel.add(label, gbv);
    }

    private void addEditableEntry(JPanel panel, final String key, String value, GridBagConstraints gbk, GridBagConstraints gbv, final PDF pdf) {
        if (value == null) {
            value = "";
        }
        JLabel label = new JLabel(key);
//        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbk);
        final JTextField field = new JTextField((String)value);
        field.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                pdf.setInfo(key, field.getText());
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
            }
        });
        panel.add(field, gbv);
    }

    //-----------------------------------------------------------------
    // Info panel stuff

    public JComponent getInfoPanel(final PDF pdf, PDFPage page, File file) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(450, 300));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbk = new GridBagConstraints();
        GridBagConstraints gbv = new GridBagConstraints();
        GridBagConstraints gbs = new GridBagConstraints();
        gbk.fill = gbv.fill = gbs.fill = GridBagConstraints.HORIZONTAL;
        gbv.weightx = 1;
        gbk.insets = new Insets(4, 5, 4, 20);
        gbv.gridwidth = gbs.gridwidth = GridBagConstraints.REMAINDER;
        gbv.insets = new Insets(0, 0, 0, 5);
        gbs.insets = new Insets(4, 5, 4, 5);
        if (file != null) {
            addEntry(panel, "PDFViewer.File", file.toString(), gbk, gbv);
            addEntry(panel, "PDFViewer.FileSize", getFileSize(file.length()), gbk, gbv);
            panel.add(new JSeparator(), gbs);
        }
        Map<String,Object> info = pdf.getInfo();
        addEntry(panel, "PDFViewer.CreationDate", info.get("CreationDate"), gbk, gbv);
        addEntry(panel, "PDFViewer.ModDate", info.get("ModDate"), gbk, gbv);
        addEntry(panel, "PDFViewer.Language", (pdf.getLocale()==null ? null : pdf.getLocale().toString()), gbk, gbv);
        addEntry(panel, "PDFViewer.Producer", info.get("Producer"), gbk, gbv);
        panel.add(new JSeparator(), gbs);
        Set<String> fields = new LinkedHashSet<String>();
        Collection<String> knownfields = Arrays.asList(new String[] { "Author", "Title", "Subject", "Keywords", "Creator" });
        Map<String,Object> infomap = pdf.getInfo();
        fields.addAll(knownfields);
        fields.addAll(infomap.keySet());
        for (Iterator<String> i = fields.iterator();i.hasNext();) {
            String key = i.next();
            Object val = infomap.get(key);
            if (!(key.equals("CreationDate") || key.equals("ModDate") || key.equals("Producer") || val instanceof Calendar)) {
                String text;
                if (knownfields.contains(key)) {
                    text = UIManager.getString("PDFViewer."+key);
                    if (text == null) {
                        text = key;
                    }
                } else {
                    text = key;
                }
                addEditableEntry(panel, text, val==null ? "" : val.toString(), gbk, gbv, pdf);
            }
        }
        panel.add(new JSeparator(), gbs);
        addEntry(panel, "PDFViewer.Pages", Integer.toString(pdf.getNumberOfPages()), gbk, gbv);
        float[] box = page.getBox("Media");
        addEntry(panel, "PDFViewer.PageSize", getPageSize(page.getWidth(), page.getHeight()), gbk, gbv);
        float[] newbox = page.getBox("Crop");
        if (newbox != null && !Arrays.equals(box, newbox)) {
            addEntry(panel, "PDFViewer.CropBox", getPageSize(newbox[2]-newbox[0], newbox[3]-newbox[1]), gbk, gbv);
            box = newbox;
        }
        newbox = page.getBox("Bleed");
        if (newbox != null && !Arrays.equals(box, newbox)) {
            addEntry(panel, "PDFViewer.BleedBox", getPageSize(newbox[2]-newbox[0], newbox[3]-newbox[1]), gbk, gbv);
        }
        newbox = page.getBox("Art");
        if (newbox != null && !Arrays.equals(box, newbox)) {
            addEntry(panel, "PDFViewer.ArtBox", getPageSize(newbox[2]-newbox[0], newbox[3]-newbox[1]), gbk, gbv);
        }
        panel.add(new JLabel(""), new GridBagConstraints() { { weighty = 1; gridheight = REMAINDER; }});
        return panel;
    }

    private String getPageSize(float w, float h) {
        if (Math.round(Math.min(w, h)) == 595 && Math.round(Math.max(w, h)) == 842) {
            return "A4 (210x297mm)";
        } else if (Math.round(Math.min(w, h)) == 612 && Math.round(Math.max(w, h)) == 792) {
            return "Letter (8.5x11\")";
        } else {
            DecimalFormat df = new DecimalFormat("#0.00");
            String s = df.format(w/72f)+"x"+df.format(h/72f)+"\"   ";
            df = new DecimalFormat("#0");
            s += df.format(w/2.8346457)+"x"+df.format(h/2.8346457)+"mm   ";
            s += df.format(w)+"x"+df.format(h)+"pt";
            return s;
        }
    }

    private String getFileSize(long size) {
        DecimalFormat df = new DecimalFormat("#0.00");
        DecimalFormat df2 = new DecimalFormat("#,##0");
        if (size > 1048576) {
            return df.format(size / 1048576.0)+"MB ("+df2.format(size)+" bytes)";
        } else if (size > 1024) {
            return df.format(size / 1024.0)+"KB ("+df2.format(size)+" bytes)";
        } else {
            return df2.format(size)+" bytes";
        }
    }

    //----------------------------------------------------------------------
    // Encryption Panel stuff

    public JComponent getStandardEncryptionPanel(PDF pdf) {
        final StandardEncryptionHandler handler = (StandardEncryptionHandler)pdf.getEncryptionHandler();
        int fchange = handler.getChange();
        int fprint = handler.getPrint();
        int fextract = handler.getExtract();
        return getGeneralEncryptionPanel(fchange, fprint, fextract, "PDFViewer.PasswordSecurity", handler.getDescription());
    }

    public JComponent getPublicKeyEncryptionPanel(PDF pdf) {
        final PublicKeyEncryptionHandler handler = (PublicKeyEncryptionHandler)pdf.getEncryptionHandler();
        int fchange = handler.getChange();
        int fprint = handler.getPrint();
        int fextract = handler.getExtract();
        return getGeneralEncryptionPanel(fchange, fprint, fextract, "PDFViewer.CertificateSecurity", null);
    }

    private JComponent getGeneralEncryptionPanel(int fchange, int fprint, int fextract, String type, String algorithm) {
        String print, change, form, comment, copy, copyac;
        switch (fprint) {
          case StandardEncryptionHandler.PRINT_NONE:
            print = "PDFViewer.NotAllowed";
            break;
          case StandardEncryptionHandler.PRINT_LOWRES:
            print = "PDFViewer.LowResolution";
            break;
          default:
            print = "PDFViewer.Allowed";
            break;
        }
        switch (fextract) {
          case StandardEncryptionHandler.EXTRACT_NONE:
            copy = "PDFViewer.NotAllowed";
            copyac = "PDFViewer.NotAllowed";
            break;
          case StandardEncryptionHandler.EXTRACT_ACCESSIBILITY:
            copy = "PDFViewer.NotAllowed";
            copyac = "PDFViewer.Allowed";
            break;
          default:
            copy = "PDFViewer.Allowed";
            copyac = "PDFViewer.Allowed";
            break;
        }
        switch (fchange) {
          case StandardEncryptionHandler.CHANGE_NONE:
            change = "PDFViewer.NotAllowed";
            form = "PDFViewer.NotAllowed";
            comment = "PDFViewer.NotAllowed";
            break;
          case StandardEncryptionHandler.CHANGE_LAYOUT:
            change = "PDFViewer.Allowed";
            form = "PDFViewer.NotAllowed";
            comment = "PDFViewer.NotAllowed";
            break;
          case StandardEncryptionHandler.CHANGE_ANNOTATIONS:
            change = "PDFViewer.NotAllowed";
            form = "PDFViewer.NotAllowed";
            comment = "PDFViewer.Allowed";
            break;
          case StandardEncryptionHandler.CHANGE_FORMS:
            change = "PDFViewer.NotAllowed";
            form = "PDFViewer.Allowed";
            comment = "PDFViewer.Allowed";
            break;
          default:
            change = "PDFViewer.Allowed";
            form = "PDFViewer.Allowed";
            comment = "PDFViewer.Allowed";
        }

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbk = new GridBagConstraints();
        GridBagConstraints gbv = new GridBagConstraints();
        gbk.fill = gbv.fill = gbv.HORIZONTAL;
        gbk.insets = new Insets(4, 5, 4, 20);
        gbv.weightx = 1;
        gbv.gridwidth = gbv.REMAINDER;

        addEntry(panel, "PDFViewer.Security", UIManager.getString(type), gbk, gbv);
        if (algorithm!=null) {
            addEntry(panel, "PDFViewer.Algorithm", algorithm, gbk, gbv);
        }
        addEntry(panel, "PDFViewer.Printing", UIManager.getString(print), gbk, gbv);
        addEntry(panel, "PDFViewer.ChangingDocument", UIManager.getString(change), gbk, gbv);
        addEntry(panel, "PDFViewer.ContentCopying", UIManager.getString(copy), gbk, gbv);
        addEntry(panel, "PDFViewer.ContentCopyingAcc", UIManager.getString(copyac), gbk, gbv);
        addEntry(panel, "PDFViewer.Commenting", UIManager.getString(comment), gbk, gbv);
        addEntry(panel, "PDFViewer.FormFilling", UIManager.getString(form), gbk, gbv);
        panel.add(new JLabel(""), new GridBagConstraints() { { weighty = 1; gridheight = REMAINDER; }});

        return panel;
    }

    //----------------------------------------------------------------------
    // Profile panel stuff

    public JComponent getProfilePanel(final PDFParser parser) {
        final OutputProfiler profiler = new OutputProfiler(parser);
        final JPanel outer = new JPanel(new GridBagLayout());
        final JPanel waiting = new JPanel();
        final JProgressBar progressbar = new JProgressBar(0, 100);
        final JButton cancelbutton = new JButton(UIManager.getString("PDFViewer.Cancel"));
        cancelbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                profiler.cancel();
                cancelbutton.setEnabled(false);
            }
        });
        waiting.add(new JLabel(UIManager.getString("PDFViewer.Loading")), BorderLayout.NORTH);
        waiting.add(progressbar, BorderLayout.CENTER);
        waiting.add(cancelbutton, BorderLayout.EAST);
        outer.add(waiting, new GridBagConstraints() {{ fill=NONE; anchor=CENTER; }});
        outer.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new Thread("BFO-ProfilePanel") {
            public void run() {
                profiler.run();
            }
        }.start();

        // Thread to update progress bar.
        Thread progressthread = new Thread("BFO-ProfilePanel-ProgressBar") {
            int pp;
            public void run() {
                do {
                    pp = Math.round(profiler.getProgress() * 100);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            progressbar.setValue(pp);
                        }
                    });
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) { }
                } while (profiler.isRunning());
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        OutputProfile profile = profiler.getProfile();
                        if (profile != null) {
                            DefaultListModel<String> model = new DefaultListModel<String>();
                            outer.remove(waiting);
                            outer.setCursor(null);
                            outer.add(new JList<String>(model), new GridBagConstraints() {{ fill=BOTH; weightx=weighty=1; anchor=NORTHWEST; }});
                            for (int i=0;i<OutputProfile.Feature.ALL.length;i++) {
                                OutputProfile.Feature feature = OutputProfile.Feature.ALL[i];
                                if (profile.isSet(feature)) {
                                    model.addElement("\u2022 "+feature);
                                }
                            }
                        } else {
                            outer.remove(waiting);
                            outer.setCursor(null);
                            JLabel label = new JLabel("Cancelled");
                            label.setHorizontalAlignment(label.CENTER);
                            outer.add(label, new GridBagConstraints() {{ fill=BOTH; weightx=weighty=1; anchor=CENTER; }});
                        }
                        outer.revalidate();
                    }
                });
            }
        };
        progressthread.setDaemon(true);
        progressthread.start();

        return outer;
    }

    //----------------------------------------------------------------------

//    private JTable getFontMapPanel(PDFParser parser) {
//        Map fontMap = parser.getFontMap();
//        Map namemap = new HashMap();
//        for (Iterator i = fontMap.entrySet().iterator();i.hasNext();) {
//            Map.Entry e = (Map.Entry)i.next();
//            namemap.put(((PDFFont)e.getKey()).getBaseName(), e.getValue());
//        }
//        return new JTable(new FontMapTableModel(namemap));
//    }
//
//    static class FontMapTableModel implements TableModel {
//
//        final ArrayList entries;
//
//        FontMapTableModel(Map fontMap) {
//            entries = new ArrayList(fontMap.entrySet());
//        }
//
//        public int getRowCount() {
//            return entries.size();
//        }
//
//        public int getColumnCount() {
//            return 2;
//        }
//
//        public String getColumnName(int columnIndex) {
//            return null;
//        }
//
//        public Class getColumnClass(int columnIndex) {
//            return String.class;
//        }
//
//        public boolean isCellEditable(int rowIndex, int columnIndex) {
//            return false;
//        }
//
//        public Object getValueAt(int rowIndex, int columnIndex) {
//            try {
//                Map.Entry entry = (Map.Entry) entries.get(rowIndex);
//                switch (columnIndex) {
//                  case 0:
//                    return entry.getKey();
//                  case 1:
//                    return toString((Font) entry.getValue());
//                  default:
//                    return null;
//                }
//            } catch (ArrayIndexOutOfBoundsException e) {
//                return null;
//            }
//        }
//
//        public void setValueAt(Object vaue, int rowIndex, int columnIndex) {
//            // NOOP
//        }
//
//        public void addTableModelListener(TableModelListener l) {
//        }
//
//        public void removeTableModelListener(TableModelListener l) {
//        }
//
//        public String toString(Font font) {
//            StringBuilder buf = new StringBuilder(font.getFamily());
//            int style = font.getStyle();
//            if (style != Font.PLAIN) {
//                buf.append(" [");
//                if ((style & Font.BOLD) != 0)
//                    buf.append("Bold");
//                if ((style & Font.ITALIC) != 0)
//                    buf.append("Italic");
//                buf.append("]");
//            }
//            return buf.toString();
//        }
//
//    }
}
