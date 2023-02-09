// $Id: EmbeddedFilePanel.java 40443 2021-06-29 12:55:28Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.util.*;
import java.text.*;
import java.util.List;

/**
 * Create a {@link SidePanel} that will display a list of embedded files in the PDF.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>save</th><td><code>true</code>, <code>false</code> or <code>desktop</code>, for {@link #setSaveEnabled setSaveEnabled()}. Default is true</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ShowHideEmbeddedFiles</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.18
 */
public class EmbeddedFilePanel extends SidePanelFactory {

    private boolean saveEnabled = true, saveToDesktop = false;

    /**
     * Create a new EmbeddedFilePanel
     */
    public EmbeddedFilePanel() {
        super("ShowHideEmbeddedFiles");
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        String val = getFeatureProperty(viewer, "save");
        if (val != null) {
            setSaveEnabled("true".equals(val) || "desktop".equals(val));
            setSaveToDesktop("desktop".equals(val));
        }
    }

    public boolean isSidePanelRequired(DocumentPanel docpanel) {
        PDF pdf = docpanel.getPDF();
        // If we are still loading, assume that we are required
        // until all pages have arrived and we can safely say we are not.
        return pdf.getLoadState(-1) != null || !pdf.getEmbeddedFiles().isEmpty();
    }

    /**
     * Set whether to allow embedded files to be saved to the filesystem
     * @param enabled if true, embedded files can be saved to the filesystem
     */
    public void setSaveEnabled(boolean enabled) {
        saveEnabled = enabled;
    }

    /**
     * If save is enabled, implement this by creating a temporary file and openinng
     * it with the {@link Desktop#open} method. This defaults to false.
     */
    public void setSaveToDesktop(boolean desktop) {
        saveToDesktop = desktop;
    }

    /**
     * Return whether files can be saved to the filesystem, as set by {@link #setSaveEnabled}
     * @return if true, embedded files can be saved to the filesystem
     */
    public boolean isSaveEnabled() {
        return saveEnabled;
    }

    public boolean isSaveToDesktop() {
        return saveToDesktop;
    }

    public SidePanel createSidePanel() {
        return new EmbeddedFilePanelImpl(this);
    }
}

/**
 * A {@link SidePanel} representing the "Embedded Files" or "Portfolio" panel.
 *
 * <p><i>
 * This code is copyright the Big Faceless Organization. You're welcome to
 * use, modify and distribute it in any form in your own projects, provided
 * those projects continue to make use of the Big Faceless PDF library.
 * </i></p>
 */
class EmbeddedFilePanelImpl extends JPanel implements SidePanel, PropertyChangeListener, Runnable {

    private EmbeddedFilePanel factory;
    private JScrollPane scrollpane;
    private DocumentPanel docpanel;
    private Icon icon;
    private List<EmbeddedFile> eflist;
    private Map<String,Portfolio.FieldType> schema;
    private JTable table;
    private Model model;
    private JLabel spinner;
    private DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    EmbeddedFilePanelImpl(EmbeddedFilePanel factory) {
        super(new BorderLayout());
        this.icon = UIManager.getIcon("PDFViewer.Feature.EmbeddedFilePanel.icon");
        boolean allowSave = factory.isSaveEnabled();
        final boolean saveToDesktop = factory.isSaveToDesktop();
        setOpaque(true);
        this.factory = factory;
        eflist = new ArrayList<EmbeddedFile>();
        model = new Model();
        table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                Point point = e.getPoint();
                int row = table.rowAtPoint(point);
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                } else {
                    table.clearSelection();
                }
            }
            public void mousePressed(MouseEvent e) {
                Point point = e.getPoint();
                int row = table.rowAtPoint(point);
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    if (e.getClickCount() == 2) {
                        handleOpen(eflist.get(table.convertRowIndexToModel(table.getSelectedRow())));
                    }
                } else {
                    table.clearSelection();
                }
            }
        });
        if (allowSave) {
            final JPopupMenu menu = new JPopupMenu();
            JMenuItem open = new JMenuItem(Util.getUIString("PDFViewer.Open"));
            open.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        handleOpen(eflist.get(table.convertRowIndexToModel(row)));
                    }
                }
            });
            JMenuItem save = new JMenuItem(Util.getUIString("PDFViewer.Save"));
            save.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        if (saveToDesktop) {
                            handleSaveToDesktop(eflist.get(table.convertRowIndexToModel(row)));
                        } else {
                            handleSave(eflist.get(table.convertRowIndexToModel(row)));
                        }
                    }
                }
            });
            menu.add(open);
            menu.add(save);
            table.setComponentPopupMenu(menu);
        }

        Insets i = UIManager.getInsets("PDFViewer.Feature.EmbeddedFilePanel.margin");
        if (i != null) {
            table.setBorder(BorderFactory.createEmptyBorder(i.top, i.left, i.bottom, i.right));
        }
        scrollpane = new JScrollPane();
        scrollpane.setViewportView(table);
        Icon spinnerIcon = UIManager.getIcon("PDFViewer.Spinner.icon");
        spinner = new JLabel(spinnerIcon);
    }

    public Icon getIcon() {
        return icon;
    }

    public String getName() {
        return "Attachments";
    }

    public void panelVisible() {
    }

    public void panelHidden() {
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

    /**
     * Run on document load.
     */
    public void run() {
        PDF pdf = docpanel.getPDF();
        init(pdf);
        pdf.addPropertyChangeListener(this);
    }

    private void init(PDF pdf) {
        eflist.clear();
        try {
            Deque<EmbeddedFile> q = new ArrayDeque<EmbeddedFile>();
            q.addAll(pdf.getEmbeddedFiles().values());
            List<PDF> all = docpanel.getAllPDFs();
            PDF subpdf = null;
            while (!q.isEmpty()) {
                EmbeddedFile ef = q.removeFirst();
                if (ef != null) {
                    eflist.add(ef);
                    if (ef.hasPDF() && all.contains(subpdf = ef.getPDF())) {
                        q.addAll(subpdf.getEmbeddedFiles().values());
                    }
                }
            }
        } catch (java.io.IOException e) {}      // Can't happen
        if (eflist.size() == 0) {
            // All pages now loaded and there are no attachments
            docpanel.removeSidePanel(this);
        } else {
            schema = new LinkedHashMap<String,Portfolio.FieldType>(docpanel.getPDF().getPortfolio().getSchema());
            if (schema.isEmpty()) {
                schema.put("name", new Portfolio.FieldType("Name", "name"));
                schema.put("description", new Portfolio.FieldType("Description", "description"));
                schema.put("moddate", new Portfolio.FieldType("Modified", "moddate"));
                schema.put("creationdate", new Portfolio.FieldType("Created", "creationdate"));
                schema.put("size", new Portfolio.FieldType("Size", "size"));
            }
            for (Iterator<Map.Entry<String,Portfolio.FieldType>> i = schema.entrySet().iterator();i.hasNext();) {
                Map.Entry<String,Portfolio.FieldType> e = i.next();
                boolean set = false;
                for (EmbeddedFile ef : eflist) {
                    Object o = e.getValue().getValue(ef, e.getKey());
                    set |= o != null && !"".equals(o);
                }
                if (!set) {
                    i.remove();
                }
            }

            model.fireTableDataChanged();
            model.fireTableStructureChanged();
            remove(spinner);
            add(scrollpane, BorderLayout.CENTER);
            setVisible(true);
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

    /**
     * Document changed.
     */
    public void propertyChange(PropertyChangeEvent e) {
        String name = e.getPropertyName();
        if (name.startsWith("embeddedfile.")) {
            init((PDF) e.getSource());
        }
    }

    private class Model extends AbstractTableModel {

        public int getRowCount() {
            return eflist.size();
        }

        public int getColumnCount() {
            return schema == null ? 0 : schema.size();
        }

        public String getColumnName(int col) {
            for (Map.Entry<String,Portfolio.FieldType> e : schema.entrySet()) {
                if (col-- == 0) {
                    return e.getValue().getName();
                }
            }
            return null;
        }

        public Object getValueAt(int row, int col) {
            EmbeddedFile ef = eflist.get(row);
            for (Map.Entry<String,Portfolio.FieldType> e : schema.entrySet()) {
                if (col-- == 0) {
                    Object o = e.getValue().getValue(ef, e.getKey());
                    if (o instanceof Calendar) {
                        o = df.format(((Calendar)o).getTime());
                    }
                    return o;
                }
            }
            return null;
        }

    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {

        public HeaderRenderer() {
            setHorizontalAlignment(LEFT);
            setHorizontalTextPosition(LEFT);
            setVerticalAlignment(BOTTOM);
            setOpaque(false);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            JTableHeader tableHeader = table.getTableHeader();
            if (tableHeader != null) {
                setForeground(tableHeader.getForeground());
            }
            setIcon(getIcon(table, column));
            setBorder(UIManager.getBorder("TableHeader.cellBorder"));
            return this;
        }

        protected Icon getIcon(JTable table, int column) {
            RowSorter.SortKey sortKey = getSortKey(table, column);
            if (sortKey != null && table.convertColumnIndexToView(sortKey.getColumn()) == column) {
                if (sortKey.getSortOrder() == SortOrder.ASCENDING) {
                    return UIManager.getIcon("Table.ascendingSortIcon");
                } else if (sortKey.getSortOrder() == SortOrder.DESCENDING) {
                    return UIManager.getIcon("Table.descendingSortIcon");
                }
            }
            return null;
        }

        protected RowSorter.SortKey getSortKey(JTable table, int column) {
            RowSorter<?> rowSorter = table.getRowSorter();
            if (rowSorter == null) {
                return null;
            }

            List<?> sortedColumns = rowSorter.getSortKeys();
            if (sortedColumns.size() > 0) {
                return (RowSorter.SortKey) sortedColumns.get(0);
            }
            return null;
        }
    }

    private void handleOpen(EmbeddedFile ef) {
        String type = ef.getType();
        PDFViewer viewer = docpanel.getViewer();
        ViewerFeature[] features = viewer.getFeatures();
        for (ViewerFeature feature : features) {
            if (feature instanceof Importer) {
                Importer importer = (Importer) feature;
                if (importer.canLoad(type)) {
                    String title = ef.getName();
                    InputStream in = ef.getData();
                    importer.getImporter(viewer, in, title, null).start(this, UIManager.getString("PDFViewer.Loading"));
                    return;
                }
            }
        }
        handleSaveToDesktop(ef);
    }

    private void handleSaveToDesktop(EmbeddedFile ef) {
        if (Desktop.isDesktopSupported()) {
            String name = ef.getName();
            String prefix = name, suffix = null;
            if (name.lastIndexOf(".") >= 0) {
                suffix = name.substring(name.lastIndexOf("."));
                prefix = name.substring(0, name.lastIndexOf(".")) + "-";
            }
            OutputStream out = null;
            InputStream in = null;
            File file = null;
            try {
                file = File.createTempFile(prefix, suffix);
                out = new BufferedOutputStream(new FileOutputStream(file));
                in = new BufferedInputStream(ef.getData());
                int l;
                byte[] buf = new byte[8192];
                while ((l=in.read(buf)) >= 0) {
                    out.write(buf, 0, l);
                }
                try { in.close(); } catch (IOException e) {}
                try { out.close(); } catch (IOException e) {}
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                Util.displayThrowable(e, docpanel);
                try { if (in != null) in.close(); } catch (IOException e2) {}
                try { if (out != null) out.close(); } catch (IOException e2) {}
                if (out != null) {
                    file.delete();
                }
            }
        }
    }

    private void handleSave(EmbeddedFile ef) {
        JFileChooser chooser = new JFileChooser();
        File file = docpanel.getAssociatedFile();
        if (file != null) {
            if (!file.isDirectory()) {
                file = file.getParentFile();
            }
            chooser.setCurrentDirectory(file);
        }
        chooser.setSelectedFile(new File(ef.getName()));
        int ret = chooser.showSaveDialog(docpanel);
        if (ret == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile();
            OutputStream out = null;
            InputStream in = null;
            try {
                out = new BufferedOutputStream(new FileOutputStream(file));
                in = new BufferedInputStream(ef.getData());
                int l;
                byte[] buf = new byte[8192];
                while ((l=in.read(buf)) >= 0) {
                    out.write(buf, 0, l);
                }
                try { in.close(); } catch (IOException e) {}
                try { out.close(); } catch (IOException e) {}
            } catch (IOException e) {
                Util.displayThrowable(e, docpanel);
                try { if (in != null) in.close(); } catch (IOException e2) {}
                try { if (out != null) out.close(); } catch (IOException e2) {}
                if (out != null) {
                    file.delete();
                }
            }
        }
    }

}
