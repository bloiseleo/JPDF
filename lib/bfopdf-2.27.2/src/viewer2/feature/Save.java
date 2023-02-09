// $Id: Save.java 43619 2022-07-13 18:54:45Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.DialogPanel;
import org.faceless.pdf2.*;
import org.faceless.util.Langolier;
import javax.swing.*;
import javax.swing.plaf.basic.BasicFileChooserUI;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.filechooser.FileFilter;
import java.io.*;

/**
 * <p>
 * Create a button that will allow the PDF to be saved to disk. If the PDF was originally
 * loaded from a File, the user will not be prompted for a filename, otherwise this feature
 * functions like {@link SaveAs}.
 * </p>
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>promptOnOverwrite</th><td>true to prompt before overwriting files, false otherwise (the default)</td></tr>
 * <tr><th>disableUnlessDirty</th><td>true to disable this feature until the PDF has been marked as "dirty" (ie it has been altered), false to always enable this feature (the defualt)</td></tr>
 * <tr><th>alwaysCopy</th><td>true to <i>always</i> do an inefficient copy from the temporary file to the actual file, to work around a reported issue with saving to Windows Network Drives</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">Save</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class Save extends ViewerWidget implements DocumentPanelListener, PropertyChangeListener {

    private Action action;
    private boolean overwriteprompt = false;
    private boolean disableUnlessDirty = false;
    private boolean enableForNewFiles = false;

    public Save() {
        super("Save");
        setButton("Document", "PDFViewer.Feature.Save.icon", "PDFViewer.tt.Save");
        setMenu("File\tSave", 's');
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                Save.super.createActionListener().actionPerformed(e);
            }
        };
    }

    public void action(ViewerEvent event) {
        List<Exporter> exporters = new ArrayList<Exporter>();
        for (ViewerFeature feature : event.getViewer().getFeatures()) {
            if (feature instanceof PDFExporter && ((Exporter)feature).isEnabled(event.getDocumentPanel())) {
                exporters.add((Exporter)feature);
                break;
            }
        }
        if (exporters.isEmpty()) {
            exporters.add(new PDFExporter());
        }
        boolean success = save(null, event, exporters, false, overwriteprompt);
        if (success) {
            event.getDocumentPanel().setDirty(false);
        }
    }

    public boolean isEnabledByDefault() {
        return Util.hasFilePermission();
    }

    protected ActionListener createActionListener() {
        return action;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);

        String val = getFeatureProperty(viewer, "promptOnOverwrite");
        if (val != null) {
            setPromptOnOverwrite("true".equals(val));
        }
        val = getFeatureProperty(viewer, "disableUnlessDirty");
        if (val != null) {
            disableUnlessDirty = true;
        }
        val = getFeatureProperty(viewer, "enableForNewFiles");
        if (val != null) {
            enableForNewFiles = true;
        }
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        DocumentPanel docpanel = event.getDocumentPanel();
        if (type.equals("activated")) {
            if (disableUnlessDirty) {
                docpanel.addPropertyChangeListener(this);
            }
            updateEnabled(docpanel);
        } else if (type.equals("permissionChanged") && docpanel != null && docpanel == getViewer().getActiveDocumentPanel()) {
            updateEnabled(docpanel);
        } else if (type.equals("deactivated")) {
            docpanel.removePropertyChangeListener(this);
            action.setEnabled(false);
        }
    }

    public void propertyChange(PropertyChangeEvent event) {
        DocumentPanel docpanel = (DocumentPanel) event.getSource();
        if ("dirty".equals(event.getPropertyName())) {
            updateEnabled(docpanel);
        }
    }

    private void updateEnabled(DocumentPanel docpanel) {
        boolean enable = docpanel.hasPermission("Save") &&
                         docpanel.getPDF() != null &&
                         (docpanel.isDirty() || !disableUnlessDirty) &&
                         (enableForNewFiles || docpanel.getAssociatedFileEnablesSave());
        action.setEnabled(enable);
    }

    /**
     * Set whether this feature should prompt before overwriting a file
     * @param prompt whether to prompt before overwriting a file (detault is false)
     * @since 2.11.25
     */
    public void setPromptOnOverwrite(boolean prompt) {
        this.overwriteprompt = prompt;
    }

    /**
     * Indicates whether this feature should prompt before overwriting a file
     * @since 2.18
     */
    public boolean isPromptOnOverwrite() {
        return overwriteprompt;
    }

    /**
     * Set whether this feature should be disabled unless the PDF is marked as "dirty",
     * i.e. it has been changed since it was loaded. The default is false.
     * @param value whether to disable this feature unless the PDF is marked as dirty.
     * @since 2.16.1
     */
    public void setDisableUnlessDirty(boolean value) {
        this.disableUnlessDirty = value;
    }

    /**
     * Set whether this feature is disabled when the PDF is marked as "new", i.e. it
     * was not loaded from a local PDF file originally. Prior to 2.24.3 this was false,
     * so the "Save" prompt was always available even for new files. In 2.24.3 this
     * value defaults to true - Save is disabled if the file was imported from an image,
     * or from a URL (although the "Save As" option still allows the file to be saved)
     * @since 2.24.3
     */
    public void setEnabledForNewFiles(boolean value) {
        this.enableForNewFiles = value;
    }

    /** 
     * Save the Document. Calls {@link #save(File,ViewerEvent,List,boolean,boolean)}
     * with a list of all the exporters availabe in the viewer, and the specified one first.
     * Note this method is no longer called from within the API, it's here for legacy reasons only.
     *
     * @param event the ViewerEvent that launched this action
     * @param exporter if not null, the initial exporter to use.
     * @param initialpath the Path to display by default, or <code>null</code> to use the same
     * path as the source file
     * @param displayprompt whether to prompt the user for a filename. We will always prompt
     * anyway if the document has not yet been saved.
     * @param overwriteprompt whether to prompt the user if we are about to overwrite a filename.
     * @return true if the file was saved, false otherwise
     * @since 2.11.10
     */
    public static boolean save(ViewerEvent event, Exporter exporter, String initialpath, boolean displayprompt, boolean overwriteprompt) {
        List<Exporter> exporters = new ArrayList<Exporter>();
        if (exporter != null) {
            exporters.add(exporter);
        }
        for (ViewerFeature feature : event.getViewer().getFeatures()) {
            if (feature instanceof Exporter && feature != exporter && ((Exporter)feature).isEnabled(event.getDocumentPanel())) {
                exporters.add((Exporter)feature);
            }
        }
        if (exporters.isEmpty()) {
            exporters.add(new PDFExporter());
        }
        File file = initialpath == null ? null : new File(initialpath);
        return save(file, event, exporters, displayprompt, overwriteprompt);
    }

    /**
     * Save the Document.
     * @param file the File to save to, or null to use the existing file (if there is one) or prompt otherwise.
     * @param event the ViewerEvent that launched this action
     * @param exporters the list of {@link Exporter} objects. If a dialog is displayed the list will be presented
     * as options for users, otherwise the first exporter will be used. The list must have at least one item.
     * @param displayprompt whether to prompt the user for a filename.
     * We will always prompt anyway if the document has not yet been saved.
     * @param overwriteprompt whether to prompt the user if we are about to overwrite a filename.
     * @return true if the file was saved, false otherwise
     * @since 2.24.3
     */
    public static boolean save(final File file, final ViewerEvent event, final List<Exporter> exporters, final boolean displayprompt, final boolean overwriteprompt) {
        if (exporters == null || exporters.isEmpty()) {
            throw new IllegalArgumentException("At least one exporter is required");
        }
        try {
            return JSEngine.doPrivileged(new Callable<Boolean>() {
                public Boolean call() {
                    File lfile = file;
                    if (lfile == null) {
                        lfile = event.getDocumentPanel().getAssociatedFile();
                    }
                    return Boolean.valueOf(doSave(lfile, exporters, event, displayprompt, overwriteprompt));
                }
            });
        } catch (Exception e) {
            throw (RuntimeException)e;
        }
    }

    private static boolean doSave(File file, List<Exporter> exporters, ViewerEvent event, boolean displayprompt, boolean overwriteprompt) {
        final DocumentPanel docpanel = event.getDocumentPanel();
        final PDFViewer viewer = event.getViewer();
        final Preferences preferences = viewer.getPreferences();
        final PDF pdf = event.getPDF();

        if (file == null || displayprompt) {
            File directory = file == null ? null : file.getParentFile();
            if (directory == null && preferences != null) {
                try {
                    directory = new File((String) preferences.get("lastDirectory", null));
                } catch (Exception e) { }
            }
            if (directory == null) { // Slightly over paranoid, there appear to
                try {                // be Windows bugs in this area.
                    directory = new File(System.getProperty("user.dir"));
                } catch (SecurityException e) {
                    directory = File.listRoots()[0];
                }
            }
            if (file == null) {
                file = directory;
            } else if (file.getParentFile() == null) {
                file = new File(directory, file.getName());
            }

            SaveFileChooser chooser = new SaveFileChooser(file, docpanel, exporters, overwriteprompt);
            Util.fixFileChooser(chooser);
            if (chooser.showDialog(docpanel, UIManager.getString("FileChooser.saveButtonText")) == JFileChooser.APPROVE_OPTION) {
                final Exporter exporter = chooser.getExporter();
                final JComponent exporterComponent = chooser.getExporterComponent();
                final File targetfile = chooser.getSelectedFile();

                boolean save = true;
                if (chooser.isExporterPopupRequired()) {
                    DialogPanel dialog = new DialogPanel() {
                        public String validateDialog() {
                            return exporter.validateComponent(exporterComponent);
                        }
                    };
                    exporterComponent.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                    dialog.addComponent(exporterComponent);
                    dialog.setButtonText("ok", UIManager.getString("PDFViewer.Save"));
                    if (!dialog.showDialog(docpanel, Util.getUIString("PDFViewer.SavingFile", "PDF"))) {
                        save = false;
                    }
                }

                if (save && targetfile != null) { // Targetfile is never null if JFileChooser behaves - never assume this.
                    return doSave1(pdf, exporter, exporterComponent, targetfile, file, viewer, preferences, docpanel);
                }
            }
            return false;       // Dialog was cancelled
        } else {
            if (file.exists() && (!file.canWrite() || file.isDirectory())) {
                String msg = Util.getUIString("PDFViewer.ReadOnly", file.getName());
                JOptionPane.showMessageDialog(docpanel.getViewer(), msg, UIManager.getString("PDFViewer.Error"), JOptionPane.ERROR_MESSAGE);
                return false;
            } else {
                if (exporters == null || exporters.isEmpty()) {
                    throw new IllegalArgumentException("Exporters must have at least one entry");
                }
                Exporter exporter = exporters.get(0);
                return doSave1(pdf, exporter, null, file, file, viewer, preferences, docpanel);
            }
        }
    }

    private static boolean doSave1(final PDF pdf, final Exporter exporter, final JComponent exporterComponent, final File file, final File origfile, final PDFViewer viewer, final Preferences preferences, final DocumentPanel docpanel) {
        if (preferences != null) {
            String lastDirectory = file.getParent();
            if (lastDirectory != null) {
                preferences.put("lastDirectory", lastDirectory);
            }
        }
        try {
            final File canonFile = file.getCanonicalFile();
            final java.util.List<PDFBackgroundTask> tasks = new ArrayList<PDFBackgroundTask>();
            final Set<PDF> pdfs = new LinkedHashSet<PDF>();
            pdfs.add(docpanel.getPDF());

            // If the file we're saving to exists, render to a temporary file
            // in the same directory and rename. If it's the same file we've
            // read from, we need to ensure file backing store remains
            // uncorrupted. We do this by setting the "MultipleRevisions"
            // feature to required. This will preserve the file structure
            // for File based PDF (and do nothing otherwise)
            //
            pdf.getBasicOutputProfile().clearRequired(OutputProfile.Feature.MultipleRevisions);
            final boolean windows = System.getProperty("os.name").startsWith("Windows");

            // Report: file.exists() = true && file.delete() returns true, but instead
            // of the file being deleted, it remains with original size, but no owner
            // or access rights. Only happens on network drives. In 2022.
            // Add a workaround - system property. Checked this way because this method
            // is static, argh, and we should have done some sort of config class
            // instead of a long list of params we want to add. But we're stuck with it.
            //
            // 20220713 - unable to reproduce, even reported "net use I: \\localhost\C$ /Persistent:No"
            final boolean alwaysCopy = viewer.getPropertyManager().getProperty("feature.Save.alwaysCopy") != null;

            final File tempfile;
            if (file.exists()) {
                String tempname = file.getName();
                if (tempname.lastIndexOf(".") > 0) {
                    tempname = tempname.substring(0, tempname.lastIndexOf(".") + 1);
                }
                while (tempname.length() < 3) { // Minimum length of 3, apparently
                    tempname += 'x';
                }
                if (alwaysCopy) {
                    // We are going to run the "inefficient copy" code, so to trim network traffic,
                    // make the temp file local.
                    tempfile = File.createTempFile(tempname, null, null);
                } else {
                    tempfile = File.createTempFile(tempname, null, file.getParentFile());
                }
                if (pdf.getBasicOutputProfile().isSet(OutputProfile.Feature.FileBased) && canonFile.equals(origfile.getCanonicalFile())) {
                    pdf.getBasicOutputProfile().setRequired(OutputProfile.Feature.MultipleRevisions);
                }
            } else {
                tempfile = file;
            }

            DocumentPanel[] panels = viewer.getDocumentPanels();
            for (DocumentPanel panel : panels) {
                File f = panel.getAssociatedFile();
                if (f != null && canonFile.equals(f.getCanonicalFile())) {
                    pdfs.add(panel.getPDF());
                    for (SidePanel sidePanel : panel.getSidePanels()) {
                        if (sidePanel instanceof PDFBackgroundTask) {
                            tasks.add((PDFBackgroundTask) sidePanel);
                        }
                    }
                }
            }

            docpanel.putClientProperty("bfo.tempfile", tempfile);
            final OutputStream out = new FileOutputStream(tempfile);
            final Exporter.ExporterTask task = exporter.getExporter(docpanel, docpanel.getPDF(), exporterComponent, out);
            task.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent event) {
                    // Run when we cancel or complete
                    if (event.getPropertyName().equals("state") && "running".equals(event.getOldValue())) {
                        if ("cancelled".equals(event.getNewValue())) {
                            try {
                                out.close();
                            } catch (IOException e) {
                                Util.displayThrowable(e, viewer);
                            }
                            if (!tempfile.delete()) {
                                tempfile.deleteOnExit();
                            }
                        }
                        if (tempfile != file) {         // If we're writing to a temp file
                            if ("completed".equals(event.getNewValue())) {
                                boolean ok = false;
                                try {
                                    // Pause all related tasks
                                    for (PDFBackgroundTask task : tasks) {
                                        task.pause();
                                    }
                                    for (PDF p : pdfs) {
                                        p.close();
                                    }
                                    if (windows) {
                                        // This is because of the following
                                        // situation on Windows:
                                        // 1. Open file A.pdf
                                        // 2. Close file
                                        // 3. Open file B.pdf save as A.pdf
                                        // If 3 happens before PDF object A
                                        // was gc'd, Windows will
                                        // refuse to delete the open file.
                                        // Ugly but necessary for now.
                                        //
                                        String[] pendinggc = Langolier.list();
                                        String test = "closer:" + file.getPath();
                                        for (int i=0;i<pendinggc.length;i++) {
                                            if (pendinggc[i].equals(test)) {
                                                System.gc();
                                                break;
                                            }
                                        }
                                    }
                                    boolean copy = false;
                                    Boolean deleted = null, exists = null, renamed = null;      // for debug
                                    if (alwaysCopy) {
                                        copy = true;
                                    } else if (windows && (exists=file.exists()) && !(deleted=file.delete())) {
                                        copy = true;
                                        Util.LOGGER.warning("SV1", "Unable to delete \""+file+"\" during save, inefficiently copying");
                                    } else if (!(renamed=tempfile.renameTo(file))) {
                                        copy = true;
                                        Util.LOGGER.warning("org.faceless.pdf2", "SV1", "Unable to rename to \""+file+"\" during save, inefficiently copying");
                                    }
                                    if (copy) {
                                        // Just copy the contents
                                        InputStream tin = null;
                                        OutputStream fout = null;
                                        try {
                                            tin = new BufferedInputStream(new FileInputStream(tempfile));
                                            fout = new BufferedOutputStream(new FileOutputStream(file));
                                            byte[] buf = new byte[8192];
                                            for (int len = tin.read(buf); len >= 0; len = tin.read(buf)) {
                                                fout.write(buf, 0, len);
                                            }
                                        } catch (IOException e) {
                                            Util.displayThrowable(e, viewer);
                                            docpanel.setDirty(true); // Save did not succeed
                                        } finally {
                                            if (fout != null) try { fout.close(); } catch (IOException e) {}
                                            if (tin != null) try { tin.close(); } catch (IOException e) {}
                                        }
                                    }
                                    ok = true;
                                } catch (InterruptedException e) {
                                    Util.displayThrowable(e, viewer);
                                } finally {
                                    if (tempfile.exists() && !tempfile.delete()) {
                                        tempfile.deleteOnExit();
                                        // Truncate it as well
                                        try {
                                            new FileOutputStream(tempfile).close();
                                        } catch (IOException e) { }
                                    } else {
                                        docpanel.putClientProperty("bfo.tempfile", null);
                                    }
                                    // Unpause all related tasks
                                    for (PDFBackgroundTask task : tasks) {
                                        task.unpause();
                                    }
                                    docpanel.raiseDocumentPanelEvent(DocumentPanelEvent.createStateChanged(docpanel, ok ? "save.completed" : "save.failed"));
                                }
                            }
                        }
                        if (exporter instanceof PDFExporter && "completed".equals(event.getNewValue())) {
                            docpanel.setAssociatedFile(file);
                            docpanel.setWindowTitle(file.getName());
                        }
                    }
                }
            });
            task.start(viewer, Util.getUIString("PDFViewer.SavingFile", file.toString()));
            return true;
        } catch (IOException e) {
            Util.displayThrowable(e, viewer);
            return false;
        }
    }

    /** 
     * Extends the abomination that is JFileChooser to allow an options panel in the main dialog.
     */
    protected static class SaveFileChooser extends JFileChooser {

        // Some notes from testing - note Nimbus/Metal, Aqua and Windows all behave differently.

        // Aqua:
        // * Filter will always show all files, those that don't match are greyed out but are still selectable
        // * SaveDialog will grey out all files, but they're still selectable. Use a Custom dialog to prevent this
        // * With Save Dialog, directories are greyed out regardless of filter and "Save" cannot be selected. Double click will traverse in.
        // * With Custom Dialog, directories are not greyed out if filter allows thenm and "Save" or double click will traverse into it
        // * SelectedFile property is not changed when text field is edited, only when Save button is clicked.
        // * Calling setSelectedFile will set the filename but will not highlight the file if it exists.
        // 
        // Nimbus:
        // * Filter will only show selectable files, and this includes directories. Selecting "Save" on a directory
        //   will traverse into it.
        // * New Folder icon will create a folder called "NewFolder" immediately.
        // * Selecting a folder with custom dialog will cause SelectedFile->null, although text field is unchanged.
        // * SelectedFile property is not changed when text field is edited, only when Save button is clicked.
        // * Calling setSelectedFile will set the filename but will not highlight the file if it exists.
        // 
        // Windows:
        // * Filter will only show selectable files, and this includes directories. Selecting "Save" on a directory
        //   will traverse into it.
        // * Selecting a folder will change Save button text to Open,
        // * Selecting a folder with custom dialog will cause SelectedFile->null, although text field is unchanged.
        // * SelectedFile property is not changed when text field is edited, only when Save button is clicked.
        // * Calling setSelectedFile will set the filename but will not highlight the file if it exists.
        // * Initial filter is not set automatically to the first choosable option - this must be done manually.
        //
        // To sum up:
        // * Directories must be selectable if user is to change directory
        // * SelectedFile cannot be assumed to be set to anything useful until after the Save button
        // * Really, filter should be independent of file-format
        // * Must use CustomDialog instead of SaveDialog for Aqua interface to make sense. No other side-effects
        // * There is some voodoo regarding approveSelection - attempts to put this class into our own dialog
        //   with our own selection action failed, due to the SelectedFile not being set. So we have to use
        //   their dialogs

        private Map<FileFilter,Exporter> filters;
        private final JPanel jpanel;
        private final DocumentPanel docpanel;
        private final boolean overwriteprompt;
        private final JTextField filenamefield;
        private boolean panelpopup;
        private Exporter exporter;
        private JComponent accessory;

        /**
         * @param file the initial file
         * @param docpanel the DocumentPanel this relates to
         * @param overwriteprompt if true, user will be prompted when attempting to overwrite a file
         */
        protected SaveFileChooser(File file, DocumentPanel docpanel, List<Exporter> exporters, boolean overwriteprompt) {
            super(file);
            this.docpanel = docpanel;
            this.overwriteprompt = overwriteprompt;
            this.jpanel = new JPanel(new BorderLayout());
            filenamefield = Util.getJFileChooserFileName(this);
            // Next line had "Required otherwise user can't traverse directories"
            // But testing in 20220713 shows that's not the case in Nimbus/Aque/Windows
            //setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);   // Required otherwise user can't traverse directories...
            setMultiSelectionEnabled(false);

            if (!file.isDirectory()) {
                ensureFileIsVisible(file);
                setSelectedFile(file);
            } else {
                setCurrentDirectory(file);
            }

            FileFilter initialfilter = null;
            PDFViewer viewer = docpanel.getViewer();
            ViewerFeature[] features = viewer.getFeatures();
            for (Exporter exporter : exporters) {
                FileFilter filter = exporter.getFileFilter();
                addChoosableFileFilter(filter);
                filters.put(filter, exporter);
                if (initialfilter == null) {
                    initialfilter = filter;
                }
            }

            // Ideally FileFilter and format would be different, because you might want to save a
            // PDF with an extension of ".tif" and be able to browser those files. We don't do this
            // currently for simplicity - customizing JFileChooser is a nightmare - so the FileFilter
            // is required to determine which exporter to use.  
            // In the case where only one exporter is available, we can let the user browse all files.
            // 
            // Also, Windows L&F requires an initial filter to be set, even if it's AcceptAll
            //
            if (filters.size() == 1) {
                setAcceptAllFileFilterUsed(true);
                setFileFilter(initialfilter);                           // To ensure exporter & exporterComponent are set
                setFileFilter(getAcceptAllFileFilter());
            } else {
                setFileFilter(initialfilter);
            }
        }

        @Override public void setFileFilter(FileFilter filter) {
            if (filters == null) {
                this.filters = new LinkedHashMap<FileFilter,Exporter>();
            }
            File file = getSelectedFile();
            super.setFileFilter(filter);
            Exporter exporter = filters.get(filter);
            if (exporter != null) {
                String oldsuffix = this.exporter == null ? null : this.exporter.getFileSuffix();
                String filename = null;
                if (filenamefield != null) {
                    filename = filenamefield.getText();
                } else if (file != null) {
                    filename = file.getName();
                }
                if (filename != null && oldsuffix != null) {
                    if (filename.endsWith("." + oldsuffix)) {
                        filename = filename.substring(0, filename.length() - oldsuffix.length()) + exporter.getFileSuffix();
                        setSelectedFile(new File(file.getParentFile(), filename));
                    }
                }
                this.exporter = exporter;

                JComponent newaccessory = exporter.getComponent(docpanel, file);
                if (accessory != null && newaccessory == null && panelpopup) {
                    Util.patchJFileChooser(this, jpanel, false);                        // Here be dragons
                }
                if (accessory == null && newaccessory != null) {
                    panelpopup = !Util.patchJFileChooser(this, jpanel, true);
                }
                accessory = newaccessory;
                jpanel.removeAll();
                if (accessory != null) {
                    jpanel.add(accessory);
                }
                JDialog dialog = (JDialog)SwingUtilities.getAncestorOfClass(JDialog.class, jpanel);
                if (dialog != null) {
                    jpanel.revalidate();
                    dialog.revalidate();
                    dialog.pack();
                }
            }
        }

        @Override public void approveSelection() {
            File file = getSelectedFile();
            String msg = null;
            if (file == null) {
                return;
            }

            if (file.exists()) {
                if (!file.canWrite() || file.isDirectory()) {
                    msg = Util.getUIString("PDFViewer.ReadOnly", file.getName());
                } else if (overwriteprompt) {
                    String prompt = UIManager.getString("PDFViewer.OverwriteFile");
                    if (JOptionPane.showConfirmDialog(docpanel.getViewer(), prompt, prompt, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
            }
            try {
                if (file.toPath().getFileSystem().isReadOnly()) {                      // Java 7
                    msg = Util.getUIString("PDFViewer.ReadOnly", file.getName());
                }
            } catch (Throwable e) { }

            if (msg == null && accessory != null && !panelpopup) {
                msg = exporter.validateComponent(accessory);
            }
            if (msg != null) {
                JOptionPane.showMessageDialog(docpanel.getViewer(), msg, UIManager.getString("PDFViewer.Error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            super.approveSelection();
        }

        public Exporter getExporter() {
            return exporter;
        }

        public JComponent getExporterComponent() {
            return accessory;
        }

        public boolean isExporterPopupRequired() {
            return panelpopup && accessory != null;
        }

    }

}
