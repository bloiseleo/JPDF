// $Id: SearchPanel.java 39833 2021-04-23 14:43:25Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import java.awt.*;
import java.beans.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.text.Bidi;
import java.util.List;
import java.util.regex.Pattern;
import java.awt.geom.*;
import java.awt.event.*;

/**
 * Creates a {@link SidePanel} that displays search results. This panel may be used instead
 * of or as well as the {@link SearchField} - if no SearchField is found the panel will have
 * a field placed at the top automatically (see {@link #setCreateSearchField}).
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>createSearchField</th><td><code>true</code> or <code>false</code> for {@link #setCreateSearchField}</td></tr>
 * <tr><th>regex</th><td><code>true</code> or <code>false</code> for {@link #setUseRegex}</td></tr>
 * </table>
 * </div>
 *
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">SearchPanel</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class SearchPanel extends SidePanelFactory {

    private boolean createfield;
    private TextTool texttool;
    private boolean regex;

    /**
     * Create a new SearchPanel
     */
    public SearchPanel() {
        super("SearchPanel");
        createfield = true;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        String val = getFeatureProperty(viewer, "createSearchField");
        if (val!=null) {
            setCreateSearchField("true".equals(val));
        }
        if ("true".equals(getFeatureProperty(viewer, "regex"))) {
            setUseRegex(true);
        }
    }

    /**
     * Set whether to create a {@link Field} at the top of the {@link Results} panel.
     * By default this is the case, although if a {@link SearchField} is included in the
     * feature list this is turned off.
     * @param createfield whether to create a Field object or not
     */
    public void setCreateSearchField(boolean createfield) {
        this.createfield = createfield;
    }

    /**
     * Set whether searches run through the Search Field created by this class
     * use a regular-expression instead of a text value. The default is false.
     * @param regex whether to use a regular expression or not
     * @since 2.18.3
     */
    public void setUseRegex(boolean regex) {
        this.regex = regex;
    }

    /**
     * Set the {@link TextTool} object that will display the selection when an item
     * in this panel is clicked on. A value of <code>null</code> (the default) will cause
     * the panel to use the first TextTool object it finds in the viewer, or if none
     * exists a new one will be created.
     * @param selection the selection object that will be used to display the selected results
     */
    public void setTextTool(TextTool selection) {
        this.texttool = selection;
    }

    public SidePanel createSidePanel() {
        final Results results = new Results(regex);
        if (texttool!=null) {
            results.setTextTool(texttool);
        }
        if (createfield) {
            final Field field = new Field();
            results.add(field, BorderLayout.NORTH);
            results.addChangeListener(field);
            field.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    results.search(field.getText());
                }
            });
            field.getKeymap().addActionForKeyStroke(KeyStroke.getKeyStroke("ESCAPE"), new AbstractAction() {
                public void actionPerformed(ActionEvent event) {
                    field.setText("");
                    results.clearResults();
                    results.cancel();
                }
            });
        }
        return results;
    }

    /**
     * This class is the SidePanel returned by the parent factory, and represents
     * the search results panel. It may be created by the parent factory, or may
     * be instantiated on it's own.
     */
    public static class Results extends JPanel implements SidePanel, DocumentPanelListener, PropertyChangeListener  {
        private boolean regex;
        private TextTool texttool;
        private DocumentPanel docpanel;
        private List<PageExtractor> extractors;
        private volatile SearchThread searchthread;
        private volatile ExtractThread extractthread;
        private DefaultListModel<Object> resultlist;
        private Collection<ChangeListener> listeners;
        private float progress;
        private Icon icon;

        /**
         * Create a new Results object.
         */
        public Results() {
            this(false); // fallback for when there is no SearchPanel feature
        }

        Results(boolean regex) {
            setLayout(new BorderLayout());
            this.regex = regex;
            this.listeners = new ArrayList<ChangeListener>(1);
            this.resultlist = new DefaultListModel<Object>();
            this.icon = UIManager.getIcon("PDFViewer.Feature.SearchPanel.icon");

            final JList<Object> results = new JList<Object>(resultlist);
            results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            results.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    if (!e.getValueIsAdjusting()) {
                        if (results.getSelectedIndex() != 0) {
                            Result result = (Result)results.getSelectedValue();
                            if (result!=null) {
                                texttool.select(result.result);
                            }
                        }
                    }
                }
            });
            // Add a MouseListener as well as a ListSelectionListener so we can click on
            // an already selected index and have it act the same way - scroll to the correct
            // location on the page.
            results.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    int i = results.locationToIndex(e.getPoint());
                    if (i == results.getSelectedIndex() && i >= 0 && results.getCellBounds(i, i).contains(e.getPoint())) {
                        Object result = results.getModel().getElementAt(i);
                        if (result instanceof Result) {
                            results.setSelectedIndex(i);
                            texttool.select(((Result)result).result);
                        }
                    }
                }
            });
            results.setCellRenderer(new ListCellRenderer<Object>() {
                public Component getListCellRendererComponent(JList<? extends Object> list, Object value, int index, boolean selected, boolean focused) {
                    if (index == 0) {
                        JLabel label = new JLabel(value.toString());
                        label.setForeground(Color.gray);
                        return label;
                    } else {
                        Result r = (Result)value;
                        r.setComponentOrientation(list.getComponentOrientation());
                        r.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                        r.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
                        r.setEnabled(list.isEnabled());
                        r.setFont(list.getFont());
                        r.setBorder(focused ? UIManager.getBorder("List.focusCellHighlightBorder") : BorderFactory.createEmptyBorder(1, 1, 1, 1));
                        return r;
                    }
                }
            });
            add(new JScrollPane(results));
        }

        /**
         * Return the name of this tab - "Find"
         */
        public String getName() {
            return "Find";
        }

        public Icon getIcon() {
            return icon;
        }


        /**
         * Set the {@link TextTool} object this object will highlight its results
         * on.
         */
        public void setTextTool(TextTool selection) {
            this.texttool = selection;
        }

        /**
         * Add the specified {@link org.faceless.pdf2.PageExtractor.Text} to the list of results
         */
        public void addResult(final PageExtractor.Text text) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (texttool.getViewer() != null && !texttool.getViewer().isClosing()) {
                        if (resultlist.size() == 0) {
                            resultlist.addElement(Util.getUIString("PDFViewer.nResults", "1")+"\u2026");
                        } else {
                            resultlist.set(0, Util.getUIString("PDFViewer.nResults", Integer.toString(resultlist.size()))+"\u2026");
                        }
                        resultlist.addElement(new Result(text, docpanel));
                    }
                }
            });
        }

        /**
         * Clear the list of results
         */
        public void clearResults() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    resultlist.clear();
                    texttool.clear();
                }
            });
        }

        /**
         * Add a {@link ChangeListener} to this panel. A {@link ChangeEvent}
         * will be raised when the status of the search is updated.
         */
        public void addChangeListener(ChangeListener listener) {
            listeners.add(listener);
        }

        /**
         * Remove a {@link ChangeListener} from this panel.
         */
        public void removeChangeListener(ChangeListener listener) {
            listeners.remove(listener);
        }

        /**
         * Search for an item of text in the PDF currently displayed in this {@link DocumentPanel}.
         * If a search is currently running it is interrupted, and any previously found results
         * are cleared.
         * @param text the text to search for
         */
        public void search(String text) {
            cancel();
            clearResults();
            docpanel.setSelectedSidePanel(this);
            extractthread = new ExtractThread(extractors);
            searchthread = new SearchThread(text, regex, extractors);
            extractthread.start();
            searchthread.start();
        }

        /**
         * Cancel any currently running search
         */
        public void cancel() {
            if (extractthread!=null) {
                extractthread.cancel();
                extractthread = null;
            }
            if (searchthread!=null) {
                searchthread.cancel();
                searchthread = null;
            }
        }

        /**
         * Return the progress of the current search.
         * The returned value is 0 if no search is currently running (because it's
         * been cancelled, completed or not yet started), or a value between 0
         * and 1.
         */
        public float getSearchProgress() {
            return progress;
        }

        /**
         * Index the page. The default implementation of this method is a no-op,
         * but theoretically we could create an index with Lucene here for access
         * in {@link #mayContain}
         * @param pagenumber the page number
         * @param extractor the extractor for that page
         */
        protected synchronized void indexPage(int pagenumber, PageExtractor extractor) {
        }

        /**
         * Return whether the specified text may be found on the specified pagenumber.
         * If this method returns true, the page will have {@link PageExtractor#getMatchingText}
         * run on it to find and (possibly) return matching values. Although this method
         * could theoretically use some sort of index created in {@link #indexPage} to narrow down
         * which pages to search, but the default implementation always returns true.
         * @param pagenumber the pagenumber to search
         * @param value the text to search for
         */
        protected boolean mayContain(int pagenumber, String value) {
            return true;
        }

        public void setDocumentPanel(DocumentPanel docpanel) {
            if (this.docpanel != docpanel) {
                cancel();
                clearResults();
                if (docpanel == null) {
                    this.docpanel.removeDocumentPanelListener(this);
                    if (this.docpanel.getPDF() != null) {
                        this.docpanel.getPDF().removePropertyChangeListener(this);
                    }
                } else {
                    docpanel.addDocumentPanelListener(this);
                }
                if (docpanel == null || docpanel.getPDF() == null) {
                    this.extractors = null;
                } else {
                    this.extractors = Arrays.<PageExtractor>asList(new PageExtractor[docpanel.getNumberOfPages()]);
                    if (texttool == null) {
                        if (docpanel.getViewer()!=null) {
                            texttool = docpanel.getViewer().getFeature(TextTool.class);
                        }
                        if (texttool == null) {
                            texttool = new TextTool();
                        }
                        setTextTool(texttool);
                    }
                    docpanel.getPDF().addPropertyChangeListener(this);
                }
                this.docpanel = docpanel;
            }
        }

        public void documentUpdated(DocumentPanelEvent event) {
            if (event.getType() == "loaded") {
                event.getDocumentPanel().getPDF().addPropertyChangeListener(this);
            } else if (event.getType() == "closing") {
                event.getDocumentPanel().getPDF().removePropertyChangeListener(this);
            }
        }

        public void propertyChange(PropertyChangeEvent event) {
            if (event.getPropertyName().equals("pages")) {
                // Lack of thread safety OK here.
                cancel();
                clearResults();
                this.extractors = Arrays.<PageExtractor>asList(new PageExtractor[docpanel.getNumberOfPages()]);
            }
        }

        public void panelVisible() {
        }

        public void panelHidden() {
            if (searchthread!=null) {
                searchthread.cancel();
                searchthread = null;
            }
        }

        private void setProgress(float progress) {
            this.progress = progress;
            ChangeEvent event = new ChangeEvent(this);
            for (Iterator<ChangeListener> i = listeners.iterator();i.hasNext();) {
                i.next().stateChanged(event);
            }
        }

        //------------------------------------------------------------------------------------

        /**
         * This class represents a single result. It's really just a way of rendering a PageExtractor.Text
         * in the results list. For simplicity we do this at a very low level - Swing was not co-operating.
         */
        private static class Result extends JPanel {
            final DocumentPanel docpanel;
            final PageExtractor.Text result;
            private String prefix = null, suffix = null, content = null;        // in logical order
            private int width;

            Result(PageExtractor.Text text, DocumentPanel docpanel) {
                this.result = text;
                this.docpanel = docpanel;
                this.content = visualToLogicalOrder(result.getText());
                setOpaque(true);
            }

            public Dimension getPreferredSize() {
                FontMetrics rfm = getFontMetrics(getFont().deriveFont(Font.BOLD));
                FontMetrics nfm = getFontMetrics(getFont().deriveFont(Font.ITALIC));
                int len = SwingUtilities.computeStringWidth(rfm, result.getText());
                len += SwingUtilities.computeStringWidth(nfm, " p"+docpanel.getNumberOfPages());
                return new Dimension(len, rfm.getHeight());
            }

            /**
             * The display of the results is divided into four sections - prefix, result, suffix and
             * pagenumber. The result and pagenumber are always displayed, and the prefix/suffix
             * consist of enough text before and after the result to fill the box horizontally.
             */
            public void paintComponent(Graphics gg) {
                Graphics2D g = (Graphics2D)gg;
                super.paintComponent(g);
                if (width != getWidth()) {        // Width has been reset, recreate prefix/suffix.
                    width = getWidth();
                    prefix = suffix = null;
                }
                final Font prefixfont = getFont();
                final Font resultfont = getFont().deriveFont(Font.BOLD);
                final Font numberfont = getFont().deriveFont(Font.ITALIC);
                final int fontsize = getFont().getSize();
                FontMetrics pfm = getFontMetrics(prefixfont);
                FontMetrics rfm = getFontMetrics(resultfont);
                FontMetrics nfm = getFontMetrics(numberfont);
                String pagenum = " p"+(docpanel.getPageNumber(result.getPage()) + 1);

                int resultlen = SwingUtilities.computeStringWidth(rfm, result.getText());
                int pagenumlen = SwingUtilities.computeStringWidth(nfm, pagenum) + 4;
                int prefixlen = (width-resultlen-pagenumlen) / 2;
                if (prefix == null) {
                    prefix = "";
                    PageExtractor.Text t = result.getRowPrevious();
                    String v = t == null ? "" : t.getText();
                    while (v.length() > 0 && SwingUtilities.computeStringWidth(pfm, "\u2026\u2026" + prefix) < prefixlen) {
                        prefix = v.charAt(v.length()-1) + prefix;
                        v = v.substring(0, v.length()-1);
                        if (v.length() == 0) {
                            t = t.getRowPrevious();
                            if (t != null) {
                                v = t.getText();
                            }
                        }
                    }
                    if (t != null) {
                        prefix = "\u2026" + prefix;
                    }
                    prefix = visualToLogicalOrder(prefix);
                }
                if (suffix == null) {
                    suffix = "";
                    PageExtractor.Text t = result.getRowNext();
                    String v = t == null ? "" : t.getText();
                    while (v.length() > 0 && SwingUtilities.computeStringWidth(pfm, suffix + "\u2026\u2026") < prefixlen) {
                        suffix += v.charAt(0);
                        v = v.substring(1);
                        if (v.length() == 0) {
                            t = t.getRowNext();
                            if (t != null) {
                                v = t.getText();
                            }
                        }
                    }
                    if (t != null) {
                        suffix = suffix + "\u2026";
                    }
                    suffix = visualToLogicalOrder(suffix);
                }

                int x = 0, y = fontsize;
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(getForeground());

                g.setFont(prefixfont);
                g.drawString(prefix, x, y);
                x += SwingUtilities.computeStringWidth(pfm, prefix);

                g.setFont(resultfont);
                g.drawString(content, x, y);
                x += resultlen;

                g.setFont(prefixfont);
                g.drawString(suffix, x, y);

                g.setFont(numberfont);
                g.drawString(pagenum, width-pagenumlen, fontsize);
            }
        }

        /**
         * Deep magic that takes a String ordered visually, as we'd get out of the
         * TextExtractor, and reorder it logically, as we pass it into drawString.
         * Done with reflection as sun.font not always available
         */
        @SuppressWarnings("unchecked")
        private static String visualToLogicalOrder(String in) {
            try {
                Bidi bidi = new Bidi(in, java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
                Class<?> cl = Class.forName("sun.font.BidiUtils");
                byte[] levels = (byte[])cl.getMethod("getLevels", Bidi.class).invoke(null, bidi);
                int[] order = (int[])cl.getMethod("createVisualToLogicalMap", byte[].class).invoke(null, levels);
                char[] c = new char[order.length];
                for (int i=0;i<c.length;i++) {
                    c[i] = in.charAt(order[i]);
                }
                return new String(c);
            } catch (Throwable e) {
                return in;       // give up
            }
        }

        /**
         * This thread iterates through all the PageExtractors in the "extractors" list,
         * checking each to see if it mayContain the text we're searching for - if it
         * does it calls addResult to add it to the list. If it hits an extractor
         * that hasn't been created yet it will wait() until the ExtactThread notify()'s
         * it that it's been created.
         */
        private class SearchThread extends Thread {
            private String value;
            private boolean regex;
            private List<PageExtractor> extractors;
            private volatile boolean interrupt;

            SearchThread(String value, boolean regex, List<PageExtractor> extractors) {
                super("BFO-SearchThread");
                this.value = value;
                this.regex = regex;
                this.extractors = extractors;
                setDaemon(true);
                setName("SearchSearcher");
            }

            void cancel() {
                interrupt = true;
                synchronized(extractors) {
                    extractors.notifyAll();
                }
            }

            public void run() {
                for (int i=0;!interrupt && i<extractors.size();) {
                    PageExtractor ex = null;
                    synchronized(extractors) {
                        // Wait until extractors[i] is set
                        // Theoretical hole here - we could keep looping forever
                        // if ExtractThread was cancelled and this one wasn't.
                        // In practice program logic prevents that from happening
                        while (!interrupt && (ex=extractors.get(i)) == null) {
                            try {
                                extractors.wait();
                            } catch (InterruptedException e) { }
                        }
                    }
                    if (!interrupt && mayContain(i, value)) {
                        Collection<PageExtractor.Text> c;
                        if (regex) {
                            Pattern pattern = Pattern.compile(value);
                            c = ex.getMatchingText(pattern);
                        } else {
                            c = ex.getMatchingText(new String[] { value }, true);
                        }
                        for (Iterator<PageExtractor.Text> j = c.iterator();!interrupt && j.hasNext();) {
                            PageExtractor.Text text =j.next();
                            if (!interrupt) {
                                addResult(text);
                            }
                        }
                        i++;
                        setProgress((float)i / extractors.size());
                    }
                }
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (resultlist.size()==0) {
                            resultlist.addElement(Util.getUIString("PDFViewer.nResults", "0"));
                        } else {
                            resultlist.set(0, Util.getUIString("PDFViewer.nResults", Integer.toString(resultlist.size()-1)));
                        }
                    }
                });
                setProgress(0);
            }
        }

        /**
         * This thread goes through all the pages in the specified PDFParser,
         * getting a PageExtractor for each, ensuring the extraction is run
         * and adding it to the specified list of Extractors. Once a new
         * extractor is added the "extractors" list is notify()ed - the SearchThread
         * will be waiting on it.
         */
        private class ExtractThread extends Thread {
            private List<PageExtractor> extractors;
            private volatile boolean interrupt;

            ExtractThread(List<PageExtractor> extractors) {
                this.extractors = extractors;
                setDaemon(true);
                setName("SearchExtractor");
            }

            void cancel() {
                interrupt = true;
            }

            public void run() {
                for (int i=0;!interrupt && i<extractors.size();i++) {
                    PageExtractor extractor = docpanel.getPageExtractor(docpanel.getPage(i));
                    extractors.set(i, extractor);
                    if (!interrupt) {
                        extractors.get(i).getTextUnordered();
                    }
                    if (!interrupt) {
                        indexPage(i, extractors.get(i));
                    }
                    if (!interrupt) {
                        synchronized(extractors) {
                            extractors.notifyAll();
                        }
                    }
                }
            }
        }
    }

    /**
     * A subclass of JTextField customized for searching - it has a
     * setProgress() method to report the progress of the search,
     * and an icon in the background
     */
    public static class Field extends JTextField implements ChangeListener {
        private float progress;
        private Paint progresscolor;
        private GeneralPath icon;
        private Insets insets;

        public Field() {
            this(15);
        }

        public Field(int numchars) {
            super(numchars);
            progresscolor = UIManager.getColor("PDFViewer.Feature.SearchPanel.progress.color");
            icon = new GeneralPath();
            icon.moveTo(1,1);
            icon.lineTo(0.683f, 0.683f);
            icon.append(new Ellipse2D.Float(0, 0, 0.8f, 0.8f), false);
            insets = getInsets();
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = (int)((getWidth() - insets.left - insets.right) * progress);
            int h = getHeight() - insets.top - insets.bottom;
            ((Graphics2D)g).setPaint(progresscolor);
            g.fillRect(insets.left, insets.top, w, h);
            ((Graphics2D)g).setColor(UIManager.getColor("PDFViewer.Feature.SearchPanel.magnifier.color"));
            ((Graphics2D)g).setStroke(new BasicStroke(UIManager.getInt("PDFViewer.Feature.SearchPanel.magnifier.width")));
            double size = getFont().getSize2D() - 2;
            if (getComponentOrientation().isLeftToRight()) {
                ((Graphics2D)g).draw(icon.createTransformedShape(new AffineTransform(size, 0, 0, size, getWidth()-insets.right-size-2, insets.top+(h-size)/2)));
            } else {
                ((Graphics2D)g).draw(icon.createTransformedShape(new AffineTransform(size, 0, 0, size, insets.left+2, insets.top+(h-size)/2)));
            }
        }

        public void stateChanged(ChangeEvent event) {
            if (event.getSource() instanceof SearchPanel.Results) {
                progress = Math.max(0, Math.min(1, ((SearchPanel.Results)event.getSource()).getSearchProgress()));
                repaint();
            }
        }
    }

}
