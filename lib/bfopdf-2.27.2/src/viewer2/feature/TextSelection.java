// $Id: TextSelection.java 39939 2021-05-06 16:37:40Z mike $

package org.faceless.pdf2.viewer2.feature;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.PagePanel;
import org.faceless.pdf2.viewer2.DocumentPanel;
import java.util.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.text.AttributedString;
import org.faceless.util.AttributedStringBuilder;

/**
 * This class is a transparent panel which is laid overtop of the PagePanel. It will
 * highlight any text in the {@link RangeList} returned by {@link #getRangeList}.
 * This class is used by the {@link TextTool} and {@link TextHighlighter} classes
 * to manage text markup, and it's unlikely that customers would need to access this
 * class directly.
 */
public abstract class TextSelection extends JPanel {

    protected final PagePanel pagepanel;
    private final Paint color;
    private final Stroke stroke;
    private final float margin;
    private final int type;

    /**
     * A type of highlight which will highlight the selected
     * text using a solid block of (usually translucent) color
     */
    public static final int TYPE_BLOCK = 1;

    /**
     * A type of highlight which will highlight the selected
     * text using a single underline.
     * @since 2.11.4
     */
    public static final int TYPE_UNDERLINE = 2;

    /**
     * A type of highlight which will highlight the selected
     * text using a double underline.
     * @since 2.11.4
     */
    public static final int TYPE_DOUBLEUNDERLINE = 4;

    /**
     * A type of highlight which will outline the selected
     * text.
     * @since 2.11.4
     */
    public static final int TYPE_OUTLINE = 8;

    /**
     * A type of highlight which will strike-out the selected
     * text.
     * @since 2.11.4
     */
    public static final int TYPE_STRIKEOUT = 16;

    /**
     * A type of highlight which will strike-out the selected
     * text with a double line
     * @since 2.11.4
     */
    public static final int TYPE_DOUBLESTRIKEOUT = 32;

    public TextSelection(PagePanel panel, int type, Paint color, Stroke stroke, float margin) {
        this.pagepanel = panel;
        if (color == null) {
            color = UIManager.getColor("PDFViewer.Feature.TextHighlighter.color");
        }
        this.color = color;
        this.type = type;
        this.stroke = stroke;
        this.margin = margin;
        setOpaque(false);
        PDFPage page = panel.getPage();
        if (page == null) {
            throw new IllegalStateException("Panel has no page");
        }
        putClientProperty("pdf.rect", page.getBox("ViewBox"));
        panel.setExtractText(true);
    }

    /**
     * Return true if this panel should be treated as a GlassPane. By default
     * this is the case, in which case the cursor will not be modified just
     * because this panel is visible.
     * @since 2.11.25
     */
    public boolean isGlassPane() {
        return true;
    }

    public boolean contains(int x, int y) {
        // Yuck. http://weblogs.java.net/blog/alexfromsun/archive/2006/09/a_wellbehaved_g.html
        return !isGlassPane() && super.contains(x, y);
    }

    /**
     * Return the RangeList that will be highlighted by this TextTool
     */
    protected abstract RangeList getRangeList();

    public void paintComponent(Graphics gg) {
        super.paintComponent(gg);

        AffineTransform pagetransform = pagepanel.getPageToScreenTransform();
        RangeList rangelist = getRangeList();
        if (pagetransform == null) {
            return;
        }

        Graphics2D g = (Graphics2D)gg;
        boolean path = type != TYPE_BLOCK && type != TYPE_OUTLINE;

        for (int j=0;j<rangelist.size();j++) {
            Range range = rangelist.get(j);
            if (range.isValid()) {
                PageExtractor.Text first = range.getFirst();
                PageExtractor.Text last = range.getLast();
                AffineTransform tran = g.getTransform();
                g.transform(pagetransform);
                int firstpos = range.getFirstPosition();
                int lastpos = range.getLastPosition();
                if (first==last) {
                    drawHighlight(type, color, stroke, g, TextTool.getShape(first, firstpos, lastpos - 1, margin, path));
                } else {
                    boolean inside = false;
                    PageExtractor extractor = first.getPageExtractor();
                    Collection<? extends PageExtractor.Text> c = extractor.getText(rangelist.order);
                    for (Iterator<? extends PageExtractor.Text> i = c.iterator();i.hasNext();) {
                        PageExtractor.Text text = i.next();
                        if (text == first) {
                            inside = true;
                            drawHighlight(type, color, stroke, g, TextTool.getShape(text, firstpos, text.getTextLength()-1, margin, path));
                        } else if (text == last) {
                            inside = false;
                            drawHighlight(type, color, stroke, g, TextTool.getShape(text, 0, lastpos - 1, margin, path));
                        } else if (inside) {
                            drawHighlight(type, color, stroke, g, TextTool.getShape(text, margin, path));
                        }
                    }
                }
                g.setTransform(tran);
            }
        }
    }

    //-----------------------------------------------------------------------------------------

    /**
     * Draw a highlight of a certain type onto the specified graphics.
     *
     * @param type the shape type
     * @param color the Color
     * @param g the Graphics Object
     * @param shape for Outline or Block, any shape. For underline types,
     * a GeneralPath of the form (x1,y1, x1,y2, x2,y2, x2,y1)
     */
    public void drawHighlight(int type, Paint color, Stroke stroke, Graphics2D g, Shape shape) {
        if (shape == null) {
            return;
        }
        g.setPaint(color);
        if (type==TYPE_BLOCK) {
            g.fill(shape);
        } else if (type==TYPE_OUTLINE) {
            g.draw(shape);
        } else if (type==TYPE_UNDERLINE || type==TYPE_DOUBLEUNDERLINE || type==TYPE_STRIKEOUT || type==TYPE_DOUBLESTRIKEOUT) {
            Point2D.Float p1 = null, p2 = null;

            // Here we assume path is bl, tl, tr, br
            PathIterator i = ((GeneralPath)shape).getPathIterator(new AffineTransform());
            float[][] f = new float[4][6];
            i.currentSegment(f[0]); i.next();
            i.currentSegment(f[1]); i.next();
            i.currentSegment(f[2]); i.next();
            i.currentSegment(f[3]); i.next();

            float linewidth = (float)Math.sqrt((f[1][0]-f[0][0])*(f[1][0]-f[0][0])+(f[1][1]-f[0][1])*(f[1][1]-f[0][1])) / 20;
            if (type==TYPE_STRIKEOUT || type==TYPE_DOUBLESTRIKEOUT) {
                p1 = new Point2D.Float((f[1][0]+f[0][0])/2, (f[1][1]+f[0][1])/2);
                p2 = new Point2D.Float((f[2][0]+f[3][0])/2, (f[2][1]+f[3][1])/2);
            } else {
                p1 = new Point2D.Float(f[0][0], f[0][1]);
                p2 = new Point2D.Float(f[3][0], f[3][1]);
            }
            if (stroke==null) {
                stroke = new BasicStroke(linewidth);
            }
            g.setStroke(stroke);

            Line2D.Float line = new Line2D.Float(p1, p2);
            g.draw(line);
            if (type==TYPE_DOUBLEUNDERLINE || type==TYPE_DOUBLESTRIKEOUT) {
                if (stroke instanceof BasicStroke) {
                    linewidth = ((BasicStroke)stroke).getLineWidth();
                }
                // Move linewidth*2 pixels perpendicular to vector (p1, p2)
                float vx = (float)(p2.getX()-p1.getX());
                float vy = (float)(p2.getY()-p1.getY());
                double len = linewidth * 2 / Math.sqrt(vx*vx+vy*vy);
                g.draw(AffineTransform.getTranslateInstance(vy*len, -vx*len).createTransformedShape(line));
            }
        }
    }

    /**
     * A Range represents a selected range of {@link org.faceless.pdf2.PageExtractor.Text} items.
     * The range may only cover a single page
     */
    public static abstract class Range {

        // Note this whole class has revealed itself to be a bodge, now the ordering issues
        // have surfaced. In hindsight we should have a sorted collection internally rather than
        // a first, last and ordering. However this API is referenced in the
        // TextSelectionActions so changing it may break things. The current design will work
        // and doesn't block any theoretical expansion to include RTL text selection.
        Comparator<PageExtractor.Text> order;

        /**
         * Return the {@link PDFPage} this range object relates to.
         */
        public PDFPage getPage() {
            return isValid() ? getFirst().getPage() : null;
        }

        /**
         * Return the first {@link org.faceless.pdf2.PageExtractor.Text} object that is selected
         */
        public abstract PageExtractor.Text getFirst();

        /**
         * Return the last {@link org.faceless.pdf2.PageExtractor.Text} object that is selected
         */
        public abstract PageExtractor.Text getLast();

        /**
         * Return offset into the Text item returned by {@link #getFirst} that
         * begins the selection.
         */
        public abstract int getFirstPosition();

        /**
         * Return offset into the Text item returned by {@link #getLast} that
         * ends the selection. Note this is the offset of the last selected character,
         * not the first unselected character.
         */
        public abstract int getLastPosition();

        /**
         * Return the text this Range covers
         */
        public StringBuffer getText() {
            return getFirst().getPageExtractor().getText(getFirst(), getFirstPosition(), getLast(), getLastPosition()+1, order);
        }

        /**
         * Return the text this Range covers
         */
        public AttributedString getStyledText() {
            return getFirst().getPageExtractor().getStyledText(getFirst(), getFirstPosition(), getLast(), getLastPosition()+1, order);
        }

        /**
         * Return true if this Range is valid, false otherwise
         */
        public abstract boolean isValid();

        /**
         * Create a new Range that matches the specified text item
         */
        public static Range createRange(PageExtractor.Text text) {
            return new FixedRange(text);
        }

        /**
         * Create a new Range that covers the range of text from <code>first</code>
         * character <code>firstposition</code>. to <code>last</code> character <code>lastposition</code>
         * inclusive.
         * @param first the first text
         * @param firstposition the offset into the first text to start at
         * @param last the last text
         * @param lastposition the offset into the last text to end at
         */
        public static Range createRange(PageExtractor.Text first, int firstposition, PageExtractor.Text last, int lastposition) {
            return new FixedRange(first, firstposition, last, lastposition);
        }

    }

    private static class FixedRange extends Range {
        private WeakReference<PageExtractor.Text> first, last;
        private int firstposition, lastposition;

        FixedRange(PageExtractor.Text text) {
            this (text, 0, text, text.getTextLength()-1);
        }

        FixedRange(PageExtractor.Text first, int firstposition, PageExtractor.Text last, int lastposition) {
            if (first.getPageExtractor() != last.getPageExtractor()) {
                throw new IllegalArgumentException("PageExtractor mismatch");
            }
            while (first.getPrimaryText()!=null) {
                firstposition += first.getPrimaryTextOffset();
                first = first.getPrimaryText();
            }
            while (last.getPrimaryText()!=null) {
                lastposition += last.getPrimaryTextOffset();
                last = last.getPrimaryText();
            }
            this.first = new WeakReference<PageExtractor.Text>(first);
            this.last = new WeakReference<PageExtractor.Text>(last);
            this.firstposition = firstposition;
            this.lastposition = lastposition;
        }

        public PageExtractor.Text getFirst() {
            return first.get();
        }

        public PageExtractor.Text getLast() {
            return last.get();
        }

        public int getFirstPosition() {
            return firstposition;
        }

        public int getLastPosition() {
            return lastposition;
        }

        boolean concatenate(FixedRange range) {
            if (getLast()==range.getFirst() && getLastPosition() + 1 <= range.getFirstPosition()) {
                last = range.last;
                lastposition = range.lastposition;
                return true;
            } else {
                return false;
            }
        }

        public boolean isValid() {
            return true;
        }

        public int hashCode() {
            PageExtractor.Text ff = first.get();
            PageExtractor.Text lf = last.get();
            return (firstposition<<20) ^ (lastposition<<10) ^ (ff==null?0:ff.hashCode()) ^ (lf==null?0:lf.hashCode());
        }

        public boolean equals(Object o) {
            if (o instanceof FixedRange) {
                FixedRange f = (FixedRange)o;
                PageExtractor.Text ff = first.get();
                PageExtractor.Text lf = last.get();

                return (ff == null ? f.first.get() == null : ff.equals(f.first.get())) && 
                       (lf == null ? f.last.get() == null : lf.equals(f.last.get())) && 
                       firstposition == f.firstposition &&
                       lastposition == f.lastposition;
            }
            return false;
        }
    }

    /**
     * A <code>RangeList</code> is a read-only list of <code>Range</code> objects.
     * The range may cover more than one page.
     */
    public static class RangeList extends AbstractList<Range> {

        private final List<? extends Range> list;
        private final Comparator<PageExtractor.Text> order;

        static final RangeList EMPTY_RANGELIST = new RangeList(null, Collections.<Range>emptyList(), PageExtractor.NATURALORDER);

        /**
         * Create a RangeList containing a single {@link Range}
         */
        public RangeList(DocumentPanel docpanel, Range range, Comparator<PageExtractor.Text> order) {
            this(docpanel, Collections.singletonList(range), order);
            range.order = order;
        }

        /**
         * Create a RangeList containing a list of {@link Range} objects
         * @param list the list of {@link Range} objects
         */
        public RangeList(final DocumentPanel docpanel, List<? extends Range> list, final Comparator<PageExtractor.Text> order) {
            if (list.size() > 1) {
                Collections.sort(list, new Comparator<Range>() {
                    public int compare(Range r1, Range r2) {
                        PDFPage page1 = r1.getPage();
                        PDFPage page2 = r2.getPage();
                        int v = page1 == page2 ? 0 : docpanel.getPageNumber(page1) - docpanel.getPageNumber(page2);
                        if (v == 0) {
                            v = order.compare(r1.getFirst(), r2.getFirst());
                            if (v == 0) {
                                v = r1.getFirstPosition() - r2.getFirstPosition();
                            }
                        }
                        return v;
                    }
                });
                Range range = list.get(0);
                range.order = order;
                for (int i=1;i<list.size();i++) {
                    Range range2 = list.get(i);
                    range2.order = order;
                    if (range instanceof FixedRange && range2 instanceof FixedRange) {
                        if (((FixedRange)range).concatenate((FixedRange)range2)) {
                            list.remove(i--);
                        } else {
                            range = range2;
                        }
                    }
                }
            }
            this.list = list;
            this.order = order;
        }

        public Range get(int index) {
            return list.get(index);
        }

        public int size() {
            return list.size();
        }

        /**
         * Return true if this list is not empty and every Range it contains is valid,
         * false otherwise
         */
        public boolean isValid() {
            boolean valid = list.size() > 0;
            for (int i=0;valid && i<list.size();i++) {
                valid &= list.get(i).isValid();
            }
            return valid;
        }

        /**
         * Return the list of Pages included with this Range
         * @since 2.11.8
         */
        public Set<PDFPage> getPages() {
            Set<PDFPage> set = new LinkedHashSet<PDFPage>();
            for (int i=0;i<list.size();i++) {
                Range range = list.get(i);
                set.add(range.getPage());
            }
            return set;
        }

        /**
         * Get the Corners of the area marked by this highlight. These are
         * in the same order as those returned by {@link org.faceless.pdf2.PageExtractor.Text#getCorners}
         * @param page the PDFPage that we want the corners for.
         */
        public float[] getCorners(PDFPage page) {
            List<Float> fcorners = new ArrayList<Float>();
            for (int i=0;i<list.size();i++) {
                Range range = list.get(i);
                if (range.getPage() == page) {
                    PageExtractor.Text first = range.getFirst();
                    PageExtractor.Text last = range.getLast();
                    int firstpos = range.getFirstPosition();
                    int lastpos = range.getLastPosition();
                    if (first == last) {
                        int len = lastpos - firstpos + 1;
                        if (len > 0) {
                            PageExtractor.Text sub = first.getSubText(firstpos, len);
                            float[] q = sub.getCorners();
                            for (int k=0;k<8;k++) {
                                fcorners.add(Float.valueOf(q[k]));
                            }
                        }
                    } else {
                        boolean inside = false;
                        PageExtractor extractor = first.getPageExtractor();
                        for (Iterator<? extends PageExtractor.Text> j = extractor.getText(order).iterator();j.hasNext();) {
                            PageExtractor.Text text = j.next();
                            if (text == first) {
                                inside = true;
                                int len = first.getTextLength() - firstpos;
                                if (len > 0) {
                                    PageExtractor.Text sub = first.getSubText(firstpos, len);
                                    float[] q = sub.getCorners();
                                    for (int k=0;k<8;k++) {
                                        fcorners.add(Float.valueOf(q[k]));
                                    }
                                }
                            } else if (text == last) {
                                inside = false;
                                PageExtractor.Text sub = last.getSubText(0, lastpos+1);
                                float[] q = sub.getCorners();
                                for (int k=0;k<8;k++) {
                                    fcorners.add(Float.valueOf(q[k]));
                                }
                            } else if (inside) {
                                float[] q = text.getCorners();
                                for (int k=0;k<8;k++) {
                                    fcorners.add(Float.valueOf(q[k]));
                                }
                            }
                        }
                    }
                }
            }
            float[] corners = new float[fcorners.size()];
            for (int i=0;i<corners.length;i++) {
                corners[i] = fcorners.get(i).floatValue();
            }
            return corners;
        }

        /**
         * Return the text included in this RangeList
         */
        public StringBuffer getText() {
            StringBuffer sb = new StringBuffer();
            PDFPage lastpage = null;
            for (int i=0;i<list.size();i++) {
                Range range = list.get(i);
                if (sb.length() > 0) {
                    sb.append(range.getPage() == lastpage ? ' ' : '\n');
                }
                sb.append(range.getText());
                lastpage = range.getPage();
            }
            return sb;
        }

        /**
         * Return the text included in this RangeList
         */
        public AttributedString getStyledText() {
            AttributedStringBuilder buf = new AttributedStringBuilder();
            PDFPage lastpage = null;
            for (int i=0;i<list.size();i++) {
                Range range = list.get(i);
                if (buf.length() > 0) {
                    buf.append(range.getPage() == lastpage ? " " : "\n");
                }
                buf.append(range.getStyledText());
                lastpage = range.getPage();
            }
            return buf.toAttributedString();
        }

        public String toString() {
            return list.toString();
        }

    }

}
