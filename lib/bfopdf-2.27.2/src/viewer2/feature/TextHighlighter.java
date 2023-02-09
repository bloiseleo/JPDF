// $Id: TextHighlighter.java 41473 2021-10-27 11:11:07Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.PropertyParser;
import org.faceless.pdf2.*;
import javax.swing.*;
import java.awt.geom.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.lang.ref.*;

/**
 * <p>
 * A feature that allows the highlighting of text in the viewer. This takes a fairly
 * simplistic approach, allowing words to be matched manually via the {@link #addWord} method,
 * or to a regular expression via the {@link #setPattern} method.
 * A highlight will then be applied whenever a page containing that word is displayed.
 * </p><p>
 * The {@link SearchPanel} has similar functionality but takes a more integrated approach,
 * using the {@link TextTool} to highlight text. Although both {@link TextTool} and this
 * class can be used to highlight text matching a regular expression, this class does not
 * allow you to copy the selected text to the clipboard. This difference means the selection
 * can be done page by page, whereas TextTool has to extract all the text from the document
 * first.
 * </p><p>
 * Given the non-interactive nature of this class it's likely that this class will be used
 * in a more standalone environment. Here's an example of how to do this:
 * </p>
 * <pre class="brush:java">
 *   Pattern pattern = Pattern.compile("(apples|oranges|[a-z]*berries)");
 *   TextHighlighter hl = new TextHighlighter();
 *   hl.setPattern(pattern);
 *   final Collection&lt;ViewerFeature&gt; features = new ArrayList(ViewerFeature.getAllFeatures());
 *   features.add(hl);
 *   SwingUtilities.invokeLater(new Runnable() {
 *     void run() {
 *       PDFViewer viewer = PDFViewer.newPDFViewer(features);
 *       viewer.loadPDF(new File("CropReport.pdf"));
 *     }
 *   });
 * }
 * </pre>
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>highlightColor</th><td>A 32-bit color value, eg 0x80FF0000 (for translucent red)</td></tr>
 * <tr><th>highlightType</th><td>One of <code>block</code>, <code>underline</code>, <code>outline</code>, <code>doubleunderline</code>, <code>strikeout</code> or <code>doublestrikeout</code></td></tr>
 * <tr><th>highlightMargin</th><td>A floating point value &gt;= 0</td></tr>
 * <tr><th>highlightMargin</th><td>A floating point value &gt;= 0</td></tr>
 * </table>
 * </div>
 *
 * <p>
 * Here's an example showing how to set those attributes:
 * </p>
 * <pre class="brush:plain">
 * java -Dorg.faceless.pdf2.viewer2.TextHighlighter.word0=apples \
 *      -Dorg.faceless.pdf2.viewer2.TextHighlighter.word1=oranges \
 *      -jar bfopdf.jar
 * </pre>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">TextHighlighter</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8.1
 * @see SearchPanel
 * @see SearchField
 * @see TextTool
 */
public class TextHighlighter extends ViewerFeature implements DocumentPanelListener, PagePanelListener {

    private WeakReference<DocumentViewport> viewportRef;
    private Map<PageExtractor,SoftReference<TextSelection.RangeList>> rangelists;
    private String[] words;
    private Pattern pattern;
    private Paint color;
    private Stroke stroke;
    private int type;
    private float margin;

    /**
     * Create a new TextHighlighter
     */
    public TextHighlighter() {
        super("TextHighlighter");
        setHighlightType(TextSelection.TYPE_BLOCK, null, null, 0);
        rangelists = new WeakHashMap<PageExtractor,SoftReference<TextSelection.RangeList>>();
    }

    /**
     * Set the Regular Expression used to determine which words to highlight.
     * Calling this method will cause any words added by the {@link #addWord}
     * method to be ignored - you should call one or the other, not both.
     * @param pattern the Pattern to match, or <code>null</code> to match
     * whataver words have been added via {@link #addWord}
     * @since 2.11
     */
    public synchronized void setPattern(Pattern pattern) {
        this.pattern = pattern;
        rangelists.clear();
        repaintTextSelections();
    }

    /**
     * Add a word to highlight to this TextHighlighter. For more than a couple
     * of words it's likely to be more efficient to call {@link #setPattern}, and
     * this is the approach we recommend for new implementations.
     * @param word the new word to highlight if found
     */
    public synchronized void addWord(String word) {
        if (word!=null && word.length()>0 && (words==null || !Arrays.asList(words).contains(word))) {
            String[] t = new String[words==null ? 1 : words.length+1];
            if (words!=null) {
                System.arraycopy(words, 0, t, 0, words.length);
            }
            t[t.length-1] = word;
            words = t;
            rangelists.clear();
            repaintTextSelections();
        }
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        if (color == null) {
            color = UIManager.getColor("PDFViewer.Feature.TextHighlighter.color");
        }
        color = PropertyParser.getPaint(getFeatureProperty(viewer, "highlightColor"), color);
        type = PropertyParser.getHighlightType(getFeatureProperty(viewer, "highlightType"), type);
        stroke = PropertyParser.getStroke(getFeatureProperty(viewer, "highlightStroke"), stroke);
        margin = PropertyParser.getMargin(getFeatureProperty(viewer, "highlightMargin"), margin);
        String pattern = getFeatureProperty(viewer, "pattern");
        if (pattern!=null) {
            setPattern(Pattern.compile(pattern));
        } else {
            int i=0;
            while ((pattern=getFeatureProperty(viewer, "word"+(i++)))!=null) {
                addWord(pattern);
            }
        }
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        if (event.getType()=="viewportChanged") {
            DocumentViewport viewport = getViewport();
            if (viewport!=null) {
                viewport.removePagePanelListener(this);
            }
            viewport = event.getDocumentPanel().getViewport();
            if (viewport!=null) {
                viewport.addPagePanelListener(this);
                viewportRef = new WeakReference<DocumentViewport>(viewport);
            } else {
                viewportRef = null;
            }
        }
    }

    private DocumentViewport getViewport() {
        return viewportRef==null ? null : (DocumentViewport)viewportRef.get();
    }

    /**
     * Set the type of Highlight to use.
     * @param type one of {@link TextTool#TYPE_BLOCK}, {@link TextTool#TYPE_OUTLINE},
     * {@link TextTool#TYPE_UNDERLINE}, {@link TextTool#TYPE_DOUBLEUNDERLINE}, {@link TextTool#TYPE_STRIKEOUT}
     * or {@link TextTool#TYPE_DOUBLESTRIKEOUT}
     * @param paint the paint to use - must not be null. For {@link TextTool#TYPE_BLOCK} highlights
     * this color will typically be translucent.
     * @param stroke the stroke to use for outline or underlining, or <code>null</code> to choose
     * automatically. Not used with {@link TextTool#TYPE_BLOCK}.
     * @param margin how many points around the text to use as a margin.
     */
    public void setHighlightType(int type, Paint paint, Stroke stroke, float margin) {
        this.type = type;
        this.color = paint;
        this.stroke = stroke;
        this.margin = margin;
        repaintTextSelections();
    }

    public void pageUpdated(PagePanelEvent event) {
        if (event.getType()=="redrawing") {
            if (pattern!=null && words!=null) {
                event.getPagePanel().setExtractText(true);
            }
        } else if (event.getType()=="redrawn") {
            getOrCreateTextSelection(event.getPagePanel()).repaint();
        }
    }

    private void repaintTextSelections() {
        DocumentViewport viewport = getViewport();
        if (viewport != null) {
            for (Iterator<PagePanel> i = viewport.getPagePanels().iterator();i.hasNext();) {
                PagePanel pagepanel = i.next();
                if (pagepanel.getPage() != null) {
                    getOrCreateTextSelection(pagepanel).repaint();
                }
            }
        }
    }

    private TextSelection getOrCreateTextSelection(PagePanel pagepanel) {
        TextSelection selection = null;
        for (int i=0;selection==null && i<pagepanel.getComponentCount();i++) {
            Component c = pagepanel.getComponent(i);
            if (c instanceof TextHighlighterTextSelection && ((TextHighlighterTextSelection)c).getTextHighlighter() == this) {
                selection = (TextSelection)c;
            }
        }
        if (selection == null) {
            selection = new TextHighlighterTextSelection(pagepanel, type, color, stroke, margin);
            pagepanel.add(selection);
        }
        return selection;
    }

    private class TextHighlighterTextSelection extends TextSelection {
        TextHighlighterTextSelection(PagePanel panel, int type, Paint color, Stroke stroke, float margin) {
            super(panel, type, color, stroke, margin);
        }

        TextHighlighter getTextHighlighter() {
            return TextHighlighter.this;
        }

        protected RangeList getRangeList() {
            try {
                PageExtractor extractor = pagepanel.getPageExtractor();
                if ((words==null && pattern==null) || extractor==null) {
                    return RangeList.EMPTY_RANGELIST;
                } else {
                    RangeList list;
                    synchronized(rangelists) {
                        SoftReference<RangeList> listref = rangelists.get(extractor);
                        list = listref == null ? null : listref.get();
                        if (list == null) {
                            Collection<PageExtractor.Text> matching;
                            if (pattern != null) {
                                matching = extractor.getMatchingText(pattern);
                            } else {
                                matching = extractor.getMatchingText(words);
                            }
                            if (matching.isEmpty()) {
                                list = RangeList.EMPTY_RANGELIST;
                            } else {
                                ArrayList<Range> l = new ArrayList<Range>(matching.size());
                                for (Iterator<PageExtractor.Text> i = matching.iterator();i.hasNext();) {
                                    l.add(Range.createRange(i.next()));
                                }
                                list = new RangeList(pagepanel.getDocumentPanel(), l, PageExtractor.NATURALORDER);
                            }
                            rangelists.put(extractor, new SoftReference<RangeList>(list));
                        }
                    }
                    return list;
                }
            } catch (Exception e) {
                return RangeList.EMPTY_RANGELIST;
            }
        }
    }
}
